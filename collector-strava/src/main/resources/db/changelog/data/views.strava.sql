-- ============================================================
-- VISTAS DE ANÁLISIS — zensyra_collector
-- collector-strava / strava analytics views
-- ============================================================

-- 1. Resumen semanal por actividad
CREATE OR REPLACE VIEW v_weekly_activity_summary AS
SELECT
    a.strava_id,
    a.name,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS fecha,
    a.sport_type,
    ROUND(a.distance / 1000, 2) AS km,
    CONCAT(
        LPAD((a.moving_time / 3600)::text, 2, '0'), ':',
        LPAD(((a.moving_time % 3600) / 60)::text, 2, '0'), ':',
        LPAD((a.moving_time % 60)::text, 2, '0')
    ) AS tiempo_movimiento,
    CONCAT(
        FLOOR(1000 / NULLIF(a.average_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(a.average_speed, 0)) % 60)::text, 2, '0')
    ) AS ritmo_min_km,
    a.average_heartrate AS fc_media,
    a.max_heartrate AS fc_max,
    a.total_elevation_gain AS desnivel,
    a.calories,
    a.suffer_score,
    g.name AS zapatilla,
    g.brand_name AS marca_zapatilla
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC;

-- 2. Progresión de laps por entreno
CREATE OR REPLACE VIEW v_lap_progression AS
SELECT
    a.strava_id AS activity_strava_id,
    a.name AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS fecha,
    a.sport_type,
    l.lap_index + 1 AS lap,
    l.name AS nombre_lap,
    ROUND(l.distance / 1000, 3) AS km,
    CONCAT(
        LPAD((l.moving_time / 60)::text, 2, '0'), ':',
        LPAD((l.moving_time % 60)::text, 2, '0')
    ) AS tiempo,
    CONCAT(
        FLOOR(1000 / NULLIF(l.average_speed, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(l.average_speed, 0)) % 60)::text, 2, '0')
    ) AS ritmo_min_km,
    l.average_speed,
    l.average_heartrate AS fc_media,
    l.max_heartrate AS fc_max,
    l.total_elevation_gain AS desnivel,
    l.pace_zone AS zona_ritmo
FROM activity_laps l
JOIN activities a ON a.strava_id = l.activity_strava_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC, l.lap_index ASC;

-- 3. Degradación de ritmo — primer vs último lap
CREATE OR REPLACE VIEW v_lap_degradation AS
WITH primer_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed AS velocidad_inicio,
        average_heartrate AS fc_inicio
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index ASC
),
ultimo_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed AS velocidad_fin,
        average_heartrate AS fc_fin
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index DESC
)
SELECT
    a.strava_id,
    a.name AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid' AS fecha,
    CONCAT(
        FLOOR(1000 / NULLIF(p.velocidad_inicio, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(p.velocidad_inicio, 0)) % 60)::text, 2, '0')
    ) AS ritmo_inicio,
    CONCAT(
        FLOOR(1000 / NULLIF(u.velocidad_fin, 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(u.velocidad_fin, 0)) % 60)::text, 2, '0')
    ) AS ritmo_fin,
    ROUND(((u.velocidad_fin - p.velocidad_inicio) / NULLIF(p.velocidad_inicio, 0) * 100)::numeric, 1) AS variacion_velocidad_pct,
    ROUND(p.fc_inicio::numeric, 1) AS fc_inicio,
    ROUND(u.fc_fin::numeric, 1) AS fc_fin,
    ROUND((u.fc_fin - p.fc_inicio)::numeric, 1) AS deriva_cardiaca
FROM activities a
JOIN primer_lap p ON p.activity_strava_id = a.strava_id
JOIN ultimo_lap u ON u.activity_strava_id = a.strava_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
ORDER BY a.start_date DESC;

-- 4. Estadísticas semanales por zapatilla
CREATE OR REPLACE VIEW v_weekly_gear_stats AS
SELECT
    g.strava_id AS gear_strava_id,
    g.name AS zapatilla,
    g.brand_name AS marca,
    g.model_name AS modelo,
    COUNT(a.id) AS sesiones,
    ROUND(SUM(a.distance) / 1000, 2) AS km_totales_semana,
    ROUND(SUM(a.moving_time) / 3600.0, 2) AS horas_totales,
    ROUND(AVG(a.average_heartrate), 1) AS fc_media_global,
    ROUND(AVG(a.average_speed * 3.6), 2) AS velocidad_media_kmh,
    SUM(a.calories) AS calorias_totales,
    ROUND(g.distance / 1000, 0) AS km_acumulados_zapatilla
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
GROUP BY g.strava_id, g.name, g.brand_name, g.model_name, g.distance
ORDER BY km_totales_semana DESC;

-- 5. Rendimiento por franja horaria
CREATE OR REPLACE VIEW v_performance_by_time_of_day AS
SELECT
    CASE
        WHEN EXTRACT(HOUR FROM a.start_date AT TIME ZONE 'Europe/Madrid') < 12 THEN 'Mañana'
        WHEN EXTRACT(HOUR FROM a.start_date AT TIME ZONE 'Europe/Madrid') < 17 THEN 'Mediodía'
        ELSE 'Tarde'
    END AS franja,
    COUNT(*) AS entrenos,
    ROUND(AVG(a.distance) / 1000, 2) AS km_medio,
    ROUND(AVG(a.average_heartrate), 1) AS fc_media,
    CONCAT(
        FLOOR(1000 / NULLIF(AVG(a.average_speed), 0) / 60)::int, ':',
        LPAD((FLOOR(1000 / NULLIF(AVG(a.average_speed), 0)) % 60)::text, 2, '0')
    ) AS ritmo_medio_min_km,
    ROUND(AVG(a.suffer_score), 1) AS suffer_medio,
    ROUND(AVG(a.calories), 0) AS calorias_medio
FROM activities a
WHERE a.start_date >= date_trunc('week', now()) - INTERVAL '1 week'
  AND a.start_date < date_trunc('week', now())
GROUP BY franja
ORDER BY franja;