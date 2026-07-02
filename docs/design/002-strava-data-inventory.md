# Strava Data Inventory — Stored Fields and Computed Metrics

- Status: Accepted  
- Date: 2026-06-30  
- Relates to: Issue #13 — audit and document stored Strava data and computed metrics  
- Relates to: [ADR-001 — `collector-api` Scope as a Multi-Integration API](../adr/001-collector-api-scope.md)  
- Relates to: [Design 001 — Neutral Read-Models for `collector-api`](001-activity-read-models.md)

## Purpose

This document answers three questions for Issue #13:

1. What Strava data does the system store, and in which fields?
2. What metrics does the system compute from that data, and how?
3. What questions cannot be answered with current data, including gaps that will
   matter when a second source (Suunto, Garmin, Apple Health) is added?

It is a reference document, not a design proposal. No solutions are defined here;
closing any gap is future work (Epic 5).

---

## 1. Stored Data Inventory

Tables live in the `collector` schema. All are populated by `collector-strava`
ingestion jobs. Fields marked **[operational]** are internal sync-state columns
not intended for analytics.

### 1.1 `athletes`

Source: Strava `/athlete` endpoint.

| Column | Type | Notes |
|---|---|---|
| `strava_id` | BIGINT UNIQUE | Strava numeric athlete ID |
| `username` | VARCHAR(255) | |
| `firstname`, `lastname` | VARCHAR(255) | |
| `city`, `country` | VARCHAR(255) | |
| `sex` | VARCHAR(1) | `M` / `F` |
| `weight` | DECIMAL(5,2) | kg; current snapshot only, no history |
| `profile` | VARCHAR(500) | Avatar URL |
| `measurement_preference` | VARCHAR(20) | `feet` or `meters` |
| `ftp` | INTEGER | Functional threshold power (W). Source of truth is Strava `/athlete`, not `/athlete/zones` (which never exposes FTP). Promoted to `athlete_profiles.ftp_watts` (Issue #28) so the read side can access it under ADR-001; not yet wired into any computation (see §3) |
| `follower_count`, `friend_count` | INTEGER | |
| `premium` | BOOLEAN | Strava subscription tier |

---

### 1.2 `activities`

Source: Strava list-activities and activity-detail endpoints.

**Core fields** (migration 004)

| Column | Type | Notes |
|---|---|---|
| `strava_id` | BIGINT UNIQUE | |
| `athlete_id` | BIGINT | FK → `athletes.id` |
| `name` | VARCHAR(255) | Activity title |
| `type` | VARCHAR(50) | Legacy field; superseded by `sport_type` |
| `sport_type` | VARCHAR(50) | See §1.2.1 |
| `distance` | DECIMAL(10,2) | Meters |
| `moving_time` | INTEGER | Seconds (excludes pauses) |
| `elapsed_time` | INTEGER | Seconds (wall clock) |
| `start_date` | TIMESTAMPTZ | UTC |

**Effort and intensity fields** (migration 006)

| Column | Type | Notes |
|---|---|---|
| `total_elevation_gain` | DECIMAL(8,2) | Meters |
| `average_speed` | DECIMAL(8,4) | m/s |
| `max_speed` | DECIMAL(8,4) | m/s |
| `average_heartrate` | DECIMAL(5,2) | bpm; null if no HR monitor |
| `max_heartrate` | DECIMAL(5,2) | bpm |
| `average_watts` | DECIMAL(8,2) | W; null if no power meter |
| `kilojoules` | DECIMAL(10,2) | Mechanical work |
| `suffer_score` | INTEGER | Strava relative-effort score (proprietary) |
| `calories` | INTEGER | Strava-estimated kcal |

**Detail fields** (migration 009)

| Column | Type | Notes |
|---|---|---|
| `perceived_exertion` | DECIMAL(3,1) | RPE 1–10 entered by athlete |
| `description` | TEXT | Free-text notes |
| `device_name` | VARCHAR(255) | Recording device |

**Flags and context** (migration 006)

| Column | Type | Notes |
|---|---|---|
| `trainer` | BOOLEAN | Indoor activity |
| `commute` | BOOLEAN | |
| `manual` | BOOLEAN | Manually entered, no GPS |
| `private` | BOOLEAN | |
| `flagged` | BOOLEAN | |
| `gear_id` | VARCHAR(50) | FK → `gears.strava_id` |
| `timezone` | VARCHAR(100) | IANA timezone string |
| `start_latlng`, `end_latlng` | VARCHAR(50) | `[lat, lng]` strings |

**Stream-sync state** (migration 013) — [operational]

| Column | Type | Notes |
|---|---|---|
| `streams_synced_at` | TIMESTAMPTZ | Last successful stream fetch |
| `streams_sync_status` | VARCHAR(20) | `PENDING`, `SYNCED`, `FAILED`, … |
| `streams_sync_attempts` | INTEGER | Retry counter |
| `streams_last_error` | TEXT | Last error message |
| `streams_last_requested_at` | TIMESTAMPTZ | |

#### 1.2.1 `sport_type` — known values

`sport_type` is stored as a free `String` by design (see Design 001 §2). The local
development database currently holds no activity rows, so distinct values cannot be
queried. The values below are sourced from **code analysis**: SQL views and WHERE
clauses in `collector-strava` reference the following strings explicitly.

| Value | Used in analytics code? |
|---|---|
| `Run` | Yes — all running views and queries |
| `VirtualRun` | Yes — included alongside `Run` in all run-scoped views |
| `TrailRun` | Yes — included in all run-scoped views |
| `Hike` | Yes — included in volume views; excluded from pace/intensity views |

No other values appear in the current view code. Values such as `Ride`,
`MountainBikeRide`, `GravelRide`, `Swim`, `Walk`, `WeightTraining` are valid Strava
`sportType` strings that may be stored if the synced athlete has activities of those
types, but the analytics layer does not currently handle them. **This gap matters
before Suunto or any second source is added** (see §3).

---

### 1.3 `gears`

Source: Strava gear endpoints.

| Column | Type | Notes |
|---|---|---|
| `strava_id` | VARCHAR(50) | Strava gear ID (e.g. `g12345`) |
| `athlete_id` | BIGINT | FK → `athletes.id` |
| `name` | VARCHAR(255) | Display name |
| `brand_name`, `model_name` | VARCHAR(255) | |
| `description` | TEXT | |
| `distance` | DECIMAL(12,2) | Strava-tracked accumulated distance (m) |
| `primary_gear` | BOOLEAN | |
| `retired` | BOOLEAN | |
| `gear_type` | VARCHAR(20) | `shoes` or `bike` |

---

### 1.4 `activity_laps`

Source: Strava laps sub-resource on the activity detail endpoint.

| Column | Type | Notes |
|---|---|---|
| `activity_strava_id` | BIGINT | FK → `activities.strava_id` |
| `lap_index` | INTEGER | 0-based |
| `name` | VARCHAR(255) | |
| `elapsed_time`, `moving_time` | INTEGER | Seconds |
| `start_date` | TIMESTAMPTZ | |
| `distance` | DECIMAL(10,2) | Meters |
| `average_speed`, `max_speed` | DECIMAL(8,4) | m/s |
| `average_heartrate`, `max_heartrate` | DECIMAL(5,2) | bpm |
| `average_watts` | DECIMAL(8,2) | W |
| `total_elevation_gain` | DECIMAL(8,2) | Meters |
| `pace_zone` | INTEGER | Strava pace zone (1–5) |
| `split` | INTEGER | Strava split number |
| `start_index`, `end_index` | INTEGER | Stream array indices for this lap |

---

### 1.5 `activity_streams`

Source: Strava streams API (`/activities/{id}/streams`). TimescaleDB hypertable,
partitioned by day.

| Column | Type | Notes |
|---|---|---|
| `activity_id` | BIGINT | FK → `activities.id` |
| `athlete_id` | BIGINT | FK → `athletes.id` |
| `time` | TIMESTAMPTZ | Absolute timestamp (PK with `elapsed_seconds`) |
| `elapsed_seconds` | INTEGER | Seconds from activity start |
| `distance_m` | DOUBLE PRECISION | Cumulative distance (m) |
| `latitude`, `longitude` | DOUBLE PRECISION | GPS coordinates |
| `altitude_m` | DOUBLE PRECISION | Elevation (m) |
| `heartrate_bpm` | INTEGER | |
| `watts` | INTEGER | Power output (W); null if no power meter |
| `cadence_rpm` | INTEGER | Steps/min (running) or rpm (cycling); stored but unused (see §3) |

Two TimescaleDB **continuous aggregates** are maintained over this table:

| View | Bucket | Retained fields |
|---|---|---|
| `cagg_activity_streams_weekly` | 1 week | `distance_m`, `moving_time_s`, `elevation_gain_m` per activity |
| `cagg_activity_streams_monthly` | 1 month | Same |

These back the `weekly_activity_summary` and `monthly_activity_summary` views.

---

### 1.6 `athlete_zones`

Source: Strava `/athlete/zones`.

| Column | Type | Notes |
|---|---|---|
| `athlete_id` | BIGINT | FK → `athletes.strava_id` |
| `zone_type` | VARCHAR(20) | `heartrate` or `power` |
| `zone_index` | INTEGER | 0-based zone number |
| `min_value`, `max_value` | INTEGER | bpm or W |

**Note:** The stored zones are not used by the analytics views. `fn_hr_zone()` in
`views_strava_advanced.sql` classifies HR using the estimated formula `220 − age`
instead (see §3).

---

### 1.7 `athlete_stats_snapshots`

Source: Strava `/athletes/{id}/stats`. One row per athlete per sync date.

| Column group | Description |
|---|---|
| `biggest_ride_distance` | All-time longest ride (m) |
| `biggest_climb_elevation_gain` | All-time largest single elevation gain (m) |
| `ytd_{ride,run,swim}_{count,distance,moving_time,elapsed_time,elevation_gain}` | Year-to-date totals for each sport |
| `all_{ride,run,swim}_{count,distance,moving_time,elapsed_time,elevation_gain}` | All-time totals for each sport |

The sport taxonomy here is **hard-coded to Ride / Run / Swim** by the Strava API
response shape. See §3 for the multi-source implication.

---

### 1.8 `activity_best_efforts`

Source: Strava `best_efforts` sub-resource on activity detail.

| Column | Type | Notes |
|---|---|---|
| `activity_strava_id` | BIGINT | FK → `activities.strava_id` |
| `name` | VARCHAR(255) | Distance label (e.g. `"400m"`, `"1 mile"`, `"5k"`) |
| `distance` | INTEGER | Meters |
| `elapsed_time` | INTEGER | Seconds |
| `pr_rank` | INTEGER | 1 = personal record; null if not a PR |
| `is_kom` | BOOLEAN | KOM/CR on a Strava segment (Strava-specific; excluded from neutral read-model) |

---

### 1.9 `activity_metrics`

Computed by this system (see §2). Not from Strava.

| Column | Type | Status |
|---|---|---|
| `activity_id` | BIGINT (PK) | FK → `activities.id` |
| `normalized_power` | DECIMAL(8,2) | Written by `ActivityMetricsService` |
| `variability_index` | DECIMAL(6,4) | Written by `ActivityMetricsService` |
| `efficiency_factor` | DECIMAL(6,4) | Written by `ActivityMetricsService` |
| `intensity_factor` | DECIMAL(6,4) | **Column defined; never written** (see §2 — IF, §3) |

---

### 1.10 `athlete_training_load`

Computed by this system (see §2). One row per athlete per calendar day.

| Column | Type | Notes |
|---|---|---|
| `athlete_id` | BIGINT | FK → `athletes.strava_id` |
| `date` | DATE | |
| `tss_day` | DOUBLE PRECISION | Estimated TSS for that day |
| `ctl` | DOUBLE PRECISION | Chronic Training Load (fitness) |
| `atl` | DOUBLE PRECISION | Acute Training Load (fatigue) |
| `tsb` | DOUBLE PRECISION | Training Stress Balance (form) |

---

### 1.11 `routes`

Source: Strava routes endpoint.

| Column | Type | Notes |
|---|---|---|
| `strava_id` | BIGINT UNIQUE | |
| `athlete_id` | BIGINT | FK → `athletes.id` |
| `name` | VARCHAR(255) | |
| `distance` | FLOAT | Meters |
| `elevation_gain` | FLOAT | Meters |
| `type` | INTEGER | `1` = ride, `2` = run |
| `polyline` | TEXT | Encoded Google polyline |

---

## 2. Computed Metrics

None of these values are sourced from Strava. All are computed by jobs in
`collector-strava` and stored in the tables described in §1.9 and §1.10.

### TSS — Training Stress Score (per activity, per day)

**Computed by:** `TrainingLoadService.estimateTss()` → stored in `athlete_training_load.tss_day`  
**Formula:**

```
TSS = (moving_time_seconds / 3600) × IF² × 100
```

**Inputs:** `activities.moving_time`  
**Behavior:** `IF` is each activity's real intensity factor (`IF = NP / FTP`) when
`activity_metrics.intensity_factor` is populated (#29); it falls back to a fixed
`IF = 0.75` only when no real IF exists (no power meter, or FTP unavailable). See §3.

---

### CTL — Chronic Training Load (fitness, daily)

**Computed by:** `TrainingLoadService.computeAndUpsert()` → `athlete_training_load.ctl`  
**Formula:** 42-day exponential moving average (α = 1/42):

```
CTL(day) = CTL(day−1) × (1 − α) + TSS(day) × α
```

**Inputs:** `tss_day` values for the 90-day window ending on `targetDate`.  
Seeded from zero; the 90-day window provides enough history for the EMA to stabilise.

---

### ATL — Acute Training Load (fatigue, daily)

**Computed by:** `TrainingLoadService.computeAndUpsert()` → `athlete_training_load.atl`  
**Formula:** 7-day exponential moving average (α = 1/7):

```
ATL(day) = ATL(day−1) × (1 − α) + TSS(day) × α
```

**Inputs:** same as CTL.

---

### TSB — Training Stress Balance (form, daily)

**Computed by:** `TrainingLoadService.computeAndUpsert()` → `athlete_training_load.tsb`  
**Formula:**

```
TSB = CTL − ATL
```

Positive TSB = fresh; negative TSB = fatigued.

---

### NP — Normalized Power (per activity)

**Computed by:** `ActivityMetricsService.computeNormalizedPower()` → `activity_metrics.normalized_power`  
**Requires:** `activity_streams.watts` with ≥ 30 data points (i.e. activity has a
power meter and streams were fetched).  
**Formula (Coggan):**

1. Compute the 30-second trailing rolling average of instantaneous watts at each sample.
2. Raise each rolling average to the 4th power.
3. Take the mean of all 4th-power values.
4. Take the 4th root of that mean.

```
NP = ( mean( roll30(watts)^4 ) )^(1/4)
```

---

### VI — Variability Index (per activity)

**Computed by:** `ActivityMetricsService.computeForActivity()` → `activity_metrics.variability_index`  
**Formula:**

```
VI = NP / average_watts
```

Values near 1.0 indicate constant-effort pacing; higher values indicate variable
effort (surges, hills, intervals).

---

### EF — Efficiency Factor (per activity)

**Computed by:** `ActivityMetricsService.computeForActivity()` → `activity_metrics.efficiency_factor`  
**Formula:**

```
EF = NP / average_heartrate
```

Only written when `average_heartrate > 0`. A rising EF trend over time indicates
improving aerobic fitness: more power output at the same cardiac cost.

---

### IF — Intensity Factor (per activity)

**Column:** `activity_metrics.intensity_factor`  
**Status: populated (#29).** `ActivityMetricsService` computes `IF = NP / FTP` at
ingestion and persists it to the column; historical rows are corrected by the
`strava.backfill-training-load` job (#30). Null only when FTP is unavailable.

Standard formula:

```
IF = NP / FTP
```

`athlete_profiles.ftp_watts` (promoted from Strava in #28) supplies the per-athlete
FTP. `ActivityMetricsService` computes `IF = NP / FTP` and `TrainingLoadService` uses
this real IF for TSS, falling back to a fixed `IF = 0.75` only when it is null. See §3.

---

### Query-time derived values (not stored)

These are computed inside SQL views in `views_strava_advanced.sql` and not persisted.

| Metric | Formula | Function / View |
|---|---|---|
| Pace (MM:SS/km) | `1000 / average_speed / 60` → formatted | `fn_pace_text()` |
| Duration (HH:MM:SS) | Formatted `moving_time` | `fn_duration_text()` |
| HR zone | 5-zone Karvonen using estimated max HR (220 − age) | `fn_hr_zone()` |
| Aerobic efficiency | `average_speed / average_heartrate` | `v_historic_aerobic_efficiency`, `v_rolling_weekly_fitness_trend` |
| Estimated TRIMP | `moving_time_minutes × average_heartrate` | `v_rolling_weekly_fitness_trend`, `v_current_week_summary` |
| Pace degradation | `(end_lap_speed − start_lap_speed) / start_lap_speed × 100` | `v_lap_degradation`, `v_last_week_degradation` |
| HR drift | `end_lap_heartrate − start_lap_heartrate` | same |
| Gear wear % | `gear.distance / 700,000 × 100` | `v_gear_wear_tracking` |
| Long-run ratio | `COUNT(distance > 15 km) / COUNT(*) × 100` | `v_rolling_6months_by_month` |

---

## 3. Gaps

### 3.1 Unimplemented computations

| Gap | Detail |
|---|---|
| **IF is never written** — *resolved (#29, #30)* | `activity_metrics.intensity_factor` is now populated at ingestion (`IF = NP / FTP`, #29) and backfilled for historical rows via the admin-triggered `strava.backfill-training-load` job (#30). Still null for athletes without a power meter or FTP — by design. |
| **TSS is an approximation** — *resolved for power activities (#30)* | `TrainingLoadService.estimateTss()` now uses each activity's real `intensity_factor` when present; the fixed `IF = 0.75` remains only as a per-activity fallback when no real IF exists (no power meter, or FTP unavailable). Athletes without power data still get the approximation — inherent limit, not a gap. |
| **Athlete zones not used in views** | `athlete_zones` stores Strava's actual HR and power zones, but `fn_hr_zone()` ignores them and uses `220 − age` to estimate max HR. Time-in-zone figures derived from the estimated zones may not match what Strava shows. |

### 3.2 Missing aggregations

| Gap | Detail |
|---|---|
| **Time in HR zone per activity** | `activity_streams.heartrate_bpm` is stored at 1 Hz. `athlete_zones` holds the zone thresholds. No query or job computes time-in-zone per activity. |
| **Cadence analysis** | `activity_streams.cadence_rpm` is stored but no metric, view, or API surface uses it. Running cadence (steps/min) is a primary injury-prevention and efficiency signal. |
| **`biggest_climb_elevation_gain`** | Stored in `athlete_stats_snapshots` but not surfaced by any API endpoint or analytics view. |

### 3.3 Missing data

| Gap | Detail |
|---|---|
| **Historical body weight** | Only the current `athletes.weight` is kept. Weight trends over training blocks cannot be reconstructed. |
| **Race vs training distinction** | No field distinguishes race activities from training activities. Best performances are estimated from distance ranges (`v_historic_best_performances`), not from athlete-tagged race events. |
| **VO2max** | Neither estimated nor collected. Strava exposes an estimated VO2max in some premium contexts but it is not ingested. |
| **Athlete maximum HR** | No stored `max_hr` field on `athletes`. The `220 − age` estimate is used in `fn_hr_zone()`. Recorded `activities.max_heartrate` could proxy for it but is not used that way. |
| **Training plan / goal context** | There is no concept of a target race, a planned weekly mileage goal, or a training block. `v_current_week_summary.km_remaining_to_target` proxies a goal using the 4-week rolling average, which is not the same thing. |

### 3.4 Multi-source gaps (relevant to Epic 5)

These gaps are relatively minor with a single Strava source but become design
constraints as soon as a second source (Suunto, Garmin, Apple Health) is added.

| Gap | Why it matters for multi-source |
|---|---|
| **`sport_type` taxonomy is Strava-only** | Current analytics views hard-code Strava sport strings (`Run`, `TrailRun`, `VirtualRun`, `Hike`). Suunto uses a different taxonomy (e.g. `Running`, `Trail running`). There is no normalization layer between ingested strings and analytics queries. A cross-source "running workouts" query would require each source to be listed explicitly in every WHERE clause, or a mapping table. |
| **`athlete_stats_snapshots` hard-codes Ride / Run / Swim** | This table's schema mirrors Strava's fixed sport categories. A Suunto source that reports `OpenWater` or `Rowing` has no column. The neutral `SportAggregate` read-model handles this correctly (free `sportType` string + list), but the underlying Strava table does not. Any cross-source athlete-stats view would need a different backing table. |
| **TSS is not sport-normalized** | The fixed-IF formula makes no distinction between a 1-hour run and a 1-hour easy bike ride. When a second source adds cycling or swimming data, all load metrics (CTL/ATL/TSB) will be distorted unless sport-specific IF values or separate TSS models are introduced. |
| **Training load vs nutrition / health events** | `athlete_training_load` models only exercise stress. There is no place to record sleep quality, HRV, illness, or caloric intake that would let the platform explain why CTL drops or form does not recover as expected. This is not a gap the platform is expected to fill alone, but it is the gap that limits any cross-domain analytics story. |
| **No race-result ingestion** | Official race results (chip time, age-group rank, course) come from third parties (race organisers, results databases). There is no ingestion path or schema for them. Cross-referencing a training block's TSB curve against a race outcome — the core use case for the PMC — is not possible. |
| **Strava-specific fields excluded from neutral model** | `suffer_score`, `gear_id`, `start_latlng`/`end_latlng`, `streamsSyncStatus`, `perceivedExertion`, `trainer`, `commute`, `flagged` — all present in the Strava entity — were deliberately excluded from the neutral `Activity` read-model (see Design 001 §1). This is correct, but it means any analytics that depends on these fields (e.g. indoor vs outdoor workload split, shoe rotation) cannot be expressed in a source-neutral API response today. |
