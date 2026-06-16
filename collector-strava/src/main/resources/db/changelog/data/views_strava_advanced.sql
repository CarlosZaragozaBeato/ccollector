-- ============================================================
-- ZENSYRA COLLECTOR — STRAVA RUNNING ANALYTICS
-- Análisis completo de rendimiento para atleta de running
-- ============================================================
-- Estructura:
--   [A] Utilidades y zonas FC estimadas
--   [B] Análisis histórico total
--   [C] Análisis de últimos N meses (rolling)
--   [D] Análisis del mes actual
--   [E] Análisis semana anterior
--   [F] Análisis semana actual
-- ============================================================
-- CONVENCIONES:
--   - Todas las fechas se muestran en Europe/Madrid
--   - Ritmo en formato MM:SS /km (calculado desde average_speed en m/s)
--   - Zonas FC estimadas con fórmula: FC_max = 220 - edad (configurable)
--   - Solo sport_type IN ('Run','VirtualRun','TrailRun','Hike') salvo se indique
--   - Se filtra trainer = false salvo análisis de carga total
-- ============================================================


-- ============================================================
-- [A] UTILIDADES — ZONAS DE FRECUENCIA CARDÍACA ESTIMADAS
-- ============================================================
-- INTERPRETACIÓN:
--   Sin FC máx real del atleta, estimamos 220 - edad.
--   Ajusta el valor de fc_max_estimada según el atleta real.
--   Las zonas siguen el modelo de 5 zonas de Karvonen simplificado:
--     Z1 Recuperación   < 60% FCmax
--     Z2 Base aeróbica  60–70% FCmax   ← zona clave de volumen
--     Z3 Tempo          70–80% FCmax
--     Z4 Umbral         80–90% FCmax   ← zona de calidad
--     Z5 VO2max         > 90% FCmax
--
-- RECOMENDACIÓN: ~80% del volumen debería estar en Z1-Z2 (polarizado).
-- ============================================================

-- Función auxiliar: clasificar FC en zona (PostgreSQL)
-- Uso: SELECT fn_fc_zone(fc_media, 185) AS zona
CREATE OR REPLACE FUNCTION fn_fc_zone(fc DECIMAL, fc_max DECIMAL)
RETURNS TEXT AS $$
BEGIN
    IF fc IS NULL OR fc_max IS NULL OR fc_max = 0 THEN RETURN 'Sin dato'; END IF;
    IF fc < fc_max * 0.60 THEN RETURN 'Z1-Recuperación';
    ELSIF fc < fc_max * 0.70 THEN RETURN 'Z2-Base';
    ELSIF fc < fc_max * 0.80 THEN RETURN 'Z3-Tempo';
    ELSIF fc < fc_max * 0.90 THEN RETURN 'Z4-Umbral';
    ELSE RETURN 'Z5-VO2max';
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Función auxiliar: convertir velocidad (m/s) → ritmo texto (MM:SS/km)
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

-- Función auxiliar: segundos → HH:MM:SS
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
-- [B] ANÁLISIS HISTÓRICO TOTAL
-- ============================================================


-- ------------------------------------------------------------
-- B1. Resumen anual del atleta (toda la carrera deportiva)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Vista macro para ver la evolución año a año.
--   Métricas clave: volumen (km), carga (horas), eficiencia (ritmo medio vs FC).
--   El índice de eficiencia aeróbica (velocidad/FC) debe crecer con los años
--   si el atleta mejora su base aeróbica — más velocidad al mismo coste cardíaco.
--   Un suffer_score medio alto con poco km → sesiones muy intensas pero escaso volumen.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_annual_summary AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS anio,
    COUNT(*)                                                                AS sesiones,
    ROUND(SUM(a.distance) / 1000.0, 1)                                     AS km_totales,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                  AS horas_totales,
    fn_duration_text(ROUND(AVG(a.moving_time))::INTEGER)                   AS duracion_media_sesion,
    ROUND(AVG(a.distance) / 1000.0, 2)                                     AS km_medio_sesion,
    fn_pace_text(AVG(a.average_speed))                                      AS ritmo_medio,
    ROUND(AVG(a.average_heartrate), 1)                                      AS fc_media,
    ROUND(AVG(a.max_heartrate), 1)                                          AS fc_max_media,
    ROUND(AVG(a.total_elevation_gain), 0)                                   AS desnivel_medio,
    SUM(a.total_elevation_gain)                                             AS desnivel_acumulado,
    SUM(a.calories)                                                         AS calorias_totales,
    ROUND(AVG(a.suffer_score), 1)                                           AS suffer_medio,
    -- Índice de eficiencia aeróbica: m/s por latido medio (mayor = mejor)
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC, 5) AS eficiencia_aerobica,
    -- Sesiones por semana promedio
    ROUND(COUNT(*) / 52.0, 1)                                               AS sesiones_semana_avg
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
GROUP BY anio
ORDER BY anio DESC;


-- ------------------------------------------------------------
-- B2. Progresión mensual histórica completa
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Permite ver patrones de temporada (picos de volumen en primavera/otoño
--   típicos del corredor popular), períodos de lesión (caídas bruscas de km),
--   y la evolución del ritmo medio a lo largo de los años.
--   Comparar el mismo mes en distintos años es muy revelador.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_monthly_progression AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER  AS anio,
    EXTRACT(MONTH FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS mes,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMMonth')          AS nombre_mes,
    COUNT(*)                                                                 AS sesiones,
    ROUND(SUM(a.distance) / 1000.0, 1)                                      AS km_totales,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                   AS horas_totales,
    fn_pace_text(AVG(a.average_speed))                                       AS ritmo_medio,
    ROUND(AVG(a.average_heartrate), 1)                                       AS fc_media,
    SUM(a.total_elevation_gain)                                              AS desnivel_total,
    SUM(a.calories)                                                          AS calorias_totales,
    ROUND(AVG(a.suffer_score), 1)                                            AS suffer_medio,
    -- Progresión de eficiencia mes a mes
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC, 5) AS eficiencia_aerobica,
    -- Volumen semanal equivalente (normalizado a 4 semanas)
    ROUND(SUM(a.distance) / 1000.0 / 4.33, 1)                              AS km_semana_equiv
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
GROUP BY anio, mes, nombre_mes
ORDER BY anio DESC, mes DESC;


-- ------------------------------------------------------------
-- B3. Mejores marcas históricas (PRs estimados por segmento de distancia)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   No tenemos tiempos oficiales de carrera, pero podemos estimar
--   el mejor rendimiento por rango de distancia a partir de las actividades.
--   Esto da una idea de los mejores ritmos sostenidos en cada franja.
--   Útil para detectar si el atleta ha mejorado o regresado en cada distancia.
--   NOTA: Para distancias de carrera exactas (10K, 21K, 42K) se filtra
--   por actividades dentro del ±5% de la distancia objetivo.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_best_performances AS
WITH categorized AS (
    SELECT
        a.strava_id,
        a.name,
        a.start_date AT TIME ZONE 'Europe/Madrid' AS fecha,
        ROUND(a.distance / 1000.0, 2)             AS km,
        a.moving_time,
        fn_duration_text(a.moving_time)            AS tiempo_total,
        a.average_speed,
        fn_pace_text(a.average_speed)              AS ritmo_medio,
        a.average_heartrate                        AS fc_media,
        a.total_elevation_gain                     AS desnivel,
        CASE
            WHEN a.distance BETWEEN 4750 AND 5250   THEN '5K'
            WHEN a.distance BETWEEN 9500 AND 10500  THEN '10K'
            WHEN a.distance BETWEEN 14250 AND 15750 THEN '15K'
            WHEN a.distance BETWEEN 19000 AND 22000 THEN 'Media Maratón'
            WHEN a.distance BETWEEN 40000 AND 44000 THEN 'Maratón'
            WHEN a.distance > 44000                  THEN 'Ultra'
            WHEN a.distance BETWEEN 3000 AND 4749   THEN 'Short (<5K)'
            ELSE NULL
        END AS categoria_distancia,
        ROW_NUMBER() OVER (
            PARTITION BY CASE
                WHEN a.distance BETWEEN 4750 AND 5250   THEN '5K'
                WHEN a.distance BETWEEN 9500 AND 10500  THEN '10K'
                WHEN a.distance BETWEEN 14250 AND 15750 THEN '15K'
                WHEN a.distance BETWEEN 19000 AND 22000 THEN 'Media Maratón'
                WHEN a.distance BETWEEN 40000 AND 44000 THEN 'Maratón'
                WHEN a.distance > 44000                  THEN 'Ultra'
            END
            ORDER BY a.average_speed DESC NULLS LAST
        ) AS rank_velocidad
    FROM activities a
    WHERE a.sport_type IN ('Run', 'TrailRun')
      AND a.trainer = false
      AND a.average_speed IS NOT NULL
)
SELECT
    categoria_distancia,
    strava_id,
    name        AS actividad,
    fecha,
    km,
    tiempo_total,
    ritmo_medio AS mejor_ritmo,
    fc_media,
    desnivel
FROM categorized
WHERE rank_velocidad = 1
  AND categoria_distancia IS NOT NULL
ORDER BY
    CASE categoria_distancia
        WHEN '5K' THEN 1 WHEN '10K' THEN 2 WHEN '15K' THEN 3
        WHEN 'Media Maratón' THEN 4 WHEN 'Maratón' THEN 5 WHEN 'Ultra' THEN 6
        ELSE 7
    END;


-- ------------------------------------------------------------
-- B4. Evolución de la eficiencia aeróbica (tendencia de forma)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   La eficiencia aeróbica = velocidad media / FC media.
--   Si esta métrica SUBE con el tiempo → el atleta corre más rápido
--   al mismo coste cardíaco = mejora real de la forma.
--   Si BAJA → sobrecarga, fatiga acumulada o pérdida de forma.
--   Agrupar por trimestre da suficiente señal estadística.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_historic_aerobic_efficiency AS
SELECT
    EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER      AS anio,
    EXTRACT(QUARTER FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER   AS trimestre,
    CONCAT('Q', EXTRACT(QUARTER FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER,
           '-', EXTRACT(YEAR FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER) AS periodo,
    COUNT(*)                                                                     AS sesiones,
    ROUND(SUM(a.distance) / 1000.0, 1)                                          AS km_totales,
    fn_pace_text(AVG(a.average_speed))                                           AS ritmo_medio,
    ROUND(AVG(a.average_heartrate), 1)                                           AS fc_media,
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS eficiencia_x1000,
    -- Deriva cardíaca media del período (de v_lap_degradation equivalente)
    ROUND(AVG(a.suffer_score), 1)                                                AS suffer_medio
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.average_heartrate IS NOT NULL
  AND a.average_speed IS NOT NULL
GROUP BY anio, trimestre, periodo
ORDER BY anio DESC, trimestre DESC;


-- ============================================================
-- [C] ANÁLISIS ROLLING — ÚLTIMOS N MESES
-- ============================================================
-- NOTA: Las siguientes vistas usan INTERVAL parametrizable.
--   Las vistas fijas miran los últimos 6 meses.
--   Para otros rangos, usar las queries parametrizadas al final del bloque.
-- ============================================================


-- ------------------------------------------------------------
-- C1. Resumen rolling de los últimos 6 meses por mes
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Detecta tendencias de volumen y fatiga a medio plazo.
--   Un mes con alto km + bajo suffer → bien asimilado.
--   Un mes con poco km + alto suffer → sobreentrenamiento o carreras.
--   La columna sesiones_semana_avg indica adherencia al plan.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_rolling_6months_by_month AS
SELECT
    TO_CHAR(DATE_TRUNC('month', a.start_date AT TIME ZONE 'Europe/Madrid'), 'YYYY-MM') AS mes,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMMonth YYYY')                 AS mes_label,
    COUNT(*)                                                                             AS sesiones,
    ROUND(SUM(a.distance) / 1000.0, 1)                                                  AS km_totales,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                               AS horas_totales,
    fn_pace_text(AVG(a.average_speed))                                                   AS ritmo_medio,
    ROUND(AVG(a.average_heartrate), 1)                                                   AS fc_media,
    SUM(a.total_elevation_gain)                                                          AS desnivel_total,
    SUM(a.calories)                                                                      AS calorias_totales,
    ROUND(AVG(a.suffer_score), 1)                                                        AS suffer_medio,
    -- Ratio km-largo (>15km) sobre total: indica si el atleta hace tiradas largas
    ROUND(
        COUNT(*) FILTER (WHERE a.distance > 15000)::DECIMAL / NULLIF(COUNT(*), 0) * 100, 1
    )                                                                                    AS pct_sesiones_largas,
    -- Sesiones de calidad (ritmo < 5:00/km estimado → speed > 3.33 m/s)
    COUNT(*) FILTER (WHERE a.average_speed > 3.33)                                      AS sesiones_calidad,
    ROUND(COUNT(*) / 4.33, 1)                                                            AS sesiones_semana_avg
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= NOW() - INTERVAL '6 months'
GROUP BY mes, mes_label
ORDER BY mes DESC;


-- ------------------------------------------------------------
-- C2. Tendencia de forma: ritmo vs FC (eficiencia rolling)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Compara semana a semana el ritmo sostenido y la FC asociada.
--   Si el ritmo MEJORA (baja) y la FC se MANTIENE o BAJA → forma ascendente.
--   Si el ritmo EMPEORA (sube) y la FC SUBE → acumulación de fatiga.
--   Ideal ver esto como gráfico de línea en el frontend.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_rolling_weekly_fitness_trend AS
SELECT
    TO_CHAR(DATE_TRUNC('week', a.start_date AT TIME ZONE 'Europe/Madrid'), 'YYYY-"W"IW') AS semana,
    DATE_TRUNC('week', a.start_date AT TIME ZONE 'Europe/Madrid')::DATE                   AS semana_inicio,
    COUNT(*)                                                                                AS sesiones,
    ROUND(SUM(a.distance) / 1000.0, 1)                                                     AS km_totales,
    fn_pace_text(AVG(a.average_speed))                                                      AS ritmo_medio,
    ROUND(AVG(a.average_heartrate), 1)                                                      AS fc_media,
    -- Eficiencia normalizada x1000 para legibilidad
    ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS eficiencia_x1000,
    ROUND(SUM(a.moving_time) / 3600.0, 1)                                                   AS horas_totales,
    ROUND(AVG(a.suffer_score), 1)                                                            AS suffer_medio,
    -- Carga de entrenamiento arbitraria (TRIMP simplificado): duration_min * FC_media
    ROUND(SUM(a.moving_time / 60.0 * COALESCE(a.average_heartrate, 0)), 0)                 AS trimp_estimado
FROM activities a
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= NOW() - INTERVAL '6 months'
  AND a.average_heartrate IS NOT NULL
GROUP BY semana, semana_inicio
ORDER BY semana_inicio DESC;


-- ------------------------------------------------------------
-- C3. Estado del material — desgaste de zapatillas
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   El km acumulado en cada zapatilla determina su vida útil.
--   Rango recomendado: 600-800 km para la mayoría de modelos.
--   Columna alerta_desgaste indica si se aproxima al límite.
--   Rotar entre 2-3 zapatillas reduce el riesgo de lesión.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_gear_wear_tracking AS
SELECT
    g.strava_id                                  AS gear_id,
    g.name                                       AS zapatilla,
    g.brand_name                                 AS marca,
    g.model_name                                 AS modelo,
    g.primary_gear                               AS principal,
    g.retired                                    AS retirada,
    ROUND(g.distance / 1000.0, 0)               AS km_acumulados_total,
    COUNT(a.id)                                  AS sesiones_recientes,
    ROUND(SUM(a.distance) / 1000.0, 1)          AS km_ultimos_6m,
    -- Porcentaje de vida útil consumida (base: 700km)
    ROUND(g.distance / 7000.0 * 100, 1)         AS pct_vida_util_consumida,
    CASE
        WHEN g.retired = true           THEN '⚫ Retirada'
        WHEN g.distance > 700000        THEN '🔴 Sustituir ya'
        WHEN g.distance > 550000        THEN '🟠 Cerca del límite'
        WHEN g.distance > 400000        THEN '🟡 Vigilar'
        ELSE                                 '🟢 OK'
    END                                          AS alerta_desgaste
FROM gears g
LEFT JOIN activities a ON a.gear_id = g.strava_id
    AND a.start_date >= NOW() - INTERVAL '6 months'
    AND a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
WHERE g.gear_type = 'shoes'
GROUP BY g.strava_id, g.name, g.brand_name, g.model_name,
         g.primary_gear, g.retired, g.distance
ORDER BY g.primary_gear DESC, g.distance DESC;


-- ============================================================
-- [D] ANÁLISIS DEL MES ACTUAL
-- ============================================================


-- ------------------------------------------------------------
-- D1. Resumen KPIs del mes en curso
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Snapshot del estado del mes actual.
--   Comparar con el promedio histórico mensual de los últimos 12 meses
--   para saber si el mes está por encima o por debajo de la media.
--   Las columnas *_vs_avg muestran el % de desviación.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_month_summary AS
WITH current_month AS (
    SELECT
        COUNT(*)                                                    AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 1)                         AS km_totales,
        ROUND(SUM(a.moving_time) / 3600.0, 1)                      AS horas_totales,
        AVG(a.average_speed)                                        AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                          AS fc_media,
        SUM(a.total_elevation_gain)                                 AS desnivel_total,
        SUM(a.calories)                                             AS calorias_totales,
        ROUND(AVG(a.suffer_score), 1)                               AS suffer_medio,
        COUNT(*) FILTER (WHERE a.distance > 15000)                  AS sesiones_largas,
        COUNT(*) FILTER (WHERE a.average_speed > 3.33)              AS sesiones_calidad,
        -- Días con actividad en el mes
        COUNT(DISTINCT DATE(a.start_date AT TIME ZONE 'Europe/Madrid')) AS dias_con_actividad
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW())
),
avg_last_12 AS (
    SELECT
        ROUND(AVG(mes_km), 1)       AS avg_km,
        ROUND(AVG(mes_sesiones), 1) AS avg_sesiones,
        ROUND(AVG(mes_horas), 1)    AS avg_horas
    FROM (
        SELECT
            DATE_TRUNC('month', a.start_date) AS mes,
            COUNT(*)                           AS mes_sesiones,
            SUM(a.distance) / 1000.0           AS mes_km,
            SUM(a.moving_time) / 3600.0        AS mes_horas
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= NOW() - INTERVAL '12 months'
          AND a.start_date < DATE_TRUNC('month', NOW())
        GROUP BY mes
    ) sub
)
SELECT
    TO_CHAR(NOW() AT TIME ZONE 'Europe/Madrid', 'TMMonth YYYY') AS mes_actual,
    cm.sesiones,
    cm.km_totales,
    cm.horas_totales,
    fn_pace_text(cm.avg_speed)                                   AS ritmo_medio,
    cm.fc_media,
    cm.desnivel_total,
    cm.calorias_totales,
    cm.suffer_medio,
    cm.sesiones_largas,
    cm.sesiones_calidad,
    cm.dias_con_actividad,
    -- Comparativa vs promedio 12 meses anteriores
    al.avg_km                                                    AS avg_km_12m,
    al.avg_sesiones                                              AS avg_sesiones_12m,
    ROUND((cm.km_totales - al.avg_km) / NULLIF(al.avg_km, 0) * 100, 1) AS km_vs_avg_pct,
    ROUND((cm.sesiones - al.avg_sesiones) / NULLIF(al.avg_sesiones, 0) * 100, 1) AS sesiones_vs_avg_pct
FROM current_month cm, avg_last_12 al;


-- ------------------------------------------------------------
-- D2. Comparativa mes actual vs mes anterior (día a día)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Permite ver si el atleta va a un ritmo mayor o menor que el mes pasado
--   en el mismo punto del mes. Útil para saber si el mes actual va bien.
--   Comparar km_acumulados_actual vs km_acumulados_anterior en el mismo día.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_vs_last_month_daily AS
WITH current_m AS (
    SELECT
        EXTRACT(DAY FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS dia,
        COUNT(*)                                   AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 2)        AS km_dia
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW())
    GROUP BY dia
),
prev_m AS (
    SELECT
        EXTRACT(DAY FROM a.start_date AT TIME ZONE 'Europe/Madrid')::INTEGER AS dia,
        COUNT(*)                                   AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 2)        AS km_dia
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('month', NOW()) - INTERVAL '1 month'
      AND a.start_date  < DATE_TRUNC('month', NOW())
    GROUP BY dia
),
dias AS (SELECT generate_series(1, 31) AS dia)
SELECT
    d.dia,
    COALESCE(cm.sesiones, 0)  AS sesiones_mes_actual,
    COALESCE(cm.km_dia, 0)    AS km_mes_actual,
    COALESCE(pm.sesiones, 0)  AS sesiones_mes_anterior,
    COALESCE(pm.km_dia, 0)    AS km_mes_anterior,
    -- Acumulados progresivos
    SUM(COALESCE(cm.km_dia, 0)) OVER (ORDER BY d.dia)  AS km_acum_actual,
    SUM(COALESCE(pm.km_dia, 0)) OVER (ORDER BY d.dia)  AS km_acum_anterior
FROM dias d
LEFT JOIN current_m cm ON cm.dia = d.dia
LEFT JOIN prev_m    pm ON pm.dia  = d.dia
ORDER BY d.dia;


-- ------------------------------------------------------------
-- D3. Sesiones del mes actual — detalle individual
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Listado completo de cada sesión del mes con todos los KPIs.
--   Permite identificar las sesiones de mayor calidad, ver si hay
--   progresión en ritmo y detectar sesiones con FC anómala.
--   suffer_score > 100 = sesión muy exigente; conviene no acumular muchas seguidas.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_month_sessions AS
SELECT
    a.strava_id,
    a.name                                                          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS fecha,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'Day')       AS dia_semana,
    a.sport_type,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS tiempo_movimiento,
    fn_pace_text(a.average_speed)                                   AS ritmo_medio,
    fn_pace_text(a.max_speed)                                       AS ritmo_max,
    a.average_heartrate                                             AS fc_media,
    a.max_heartrate                                                 AS fc_max,
    a.total_elevation_gain                                          AS desnivel,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS esfuerzo_percibido,
    a.device_name                                                   AS dispositivo,
    g.name                                                          AS zapatilla,
    -- Clasificación de intensidad por suffer_score
    CASE
        WHEN a.suffer_score < 25  THEN 'Regenerativo'
        WHEN a.suffer_score < 50  THEN 'Suave'
        WHEN a.suffer_score < 100 THEN 'Moderado'
        WHEN a.suffer_score < 150 THEN 'Duro'
        ELSE                           'Muy duro'
    END                                                             AS intensidad_estimada,
    -- ¿Es tirada larga? (>15km)
    CASE WHEN a.distance > 15000 THEN 'Sí' ELSE 'No' END           AS tirada_larga,
    -- Número de laps registrados
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS num_laps
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('month', NOW())
ORDER BY a.start_date DESC;


-- ============================================================
-- [E] ANÁLISIS SEMANA ANTERIOR — DETALLE COMPLETO
-- ============================================================


-- ------------------------------------------------------------
-- E1. Resumen semana anterior con comparativa vs 4 semanas previas
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   La semana anterior es la más reciente con datos completos.
--   Comparar con la media de las 4 semanas anteriores revela si fue
--   una semana de carga, descarga o dentro de la normalidad.
--   Una semana de carga típica tiene un 20-30% más de km que la media.
--   Una semana de descarga (tapering) debería rondar el 50-60% del volumen normal.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_summary AS
WITH last_week_data AS (
    SELECT
        COUNT(*)                                                     AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 1)                          AS km_totales,
        ROUND(SUM(a.moving_time) / 3600.0, 2)                       AS horas_totales,
        AVG(a.average_speed)                                         AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                           AS fc_media,
        SUM(a.total_elevation_gain)                                  AS desnivel_total,
        SUM(a.calories)                                              AS calorias_totales,
        ROUND(AVG(a.suffer_score), 1)                                AS suffer_medio,
        COUNT(*) FILTER (WHERE a.distance > 15000)                   AS sesiones_largas,
        ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS eficiencia
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
      AND a.start_date  < DATE_TRUNC('week', NOW())
),
prev_4w_avg AS (
    SELECT
        ROUND(AVG(w_km), 1)      AS avg_km,
        ROUND(AVG(w_sesiones), 1) AS avg_sesiones,
        ROUND(AVG(w_suffer), 1)  AS avg_suffer
    FROM (
        SELECT
            DATE_TRUNC('week', a.start_date) AS semana,
            COUNT(*)                          AS w_sesiones,
            SUM(a.distance) / 1000.0          AS w_km,
            AVG(a.suffer_score)               AS w_suffer
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '5 weeks'
          AND a.start_date  < DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
        GROUP BY semana
    ) sub
)
SELECT
    TO_CHAR(DATE_TRUNC('week', NOW()) - INTERVAL '1 week', 'DD/MM/YYYY') AS semana_inicio,
    TO_CHAR(DATE_TRUNC('week', NOW()) - INTERVAL '1 day',  'DD/MM/YYYY') AS semana_fin,
    lw.sesiones,
    lw.km_totales,
    lw.horas_totales,
    fn_pace_text(lw.avg_speed)                                            AS ritmo_medio,
    lw.fc_media,
    lw.desnivel_total,
    lw.calorias_totales,
    lw.suffer_medio,
    lw.sesiones_largas,
    lw.eficiencia                                                         AS eficiencia_x1000,
    -- Comparativa vs media 4 semanas anteriores
    p4.avg_km                                                             AS avg_km_4sem,
    p4.avg_sesiones                                                       AS avg_sesiones_4sem,
    p4.avg_suffer                                                         AS avg_suffer_4sem,
    ROUND((lw.km_totales - p4.avg_km) / NULLIF(p4.avg_km, 0) * 100, 1) AS km_vs_media_4s_pct,
    -- Tipo de semana inferido
    CASE
        WHEN (lw.km_totales - p4.avg_km) / NULLIF(p4.avg_km, 0) > 0.20  THEN '⬆ Semana de carga'
        WHEN (lw.km_totales - p4.avg_km) / NULLIF(p4.avg_km, 0) < -0.30 THEN '⬇ Semana de descarga / taper'
        ELSE '↔ Semana estándar'
    END                                                                   AS tipo_semana
FROM last_week_data lw, prev_4w_avg p4;


-- ------------------------------------------------------------
-- E2. Detalle de sesiones de la semana anterior
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Igual que v_current_month_sessions pero circunscrito a la semana anterior.
--   Permite revisar sesión por sesión la calidad del trabajo realizado.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_sessions AS
SELECT
    a.strava_id,
    a.name                                                          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS fecha,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMDay')     AS dia_semana,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS tiempo_movimiento,
    fn_pace_text(a.average_speed)                                   AS ritmo_medio,
    fn_pace_text(a.max_speed)                                       AS ritmo_max,
    a.average_heartrate                                             AS fc_media,
    a.max_heartrate                                                 AS fc_max,
    a.total_elevation_gain                                          AS desnivel,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS esfuerzo_percibido,
    g.name                                                          AS zapatilla,
    CASE
        WHEN a.suffer_score < 25  THEN 'Regenerativo'
        WHEN a.suffer_score < 50  THEN 'Suave'
        WHEN a.suffer_score < 100 THEN 'Moderado'
        WHEN a.suffer_score < 150 THEN 'Duro'
        ELSE                           'Muy duro'
    END                                                             AS intensidad_estimada,
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS num_laps
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC;


-- ------------------------------------------------------------
-- E3. Laps de las sesiones de la semana anterior (detalle por lap)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   El análisis por lap es la vista más granular.
--   Permite ver si el atleta mantiene o pierde el ritmo a lo largo
--   de la sesión. Un ritmo estable o negativo (acelerando al final)
--   es señal de buena gestión del esfuerzo.
--   La columna delta_ritmo_vs_primer_lap mide la degradación acumulada.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_lap_detail AS
WITH primer_lap_speed AS (
    SELECT
        activity_strava_id,
        average_speed AS velocidad_primer_lap
    FROM activity_laps
    WHERE lap_index = 0
)
SELECT
    a.strava_id     AS activity_id,
    a.name          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'   AS fecha_actividad,
    l.lap_index + 1                             AS lap,
    l.name                                      AS nombre_lap,
    ROUND(l.distance / 1000.0, 3)              AS km,
    fn_duration_text(l.moving_time)             AS tiempo,
    fn_pace_text(l.average_speed)               AS ritmo,
    l.average_heartrate                         AS fc_media,
    l.max_heartrate                             AS fc_max,
    l.total_elevation_gain                      AS desnivel,
    l.pace_zone                                 AS zona_ritmo,
    -- Delta ritmo vs primer lap: + significa más lento, - más rápido
    ROUND(
        (NULLIF(p.velocidad_primer_lap, 0) - l.average_speed)
        / NULLIF(p.velocidad_primer_lap, 0) * 100
    , 1)                                        AS delta_velocidad_vs_primer_lap_pct,
    fn_pace_text(p.velocidad_primer_lap)        AS ritmo_primer_lap_ref
FROM activity_laps l
JOIN activities a       ON a.strava_id = l.activity_strava_id
JOIN primer_lap_speed p ON p.activity_strava_id = l.activity_strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC, l.lap_index ASC;


-- ------------------------------------------------------------
-- E4. Degradación de ritmo y deriva cardíaca por sesión (semana anterior)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Versión extendida de v_lap_degradation ya existente.
--   variacion_velocidad_pct > 0 → el atleta terminó más rápido (negativo split = ideal).
--   variacion_velocidad_pct < 0 → el atleta se frenó (positive split = fatiga).
--   deriva_cardiaca > 10 lpm con distancias medias → señal de fatiga cardiovascular.
--   La eficiencia_cardiaca = variación de velocidad / variación de FC: si sube
--   velocidad más que FC → buena respuesta. Si sube más FC → sobrecoste cardíaco.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_last_week_degradation AS
WITH primer_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed      AS vel_inicio,
        average_heartrate  AS fc_inicio
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index ASC
),
ultimo_lap AS (
    SELECT DISTINCT ON (activity_strava_id)
        activity_strava_id,
        average_speed      AS vel_fin,
        average_heartrate  AS fc_fin
    FROM activity_laps
    ORDER BY activity_strava_id, lap_index DESC
)
SELECT
    a.strava_id,
    a.name                                          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'        AS fecha,
    ROUND(a.distance / 1000.0, 2)                   AS km_total,
    fn_pace_text(p.vel_inicio)                       AS ritmo_inicio,
    fn_pace_text(u.vel_fin)                          AS ritmo_fin,
    ROUND(((u.vel_fin - p.vel_inicio) / NULLIF(p.vel_inicio, 0) * 100)::NUMERIC, 1) AS variacion_velocidad_pct,
    ROUND(p.fc_inicio::NUMERIC, 1)                  AS fc_inicio,
    ROUND(u.fc_fin::NUMERIC, 1)                     AS fc_fin,
    ROUND((u.fc_fin - p.fc_inicio)::NUMERIC, 1)     AS deriva_cardiaca,
    -- Calidad de gestión del ritmo
    CASE
        WHEN ((u.vel_fin - p.vel_inicio) / NULLIF(p.vel_inicio, 0) * 100) > 2
             THEN '✅ Negativo split (aceleró)'
        WHEN ((u.vel_fin - p.vel_inicio) / NULLIF(p.vel_inicio, 0) * 100) BETWEEN -2 AND 2
             THEN '↔ Ritmo constante'
        WHEN ((u.vel_fin - p.vel_inicio) / NULLIF(p.vel_inicio, 0) * 100) BETWEEN -10 AND -2
             THEN '⚠ Ligera degradación'
        ELSE '🔴 Degradación severa'
    END                                             AS gestion_ritmo,
    -- Alerta deriva cardíaca
    CASE
        WHEN (u.fc_fin - p.fc_inicio) < 5   THEN '✅ FC estable'
        WHEN (u.fc_fin - p.fc_inicio) < 10  THEN '🟡 Deriva moderada'
        ELSE                                      '🔴 Alta deriva cardíaca'
    END                                             AS alerta_fc
FROM activities a
JOIN primer_lap p ON p.activity_strava_id = a.strava_id
JOIN ultimo_lap u ON u.activity_strava_id = a.strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '1 week'
  AND a.start_date  < DATE_TRUNC('week', NOW())
ORDER BY a.start_date DESC;


-- ============================================================
-- [F] ANÁLISIS SEMANA ACTUAL — TIEMPO REAL
-- ============================================================


-- ------------------------------------------------------------
-- F1. Resumen en tiempo real de la semana actual
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Cuadro de mando de la semana en curso.
--   km_restantes_objetivo estima los km que faltan para alcanzar
--   la media de las últimas 4 semanas (útil para planificación).
--   dias_restantes indica cuántos días quedan para completar la semana.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_summary AS
WITH this_week AS (
    SELECT
        COUNT(*)                                                     AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 1)                          AS km_totales,
        ROUND(SUM(a.moving_time) / 3600.0, 2)                       AS horas_totales,
        AVG(a.average_speed)                                         AS avg_speed,
        ROUND(AVG(a.average_heartrate), 1)                           AS fc_media,
        SUM(a.total_elevation_gain)                                  AS desnivel_total,
        SUM(a.calories)                                              AS calorias_totales,
        ROUND(AVG(a.suffer_score), 1)                                AS suffer_medio,
        -- TRIMP estimado: carga total de la semana
        ROUND(SUM(a.moving_time / 60.0 * COALESCE(a.average_heartrate, 0)), 0) AS trimp_semana
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW())
),
avg_4w AS (
    SELECT ROUND(AVG(w_km), 1) AS avg_km
    FROM (
        SELECT DATE_TRUNC('week', a.start_date) AS sem, SUM(a.distance) / 1000.0 AS w_km
        FROM activities a
        WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
          AND a.trainer = false
          AND a.start_date >= DATE_TRUNC('week', NOW()) - INTERVAL '4 weeks'
          AND a.start_date  < DATE_TRUNC('week', NOW())
        GROUP BY sem
    ) sub
)
SELECT
    TO_CHAR(DATE_TRUNC('week', NOW()), 'DD/MM/YYYY')  AS semana_inicio,
    TO_CHAR(NOW() AT TIME ZONE 'Europe/Madrid', 'DD/MM/YYYY HH24:MI') AS actualizado_a,
    tw.sesiones,
    tw.km_totales,
    tw.horas_totales,
    fn_pace_text(tw.avg_speed)                         AS ritmo_medio,
    tw.fc_media,
    tw.desnivel_total,
    tw.calorias_totales,
    tw.suffer_medio,
    tw.trimp_semana,
    a4.avg_km                                          AS objetivo_km_referencia,
    GREATEST(ROUND(a4.avg_km - tw.km_totales, 1), 0)  AS km_restantes_para_objetivo,
    -- Días transcurridos y restantes en la semana (lunes=1)
    EXTRACT(ISODOW FROM NOW())::INTEGER                AS dia_semana_actual,
    7 - EXTRACT(ISODOW FROM NOW())::INTEGER            AS dias_restantes_semana
FROM this_week tw, avg_4w a4;


-- ------------------------------------------------------------
-- F2. Carga día a día de la semana actual
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Distribución de la carga dentro de la semana en curso.
--   Ideal ver patrón: días duros alternados con suaves.
--   Un suffer_score acumulado de lunes a domingo > 300 → semana de carga alta.
--   descanso indica los días sin actividad (necesarios para la recuperación).
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_daily_load AS
WITH dias_semana AS (
    SELECT
        generate_series(
            DATE_TRUNC('week', NOW()),
            DATE_TRUNC('week', NOW()) + INTERVAL '6 days',
            INTERVAL '1 day'
        )::DATE AS dia
),
actividades_dia AS (
    SELECT
        DATE(a.start_date AT TIME ZONE 'Europe/Madrid')          AS dia,
        COUNT(*)                                                   AS sesiones,
        ROUND(SUM(a.distance) / 1000.0, 2)                        AS km,
        ROUND(SUM(a.moving_time) / 60.0, 0)                       AS minutos,
        ROUND(AVG(a.average_heartrate), 1)                         AS fc_media,
        SUM(a.suffer_score)                                        AS suffer_acum,
        SUM(a.calories)                                            AS calorias,
        STRING_AGG(a.name, ' | ' ORDER BY a.start_date)           AS nombres_sesiones
    FROM activities a
    WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
      AND a.trainer = false
      AND a.start_date >= DATE_TRUNC('week', NOW())
    GROUP BY dia
)
SELECT
    ds.dia,
    TO_CHAR(ds.dia, 'TMDay')                           AS dia_nombre,
    COALESCE(ad.sesiones, 0)                           AS sesiones,
    COALESCE(ad.km, 0)                                 AS km,
    COALESCE(ad.minutos, 0)                            AS minutos_totales,
    COALESCE(ad.fc_media, 0)                           AS fc_media,
    COALESCE(ad.suffer_acum, 0)                        AS suffer_acumulado,
    COALESCE(ad.calorias, 0)                           AS calorias,
    COALESCE(ad.nombres_sesiones, '— Descanso —')      AS sesiones_nombre,
    CASE WHEN ad.sesiones IS NULL THEN true ELSE false END AS dia_descanso,
    -- Carga del día
    CASE
        WHEN ad.suffer_acum IS NULL       THEN '💤 Descanso'
        WHEN ad.suffer_acum < 25          THEN '🔵 Regenerativo'
        WHEN ad.suffer_acum < 75          THEN '🟢 Suave'
        WHEN ad.suffer_acum < 150         THEN '🟡 Moderado'
        WHEN ad.suffer_acum < 250         THEN '🟠 Duro'
        ELSE                                   '🔴 Muy duro'
    END                                                AS carga_dia
FROM dias_semana ds
LEFT JOIN actividades_dia ad ON ad.dia = ds.dia
ORDER BY ds.dia ASC;


-- ------------------------------------------------------------
-- F3. Detalle de cada sesión de la semana actual con laps
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   La vista más granular de la semana en curso.
--   Muestra cada sesión con su desglose de laps.
--   Permite evaluar la sesión más reciente en tiempo real.
--   Si num_laps = 0 → la actividad no tiene laps registrados
--   (puede ser que el atleta no use laps automáticos o manuales).
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_session_detail AS
SELECT
    a.strava_id,
    a.name                                                          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'                       AS fecha,
    TO_CHAR(a.start_date AT TIME ZONE 'Europe/Madrid', 'TMDay DD/MM') AS dia,
    a.sport_type,
    ROUND(a.distance / 1000.0, 2)                                   AS km,
    fn_duration_text(a.moving_time)                                 AS tiempo_movimiento,
    fn_pace_text(a.average_speed)                                   AS ritmo_medio,
    fn_pace_text(a.max_speed)                                       AS ritmo_max,
    a.average_heartrate                                             AS fc_media,
    a.max_heartrate                                                 AS fc_max,
    a.total_elevation_gain                                          AS desnivel,
    a.calories,
    a.suffer_score,
    a.perceived_exertion                                            AS rpe,
    a.description,
    g.name                                                          AS zapatilla,
    g.brand_name || ' ' || COALESCE(g.model_name, '')               AS modelo_zapatilla,
    ROUND((AVG(a.average_speed) OVER ()) / NULLIF(a.average_heartrate, 0) * 1000, 3) AS eficiencia_vs_semana,
    CASE
        WHEN a.suffer_score < 25  THEN 'Regenerativo'
        WHEN a.suffer_score < 50  THEN 'Suave'
        WHEN a.suffer_score < 100 THEN 'Moderado'
        WHEN a.suffer_score < 150 THEN 'Duro'
        ELSE                           'Muy duro'
    END                                                             AS intensidad,
    (SELECT COUNT(*) FROM activity_laps l WHERE l.activity_strava_id = a.strava_id) AS num_laps,
    -- Mejor lap de la sesión (el más rápido)
    (SELECT fn_pace_text(MAX(l2.average_speed))
     FROM activity_laps l2 WHERE l2.activity_strava_id = a.strava_id)               AS mejor_lap_ritmo
FROM activities a
LEFT JOIN gears g ON g.strava_id = a.gear_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun', 'Hike')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW())
ORDER BY a.start_date DESC;


-- ------------------------------------------------------------
-- F4. Laps de la semana actual (granularidad máxima)
-- ------------------------------------------------------------
-- INTERPRETACIÓN:
--   Igual que v_last_week_lap_detail pero para la semana en curso.
--   delta_velocidad_vs_primer_lap_pct: positivo = más lento, negativo = más rápido.
--   Útil para analizar la sesión más reciente en tiempo casi real.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_current_week_lap_detail AS
WITH primer_lap_speed AS (
    SELECT activity_strava_id, average_speed AS velocidad_primer_lap
    FROM activity_laps
    WHERE lap_index = 0
)
SELECT
    a.strava_id     AS activity_id,
    a.name          AS actividad,
    a.start_date AT TIME ZONE 'Europe/Madrid'   AS fecha_actividad,
    l.lap_index + 1                             AS lap,
    ROUND(l.distance / 1000.0, 3)              AS km,
    fn_duration_text(l.moving_time)             AS tiempo,
    fn_pace_text(l.average_speed)               AS ritmo,
    fn_pace_text(l.max_speed)                   AS ritmo_max_lap,
    l.average_heartrate                         AS fc_media,
    l.max_heartrate                             AS fc_max,
    l.total_elevation_gain                      AS desnivel,
    l.pace_zone                                 AS zona_ritmo,
    ROUND(
        (NULLIF(p.velocidad_primer_lap, 0) - l.average_speed)
        / NULLIF(p.velocidad_primer_lap, 0) * 100
    , 1)                                        AS delta_velocidad_vs_primer_lap_pct
FROM activity_laps l
JOIN activities a       ON a.strava_id = l.activity_strava_id
JOIN primer_lap_speed p ON p.activity_strava_id = l.activity_strava_id
WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
  AND a.trainer = false
  AND a.start_date >= DATE_TRUNC('week', NOW())
ORDER BY a.start_date ASC, l.lap_index ASC;


-- ============================================================
-- QUERIES PARAMETRIZADAS — uso desde backend / herramienta
-- ============================================================
-- Sustituir los valores entre << >> con los parámetros reales.
-- En Java/TypeScript usar PreparedStatement o query builder.
-- ============================================================

-- QP-1: Histórico de N meses hacia atrás (reemplaza '6 months')
-- SELECT * FROM v_historic_monthly_progression
-- WHERE anio * 100 + mes >= EXTRACT(YEAR FROM NOW() - INTERVAL '<<N>> months')::INT * 100
--                         + EXTRACT(MONTH FROM NOW() - INTERVAL '<<N>> months')::INT
-- ORDER BY anio DESC, mes DESC;

-- QP-2: Resumen semanal rolling parametrizado
-- SELECT * FROM v_rolling_weekly_fitness_trend
-- WHERE semana_inicio >= NOW() - INTERVAL '<<N>> weeks';

-- QP-3: Sesión específica + sus laps (por strava_id)
-- SELECT a.*, l.*
-- FROM activities a
-- LEFT JOIN activity_laps l ON l.activity_strava_id = a.strava_id
-- WHERE a.strava_id = <<STRAVA_ID>>
-- ORDER BY l.lap_index ASC;

-- QP-4: Comparativa de rendimiento entre dos fechas
-- SELECT
--     fn_pace_text(AVG(a.average_speed)) AS ritmo_medio,
--     ROUND(AVG(a.average_heartrate), 1) AS fc_media,
--     ROUND(SUM(a.distance) / 1000.0, 1) AS km_totales,
--     ROUND((AVG(a.average_speed) / NULLIF(AVG(a.average_heartrate), 0))::NUMERIC * 1000, 3) AS eficiencia
-- FROM activities a
-- WHERE a.sport_type IN ('Run', 'VirtualRun', 'TrailRun')
--   AND a.start_date BETWEEN '<<FECHA_INICIO>>' AND '<<FECHA_FIN>>';

-- QP-5: Detector de rachas (días consecutivos corriendo)
-- WITH dias AS (
--     SELECT DISTINCT DATE(start_date AT TIME ZONE 'Europe/Madrid') AS dia
--     FROM activities
--     WHERE sport_type IN ('Run','VirtualRun','TrailRun')
-- ),
-- grupos AS (
--     SELECT dia, dia - ROW_NUMBER() OVER (ORDER BY dia) * INTERVAL '1 day' AS grp
--     FROM dias
-- )
-- SELECT MIN(dia) AS inicio_racha, MAX(dia) AS fin_racha, COUNT(*) AS dias_consecutivos
-- FROM grupos
-- GROUP BY grp
-- ORDER BY dias_consecutivos DESC
-- LIMIT 10;


-- ============================================================
-- FIN DEL ARCHIVO
-- ============================================================
-- Vistas creadas: 17
-- Funciones de apoyo: 3 (fn_fc_zone, fn_pace_text, fn_duration_text)
-- Queries parametrizadas: 5
-- ============================================================