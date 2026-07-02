# Design 003: Extended Domain Model — Training, Nutrition, Health, Races

- Status: Accepted
- Date: 2026-07-02
- Relates to: Issue #22 — design extended domain model
- Relates to: [ADR-001 — `collector-api` Scope as a Multi-Integration API](../adr/001-collector-api-scope.md)
- Relates to: [Design 002 — Strava Data Inventory](002-strava-data-inventory.md)

## Purpose

Design 002 §3.4 identifies the hard limit of a single-source Strava collector:
training load, nutrition, health events, and race outcomes are all necessary inputs
to answer the question "why did I perform the way I did on race day?" None of those
inputs is available from Strava alone, and none can be inferred from activity data.

This document defines the four new domain entities needed to close that gap —
`TrainingDay`, `NutritionLog`, `HealthEvent`, and `RaceResult` — and specifies how
they correlate with each other and with the existing `athlete_training_load` table.

**This is a design document, not an implementation spec.** No Liquibase migrations,
Java classes, or API endpoints are created here. Schema implications are noted so
that implementation issues can reference a single source of truth.

---

## 1. Entity Definitions

### 1.1 `TrainingDay`

**Role:** The correlation anchor. A single manually-maintained row that ties
subjective observations to a calendar date. Every other entity in this model
is associated with a date, and `TrainingDay` is the named concept that represents
"what the athlete recorded about that day."

**Important:** `TrainingDay` is not required to exist for analytics queries to work.
The date column in `athlete_training_load`, `nutrition_log`, `health_events`, and
`race_results` is always the join key. A `TrainingDay` row is only created when the
athlete explicitly enters subjective data. Absence of a row is not a gap in the data
model — it means no subjective data was entered that day.

| Field | Java type | Source | Notes |
|---|---|---|---|
| `id` | `UUID` | system-generated | Surrogate PK |
| `athleteId` | `UUID` | system | Canonical athlete identity (see ADR-001) |
| `date` | `LocalDate` | manually entered | Natural key (unique per athlete) |
| `perceivedEffort` | `Integer` | manually entered | RPE 1–10; nullable (no entry = no subjective data) |
| `subjectiveState` | `SubjectiveState` | manually entered | Enum: `EXCELLENT`, `GOOD`, `NEUTRAL`, `POOR`, `BAD` |
| `notes` | `String` | manually entered | Free text; nullable |
| `createdAt` | `Instant` | system | Write-once |
| `updatedAt` | `Instant` | system | Updated on each edit |

**Derived fields (never stored here):**

| Derived field | Source | Join |
|---|---|---|
| Total distance for the day | `activities` | `activities.start_date::date = training_day.date` |
| Total moving time for the day | `activities` | same |
| `tssDay`, `ctl`, `atl`, `tsb` | `athlete_training_load` | `athlete_training_load.date = training_day.date` |

Storing these derived values would create a consistency problem: if Strava syncs a
missing activity for a past date, `athlete_training_load` is recomputed but a stored
`TrainingDay.tssDay` would not be. The date key guarantees that joining always
returns the current computed value.

**Identity / natural key:** `(athleteId, date)` — unique constraint.

**TimescaleDB:** Regular relational table. Write volume is low (at most one row per
athlete per day, only when the athlete enters data). No time-series partitioning needed.

---

### 1.2 `NutritionLog`

**Role:** Records individual food intake events within a day. Each row represents
one food item in one portion, associated with a date and an optional meal context.
Macros and calories are stored at entry level — they are not recomputed on every
read from the food item's current nutritional profile.

**Preferred external source:** Open Food Facts (open-source, no paid dependency,
self-hosting-compatible). See §5.1 for deferred questions about catalog strategy.

| Field | Java type | Source | Notes |
|---|---|---|---|
| `id` | `UUID` | system-generated | Surrogate PK |
| `athleteId` | `UUID` | system | Canonical athlete identity |
| `logDate` | `LocalDate` | manually entered | Associates with `TrainingDay.date` |
| `mealType` | `MealType` | manually entered | Enum: `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`, `PRE_WORKOUT`, `POST_WORKOUT`, `OTHER` |
| `foodItemName` | `String` | manually entered or resolved | Denormalized display name; stored so the entry remains readable if the food item is deleted from the catalog |
| `quantityGrams` | `Double` | manually entered | Portion weight in grams |
| `energyKcal` | `Double` | computed at entry time | Stored; not recomputed on read |
| `carbohydratesGrams` | `Double` | computed at entry time | Stored |
| `proteinsGrams` | `Double` | computed at entry time | Stored |
| `fatsGrams` | `Double` | computed at entry time | Stored |
| `source` | `NutritionSource` | system | Enum: `OPEN_FOOD_FACTS`, `MANUAL` |
| `externalFoodItemId` | `String` | resolved from OFT | Open Food Facts barcode/product ID; nullable if `source = MANUAL` |
| `createdAt` | `Instant` | system | Write-once |

**Rationale for storing macros at entry level:** Nutrition targets and activity-level
correlation require daily totals. Recomputing from `externalFoodItemId → OFT API`
on every analytics query introduces a network call, a parse step, and a potential
staleness problem if OFT corrects a product's nutritional data. Storing macros at
write time is the standard approach in nutrition-logging applications and is correct
here.

**Identity / natural key:** None. Surrogate UUID PK. Multiple entries per
`(athleteId, logDate)` are expected (one per food item / portion).

**TimescaleDB:** Regular relational table with a composite index on
`(athlete_id, log_date)`. Volume is low (5–30 rows per athlete per day at most).
TimescaleDB hypertable classification is deferred until actual write volume is
measured — it is unlikely to be necessary.

---

### 1.3 `HealthEvent`

**Role:** Records events that affect training context: illness, injury, medication
intake, travel, high stress, or other subjective disruptions. An event spans a
date range and therefore affects multiple `TrainingDay` rows simultaneously.

**Safety constraint:** The platform **records and flags, never prescribes.**
`HealthEvent` has a `type` field that includes `MEDICATION`, but there are no dosage
recommendation fields and no treatment advice fields anywhere in the model. Any
API surface or UI feature that exposes `MEDICATION`-type events must display a
visible disclaimer: *"This app records medication events for personal tracking only.
Always consult your doctor before making any medication decisions."* This constraint
is part of the domain model specification, not just a UI detail.

| Field | Java type | Source | Notes |
|---|---|---|---|
| `id` | `UUID` | system-generated | Surrogate PK |
| `athleteId` | `UUID` | system | Canonical athlete identity |
| `startDate` | `LocalDate` | manually entered | First affected date (inclusive) |
| `endDate` | `LocalDate` | manually entered | Last affected date (inclusive); nullable for point-in-time events |
| `type` | `HealthEventType` | manually entered | Enum: `ILLNESS`, `INJURY`, `MEDICATION`, `REST_DAY`, `TRAVEL`, `HIGH_STRESS`, `SLEEP_ISSUE`, `OTHER` |
| `severity` | `HealthEventSeverity` | manually entered | Enum: `LOW`, `MEDIUM`, `HIGH`; nullable (not meaningful for `MEDICATION` or `REST_DAY`) |
| `description` | `String` | manually entered | Free text; nullable |
| `affectsTraining` | `Boolean` | manually entered | Did this event actually disrupt training? |
| `createdAt` | `Instant` | system | |
| `updatedAt` | `Instant` | system | |

**Note on `MEDICATION` type:** There is deliberately no `dosageMg`, `frequency`,
`drugName` (structured), or `recommendedAction` field. `description` (free text)
is the only place a medication name can be recorded. This is intentional — the
platform is not a medication tracker, and structured medication data would create
medical-device regulatory and liability concerns.

**Identity / natural key:** None enforced beyond surrogate UUID. Multiple events
can start on the same date (e.g., illness and travel both begin on the same day).
A uniqueness constraint at `(athleteId, startDate, type)` is plausible but is
intentionally left as an open question for the implementation issue.

**TimescaleDB:** Regular relational table. Expected volume: a few dozen events per
athlete per year. Two indexes are needed for efficient range joins:
`(athlete_id, start_date)` and `(athlete_id, end_date)`.

---

### 1.4 `RaceResult`

**Role:** Records official race outcomes and links them to the training block that
preceded the race. A race is also an activity day — it generates TSS, and its
`raceDate` is the join key into `athlete_training_load` for the PMC correlation.

The canonical use case for this entity is the Performance Management Chart (PMC):
given a race result, retrieve the CTL (fitness) and TSB (form) on race day and
in the 42-day window before it, and compare with finishing time across multiple
races.

| Field | Java type | Source | Notes |
|---|---|---|---|
| `id` | `UUID` | system-generated | Surrogate PK |
| `athleteId` | `UUID` | system | Canonical athlete identity |
| `raceDate` | `LocalDate` | manually entered | Joins to `athlete_training_load.date` and `TrainingDay.date` |
| `raceName` | `String` | manually entered | Full event name (e.g. "Valencia Marathon 2025") |
| `raceType` | `RaceType` | manually entered | Enum: `ROAD_5K`, `ROAD_10K`, `HALF_MARATHON`, `MARATHON`, `TRAIL_RACE`, `TRIATHLON`, `CYCLOSPORTIVE`, `OTHER` |
| `distanceMeters` | `Double` | manually entered | Actual measured distance; stored separately from `raceType` (real course distances vary) |
| `chipTimeSecs` | `Integer` | manually entered | Official chip time; nullable if DNF |
| `gunTimeSecs` | `Integer` | manually entered | Start-gun to finish; nullable |
| `overallRank` | `Integer` | manually entered | Optional; nullable |
| `ageGroupRank` | `Integer` | manually entered | Optional; nullable |
| `dnf` | `Boolean` | manually entered | Did-not-finish flag |
| `notes` | `String` | manually entered | Free text; nullable |
| `linkedActivityId` | `UUID` | manually entered or matched | Canonical activity UUID (from Strava) for the race effort; nullable — lets the system join with activity metrics (NP, EF) for the race itself |
| `createdAt` | `Instant` | system | |
| `updatedAt` | `Instant` | system | |

**Identity / natural key:** No unique constraint beyond surrogate UUID. An athlete
could in principle race twice on the same day (e.g., a morning 5K and an afternoon
charity run). A soft-unique index on `(athlete_id, race_date, race_type)` for
duplicate detection is recommended but not enforced.

**TimescaleDB:** Regular relational table. An athlete completes at most a few dozen
races per year. No partitioning needed.

---

## 2. Correlation Model

### 2.1 The correlation anchor: date

`TrainingDay.date` is the correlation anchor. Every entity in this model — existing
and new — is associated with a calendar date:

| Entity | Date field | Cardinality per athlete per date |
|---|---|---|
| `athlete_training_load` | `date` | exactly 1 (computed daily by job) |
| `activities` | `start_date::date` | 0..N (zero or more activities per day) |
| `TrainingDay` | `date` | 0..1 (only if athlete entered subjective data) |
| `NutritionLog` | `log_date` | 0..N (zero or more entries per day) |
| `HealthEvent` | `start_date` to `end_date` | 0..N (events spanning a range of dates) |
| `RaceResult` | `race_date` | 0..N (unlikely to be > 1) |

**Why date and not a `TrainingDay` foreign key?** A `TrainingDay` row only exists
when the athlete enters subjective data. If `NutritionLog` required a `training_day_id`
FK, logging food intake on a day with no subjective entry would either fail or
require auto-creating an empty `TrainingDay` row. Using the date column directly
on each entity avoids this coupling and keeps every entity independently useful
even when others are absent.

### 2.2 Join patterns

#### `NutritionLog` ↔ `athlete_training_load`

The primary analytics join: "what was my training load on days where I consumed
more than X kcal?"

```sql
SELECT
    atl.date,
    SUM(nl.energy_kcal)      AS daily_kcal,
    SUM(nl.carbohydrates_g)  AS daily_carbs_g,
    atl.tss_day,
    atl.ctl,
    atl.tsb
FROM athlete_training_load atl
LEFT JOIN nutrition_log nl
    ON nl.athlete_id = atl.athlete_id
    AND nl.log_date  = atl.date
WHERE atl.athlete_id = :athleteId
GROUP BY atl.date, atl.tss_day, atl.ctl, atl.tsb
```

This is a date-equality join with a GROUP BY. Expected to be efficient with the
`(athlete_id, date)` index on `athlete_training_load` and the
`(athlete_id, log_date)` index on `nutrition_log`.

#### `HealthEvent` ↔ `athlete_training_load`

Health events span ranges. The join must expand the range into individual dates:

```sql
SELECT
    atl.date,
    atl.tss_day,
    atl.ctl,
    he.type AS health_event_type,
    he.severity
FROM athlete_training_load atl
JOIN health_events he
    ON he.athlete_id = atl.athlete_id
    AND atl.date BETWEEN he.start_date AND COALESCE(he.end_date, he.start_date)
WHERE atl.athlete_id = :athleteId
```

The `COALESCE(he.end_date, he.start_date)` handles point-in-time events (where
`end_date` is null) by treating them as single-day events. This join is a range
scan — the index on `(athlete_id, start_date)` supports the lower bound; a
partial index on `(athlete_id, end_date)` supports filtering out old events.

#### `RaceResult` ↔ `athlete_training_load` (PMC correlation)

Two windows are standard for the Performance Management Chart:

| Window | Purpose | SQL |
|---|---|---|
| Race-day snapshot | CTL / ATL / TSB at peak performance | `atl.date = race.race_date` |
| 42-day preceding window | Fitness trajectory before the race | `atl.date BETWEEN race.race_date - 42 AND race.race_date - 1` |
| 7-day preceding window | Short-term fatigue load | `atl.date BETWEEN race.race_date - 7 AND race.race_date - 1` |
| 84-day preceding window | Peak CTL in the build phase | `atl.date BETWEEN race.race_date - 84 AND race.race_date - 1` |

The 42-day window matches the CTL time constant (α = 1/42). The 7-day window
matches the ATL time constant. Both are relevant to explaining race performance.
An 84-day look-back captures the full build phase typical of marathon preparation.

```sql
-- Race-day CTL, ATL, TSB for each race result
SELECT
    rr.race_name,
    rr.race_date,
    rr.chip_time_secs,
    atl.ctl  AS race_day_ctl,
    atl.atl  AS race_day_atl,
    atl.tsb  AS race_day_tsb
FROM race_results rr
JOIN athlete_training_load atl
    ON atl.athlete_id = rr.athlete_id
    AND atl.date      = rr.race_date
WHERE rr.athlete_id = :athleteId
  AND rr.dnf = false
ORDER BY rr.race_date DESC
```

#### `TrainingDay` ↔ any entity

`TrainingDay` is always a LEFT JOIN target, not a required base. Example:

```sql
-- Daily summary including subjective state where entered
SELECT
    atl.date,
    atl.tss_day,
    td.perceived_effort,
    td.subjective_state
FROM athlete_training_load atl
LEFT JOIN training_days td
    ON td.athlete_id = atl.athlete_id
    AND td.date      = atl.date
WHERE atl.athlete_id = :athleteId
```

### 2.3 Common vs. ad-hoc joins

| Join | Expected frequency | Notes |
|---|---|---|
| `athlete_training_load` + `nutrition_log` | Common — weekly load view | Core analytics use case |
| `athlete_training_load` + `health_events` | Common — load overview | Flags disrupted training weeks |
| `athlete_training_load` + `race_results` | Common — PMC view | Race performance analysis |
| `nutrition_log` + `race_results` (pre-race nutrition window) | Ad-hoc | "What did I eat the week before my fastest race?" |
| `training_days` + any entity | Ad-hoc | Only relevant when subjective data is present |
| `activities` + `race_results` via `linked_activity_id` | Ad-hoc | Joins activity metrics (NP, EF) to race results |
| All five entities in one query | Ad-hoc / reporting only | Dashboard summary; not a hot path |

N+1 risk: the PMC view that fetches race results and then their preceding 42-day
load windows is the most join-intensive common query. It should be implemented
as a single SQL query with a date range, not as N separate lookups per race.

---

## 3. Analytics Use Cases Enabled

These questions are impossible to answer with Strava data alone. Each maps to
specific joins in §2.

### UC-1: PMC taper analysis
*"What was my CTL, ATL, and TSB on the day of each race I have entered, and
how did those values correlate with my finishing time relative to course distance?"*

- Entities: `RaceResult` + `athlete_training_load`
- Join: date-equality on `race_date`
- Insight: identifies whether higher TSB (fresher form) consistently predicts
  better relative performance, and whether there is an optimal taper TSB range
  for this athlete
- Gap closed: Design 002 §3.3 — "no race vs training distinction" and
  "race result ingestion"

### UC-2: Nutritional load correlation
*"Do weeks where I consumed more than 2500 kcal/day on average correlate with
higher weekly TSS, and how does the effect differ between run-heavy weeks and
rest weeks?"*

- Entities: `NutritionLog` + `athlete_training_load` + `activities`
- Join: group by ISO week; compare AVG(daily_kcal) with SUM(tss_day)
- Insight: determines whether the athlete under-fuels on high-training weeks
  (a common cause of overtraining and injury)
- Gap closed: Design 002 §3.4 — "training load vs nutrition / health events"

### UC-3: Illness impact on load recovery
*"When I record an illness event, how many days does it take for my ATL to
drop back below 30 and for TSB to return above −5, and does severity affect
recovery time?"*

- Entities: `HealthEvent` (type = `ILLNESS`) + `athlete_training_load`
- Join: range join on illness dates; then forward-scan for recovery threshold
- Insight: gives the athlete a personal recovery baseline, making it easier
  to decide when to return to training after illness
- Gap closed: Design 002 §3.4 — "training load vs nutrition / health events"

### UC-4: Pre-race carbohydrate intake
*"What was my average daily carbohydrate intake in the 3 days before my best
marathon performance, and how does this compare to other marathon build-ups?"*

- Entities: `NutritionLog` + `RaceResult`
- Join: look back 3 days from each race date in `nutrition_log`; aggregate
  `carbohydrates_grams` per athlete per race
- Insight: identifies the athlete's personal carbohydrate loading pattern that
  precedes peak performances
- Gap closed: Design 002 §3.4 — "training load vs nutrition / health events"

### UC-5: Subjective state on high-TSS weeks
*"What subjective state do I report most often on weeks with total TSS above
400, and how does perceived effort on those days compare to weeks below 250 TSS?"*

- Entities: `TrainingDay` + `athlete_training_load`
- Join: LEFT JOIN on date; GROUP BY ISO week; filter on SUM(tss_day) thresholds
- Insight: determines whether the athlete recognises high-load weeks accurately,
  or whether perception and load diverge (a predictor of overtraining)
- Gap closed: Design 002 §3.3 — "training plan / goal context"; and §1.2 —
  `perceived_exertion` stored in activities but not aggregated at day level

### UC-6: Medication event and CTL continuity
*"On how many occasions did I enter a MEDICATION or ILLNESS health event, and
what was the average CTL drop (Δ ctl) in the 14 days following the event start
date?"*

- Entities: `HealthEvent` + `athlete_training_load`
- Join: range scan on event dates; compute CTL delta between event start and
  start + 14 days
- Insight: quantifies the training cost of health disruptions in CTL terms,
  which helps budget recovery time in future training plans
- Gap closed: Design 002 §3.4 — "training load vs nutrition / health events"

### UC-7: Race-day activity metric correlation
*"For the activities linked to race results where I have normalized power data,
is there a relationship between race-day Efficiency Factor and finishing time
for events of similar distance?"*

- Entities: `RaceResult` + `activities` (via `linked_activity_id`) + `activity_metrics`
- Join: `RaceResult.linkedActivityId → activities.id → activity_metrics.activity_id`
- Insight: tests whether NP and EF computed for the race itself predict
  performance level across events of similar distance, giving an alternative
  predictor to TSB alone
- Gap closed: Design 002 §1.9 — `activity_metrics.intensity_factor` exists but
  is never written; this use case will require IF to be implemented (tracked
  separately per §3.3 of Design 002)

---

## 4. Schema Implications

### 4.1 `training_days`

| Property | Value |
|---|---|
| Table name | `training_days` |
| Schema | `collector` |
| TimescaleDB | **No** — regular PostgreSQL table. Low write volume; no time-series analytics performed on this table itself. |
| Primary key | `id UUID` — surrogate, system-generated |
| Unique constraint | `(athlete_id, date)` — enforces one entry per athlete per day |
| Indexes | `(athlete_id, date)` covering index (unique constraint provides this) |
| FK | `athlete_id` → canonical athlete UUID (not `athletes.strava_id`) |

Note: `athlete_id` here refers to the canonical UUID identity used by ADR-001,
not the Strava-specific `BIGINT strava_id`. This table belongs to the
neutral domain layer, not to `collector-strava`.

---

### 4.2 `nutrition_log`

| Property | Value |
|---|---|
| Table name | `nutrition_log` |
| Schema | `collector` |
| TimescaleDB | **Deferred.** Volume is low for a single athlete. If multi-athlete write volume warrants it, partition by `log_date`. |
| Primary key | `id UUID` — surrogate |
| Natural grouping key | `(athlete_id, log_date)` — not unique; many rows per day |
| Indexes | `(athlete_id, log_date)` — primary analytics access pattern; `(external_food_item_id)` — for food catalog lookups (partial, if source = `OPEN_FOOD_FACTS`) |

---

### 4.3 `health_events`

| Property | Value |
|---|---|
| Table name | `health_events` |
| Schema | `collector` |
| TimescaleDB | **No** — regular PostgreSQL table. Events are sparse (tens per year per athlete) and span date ranges; time-series partitioning adds no value. |
| Primary key | `id UUID` — surrogate |
| Indexes | `(athlete_id, start_date)` — for forward-looking queries ("events active as of date X"); `(athlete_id, end_date)` — for backward-looking range scans; partial on `WHERE end_date IS NOT NULL` to exclude open-ended events from the second index efficiently |

---

### 4.4 `race_results`

| Property | Value |
|---|---|
| Table name | `race_results` |
| Schema | `collector` |
| TimescaleDB | **No** — regular PostgreSQL table. An athlete completes at most a few dozen races per year. |
| Primary key | `id UUID` — surrogate |
| Indexes | `(athlete_id, race_date)` — primary PMC join; `(athlete_id, race_type)` — for filtered queries ("show all marathons") |
| FK | `linked_activity_id UUID` → canonical `activities` identity (nullable) |

---

### 4.5 Module placement

These tables do not belong to `collector-strava`. They record manually-entered
data that is independent of any external integration source. Their natural home
is a future `collector-journal` module (or similar neutral module), analogous to
how `collector-query` holds neutral read-models.

Entities for these tables must follow the same pattern as ADR-001: the JPA entities
and repositories belong in the module that owns the write path; neutral read-models
and query ports belong in `collector-query`; `collector-api` depends only on the
neutral ports.

---

## 5. Open Questions

### 5.1 Food item normalization strategy

The current design stores `externalFoodItemId` (an Open Food Facts barcode) and
denormalized `foodItemName` + macros at entry level. Two unresolved questions:

1. Should the system maintain an internal `food_items` cache table populated from
   OFT lookups, or always look up from OFT at entry time and store results
   immediately? A cache avoids repeated OFT API calls at the cost of a sync job.
2. If a user adds the same food item repeatedly, should the system deduplicate by
   `externalFoodItemId` (autocomplete from past entries) or treat each log entry
   as standalone?

These are implementation questions, not design blockers. The schema as defined in
§1.2 supports both approaches.

### 5.2 Meal-level granularity

`NutritionLog` models individual food items with a `mealType` enum. An alternative
design introduces a `Meal` entity as a grouping layer (Meal 1:N NutritionLog),
enabling per-meal macro totals and temporal ordering within a day. This adds
a join to every daily total query. Deferred until actual usage patterns reveal
whether per-meal grouping is needed for any analytics use case.

### 5.3 Sleep tracking

Sleep quality is a significant predictor of training adaptation and recovery.
`SLEEP_ISSUE` is included in `HealthEventType` for flagging disrupted sleep, but
structured sleep data (total hours, sleep cycles, HRV during sleep) is not modelled.
Two future directions:

- Extend `HealthEvent` with optional fields `sleepHours: Double` and
  `sleepQuality: SleepQuality` for manual entry.
- Introduce a separate `SleepRecord` entity (one per night) if wearable integration
  (Apple Health, Garmin, Oura) is added. This would be a time-series entity and
  a candidate for a TimescaleDB hypertable.

Sleep tracking is not in scope for this domain model version.

### 5.4 Historical body weight

Design 002 §3.3 identifies that `athletes.weight` is a single current snapshot
with no history. If weight trend analysis is desired (e.g., "how did my weight
change during the 12-week marathon build?"), two options exist:

1. Add a `bodyWeightKg: Double` field to `TrainingDay` — simple, but assumes
   the athlete weighs themselves daily.
2. Introduce a separate `weight_log` table (date, weight_kg) — more flexible,
   does not couple body weight to subjective diary entries.

Neither is decided here.

### 5.5 RaceResult distance normalization

`raceType` is a descriptive enum; `distanceMeters` is the authoritative value.
The model does not validate that a `HALF_MARATHON` result is within a tolerance
of 21,097 m. Whether to enforce this at the application layer (warn on deviation
> 5%) or leave it entirely to the user is an implementation decision.

### 5.6 Athlete identity across sources

All four entities in this document use `athleteId: UUID` — the canonical identity
defined by ADR-001. This UUID is currently mapped to `athletes.id` (the Strava
entity). When a second source is added, the mapping layer must ensure that the
same UUID is used regardless of which source an activity came from. This is a
pre-existing constraint from ADR-001, not a new gap introduced by this document.
