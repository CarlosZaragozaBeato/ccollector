-- ============================================================
-- ZENSYRA COLLECTOR — STRAVA RUNNING ANALYTICS
-- Complete performance analytics for a running athlete
-- ============================================================
-- Structure:
--   [A] Utilities and estimated heart-rate zones
--   [B] All-time historical analysis
--   [C] Last N months analysis (rolling)
--   [D] Current month analysis
--   [E] Previous week analysis
--   [F] Current week analysis
-- ============================================================
-- CONVENTIONS:
--   - All dates are shown in Europe/Madrid.
--   - Pace uses MM:SS/km (calculated from average_speed in m/s).
--   - Estimated heart-rate zones use: max_hr = 220 - age (configurable).
--   - Only sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike') unless noted.
--   - trainer = false is filtered unless the analysis covers total load.
-- ============================================================


-- ============================================================
-- [A] UTILITIES — ESTIMATED HEART-RATE ZONES
-- ============================================================
-- INTERPRETATION:
--   Without the athlete's actual max HR, use an estimate of 220 - age.
--   Adjust the maximum heart-rate value for the actual athlete.
--   The zones follow a simplified five-zone Karvonen model:
--     Z1 Recovery       < 60% max HR
--     Z2 Aerobic base   60–70% max HR  ← key volume zone
--     Z3 Tempo          70–80% max HR
--     Z4 Threshold      80–90% max HR  ← quality zone
--     Z5 VO2max         > 90% FCmax
--
-- RECOMMENDATION: approximately 80% of volume should be in Z1-Z2 (polarized).
-- ============================================================

-- Helper function: classify heart rate into a zone (PostgreSQL).
-- Usage: SELECT fn_hr_zone(avg_hr, 185) AS zone
CREATE OR REPLACE FUNCTION fn_hr_zone(hr DECIMAL, max_hr DECIMAL)
RETURNS TEXT AS $$
BEGIN
    IF hr IS NULL OR max_hr IS NULL OR max_hr = 0 THEN RETURN 'No data'; END IF;
    IF hr < max_hr * 0.60 THEN RETURN 'Z1-Recovery';
    ELSIF hr < max_hr * 0.70 THEN RETURN 'Z2-Aerobic base';
    ELSIF hr < max_hr * 0.80 THEN RETURN 'Z3-Tempo';
    ELSIF hr < max_hr * 0.90 THEN RETURN 'Z4-Threshold';
    ELSE RETURN 'Z5-VO2max';
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Helper function: convert speed (m/s) to pace text (MM:SS/km).
CREATE OR REPLACE FUNCTION fn_pace_text(speed_ms DECIMAL)
RETURNS TEXT AS $$
DECLARE
    secs_per_km INTEGER;
BEGIN
    IF speed_ms IS NULL OR speed_ms = 0 THEN RETURN '--:--'; END IF;
    secs_per_km := ROUND(1000.0 / speed_ms);
    RETURN LPAD((secs_per_km / 60)::TEXT, 2, '0') || ':' || LPAD((secs_per_km % 60)::TEXT, 2, '0');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Helper function: seconds to HH:MM:SS.
CREATE OR REPLACE FUNCTION fn_duration_text(secs INTEGER)
RETURNS TEXT AS $$
BEGIN
    IF secs IS NULL THEN RETURN '--:--:--'; END IF;
    RETURN LPAD((secs / 3600)::TEXT, 2, '0') || ':' ||
           LPAD(((secs % 3600) / 60)::TEXT, 2, '0') || ':' ||
           LPAD((secs % 60)::TEXT, 2, '0');
END;
$$ LANGUAGE plpgsql IMMUTABLE;


-- ============================================================
-- [B] ALL-TIME HISTORICAL ANALYSIS
-- ============================================================


-- ------------------------------------------------------------
-- B1. Athlete annual summary (entire sporting career)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Macro view to see year-over-year progression.
--   Key metrics: volume (km), load (hours), efficiency (average pace versus HR).
--   The aerobic-efficiency index (speed/HR) should increase over time when the
--   athlete improves their aerobic base: more speed at the same cardiac cost.
--   A high average suffer_score with low distance means very intense, low-volume sessions.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_annual_summary AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS year,
    COUNT(*)                                                               AS sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)                                    AS total_km,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                 AS total_hours,
    fn_duration_text(ROUND(AVG(a.moving_time))::INTEGER)                  AS avg_session_duration,
    ROUND(AVG(a.distance) / 1000.0, 2)                                    AS avg_session_km,
    fn_pace_text(AVG(a.average_speed))                                    AS avg_pace,
    ROUND(AVG(a.average_heartrate), 1)                                    AS avg_hr,
    ROUND(AVG(a.max_heartrate), 1)                                        AS avg_max_hr,
    ROUND(AVG(a.total_elevation_gain), 0)                                 AS avg_elevation_gain,
    SUM(a.total_elevation_gain)                                           AS accumulated_elevation_gain,
    SUM(a.calories)                                                       AS total_calories,
    ROUND(AVG(a.suffer_score), 1)                                         AS avg_suffer,
    -- Aerobic-efficiency index: m/s per average heartbeat (higher is better).
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC, 5) AS aerobic_efficiency,
    -- Average sessions per week.
    ROUND(COUNT(*) / 52.0, 1)                                             AS avg_sessions_per_week
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
GROUP BY year
ORDER BY year DESC;


-- ------------------------------------------------------------
-- B2. Complete historical monthly progression
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Shows seasonal patterns (spring/autumn volume peaks typical of recreational
--   runners), injury periods (sharp distance drops), and average pace progression.
--   Comparing the same month across years is particularly informative.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_monthly_progression AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER  AS year,
    EXTRACT(MONTH FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS month,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMMonth')          AS month_name,
    COUNT(*)                                                                AS sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)                                     AS total_km,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                  AS total_hours,
    fn_pace_text(AVG(a.average_speed))                                     AS avg_pace,
    ROUND(AVG(a.average_heartrate), 1)                                     AS avg_hr,
    SUM(a.total_elevation_gain)                                            AS total_elevation_gain,
    SUM(a.calories)                                                        AS total_calories,
    ROUND(AVG(a.suffer_score), 1)                                          AS avg_suffer,
    -- Month-over-month efficiency progression.
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC, 5) AS aerobic_efficiency,
    -- Equivalent weekly volume (normalized to four weeks).
    ROUND(SUM(a.distance) / 1000.0 / 4.33, 1)                              AS equivalent_weekly_km
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
GROUP BY year, month, month_name
ORDER BY year DESC, month DESC;


-- ------------------------------------------------------------
-- B3. Historical best performances (estimated PRs by distance segment)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   There are no official race times, but activities allow an estimate of the
--   best performance for each distance range and its sustained pace.
--   This helps identify improvement or regression at each distance.
--   NOTE: Exact race distances (10K, 21K, 42K) use activities within ±5% of
--   the target distance.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_best_performances AS
WITH categorized AS (
    SELECT
        a.strava_id,
        a.name,
        a.start_date AT TIME ZONE 'Europe/Madrid' AS date,
        ROUND(a.distance / 1000.0, 2)             AS km,
        a.moving_time,
        fn_duration_text(a.moving_time)            AS total_time,
        a.average_speed,
        fn_pace_text(a.average_speed)              AS avg_pace,
        a.average_heartrate                        AS avg_hr,
        a.total_elevation_gain                     AS elevation_gain,
        CASE
            WHEN a.distance BETWEEN 4750 AND 5250   THEN '5K'
            WHEN a.distance BETWEEN 9500 AND 10500  THEN '10K'
            WHEN a.distance BETWEEN 14250 AND 15750 THEN '15K'
            WHEN a.distance BETWEEN 19000 AND 22000 THEN 'Half Marathon'
            WHEN a.distance BETWEEN 40000 AND 44000 THEN 'Marathon'
            WHEN a.distance > 44000                  THEN 'Ultra'
            WHEN a.distance BETWEEN 3000 AND 4749   THEN 'Short (<5K)'
            ELSE NULL
        END AS distance_category,
        ROW_NUMBER() OVER (
            PARTITION BY CASE
                WHEN a.distance BETWEEN 4750 AND 5250   THEN '5K'
                WHEN a.distance BETWEEN 9500 AND 10500  THEN '10K'
                WHEN a.distance BETWEEN 14250 AND 15750 THEN '15K'
                WHEN a.distance BETWEEN 19000 AND 22000 THEN 'Half Marathon'
                WHEN a.distance BETWEEN 40000 AND 44000 THEN 'Marathon'
                WHEN a.distance > 44000                  THEN 'Ultra'
            END
            ORDER BY a.average_speed DESC NULLS LAST
        ) AS speed_rank
    FROM activities a
    WHERE a.sport_type IN ('Run', 'TrailRun')
      AND a.trainer = false
      AND a.average_speed IS NOT NULL
)
SELECT
    distance_category,
    strava_id,
    name        AS activity,
    date,
    km,
    total_time,
    avg_pace AS best_pace,
    avg_hr,
    elevation_gain
FROM categorized
WHERE speed_rank = 1
  AND distance_category IS NOT NULL
ORDER BY
    CASE distance_category
        WHEN '5K' THEN 1 WHEN '10K' THEN 2 WHEN '15K' THEN 3
        WHEN 'Half Marathon' THEN 4 WHEN 'Marathon' THEN 5 WHEN 'Ultra' THEN 6
        ELSE 7
    END;


-- ------------------------------------------------------------
-- B4. Aerobic-efficiency progression (fitness trend)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Aerobic efficiency = average speed / average HR.
--   If it rises over time, the athlete runs faster at the same cardiac cost.
--   If it falls, it may indicate overload, accumulated fatigue, or lost fitness.
--   Grouping by quarter provides enough statistical signal.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_aerobic_efficiency AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER      AS year,
    EXTRACT(QUARTER FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER   AS quarter,
    CONCAT('Q', EXTRACT(QUARTER FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER,
           '-', EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER) AS period,
    COUNT(*)                                                                     AS sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)                                          AS total_km,
    fn_pace_text(AVG(a.average_speed))                                           AS avg_pace,
    ROUND(AVG(a.average_heartrate), 1)                                           AS avg_hr,
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS aerobic_efficiency_x1000,
    -- Average heart-rate drift (equivalent to v_lap_degradation).
    ROUND(AVG(a.suffer_score), 1)                                                AS avg_suffer
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.average_heartrate IS NOT NULL
  AND a.average_speed IS NOT NULL
GROUP BY year, quarter, period
ORDER BY year DESC, quarter DESC;


-- ============================================================
-- [C] ROLLING ANALYSIS — LAST N MONTHS
-- ============================================================
-- NOTE: The following views use a configurable INTERVAL.
--   Fixed views cover the last six months.
--   For other ranges, use the parameterized queries at the end of this file.
-- ============================================================


-- ------------------------------------------------------------
-- C1. Six-month rolling summary by month
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Detects medium-term volume and fatigue trends.
--   A month with high distance and low suffer is well absorbed.
--   A month with low distance and high suffer can indicate overtraining or races.
--   avg_sessions_per_week indicates adherence to the plan.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_rolling_6months_by_month AS
SELECT
    TO_CHAR(DATE_TRUNC('month', a.start_date AT TIME ZONE 'Europe/Madrid'), 'YYYY-MM') AS month,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMMonth YYYY')                 AS month_label,
    COUNT(*)                                                                            AS sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)                                                 AS total_km,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                              AS total_hours,
    fn_pace_text(AVG(a.average_speed))                                                 AS avg_pace,
    ROUND(AVG(a.average_heartrate), 1)                                                 AS avg_hr,
    SUM(a.total_elevation_gain)                                                        AS total_elevation_gain,
    SUM(a.calories)                                                                    AS total_calories,
    ROUND(AVG(a.suffer_score), 1)                                                      AS avg_suffer,
    -- Long-run ratio (>15 km) of the total.
    ROUND(
        COUNT(*) FILTER (WHERE a.distance > 15000)::DECIMAL / NULLIF(COUNT(*), 0) * 100, 1
    )                                                                                   AS pct_long_sessions,
    -- Quality sessions (estimated pace below 5:00/km → speed above 3.33 m/s).
    COUNT(*) FILTER (WHERE a.average_speed > 3.33)                                     AS quality_sessions,
    ROUND(COUNT(*) / 4.33, 1)                                                          AS avg_sessions_per_week
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= NOW() - INTERVAL '6 months'
GROUP BY month, month_label
ORDER BY month DESC;


-- ------------------------------------------------------------
-- C2. Fitness trend: pace versus HR (rolling efficiency)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Compares sustained pace and associated HR week by week.
--   Improving pace (lower) with stable or lower HR means rising fitness.
--   Worse pace (higher) with higher HR means accumulated fatigue.
--   Best displayed as a line chart in the frontend.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_rolling_weekly_fitness_trend AS
SELECT
    TO_CHAR(DATE_TRUNC('week', a.start_date AT TIME ZONE 'Europe/Madrid'), 'YYYY-"W"IW') AS week,
    DATE_TRUNC('week', a.start_date AT TIME ZONE 'Europe/Madrid')::DATE                   AS week_start,
    COUNT(*)                                                                               AS sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)                                                    AS total_km,
    fn_pace_text(AVG(a.average_speed))                                                     AS avg_pace,
    ROUND(AVG(a.average_heartrate), 1)                                                     AS avg_hr,
    -- Efficiency normalized by 1,000 for readability.
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS efficiency_x1000,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                                  AS total_hours,
    ROUND(AVG(a.suffer_score), 1)                                                          AS avg_suffer,
    -- Arbitrary training load (simplified TRIMP): duration_min * avg_hr.
    ROUND(SUM(a.moving_time / 60.0 * COALESCE(a.average_heartrate, 0)), 0)                AS estimated_trimp
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= NOW() - INTERVAL '6 months'
  AND a.average_heartrate IS NOT NULL
GROUP BY week, week_start
ORDER BY week_start DESC;


-- ------------------------------------------------------------
-- C3. Equipment status — shoe wear
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Accumulated distance on each shoe determines its useful life.
--   The recommended range is 600-800 km for most models.
--   wear_alert indicates when a shoe is approaching the limit.
--   Rotating between two or three shoes reduces injury risk.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_gear_wear_tracking AS
SELECT
    g.strava_id                                  AS gear_id,
    g.name                                       AS shoe,
    g.brand_name                                 AS brand,
    g.model_name                                 AS model,
    g.primary_gear                               AS primary_gear,
    g.retired                                    AS retired,
    ROUND(g.distance / 1000.0, 0)               AS total_accumulated_km,
    COUNT(a.id)                                  AS recent_sessions,
    ROUND(SUM(a.distance) / 1000.0, 1)          AS last_6_months_km,
    -- Percentage of useful life consumed (based on 700 km).
    ROUND(g.distance / 7000.0 * 100, 1)         AS pct_useful_life_consumed,
    CASE
        WHEN g.retired = true           THEN '⚫ Retired'
        WHEN g.distance > 700000        THEN '🔴 Replace now'
        WHEN g.distance > 550000        THEN '🟠 Near the limit'
        WHEN g.distance > 400000        THEN '🟡 Monitor'
        ELSE                                 '🟢 OK'
    END                                          AS wear_alert
FROM gears g
LEFT JOIN activities a ON a.gear_id = g.strava_id
    AND a.start_date >= NOW() - INTERVAL '6 months'
    AND a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
WHERE g.gear_type = 'shoes'
GROUP BY g.strava_id, g.name, g.brand_name, g.model_name,
         g.primary_gear, g.retired, g.distance
ORDER BY g.primary_gear DESC, g.distance DESC;


-- ============================================================
-- [D] CURRENT MONTH ANALYSIS
-- ============================================================


-- ------------------------------------------------------------
-- D1. Current-month KPI summary
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Snapshot of the current month.
--   Compare it with the historical monthly average of the previous 12 months
--   to determine whether it is above or below average.
--   The *_vs_avg columns show the percentage deviation.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_month_summary AS
WITH current_month AS (
    SELECT
        COUNT(*)                                                    AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 1)                         AS total_km,
        ROUND(SUM(a.moving_time) / 3600.0, 1)                      AS total_hours,
        AVG(a.average_speed)                                        AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                          AS avg_hr,
        SUM(a.total_elevation_gain)                                 AS total_elevation_gain,
        SUM(a.calories)                                             AS total_calories,
        ROUND(AVG(a.suffer_score), 1)                               AS avg_suffer,
        COUNT(*) FILTER (WHERE a.distance > 15000)                  AS long_sessions,
        COUNT(*) FILTER (WHERE a.average_speed > 3.33)              AS quality_sessions,
        -- Days with activity in the month.
        COUNT(DISTINCT DATE(a.start_date AT TIME ZONE 'Europe/Madrid')) AS active_days
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW())
),
avg_last_12 AS (
    SELECT
        ROUND(AVG(month_km), 1)       AS avg_km,
        ROUND(AVG(month_sessions), 1) AS avg_sessions,
        ROUND(AVG(month_hours), 1)    AS avg_hours
    FROM (
        SELECT
            DATE_TRUNC('month', a.start_date) AS month,
            COUNT(*)                          AS month_sessions,
            SUM(a.distance) / 1000.0          AS month_km,
            SUM(a.moving_time) / 3600.0       AS month_hours
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= NOW() - INTERVAL '12 months'
          AND a.start_date < DATE_TRUNC('month', NOW())
        GROUP BY month
    ) sub
)
SELECT
    TO_CHAR(NOW() AT TIME ZONE 'Europe/Madrid', 'TMMonth YYYY') AS current_month,
    cm.sessions,
    cm.total_km,
    cm.total_hours,
    fn_pace_text(cm.avg_speed)                                   AS avg_pace,
    cm.avg_hr,
    cm.total_elevation_gain,
    cm.total_calories,
    cm.avg_suffer,
    cm.long_sessions,
    cm.quality_sessions,
    cm.active_days,
    -- Comparison with the previous 12-month average.
    al.avg_km                                                     AS avg_km_12m,
    al.avg_sessions                                               AS avg_sessions_12m,
    ROUND((cm.total_km - al.avg_km) / NULLIF(al.avg_km, 0) * 100, 1) AS km_vs_avg_pct,
    ROUND((cm.sessions - al.avg_sessions) / NULLIF(al.avg_sessions, 0) * 100, 1) AS sessions_vs_avg_pct
FROM current_month cm, avg_last_12 al;


-- ------------------------------------------------------------
-- D2. Current month versus previous month comparison (day by day)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Shows whether the athlete is ahead of or behind last month at the same
--   point in the month. Compare current_cumulative_km and previous_cumulative_km.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_vs_last_month_daily AS
WITH current_m AS (
    SELECT
        EXTRACT(DAY FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS day,
        COUNT(*)                                   AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 2)        AS day_km
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW())
    GROUP BY day
),
prev_m AS (
    SELECT
        EXTRACT(DAY FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS day,
        COUNT(*)                                   AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 2)        AS day_km
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW()) - INTERVAL '1 month'
      AND a.start_date  < DATE_TRUNC('month', NOW())
    GROUP BY day
),
days AS (SELECT generate_series(1, 31) AS day)
SELECT
    d.day,
    COALESCE(cm.sessions, 0)  AS current_month_sessions,
    COALESCE(cm.day_km, 0)    AS current_month_km,
    COALESCE(pm.sessions, 0)  AS previous_month_sessions,
    COALESCE(pm.day_km, 0)    AS previous_month_km,
    -- Running totals.
    SUM(COALESCE(cm.day_km, 0)) OVER (ORDER BY d.day)  AS current_cumulative_km,
    SUM(COALESCE(pm.day_km, 0)) OVER (ORDER BY d.day)  AS previous_cumulative_km
FROM days d
LEFT JOIN current_m cm ON cm.day = d.day
LEFT JOIN prev_m    pm ON pm.day  = d.day
ORDER BY d.day;


-- ------------------------------------------------------------
-- D3. Current-month sessions — individual detail
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Complete list of the month's sessions with all KPIs.
--   Identifies high-quality sessions, pace progression, and anomalous HR.
--   suffer_score > 100 means a highly demanding session; avoid stacking many.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_month_sessions AS
SELECT
    a.strava_id,
    a.name                                                          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS date,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'Day')       AS day_of_week,
    a.sport_type,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS moving_time,
    fn_pace_text(a.average_speed)                                   AS avg_pace,
    fn_pace_text(a.max_speed)                                       AS max_pace,
    a.average_heartrate                                             AS avg_hr,
    a.max_heartrate                                                 AS max_hr,
    a.total_elevation_gain                                          AS elevation_gain,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS perceived_exertion,
    a.device_name                                                   AS device,
    g.name                                                          AS shoe,
    -- Intensity classification by suffer_score.
    CASE
        WHEN a.suffer_score < 25  THEN 'Recovery'
        WHEN a.suffer_score < 50  THEN 'Easy'
        WHEN a.suffer_score < 100 THEN 'Moderate'
        WHEN a.suffer_score < 150 THEN 'Hard'
        ELSE                           'Very hard'
    END                                                             AS estimated_intensity,
    -- Is this a long run? (>15 km)
    CASE WHEN a.distance > 15000 THEN 'Yes' ELSE 'No' END          AS is_long_run,
    -- Number of recorded laps.
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS lap_count
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('month', NOW())
ORDER BY a.start_date DESC;


-- ============================================================
-- [E] PREVIOUS WEEK ANALYSIS — COMPLETE DETAIL
-- ============================================================


-- ------------------------------------------------------------
-- E1. Previous-week summary compared with the prior four weeks
-- ------------------------------------------------------------
-- INTERPRETATION:
--   The previous week is the most recent with complete data.
--   Comparing its average with the prior four weeks identifies a loading,
--   recovery, or normal week. A typical loading week has 20–30% more distance.
--   A recovery week (tapering) should be around 50–60% of normal volume.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_summary AS
WITH last_week_data AS (
    SELECT
        COUNT(*)                                                     AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 1)                          AS total_km,
        ROUND(SUM(a.moving_time) / 3600.0, 2)                       AS total_hours,
        AVG(a.average_speed)                                         AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                           AS avg_hr,
        SUM(a.total_elevation_gain)                                  AS total_elevation_gain,
        SUM(a.calories)                                              AS total_calories,
        ROUND(AVG(a.suffer_score), 1)                                AS avg_suffer,
        COUNT(*) FILTER (WHERE a.distance > 15000)                   AS long_sessions,
        ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS efficiency
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
      AND a.start_date  < DATE_TRUNC('week', NOW())
),
prev_4w_avg AS (
    SELECT
        ROUND(AVG(w_km), 1)      AS avg_km,
        ROUND(AVG(w_sessions), 1) AS avg_sessions,
        ROUND(AVG(w_suffer), 1)  AS avg_suffer
    FROM (
        SELECT
            DATE_TRUNC('week', a.start_date) AS week,
            COUNT(*)                          AS w_sessions,
            SUM(a.distance) / 1000.0          AS w_km,
            AVG(a.suffer_score)               AS w_suffer
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '5 weeks'
          AND a.start_date  < DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
        GROUP BY week
    ) sub
)
SELECT
    TO_CHAR(DATE_TRUNC('week', NOW()) - INTERVAL '1 week', 'DD/MM/YYYY') AS week_start,
    TO_CHAR(DATE_TRUNC('week', NOW()) - INTERVAL '1 day',  'DD/MM/YYYY') AS week_end,
    lw.sessions,
    lw.total_km,
    lw.total_hours,
    fn_pace_text(lw.avg_speed)                                            AS avg_pace,
    lw.avg_hr,
    lw.total_elevation_gain,
    lw.total_calories,
    lw.avg_suffer,
    lw.long_sessions,
    lw.efficiency                                                         AS efficiency_x1000,
    -- Comparison with the prior four-week average.
    p4.avg_km                                                             AS avg_km_4_weeks,
    p4.avg_sessions                                                       AS avg_sessions_4_weeks,
    p4.avg_suffer                                                         AS avg_suffer_4_weeks,
    ROUND((lw.total_km - p4.avg_km) / NULLIF(p4.avg_km, 0) * 100, 1) AS km_vs_4_week_avg_pct,
    -- Inferred week type.
    CASE
        WHEN (lw.total_km - p4.avg_km) / NULLIF(p4.avg_km, 0) > 0.20  THEN '⬆ Loading week'
        WHEN (lw.total_km - p4.avg_km) / NULLIF(p4.avg_km, 0) < -0.30 THEN '⬇ Recovery week / taper'
        ELSE '↔ Standard week'
    END                                                                   AS week_type
FROM last_week_data lw, prev_4w_avg p4;


-- ------------------------------------------------------------
-- E2. Previous-week session details
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Equivalent to v_current_month_sessions, limited to the previous week.
--   Allows reviewing the quality of completed work session by session.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_sessions AS
SELECT
    a.strava_id,
    a.name                                                          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS date,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMDay')     AS day_of_week,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS moving_time,
    fn_pace_text(a.average_speed)                                   AS avg_pace,
    fn_pace_text(a.max_speed)                                       AS max_pace,
    a.average_heartrate                                             AS avg_hr,
    a.max_heartrate                                                 AS max_hr,
    a.total_elevation_gain                                          AS elevation_gain,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS perceived_exertion,
    g.name                                                          AS shoe,
    CASE
        WHEN a.suffer_score < 25  THEN 'Recovery'
        WHEN a.suffer_score < 50  THEN 'Easy'
        WHEN a.suffer_score < 100 THEN 'Moderate'
        WHEN a.suffer_score < 150 THEN 'Hard'
        ELSE                           'Very hard'
    END                                                             AS estimated_intensity,
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS lap_count
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC;


-- ------------------------------------------------------------
-- E3. Previous-week session laps (lap-level detail)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Lap analysis is the most granular view.
--   It shows whether the athlete holds or loses pace during the session. Stable
--   or negative pace (accelerating at the end) indicates good effort management.
--   speed_delta_vs_first_lap_pct measures accumulated degradation.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_lap_detail AS
WITH first_lap_speed AS (
    SELECT
        activity_strava_id,
        average_speed AS first_lap_speed
    FROM activity_laps
    WHERE lap_index = 0
)
SELECT
    a.strava_id     AS activity_id,
    a.name          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'   AS activity_date,
    l.lap_index + 1                             AS lap,
    l.name                                      AS lap_name,
    ROUND(l.distance / 1000.0, 3)              AS km,
    fn_duration_text(l.moving_time)             AS time,
    fn_pace_text(l.average_speed)               AS pace,
    l.average_heartrate                         AS avg_hr,
    l.max_heartrate                             AS max_hr,
    l.total_elevation_gain                      AS elevation_gain,
    l.pace_zone,
    -- Speed delta versus the first lap: + means slower, - means faster.
    ROUND(
        (NULLIF(p.first_lap_speed, 0) - l.average_speed)
        / NULLIF(p.first_lap_speed, 0) * 100
    , 1)                                        AS speed_delta_vs_first_lap_pct,
    fn_pace_text(p.first_lap_speed)             AS first_lap_pace_ref
FROM activity_laps l
JOIN activities a       ON a.strava_id = l.activity_strava_id
JOIN first_lap_speed p ON p.activity_strava_id = l.activity_strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC, l.lap_index ASC;


-- ------------------------------------------------------------
-- E4. Pace degradation and heart-rate drift by session (previous week)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Extended version of the existing v_lap_degradation.
--   speed_change_pct > 0 means the athlete finished faster (a negative split).
--   speed_change_pct < 0 means the athlete slowed down (a positive split).
--   heart_rate_drift > 10 bpm at medium distances may indicate cardiovascular fatigue.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_degradation AS
WITH first_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed      AS start_speed,
        average_heartrate  AS start_hr
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index ASC
),
last_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed      AS end_speed,
        average_heartrate  AS end_hr
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index DESC
)
SELECT
    a.strava_id,
    a.name                                          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'        AS date,
    ROUND(a.distance / 1000.0, 2)                   AS km_total,
    fn_pace_text(p.start_speed)                      AS start_pace,
    fn_pace_text(u.end_speed)                        AS end_pace,
    ROUND(((u.end_speed - p.start_speed) / NULLIF(p.start_speed, 0) * 100)::NUMERIC, 1) AS speed_change_pct,
    ROUND(p.start_hr::NUMERIC, 1)                    AS start_hr,
    ROUND(u.end_hr::NUMERIC, 1)                      AS end_hr,
    ROUND((u.end_hr - p.start_hr)::NUMERIC, 1)       AS heart_rate_drift,
    -- Pace-management quality.
    CASE
        WHEN ((u.end_speed - p.start_speed) / NULLIF(p.start_speed, 0) * 100) > 2
             THEN '✅ Negative split (accelerated)'
        WHEN ((u.end_speed - p.start_speed) / NULLIF(p.start_speed, 0) * 100) BETWEEN -2 AND 2
             THEN '↔ Constant pace'
        WHEN ((u.end_speed - p.start_speed) / NULLIF(p.start_speed, 0) * 100) BETWEEN -10 AND -2
             THEN '⚠ Slight degradation'
        ELSE '🔴 Severe degradation'
    END                                             AS pace_management,
    -- Heart-rate drift alert.
    CASE
        WHEN (u.end_hr - p.start_hr) < 5   THEN '✅ Stable HR'
        WHEN (u.end_hr - p.start_hr) < 10  THEN '🟡 Moderate drift'
        ELSE                                     '🔴 High heart-rate drift'
    END                                             AS heart_rate_alert
FROM activities a
JOIN first_lap p ON p.activity_strava_id = a.strava_id
JOIN last_lap u ON u.activity_strava_id = a.strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date DESC;


-- ============================================================
-- [F] CURRENT WEEK ANALYSIS — REAL TIME
-- ============================================================


-- ------------------------------------------------------------
-- F1. Real-time current-week summary
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Dashboard for the current week.
--   km_remaining_to_target estimates distance needed to reach the average of
--   the last four weeks. days_remaining indicates the time left in the week.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_summary AS
WITH this_week AS (
    SELECT
        COUNT(*)                                                     AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 1)                          AS total_km,
        ROUND(SUM(a.moving_time) / 3600.0, 2)                       AS total_hours,
        AVG(a.average_speed)                                         AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                           AS avg_hr,
        SUM(a.total_elevation_gain)                                  AS total_elevation_gain,
        SUM(a.calories)                                              AS total_calories,
        ROUND(AVG(a.suffer_score), 1)                                AS avg_suffer,
        -- Estimated TRIMP: total weekly load.
        ROUND(SUM(a.moving_time / 60.0 * COALESCE(a.average_heartrate, 0)), 0) AS weekly_trimp
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW())
),
avg_4w AS (
    SELECT ROUND(AVG(w_km), 1) AS avg_km
    FROM (
        SELECT DATE_TRUNC('week', a.start_date) AS week, SUM(a.distance) / 1000.0 AS w_km
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '4 weeks'
          AND a.start_date  < DATE_TRUNC('week', NOW())
        GROUP BY week
    ) sub
)
SELECT
    TO_CHAR(DATE_TRUNC('week', NOW()), 'DD/MM/YYYY')  AS week_start,
    TO_CHAR(NOW() AT TIME ZONE 'Europe/Madrid', 'DD/MM/YYYY HH24:MI') AS updated_at,
    tw.sessions,
    tw.total_km,
    tw.total_hours,
    fn_pace_text(tw.avg_speed)                         AS avg_pace,
    tw.avg_hr,
    tw.total_elevation_gain,
    tw.total_calories,
    tw.avg_suffer,
    tw.weekly_trimp,
    a4.avg_km                                          AS reference_km_target,
    GREATEST(ROUND(a4.avg_km - tw.total_km, 1), 0)    AS km_remaining_to_target,
    -- Elapsed and remaining days in the week (Monday = 1).
    EXTRACT(ISODOW FROM NOW())::INTEGER                AS current_weekday,
    7 - EXTRACT(ISODOW FROM NOW())::INTEGER            AS remaining_week_days
FROM this_week tw, avg_4w a4;


-- ------------------------------------------------------------
-- F2. Day-by-day load for the current week
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Load distribution within the current week.
--   Ideally, hard days alternate with easy days.
--   A Monday-to-Sunday suffer_score above 300 indicates a high-load week.
--   is_rest_day identifies days without activity, which are needed for recovery.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_daily_load AS
WITH week_days AS (
    SELECT
        generate_series(
            DATE_TRUNC('week', NOW()),
            DATE_TRUNC('week', NOW()) + INTERVAL '6 days',
            INTERVAL '1 day'
        )::DATE AS day
),
day_activities AS (
    SELECT
        DATE(a.start_date AT TIME ZONE 'Europe/Madrid')          AS day,
        COUNT(*)                                                   AS sessions,
        ROUND(SUM(a.distance) / 1000.0, 2)                        AS km,
        ROUND(SUM(a.moving_time) / 60.0, 0)                       AS minutes,
        ROUND(AVG(a.average_heartrate), 1)                         AS avg_hr,
        SUM(a.suffer_score)                                        AS cumulative_suffer,
        SUM(a.calories)                                            AS calories,
        STRING_AGG(a.name, ' | ' ORDER BY a.start_date)           AS session_names
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW())
    GROUP BY day
)
SELECT
    ds.day,
    TO_CHAR(ds.day, 'TMDay')                           AS day_name,
    COALESCE(ad.sessions, 0)                           AS sessions,
    COALESCE(ad.km, 0)                                 AS km,
    COALESCE(ad.minutes, 0)                            AS total_minutes,
    COALESCE(ad.avg_hr, 0)                             AS avg_hr,
    COALESCE(ad.cumulative_suffer, 0)                  AS cumulative_suffer,
    COALESCE(ad.calories, 0)                           AS calories,
    COALESCE(ad.session_names, '— Rest —')             AS session_names,
    CASE WHEN ad.sessions IS NULL THEN true ELSE false END AS is_rest_day,
    -- Daily load.
    CASE
        WHEN ad.cumulative_suffer IS NULL       THEN '💤 Rest'
        WHEN ad.cumulative_suffer < 25          THEN '🔵 Recovery'
        WHEN ad.cumulative_suffer < 75          THEN '🟢 Easy'
        WHEN ad.cumulative_suffer < 150         THEN '🟡 Moderate'
        WHEN ad.cumulative_suffer < 250         THEN '🟠 Hard'
        ELSE                                         '🔴 Very hard'
    END                                                AS daily_load
FROM week_days ds
LEFT JOIN day_activities ad ON ad.day = ds.day
ORDER BY ds.day ASC;


-- ------------------------------------------------------------
-- F3. Current-week session detail with laps
-- ------------------------------------------------------------
-- INTERPRETATION:
--   The most granular view of the current week.
--   Shows every session and its lap breakdown.
--   Allows real-time evaluation of the most recent session.
--   If lap_count = 0, the activity has no recorded laps.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_session_detail AS
SELECT
    a.strava_id,
    a.name                                                          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS date,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMDay DD/MM') AS day,
    a.sport_type,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS moving_time,
    fn_pace_text(a.average_speed)                                   AS avg_pace,
    fn_pace_text(a.max_speed)                                       AS max_pace,
    a.average_heartrate                                             AS avg_hr,
    a.max_heartrate                                                 AS max_hr,
    a.total_elevation_gain                                          AS elevation_gain,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS rpe,
    a.description,
    g.name                                                          AS shoe,
    g.brand_name || ' ' || COALESCE(g.model_name, '')               AS shoe_model,
    ROUND((AVG(a.average_speed) OVER ()) / NULLIF(a.average_heartrate, 0) * 1000, 3) AS efficiency_vs_week,
    CASE
        WHEN a.suffer_score < 25  THEN 'Recovery'
        WHEN a.suffer_score < 50  THEN 'Easy'
        WHEN a.suffer_score < 100 THEN 'Moderate'
        WHEN a.suffer_score < 150 THEN 'Hard'
        ELSE                           'Very hard'
    END                                                             AS intensity,
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS lap_count,
    -- Best lap of the session (the fastest).
    (SELECT fn_pace_text(MAX(l2.average_speed))
     FROM activity_laps l2 WHERE l2.activity_strava_id = a.strava_id)               AS best_lap_pace
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW())
ORDER BY a.start_date DESC;


-- ------------------------------------------------------------
-- F4. Current-week laps (maximum granularity)
-- ------------------------------------------------------------
-- INTERPRETATION:
--   Equivalent to v_last_week_lap_detail for the current week.
--   speed_delta_vs_first_lap_pct: positive = slower, negative = faster.
--   Useful for near-real-time analysis of the most recent session.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_lap_detail AS
WITH first_lap_speed AS (
    SELECT activity_strava_id, average_speed AS first_lap_speed
    FROM activity_laps
    WHERE lap_index = 0
)
SELECT
    a.strava_id     AS activity_id,
    a.name          AS activity,
    a.start_date AT TIME ZONE 'Europe/Madrid'   AS activity_date,
    l.lap_index + 1                             AS lap,
    ROUND(l.distance / 1000.0, 3)              AS km,
    fn_duration_text(l.moving_time)             AS time,
    fn_pace_text(l.average_speed)               AS pace,
    fn_pace_text(l.max_speed)                   AS max_lap_pace,
    l.average_heartrate                         AS avg_hr,
    l.max_heartrate                             AS max_hr,
    l.total_elevation_gain                      AS elevation_gain,
    l.pace_zone,
    ROUND(
        (NULLIF(p.first_lap_speed, 0) - l.average_speed)
        / NULLIF(p.first_lap_speed, 0) * 100
    , 1)                                        AS speed_delta_vs_first_lap_pct
FROM activity_laps l
JOIN activities a       ON a.strava_id = l.activity_strava_id
JOIN first_lap_speed p ON p.activity_strava_id = l.activity_strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC, l.lap_index ASC;


-- ============================================================
-- PARAMETERIZED QUERIES — for backend or tool use
-- ============================================================
-- Replace values between << >> with actual parameters.
-- Use a PreparedStatement or query builder in Java/TypeScript.
-- ============================================================

-- QP-1: Historical N-month lookback (replaces '6 months')
-- SELECT * FROM v_historic_monthly_progression
-- WHERE year * 100 + month >= EXTRACT(YEAR FROM NOW() - INTERVAL '<<N>> months')::INT * 100
--                         + EXTRACT(MONTH FROM NOW() - INTERVAL '<<N>> months')::INT
-- ORDER BY year DESC, month DESC;

-- QP-2: Parameterized rolling weekly summary
-- SELECT * FROM v_rolling_weekly_fitness_trend
-- WHERE week_start >= NOW() - INTERVAL '<<N>> weeks';

-- QP-3: Specific session and its laps (by strava_id)
-- SELECT a.*, l.*
-- FROM activities a
-- LEFT JOIN activity_laps l ON l.activity_strava_id = a.strava_id
-- WHERE a.strava_id = <<STRAVA_ID>>
-- ORDER BY l.lap_index ASC;

-- QP-4: Performance comparison between two dates
-- SELECT
--     fn_pace_text(AVG(a.average_speed)) AS avg_pace,
--     ROUND(AVG(a.average_heartrate), 1) AS avg_hr,
--     ROUND(SUM(a.distance) / 1000.0, 1) AS total_km,
--     ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS efficiency
-- FROM activities a
-- WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
--   AND a.start_date BETWEEN '<<START_DATE>>' AND '<<END_DATE>>';

-- QP-5: Streak detector (consecutive running days)
-- WITH days AS (
--     SELECT DISTINCT DATE(start_date AT TIME ZONE 'Europe/Madrid') AS day
--     FROM activities
--     WHERE sport_type IN ('Run','VirtualRun','TrailRun')
-- ),
-- groups AS (
--     SELECT day, day - ROW_NUMBER() OVER (ORDER BY day) * INTERVAL '1 day' AS grp
--     FROM days
-- )
-- SELECT MIN(day) AS streak_start, MAX(day) AS streak_end, COUNT(*) AS consecutive_days
-- FROM groups
-- GROUP BY grp
-- ORDER BY consecutive_days DESC
-- LIMIT 10;


-- ============================================================
-- END OF FILE
-- ============================================================
-- Views created: 17
-- Helper functions: 3 (fn_hr_zone, fn_pace_text, fn_duration_text)
-- Parameterized queries: 5
-- ============================================================
