-- ============================================================
-- ANALYTICS VIEWS — zensyra_collector
-- collector-strava / strava analytics views
-- ============================================================

-- 1. Weekly activity summary
CREATE OR REPLACE VIEW v_weekly_activity_summary AS
SELECT
    a.strava_id,
    a.name,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS date,
    a.sport_type,
    ROUND(a.distance / 1000, 2) AS km,
    CONCAT(
        LPAD((a.moving_time / 3600)::text, 2, '0'), ':',
        LPAD(((a.moving_time % 3600) / 60)::text, 2, '0'), ':',
        LPAD((a.moving_time % 60)::text, 2, '0')
    ) AS moving_time,
    CONCAT(
        FLOOR(1000 / NULLIF(a.average_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(a.average_speed, 0)) % 60)::text, 2, '0')
    ) AS pace_min_per_km,
    a.average_heartrate AS avg_hr,
    a.max_heartrate AS max_hr,
    a.total_elevation_gain AS elevation_gain,
    a.calories,
    a.suffer_score,
    g.name AS shoe,
    g.brand_name AS shoe_brand
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC;

-- 2. Workout lap progression
CREATE OR REPLACE VIEW v_lap_progression AS
SELECT
    a.strava_id AS activity_strava_id,
    a.name AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS date,
    a.sport_type,
    l.lap_index + 1 AS lap,
    l.name AS lap_name,
    ROUND(l.distance / 1000, 3) AS km,
    CONCAT(
        LPAD((l.moving_time / 60)::text, 2, '0'), ':',
        LPAD((l.moving_time % 60)::text, 2, '0')
    ) AS time,
    CONCAT(
        FLOOR(1000 / NULLIF(l.average_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(l.average_speed, 0)) % 60)::text, 2, '0')
    ) AS pace_min_per_km,
    l.average_speed,
    l.average_heartrate AS avg_hr,
    l.max_heartrate AS max_hr,
    l.total_elevation_gain AS elevation_gain,
    l.pace_zone
FROM activity_laps l
JOIN activities a ON a.strava_id = l.activity_strava_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC, l.lap_index ASC;

-- 3. Pace degradation — first versus last lap
CREATE OR REPLACE VIEW v_lap_degradation AS
WITH first_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed AS start_speed,
        average_heartrate AS start_hr
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index ASC
),
last_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed AS end_speed,
        average_heartrate AS end_hr
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index DESC
)
SELECT
    a.strava_id,
    a.name AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS date,
    CONCAT(
        FLOOR(1000 / NULLIF(p.start_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(p.start_speed, 0)) % 60)::text, 2, '0')
    ) AS start_pace,
    CONCAT(
        FLOOR(1000 / NULLIF(u.end_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(u.end_speed, 0)) % 60)::text, 2, '0')
    ) AS end_pace,
    ROUND(((u.end_speed - p.start_speed) / NULLIF(p.start_speed, 0) * 100)::numeric, 1) AS speed_change_pct,
    ROUND(p.start_hr::numeric, 1) AS start_hr,
    ROUND(u.end_hr::numeric, 1) AS end_hr,
    ROUND((u.end_hr - p.start_hr)::numeric, 1) AS heart_rate_drift
FROM activities a
JOIN first_lap p ON p.activity_strava_id = a.strava_id
JOIN last_lap u ON u.activity_strava_id = a.strava_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC;

-- 4. Weekly shoe statistics
CREATE OR REPLACE VIEW v_weekly_gear_stats AS
SELECT
    g.strava_id AS gear_strava_id,
    g.name AS shoe,
    g.brand_name AS brand,
    g.model_name AS model,
    COUNT(a.id) AS sessions,
    ROUND(SUM(a.distance) / 1000, 2) AS weekly_total_km,
    ROUND(SUM(a.moving_time) / 3600.0, 2) AS total_hours,
    ROUND(AVG(a.average_heartrate), 1) AS global_avg_hr,
    ROUND(AVG(a.average_speed * 3.6), 2) AS avg_speed_kmh,
    SUM(a.calories) AS total_calories,
    ROUND(g.distance / 1000, 0) AS shoe_accumulated_km
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
GROUP BY g.strava_id, g.name, g.brand_name, g.model_name, g.distance
ORDER BY weekly_total_km DESC;

-- 5. Performance by time of day
CREATE OR REPLACE VIEW v_performance_by_time_of_day AS
SELECT
    CASE
        WHEN EXTRACT(HOUR FROM a.start_date AT TIME ZONE 'Europe/Madrid') < 12 THEN 'Morning'
        WHEN EXTRACT(HOUR FROM a.start_date AT TIME ZONE 'Europe/Madrid') < 17 THEN 'Midday'
        ELSE 'Afternoon'
    END AS time_of_day,
    COUNT(*) AS workouts,
    ROUND(AVG(a.distance) / 1000, 2) AS avg_km,
    ROUND(AVG(a.average_heartrate), 1) AS avg_hr,
    CONCAT(
        FLOOR(1000 / NULLIF(AVG(a.average_speed), 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(AVG(a.average_speed), 0)) % 60)::text, 2, '0')
    ) AS avg_pace_min_per_km,
    ROUND(AVG(a.suffer_score), 1) AS avg_suffer,
    ROUND(AVG(a.calories), 0) AS avg_calories
FROM activities a
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
GROUP BY time_of_day
ORDER BY time_of_day;
