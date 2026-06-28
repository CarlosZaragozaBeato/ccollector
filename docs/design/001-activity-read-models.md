# Design: Neutral Read-Models for `collector-api`

- Status: Accepted and implemented
- Date: 2026-06-28
- Relates to: [ADR-001 — `collector-api` Scope as a Multi-Integration API](../adr/001-collector-api-scope.md)

## Purpose

This document supports issue #1.2. It inventories the Strava-coupled state of
`collector-api` before the decoupling refactor, defines the neutral read-models
and query ports that replace those dependencies, and records the module placement
decision. Implementation was completed as issue #1.3.

---

## 1. Endpoint Inventory (before decoupling)

All read endpoints live under `/api/v1/athletes/{athleteId}` and are protected by
`X-API-Key`. The register endpoint is a write path and its decoupling is out of
scope for this design.

| Method | Path | DTO returned | Strava classes injected directly |
|--------|------|-------------|----------------------------------|
| `POST` | `/api/v1/athletes/register` | `AthleteRegisterResponseDto` | `StravaOAuthService` (write path, out of scope) |
| `GET`  | `/api/v1/athletes/{athleteId}/activities` | `{ items: ActivityDto[], page, size }` | `ActivityRepository`, `Activity` |
| `GET`  | `/api/v1/athletes/{athleteId}/activity-metrics` | `ActivityMetricsDto` | `ActivityRepository`, `ActivityMetricsRepository`, `Activity`, `ActivityMetrics` |
| `GET`  | `/api/v1/athletes/{athleteId}/best-efforts` | `{ items: BestEffortDto[], limit }` | `ActivityBestEffortRepository`, `ActivityBestEffort` |
| `GET`  | `/api/v1/athletes/{athleteId}/stats` | `AthleteStatsDto` | `AthleteStatsSnapshotRepository`, `AthleteStatsSnapshot` |
| `GET`  | `/api/v1/athletes/{athleteId}/training-load` | `{ days, items: TrainingLoadDto[] }` | `AthleteTrainingLoadRepository`, `AthleteTrainingLoad` |

### Pre-refactor DTO analysis

**`ActivityDto`** — maps directly from `collector-strava`'s `Activity` entity.

| DTO field | Source entity field | Neutral? | Decision |
|-----------|-------------------|---------|---------|
| `stravaId` | `Activity.stravaId` | No | Replaced by `activityId` (canonical `TrainingSession.id` as `UUID`) |
| `name` | `Activity.name` | Yes | Kept |
| `sportType` | `Activity.sportType` | Yes (as `String`) | Kept as free `String`; no closed enum until second source confirms shared taxonomy |
| `distanceMeters` | `Activity.distance` | Yes | Kept |
| `movingTimeSecs` | `Activity.movingTime` | Yes | Kept |
| `startDate` | `Activity.startDate` | Yes | Kept |
| `totalElevationGain` | `Activity.totalElevationGain` | Yes | Kept |
| `averageHeartrate` | `Activity.averageHeartrate` | Yes | Kept |
| `averageWatts` | `Activity.averageWatts` | Yes (nullable) | Kept, null when not applicable |

Fields present in the entity but excluded from the neutral model: `stravaId`,
`gearId`, `sufferScore`, `trainer`, `commute`, `manual`, `privateActivity`,
`flagged`, `startLatlng`, `endLatlng`, `timezone`, `deviceName`,
`streamsSyncStatus`, `perceivedExertion`, `description`. None have a
confirmed cross-source equivalent.

**`BestEffortDto`** — maps from `ActivityBestEffort`.

| DTO field | Source field | Decision |
|-----------|-------------|---------|
| `activityStravaId` | `ActivityBestEffort.activityStravaId` | Replaced by canonical `activityId` (`UUID`) |
| `name` | `name` | Renamed to `name` — kept |
| `distance` | `distance` | Renamed to `distanceMeters` |
| `elapsedTime` | `elapsedTime` | Renamed to `elapsedTimeSecs` |
| `isKom` | `isKom` | **Dropped** — Strava segment leaderboard rank; no cross-source equivalent. See `BestEffort` Javadoc |
| `prRank` | `prRank` | Renamed to `personalRecordRank` |

**`AthleteStatsDto`** — flat hard-coded fields for Ride/Run/Swim × YTD/ALL\_TIME.

| Field pattern | Decision |
|--------------|---------|
| `ytdRideCount`, `ytdRideDistanceKm`, … | Replaced by `List<SportAggregate>` with `(sportType, StatsWindow)` |
| `allRideCount`, `allRunCount`, … | Same — list eliminates the fixed sport taxonomy |
| `biggestRideDistance`, `biggestClimbElevationGain` | **Dropped** — records without a window; closer to best-efforts than aggregates; no multi-source use case defined |

**`ActivityMetricsDto`** and **`TrainingLoadDto`** — straightforward mappings.
Fields are standard physiology concepts (PMC model, power/HR metrics) not tied to
Strava; no fields were dropped. `activityStravaId` was replaced by canonical
`activityId` (`UUID`). `TrainingLoad` adds `athleteId` to make the record
self-contained.

---

## 2. Neutral Read-Models

All models live in `com.zensyra.collector.query.model` within the `collector-query`
module (see §4). They are plain Java records with no JPA annotations, no Panache,
and no imports from any integration module.

### `Activity`

```java
public record Activity(
    UUID    activityId,       // canonical TrainingSession.id — never a source ID
    String  name,
    String  sportType,        // free String, not a closed enum (see note)
    Double  distanceMeters,
    Integer movingTimeSecs,
    Instant startDate,
    Double  totalElevationGain,
    Double  averageHeartrate,
    Double  averageWatts      // null when not applicable
) {}
```

`sportType` is a free `String` deliberately. A closed enum would bake Strava's
taxonomy into the canonical model; a second source may report sports Strava does not
have, or omit some Strava always has. A shared enum can be introduced once two sources
confirm overlapping values.

### `ActivityMetrics`

```java
public record ActivityMetrics(
    UUID       activityId,
    BigDecimal normalizedPower,
    BigDecimal variabilityIndex,
    BigDecimal efficiencyFactor,
    BigDecimal intensityFactor
) {}
```

Fields are standard power/HR domain concepts computable by any source that records
both channels. All are nullable — a source that cannot compute one must supply
`null`, not omit the record.

### `BestEffort`

```java
public record BestEffort(
    UUID    activityId,
    String  name,
    Integer distanceMeters,
    Integer elapsedTimeSecs,
    Integer personalRecordRank   // null when not a PR
) {}
```

`isKom` (Strava's segment leaderboard placement) is intentionally absent. It is a
social ranking within one platform's user base on one platform's segment database —
it has no meaning outside Strava and no equivalent concept in any other source. It
is not generalized to a renamed field: doing so would launder a provider-specific
fact into something that merely looks neutral. If segment leaderboard data is ever
needed, it belongs in a Strava-specific contract.

### `SportAggregate` and `StatsWindow`

```java
public enum StatsWindow { YEAR_TO_DATE, ALL_TIME }

public record SportAggregate(
    String      sportType,
    StatsWindow window,
    Integer     activityCount,
    Double      distanceMeters,
    Integer     movingTimeSecs,
    Double      elevationGainMeters
) {}
```

`sportType` is free text here for the same reason as in `Activity`. `StatsWindow`
is a closed enum because "year-to-date" and "all-time" are generic temporal concepts
independent of any source, unlike sport taxonomies.

### `AthleteStats`

```java
public record AthleteStats(
    UUID              athleteId,
    LocalDate         snapshotDate,
    List<SportAggregate> aggregates
) {}
```

A list of `SportAggregate` instead of flat columns eliminates Strava's implicit
Ride/Run/Swim ceiling: any sport any source reports becomes one more entry in the
list, with no schema change required.

### `TrainingLoad`

```java
public record TrainingLoad(
    UUID      athleteId,
    LocalDate date,
    Double    tssDay,
    Double    ctl,
    Double    atl,
    Double    tsb
) {}
```

CTL, ATL, TSB, and TSS are Coggan/Banister model concepts computed by this
system's own jobs from synced data, not values Strava reports. Straight
field-for-field translation is correct here.

### `QueryResult<T>` and `SourceFailure`

```java
public record SourceFailure(String sourceName, String reason) {}

public record QueryResult<T>(List<T> data, List<SourceFailure> failures) {
    public static <T> QueryResult<T> complete(List<T> data) { … }
    public boolean isPartial() { return !failures.isEmpty(); }
}
```

`QueryResult` wraps the outcome of a composed multi-source query. It exists for the
`/v2` contract, which surfaces partial failures explicitly rather than letting one
failing source take down the whole request. The `/v1` contract is unaffected.

---

## 3. Query Port Interfaces

Each resource injects a port interface, not a repository. Implementations live in
`collector-strava` and are wired by `collector-runner` via CDI.

```java
public interface ActivityQueryPort {
    List<Activity> listByAthlete(
        UUID athleteId, String sportType,
        Instant from, Instant to,
        int offset, int limit);
}

public interface ActivityMetricsQueryPort {
    Optional<ActivityMetrics> getByActivityId(UUID athleteId, UUID activityId);
}

public interface BestEffortQueryPort {
    List<BestEffort> listTopByAthlete(UUID athleteId, int limit);
}

public interface AthleteStatsQueryPort {
    Optional<AthleteStats> getLatestByAthlete(UUID athleteId);
}

public interface TrainingLoadQueryPort {
    List<TrainingLoad> listRecentByAthlete(UUID athleteId, LocalDate from);
}
```

`ActivityMetricsQueryPort` requires `athleteId` in addition to `activityId` so that
implementations must verify ownership; the constraint belongs in the contract, not
duplicated by each caller.

Activities also go through `ActivityQueryComposer`, which iterates all registered
`ActivityQueryPort` instances, merges results by `startDate` descending, and
applies paging after the merge. This is written as the general N-source case per
ADR-002 addendum, even though only the Strava adapter is registered today.

---

## 4. Module Placement

### Option A — read-models in `collector-core`

`collector-core` holds shared ingestion infrastructure (OAuth, credentials, sync
abstractions, rate limiting). Adding read-models here keeps the module count low.

Trade-offs:

- The rule "collector-core must not depend on other internal modules" is not
  violated — read-models and ports have no dependencies.
- `collector-core`'s existing responsibility is the ingestion side of the platform.
  Query models are the read side. Mixing the two roles widens the module's purpose.
- Future read-side complexity (composers, partial-result envelopes, conflict
  resolution) would grow inside `collector-core`, pulled in transitively by
  `collector-strava` and `collector-runner` even if they do not use it.

### Option B — dedicated `collector-query` module ✓ chosen

A new module owns neutral read-models, query ports, and read-side orchestration
(the `ActivityQueryComposer`). `collector-api` depends on `collector-query`.
Provider modules implement the ports and also depend on `collector-query`.
`collector-runner` wires the implementations.

Trade-offs:

- Adds one Maven module and one extra `<dependency>` block per provider.
- `collector-core` stays focused on ingestion infrastructure.
- Every new source has a clear, minimal target to implement against.
- Read-side aggregation logic (the composer, future conflict-resolution policies)
  has a natural home that does not affect ingestion modules.

**Decision: Option B.** The immediate cost is fixed and small. The benefit compounds
with each new source and each new read-side concern. The existing one-module-per-concern
pattern already present in the project extends naturally.

Resulting dependency graph:

```
collector-core     ← ingestion infra only; no read-model types
collector-query    ← read-models, query ports, ActivityQueryComposer
collector-strava   ← depends on collector-core + collector-query; implements ports
collector-api      ← depends on collector-core + collector-query; uses ports
collector-runner   ← aggregates all; wires Strava port implementations via CDI
```

`collector-api` has no compile-time dependency on `collector-strava`.

---

## 5. `/v2` Extension

After the initial decoupling, a `/v2` activities endpoint was added alongside `/v1`.
It uses the same read-models and `ActivityQueryComposer` but wraps the result in
`QueryResult<Activity>`, returning `200` with a `partial`/`failures` envelope when
one or more sources fail instead of propagating the exception.

`/v1` is frozen: its behavior (fail the whole request on any source error) is not
changed. `/v2` is the forward path for consumers that need explicit partial-result
semantics.
