# Audit 002 — Milestone 2 Security & Performance (issues #32–#35)

- **Type:** Incremental audit (security + performance), AUDIT ONLY — no source files modified
- **Scope:** everything merged to `develop` after `472209d` (audit 001 cutoff), i.e. PRs #77 (#32 HealthEvent), #79 (#33 RaceResult), #81 (#34 Diary tab), #83 (#35 race markers)
- **Audit basis:** `origin/develop` @ `7aa43eb`
- **Date:** 2026-07-03
- **Prior audit:** audit 001 (Milestone 1, through #31). Its accepted findings
  (single-key IDOR for single-user, unbatched backfill transaction, `/q/metrics`
  unauthenticated, `config.json` as readable static asset) are **not re-reported**.
  Where M2 **replicated** an audit-001 finding into new surface (FK-violation 500,
  unbounded `notes`), the new instances are reported below because they are new
  occurrences, not re-litigations.
- **Evidence:** file:line references from greps/reads on `7aa43eb`; live schema
  spot-checks against the running TimescaleDB dev container; full reactor run.

Diff scope verified with `git diff --stat 472209d..origin/develop`: 30 files,
+2169/−14 — all within collector-journal, collector-query, collector-api
(HealthEvent/RaceResult), collector-dashboard (Diary tab, TrendView markers),
and the two journal migrations. No runner/core/strava production code changed.

---

## Section 1 — New API surface (health-events, race-results)

### 1.1 Authentication coverage

Both new endpoint pairs live under `/api/v1/...`:

- `@Path("/api/v1/athletes/{athleteId}/health-events")` — `HealthEventResource.java:30`
- `@Path("/api/v1/athletes/{athleteId}/race-results")` — `RaceResultResource.java:27`

`ApiKeyFilter` (audit 001 §1) matches any path starting `/api/v`
(`ApiKeyFilter.java:39`), so both are behind `X-API-Key` with no per-endpoint
opt-out. Confirmed by test: `HealthEventResourceTest.shouldReturn401WithoutApiKey`
and `RaceResultResourceTest.shouldReturn401WithoutApiKey` both assert 401.
`git diff 472209d..develop` shows **no other new `@Path`** — no new admin/dev
surface (see §4). ✅

### 1.2 Input validation parity — field-by-field

Standard under audit: Java-level validation **before** any DB constraint
(clean 400, never 500), as established in #24.

**HealthEvent — POST `/health-events` (`HealthEventResource.java:91–107`)**

| Field | Java validation | Outcome |
|---|---|---|
| `startDate` required | `:91-92` | clean 400 `'startDate' is required` ✅ |
| `title` required/non-blank | `:94-95` | clean 400 ✅ |
| `title` max length | **none** — entity `@Column` without `length` (`HealthEvent.java:38`) → varchar(255) (`037`, `title VARCHAR(255)`) | **>255 chars → DB length violation → 500** ⚠️ (finding F-1) |
| `type` required | `:97-98` | clean 400 ✅ |
| `type` enum whitelist | `VALID_TYPES` set (`:43-44`, `:100-102`) checked **before** `HealthEventType.valueOf` and before DB CHECK `chk_health_events_type` | clean 400 ✅ (DB CHECK is backstop only) |
| `endDate >= startDate` | **yes, validated**: `:104-106` (`isBefore` → 400 `'endDate' must not be before 'startDate'`); DB CHECK `chk_health_events_date_order` is backstop | clean 400 ✅ |
| `notes` length | none — `TEXT` column (`HealthEvent.java:41`, migration 037) | no 500 path (unbounded), capped only by ~10 MB default body limit — replicates audit-001 finding #5 ⚠️ (F-3) |

**RaceResult — POST `/race-results` (`RaceResultResource.java:82–102`)**

| Field | Java validation | Outcome |
|---|---|---|
| `raceDate` required | `:82-83` | clean 400 ✅ |
| `raceName` required/non-blank | `:85-86` | clean 400 ✅ |
| `raceName` max length | **none** (`RaceResult.java:39`, varchar(255) in 038) | **>255 chars → 500** ⚠️ (F-1) |
| `distanceMeters` required | `:88-89` | clean 400 ✅ |
| `distanceMeters > 0` | `:91-92` (before DB CHECK `chk_race_results_distance_positive`) | clean 400 ✅ |
| `goalFinishTime > 0` if present | `:94-95` | clean 400 ✅ (no upper bound — data quality only, F-6) |
| `actualFinishTime > 0` if present | `:97-98` | clean 400 ✅ |
| `position > 0` if present | `:100-101` | clean 400 ✅ |
| `notes` length | none — `TEXT` | replicates F-3 ⚠️ |
| `linkedActivityId` referencing a non-existent activity | accepted silently — **deliberate**: nullable UUID with **no FK** (`RaceResult.java:56-65`, javadoc states the source-agnostic rationale; decision confirmed in #33) | by design, accepted (F-7) |

Note on finish times "non-negative": the implemented rule is strictly
**positive** (`<= 0` rejected), which also rejects 0 — matches both the #33 spec
and the DB CHECKs (`chk_race_results_positive_ints`). Verified in the live dev
DB: `pg_get_constraintdef` shows all three nullable ints wrapped in
`(x IS NULL OR x > 0)` (re-checked during #33 closure and spot-checked again for
this audit — constraints present on the running instance).

**Both endpoints — non-existent `athleteId`:** the pattern from audit-001
finding #3 (training-days) was **replicated, not fixed**. Neither resource
checks athlete existence; `HealthEventService.create` / `RaceResultService.create`
persist directly, and both tables carry
`fk_health_events_athlete_id` / `fk_race_results_athlete_id` →
`athlete_profiles.id` (037/038). POST for an unknown athlete → FK violation →
`PersistenceException` → **500** (still no global `ExceptionMapper`, grep clean).
⚠️ (F-2 — now three endpoints share this bug: training-days, health-events,
race-results. The already-planned hardening-block item fixes all three at once.)

### 1.3 Date-span overlap filter (health-events GET)

JPQL (`HealthEventRepository.java:22-23`):

```
athleteId = ?1 AND startDate <= ?3 AND (endDate IS NULL OR endDate >= ?2)
```

Truth table against window `[from, to]` (endDate NULL = ongoing = +∞):

| Case | startDate | endDate | `start ≤ to` | `end IS NULL OR end ≥ from` | Result |
|---|---|---|---|---|---|
| Starts before, ends inside | < from | ∈ [from,to] | T | T | included ✅ |
| Starts inside, ends after | ∈ [from,to] | > to | T | T | included ✅ |
| Fully inside | ∈ [from,to] | ∈ [from,to] | T | T | included ✅ |
| Fully spanning | < from | > to | T | T | included ✅ |
| Ongoing, started before window | < from | NULL | T | T (NULL branch) | included ✅ |
| Entirely before | any | < from | T | **F** | excluded ✅ |
| Entirely after (incl. ongoing starting after `to`) | > to | any/NULL | **F** | — | excluded ✅ |

This is the standard interval-overlap condition; **correct for all required
cases**. Not just derived on paper: `HealthEventOverlapTest` exercises the real
repository against H2 with all seven rows above (four included, three excluded)
plus an ordering assertion. ✅ Zero findings.

---

## Section 2 — New persistence layer

### 2.1 Migrations 037 / 038 — FKs, CHECKs, indexes

| Item | 037 `health_events` | 038 `race_results` |
|---|---|---|
| FK target | `athlete_profiles.id`, `ON DELETE RESTRICT` (`fk_health_events_athlete_id`) ✅ | same (`fk_race_results_athlete_id`) ✅ |
| CHECKs | `chk_health_events_type` (4-value whitelist), `chk_health_events_date_order` (`end_date IS NULL OR end_date >= start_date`) ✅ | `chk_race_results_distance_positive`, `chk_race_results_positive_ints` (all three nullable ints NULL-safe) ✅ |
| Index | `idx_health_events_athlete_start_date (athlete_id, start_date)` | `idx_race_results_athlete_race_date (athlete_id, race_date)` |
| Unique | none (no natural key — deliberate, both) | none (deliberate) |
| Rollback | full, reverse order ✅ | full, reverse order ✅ |

Both migrations were applied and inspected against the **real TimescaleDB dev
instance** during #32/#33 implementation (Liquibase `ran successfully`,
`pg_get_constraintdef` output verified for every constraint, including the full
4-value type whitelist and the NULL-safe positive-int CHECK). Re-spot-checked
for this audit on the still-running container: all constraints and both indexes
physically present. ✅

Enum/range semantics all have a CHECK; every CHECK has a Java pre-validation
except the varchar(255) length limits (F-1) and the FK existence (F-2).

### 2.2 Overlap query index support

Query shape: `athlete_id = ? AND start_date <= ? AND (end_date IS NULL OR end_date >= ?)`.
`idx_health_events_athlete_start_date (athlete_id, start_date)` supports the
equality + `start_date` range scan; `end_date` is an unindexed **residual
filter** applied to the athlete's slice. Verdict: **not a real concern.** The
entity is manual input at tens-to-hundreds of rows per athlete for the life of
the system; even a full per-athlete scan is sub-millisecond at 10³ rows, and the
residual filter touches only rows already narrowed by the index. An `end_date`
index would be wasted write cost. Explicitly checked, explicitly fine.

### 2.3 race_results dashboard range query

`raceDate >= ? AND raceDate <= ? ORDER BY raceDate ASC`
(`RaceResultRepository.java:20-21`) is exactly covered by
`idx_race_results_athlete_race_date (athlete_id, race_date)` — equality on the
leading column, range + order on the second. The dashboard (TrendView) queries a
30/60/90-day window; the API's own default is 12 months — both bounded, both
index-served, rows are sparse (a few dozen races/year). ✅ Zero findings.

### 2.4 linkedActivityId

Free `UUID`, **no FK** (`RaceResult.java:64-65`; 038 has no FK on it; verified
live — the only FK on `race_results` is `athlete_id`). Rationale documented in
the entity javadoc (`RaceResult.java:56-63`): collector-journal stays
source-agnostic; a race may be logged with no linked activity or linked to a
non-Strava source. The BIGINT/UUID identity note from #33 (also documented on
the entity, `RaceResult.java:27-35`): `athlete_training_load.athlete_id` is a
raw Strava BIGINT while journal uses the canonical UUID — any future PMC
correlation must bridge via `integration_accounts`. Implication for
`linkedActivityId`: it is expected to hold the **canonical activity UUID**
(activity_references space), never a Strava numeric id; nothing enforces this
today, which is the accepted trade-off of the no-FK decision. Round-trip of an
orphan UUID proven in `RaceResultRepositoryTest.linkedActivityIdRoundTripsAsNullAndAsUuidWithNoFk`.
Accepted as-is (F-7).

---

## Section 3 — Frontend additions (Diary tab, race markers)

### 3.1 XSS surface

```
grep -rnE "dangerouslySetInnerHTML|innerHTML|eval\(|document\.write" collector-dashboard/src
→ no matches
```

All user-controlled strings render as JSX text nodes (React-escaped):
`TrainingDay.notes` → `{notesToShow}` inside a `<p>`
(`DiaryEntryCard.tsx:86-88`, `whitespace-pre-wrap` preserves newlines but is
CSS-only — no HTML interpretation); `race.raceName` → `{race.raceName}` in the
tooltip (`TrendView.tsx:103`). No `dangerouslySetInnerHTML` anywhere in the
dashboard. ✅ Zero findings.

### 3.2 Race-marker merge — timezone handling

**The merge and marker positioning are string-to-string end to end — no `Date`
object touches `raceDate` on that path:**

- window filter: `raceList.filter(r => r.raceDate >= fromStr && r.raceDate <= toStr)` — lexicographic ISO compare (`TrendView.tsx:151`)
- map key: `new Map(races.map(r => [r.raceDate, r]))` (`:157`)
- merge: `raceByDate.get(item.date)` — raw string vs raw string (`:160`)
- marker: `<ReferenceLine x={race.raceDate}>` matched against the categorical
  x-axis whose categories are the raw `item.date` strings (`:228`)

No midnight shift is possible on positioning/attachment. ✅

Two **display-only** `Date` usages exist, both replicating pre-existing
dashboard conventions (baseline `472209d` TrendView already had
`new Date(d)` in the tick formatter at old line 44 and
`new Date(l).toLocaleDateString()` in the tooltip label at old line 128):

- `TrendTooltip` label: `new Date(String(label)).toLocaleDateString()` (`TrendView.tsx:94`)
- `DiaryEntryCard` date header: `new Date(iso).toLocaleDateString(...)` (`DiaryEntryCard.tsx:14`) — same convention as ActivitiesView's `fmtDate`

A date-only ISO string is parsed as **UTC midnight**; local formatters shift it
one day **backwards for viewers west of UTC** (UTC−n). For the deployment owner
(UTC+2) there is no shift. This affects the printed date label only — never
which race attaches to which row, nor where the marker is drawn. Low, cosmetic,
pre-existing convention (F-5).

Related boundary nuance (also pre-existing, shared with WeeklyLoadView):
`from`/`to` are computed via `toISOString()` (UTC "today", `TrendView.tsx:131-135`,
`DiaryView.tsx:24-28`) — for ~n hours after local midnight in a UTC+n timezone
the window's `to` is still UTC-yesterday, so an entry dated local-today can be
excluded until UTC catches up. Informational (F-8).

### 3.3 New fetch calls — error states, key leakage

- **DiaryView:** `.catch(e => setError(e.message))` (`DiaryView.tsx:35`) →
  visible red error block (`:73-77`); empty state is a dedicated block, not a
  blank tab. ✅
- **TrendView:** the two fetches run in `Promise.all` (`TrendView.tsx:137-152`)
  with the same catch → visible error block (`:194-198`). ⚠️ One resilience
  note: with `Promise.all`, a failure of the **race-results** call now blanks
  the *entire* Fitness Trends chart, which before M2 depended only on
  training-load. Graceful degradation (chart without markers, e.g. via
  `Promise.allSettled`) would be strictly better. Low (F-4).
- **Key leakage:** `apiFetch` sends the key as the `X-API-Key` header only —
  never in a URL; error messages surface `body.error` or `HTTP <status>`
  (`api/client.ts`), never the key. Grep for `console.*` in the three touched
  views: no console logging at all. The Diary empty state interpolates the
  **athleteId** into the hint text (`DiaryView.tsx:88-92`) — not a new
  exposure: the same id is already rendered in the page header (`App.tsx`,
  "athlete: {config.athleteId}"). ✅

### 3.4 Bundle / dependencies

```
git diff 472209d..origin/develop -- collector-dashboard/package.json
→ empty (NO changes)
```

No new dependency for M2 — Diary tab and markers use the existing react +
recharts + Tailwind stack. Nothing to license-review. ✅ Zero findings.

---

## Section 4 — Regression check against audit 001

- **New admin/dev endpoints:** none. The only `@Path` additions in the M2 diff
  are the two `/api/v1/.../health-events` and `/race-results` resources
  (verified: `git diff 472209d..origin/develop | grep '^+.*@Path('`). ✅
- **New native SQL:** none. `git diff … | grep '^+.*createNativeQuery'` → empty;
  the repo-wide inventory remains the two occurrences from audit 001 §3
  (health check `SELECT 1`; enum-whitelisted summary-view query). The `sql:`
  blocks in migrations 037/038 are static Liquibase DDL constants (CHECK
  definitions) with no runtime parameters — no injection surface. ✅
- **Test count:** full reactor on `7aa43eb`: **BUILD SUCCESS, 233 tests** —
  core 14 · query 8 · strava 101 · **api 96** · runner 14. Baseline before M2
  was 200 (api 63); all growth is +33 in collector-api (#32: 17, #33: 16
  — HealthEventResourceTest 15 + HealthEventOverlapTest 2 +
  RaceResultResourceTest 14 + RaceResultRepositoryTest 2). **Nothing
  decreased.** ✅
- **ArchUnit:** `ApiDoesNotDependOnStravaTest` — `Tests run: 2, Failures: 0`
  on this audit's own reactor run. Additionally verified by grep that neither
  collector-journal, collector-query, nor collector-api contains any
  `import com.zensyra.collector.strava` — the new journal entities introduce no
  api → strava path (journal imports only core/query/jakarta/quarkus). ✅

---

## Prioritized findings

| # | Severity | Finding | Section | Suggested action |
|---|---|---|---|---|
| F-1 | **Medium** | `title` (health_events) and `race_name` (race_results) have no Java max-length check; a >255-char value passes all resource validation and hits the varchar(255) column → DB error → **500**. New 500 path introduced by M2 (`HealthEventResource.java:94-95` validates blank only; `HealthEvent.java:38`, `RaceResult.java:39`). | 1.2 | **Fold into hardening block** (validation item): add `length() > 255` → 400 in both resources, or `@Size` + the planned global `ExceptionMapper` as backstop. |
| F-2 | **Medium** | POST with a non-existent `athleteId` → FK violation → **500**, on **both** new endpoints — replicates audit-001 finding #3 (training-days) instead of fixing the pattern; the bug now exists on three write endpoints. | 1.2 | **Fold into hardening block** (global `ExceptionMapper` mapping constraint violations to 400/404, and/or an athlete-existence check) — one fix covers all three. |
| F-3 | Low | `notes` is unbounded `TEXT` on both new tables (capped only by the ~10 MB default body limit) — replicates audit-001 finding #5 onto two new columns. No 500 path, storage/data-quality only. | 1.2 / 2.1 | **Fold into hardening block** (extend the planned notes/weightKg validation to health_events.notes and race_results.notes). |
| F-4 | Low | TrendView fetches training-load + race-results with `Promise.all` (`TrendView.tsx:137`): a race-results failure now blanks the whole Fitness Trends chart, which previously degraded independently. | 3.3 | Next block (or fold into hardening): switch to `Promise.allSettled` — render the chart, drop markers, optionally show a soft notice. |
| F-5 | Low | Date-only ISO strings displayed via `new Date(...)` local formatters (`DiaryEntryCard.tsx:14`, `TrendView.tsx:94`) shift the **printed** date one day back for viewers in UTC-negative timezones. Pre-existing dashboard convention replicated by M2; merge/marker positioning is string-safe and unaffected; deployment owner (UTC+2) unaffected. | 3.2 | Accepted as-is for self-hosted; optional cosmetic fix (format from the string parts) if the dashboard ever targets arbitrary-timezone users. |
| F-6 | Low | No upper bounds on `distanceMeters` / finish times / `position` (e.g. a 10⁹-second finish is accepted). Data-quality only; DB types absorb the values; Jackson rejects true integer overflow with a 400. | 1.2 | Accepted as-is (matches the audit-001 posture on weightKg); optionally fold sanity ranges into the hardening validation item. |
| F-7 | Info | `linkedActivityId` accepts orphan UUIDs silently (no FK, no existence check). | 2.4 | **Accepted by design** — decision made and documented in #33 (source-agnostic journal); revisit only when a correlation feature actually consumes the link. |
| F-8 | Info | Dashboard date windows use UTC "today" (`toISOString`), so for a few hours after local midnight (UTC+n zones) a local-today entry can fall outside the window. Pre-existing pattern shared by all tabs. | 3.2 | Accepted as-is; note for a future shared date-utility refactor. |

**Nothing rated High or Critical.** No new unauthenticated surface, no
injection surface, no secret exposure, no XSS, no new dependencies, no test
regression, architecture rule intact. The two Mediums are both instances of the
already-planned hardening-block work (input-validation completeness + global
exception mapping) — M2 widened the surface those fixes must cover but
introduced no new class of problem.

**Zero-finding sections (checks run, came back clean):** §1.1 auth coverage
(path-prefix filter + 401 tests), §1.3 overlap correctness (truth table + real
H2 test), §2.2/2.3 index support for both new query patterns, §3.1 XSS
(grep for dangerous rendering), §3.4 dependencies (package.json diff empty),
§4 admin surface / native SQL / ArchUnit / test-count regressions.

---

*Audit only — no source files modified. This report
(`docs/audit/002-milestone2-security-performance-audit.md`) is the single file
created, left uncommitted for review.*
