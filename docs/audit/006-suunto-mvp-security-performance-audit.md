# Audit 006 — Suunto MVP (Workouts) Security & Performance (issues #1–#8)

- **Type:** Block audit (security + data integrity + performance), AUDIT ONLY — no source files modified
- **Scope:** the Suunto Workouts MVP, issues #1–#8 — the project's first second-source integration
- **Audit basis:** `origin/develop` @ `d34b872` (fresh `git fetch` at audit start)
- **Date:** 2026-07-17
- **Prior audits:** only `docs/audit/002` exists in the repository (see finding F-2). Its accepted
  findings — and the audit-001 findings it carries forward (single-key IDOR per ADR-002, `/q/metrics`,
  `config.json`, unbatched backfill) — are **not re-reported**; §5 re-verifies they did not regress.
- **Method:** every claim below is from a fresh read/grep/run on `d34b872`, never from a closure
  report's assertion. Full clean reactor run (surefire, `-DskipITs`, matching prior audits' counting
  convention) plus a targeted real-PostgreSQL failsafe run of `CredentialSeedIT`.

---

## Section 0 — Branch state verification

### 0.1 All eight issues genuinely present in `origin/develop`

`git fetch origin && git log origin/develop --oneline -30` at audit start. Mapping (develop commit ⇄ issue):

| Issue | develop commit | PR |
|---|---|---|
| #1 SUUNTO enum | `8893a97` | #139 |
| #2 module scaffold | `915e7d4` | #140 |
| #3 OAuth + refresher + migration 040 | `5bc4b85` | #141 |
| #4 credential seeding + migration 041 | **content inside `cdf4cd0`** — see 0.2 | #146 (not its own PR) |
| #5 API client + complete DTOs | `725e3cb` | #142 |
| #6 mapper to neutral models | `472710b` | #143 |
| #7 entity/migration 042 + jobs + port + collector | `cdf4cd0` | #146 |
| #8 rate limiter + filter | `d7b067b` | #145 |

Note on the hash referenced in the audit brief: `f5c322a` exists but is the **pre-squash feature-branch
commit** of #1 (`feature/suunto-integration-source-enum`); its develop-side equivalent is `8893a97`.
Also `2ca1ad1` ("Develop (#144)") is a develop→main promotion PR, unrelated to Suunto — it entered
develop's ancestry via the later `main`→develop merges (`776fc18`, `d34b872`).

### 0.2 How #4 landed, and merge-artifact cleanliness

The lesson case from #7's closure was re-verified formally:

- `#146` (`cdf4cd0`) is a **single-parent squash commit** (`git log cdf4cd0 --format='%H %p'` → one
  parent, `d7b067b`). The PR branch `feature/suunto-workouts-sync` contained a temporary local merge
  of `feature/suunto-credential-seeding`; the squash flattened all of it into one commit.
- **None of the temporary commits leaked into develop**:
  `git merge-base --is-ancestor` for `0bd4d4b` (the local merge commit), `e55f29c` (#7's work
  commit), and `9299572` (#4's original commit) — all three **NOT in develop's ancestry**. History
  is clean; no duplicate merge commits, no orphaned local-only commits.
- **#4's content is fully present**: `git show origin/develop:…/IntegrationCredential.java` contains
  `apiSubscriptionKey` (encrypted field + accessors), migrations `040` and `041` are in
  `collector-runner/…/db/changelog/changes/`, and `CredentialSeedIT` is in the tree.
- **Tree identity**: `git diff origin/develop feature/suunto-workouts-sync --stat` → **empty**. What
  was reviewed on the branch is byte-identical to what develop now holds.

Consequence (informational, F-1): issue #4 never merged through its own PR — its diff is inside
#146's squash. Content-wise nothing is missing; the branches `feature/suunto-credential-seeding` and
`feature/suunto-workouts-sync` are now redundant and can be deleted (and #4's PR closed if one is open).

### 0.3 Fresh full-reactor count

`mvn clean test -DskipITs` on `d34b872`: **BUILD SUCCESS, 328 tests, 0 failures, 0 errors, 0 skipped.**

| Module | Tests |
|---|---|
| collector-api | 127 |
| collector-core | 20 |
| collector-query | 18 |
| collector-runner | 21 (incl. ArchUnit 2/2) |
| collector-strava | 103 |
| collector-suunto | 39 |
| **Total** | **328** |

Matches the expected ~328 exactly. Additionally, `CredentialSeedIT` was run for real against
PostgreSQL (failsafe + Docker dev-services): **6/6 green** — see §1.1.

---

## Section 1 — Credential and OAuth security (#3, #4)

### 1.1 Encryption at rest — re-proven, not trusted

`IntegrationCredential` (collector-core): `clientSecret` and `apiSubscriptionKey` both carry
`@Convert(converter = AesGcmAttributeConverter.class)` (`IntegrationCredential.java:23,29-31`).

Executed fresh for this audit:
`mvn verify -pl collector-runner -am -Dit.test=CredentialSeedIT -Dsurefire.skip=true` →
**6/6 tests green** against a real PostgreSQL container. This includes the raw-column proof
(`CredentialSeedIT.java:99-109`): a native
`SELECT api_subscription_key FROM integration_credentials WHERE source='SUUNTO'` asserting the
stored value is not, and does not contain, the plaintext. The suite also covers: Strava seed leaves
`api_subscription_key` NULL (`:143-158`), plaintext-row overwrite without decrypt attempts
(`:112-140`, `:173-203`), and the endpoint response never echoing `clientSecret`/`subscriptionKey`
(`is(nullValue())` assertions, `:67`, `:89-90`). ✅

### 1.2 Secret leakage — module-wide grep, clean

Grep of every `LOG.` and `throw new` in `collector-suunto/src/main` plus the Suunto OAuth code in
`collector-api` (where `SuuntoOAuthService` actually lives — collector-api does **not** import
collector-suunto, see §4.3):

- `SuuntoTokenRefresher.java:74-81` — non-200 refresh logs **status code only**, with an explicit
  comment forbidding the response body; the thrown `TokenRefreshException` message carries the HTTP
  status only.
- `SuuntoOAuthService.java:79` (collector-api) — same pattern for the exchange path.
- Job/upsert logs (`AbstractSuuntoJob.java:64,83`, `SyncSuuntoWorkoutsJob.java:84,106,115`,
  `SuuntoWorkoutUpsertService.java:76`) carry only: class names, Suunto usernames, workoutKeys,
  offsets, counts. Usernames/workoutKeys are identifiers, not secrets — same posture as Strava's
  athlete-id logging accepted in prior audits.
- No `access_token`, `refresh_token`, `client_secret`, `subscription_key`, `Bearer`, or response
  body appears in any log statement or exception message in the module. ✅ Zero findings.

### 1.3 Refresh-token rotation persisted

- Write-back: `OAuthTokenService.refreshIfNeeded` sets access token, **refresh token**, and expiry
  from `TokenRefreshResult` onto the managed entity (`OAuthTokenService.java`, the
  `token.setRefreshToken(result.refreshToken())` block); its log line contains source/user/expiry
  only.
- Pinned by test, still present and green in this audit's reactor run:
  `OAuthTokenServiceTest` — an expired token with `rotated-out-refresh` must call the refresher with
  the old value and persist `rotated-in-refresh` + new expiry (`OAuthTokenServiceTest.java:68-104`).
- `SuuntoTokenRefresherTest` covers the wire format (form body with `grant_type=refresh_token`,
  old token in body, rotated token parsed from the response — `:87,110-111,157`). ✅

### 1.4 `isExpired()` margin fix — no Strava regression

`OAuthToken.isExpired()` (`OAuthToken.java:66-72`):
`expiresAt.isBefore(Instant.now().plusSeconds(60))` — the 60 s margin is a safety buffer **before**
expiry (comment documents the fixed semantics; the pre-#3 bug served tokens up to 60 s **after**
expiry). Source-agnostic — one implementation for Strava's 6 h and Suunto's 24 h windows. All 103
collector-strava tests (including every job/refresh test) green in this audit's fresh run. ✅

---

## Section 2 — Data integrity: TSS/IF and the no-double-count guarantee

### 2.1 POWER > PACE > HR > MET selection under partial availability

Fresh read of the selection (`SuuntoWorkoutMapper.java:44` priority list; `:98-116` algorithm):
an entry qualifies iff `trainingStressScore != null` (`:105`) — a null `intensityFactor` never
disqualifies (HR/MET legitimately lack it); the list falls back to the single `tss` object when
absent; an unknown future method still contributes (first non-null score, `:110-113`); nothing →
`Optional.empty()`.

Test coverage goes well beyond the single real fixture (`SuuntoWorkoutMapperTest`):
all-four-present → POWER (`:36`); **POWER absent → PACE** (`:60`); **HR/MET only → TSS populates,
IF stays null, NP/VI/EF still derive** (`:76`); list absent → single `tss` (`:104`); unknown
method contributes (`:116`); nothing anywhere → empty selection and all-null metrics (`:129`);
real fixture end-to-end (`:176`, POWER selected over Suunto's own HR-defaulted `tss`). ✅

### 2.2 Idempotency under the accepted #38 concurrent-run race — traced fresh

Upsert path (`SuuntoWorkoutUpsertService.java:44-79`): `@Transactional` per workout; existence via
`findByWorkoutKey(...).orElseGet(SuuntoWorkout::new)` — the natural key, never a pre-initialized id
(#36 honored, mirroring `ActivityUpsertService`).

Two overlapping runs (admin trigger + scheduler — the guard covers admin-vs-admin only,
`AdminTriggerResource.java:55-71`; the scheduler path `JobRegistry.java:57-67` is unguarded, the
accepted #38 gap):

1. Both transactions read `findByWorkoutKey` → both may see empty → both attempt INSERT.
2. The unique constraint stops the second one: `uq_suunto_workouts_workout_key`
   (migration `042-create-suunto-workouts.yaml`, `addUniqueConstraint`) in production;
   `@Column(unique = true)` (`SuuntoWorkout.java:44`) generates the same constraint in the H2 test
   schema. **A silent double-insert is structurally impossible.**
3. The loser throws → that athlete is counted as failed in the three-state template
   (`AbstractSuuntoJob.java:78-99`) → the run reports PARTIAL/FAILED honestly; the next run's
   `findByWorkoutKey` finds the winner's row and UPDATEs. Self-healing.
4. TSS cannot double-count in any interleaving: the contribution is an **overwritten column** on
   the unique row, and no incrementally-mutated daily total exists anywhere (daily aggregation is
   the deferred foundational issue). Sum-over-rows is the only possible read.

Residual cosmetic effect (F-4, informational): the losing run records a spurious per-athlete
failure (`lastFailureAt` noise on the `SyncJobRecord`) although the data is correct.

Idempotency also proven by test against real persistence:
`SuuntoWorkoutUpsertServiceTest.shouldBeIdempotent_reSyncNeverDuplicatesRowOrTssContribution`
(same fixture twice → 1 row, 1 `ActivityReference`) and `secondUpsertUpdatesChangedValuesInPlace`. ✅

### 2.3 Deferred ports genuinely NOT registered

`grep -rn 'implements.*Port' collector-suunto/src/main` → exactly one hit:
`SuuntoActivityQueryPort implements ActivityQueryPort` (`SuuntoActivityQueryPort.java:38`).
`implements TrainingLoadQueryPort` / `implements ActivityMetricsQueryPort` exist **only** in
collector-strava. Additionally, the boundary is now guarded by a test:
`ActivityQueryPortWiringTest` (collector-runner) asserts **exactly 2** `ActivityQueryPort` beans
(Strava + Suunto) and **exactly 1** `TrainingLoadQueryPort` bean, with a comment explaining why a
second load port must not appear before the aggregation issue. The deliberate scope boundary holds. ✅

---

## Section 3 — Rate limiter correctness under real conditions

### 3.1 Filter coverage of every call path

`@RegisterProvider(SuuntoRateLimitFilter.class)` sits on the `SuuntoApiClient` interface itself
(`SuuntoApiClient.java:18`) — every method present and future on the client, and every physical
HTTP attempt including each `@Retry` re-entry, passes through `filter()` → `acquire()`.
Re-verified by test in this audit's run: `SuuntoRateLimitFilterTest` (capacity 1 / refill 1 s
profile) drives three real calls through the client against WireMock, asserts the third call is
delayed `>= 500 ms` (`:82`) and that exactly 3 physical requests arrived (`:85`). Green. ✅

### 3.2 Worst-case first historical sync vs. the budget — recomputed

Inputs (fresh read): `PAGE_SIZE = 50` (`SyncSuuntoWorkoutsJob.java:43`); defaults burst 10 tokens,
1 token / 10 s ≈ 6 req/min sustained (`application.properties`, `SUUNTO_RATELIMIT_*`). Requests for
a first sync = ⌈workouts / 50⌉. Time ≈ `(requests − 10) × 10 s` beyond the initial burst:

| Account history | Requests | First-sync duration |
|---|---|---|
| 500 workouts | 10 | ~0 s (burst) |
| 1,000 | 20 | ~1.7 min |
| 5,000 | 100 | ~15 min |
| 20,000 (extreme) | 400 | ~65 min |

The limiter **blocks**, it never errors — so a large first sync cannot "exhaust" anything; it just
takes longer. The daily 03:30 cron cannot overlap itself unless a single sync exceeded 24 h, which
would require ~8,600 requests ≈ 430,000 workouts — not a real scenario. The #7 schedule math holds.
Two informational notes: (a) during a long throttled first sync one Quartz worker thread is occupied
for the whole duration (F-5); (b) the 10/10 defaults are still documented placeholders pending the
API Zone dashboard (F-6). Per-request `read-timeout=10000` is unaffected — the filter blocks
*before* the request is sent, never inside the HTTP exchange.

### 3.3 429 interaction with bucket state

`TokenBucketRateLimiter` (collector-core) has no reset/error path: a permit consumed by a request
that then receives 429 is simply spent; the refill daemon continues on schedule; the abort
(`SyncSuuntoWorkoutsJob.java:87-92`, `return true` → `AbstractSuuntoJob.java:80-82` break) leaves
the bucket in a consistent state for the next run. No corruption possible. Bonus interaction
verified: `@Retry` on `getWorkouts` has `abortOn = ClientErrorException.class`
(`SuuntoApiClient.java:38-39`), so a 429 is **not** retried — no permit burn on doomed retries;
5xx retries each correctly consume their own permit. The abort-all-tokens behavior is itself
pinned by test (`SyncSuuntoWorkoutsJobTest.http429AbortsAllRemainingAthletesWithoutFailingTheRun` —
exactly 1 API call, second athlete never attempted). ✅

---

## Section 4 — Multi-source architecture correctness (ADR-002 addendum in practice)

### 4.1 SuuntoActivityQueryPort is genuinely additive

- Composer: `ActivityQueryComposer` iterates `Instance<ActivityQueryPort>`
  (`ActivityQueryComposer.java:36-41`, loops at `:66` and `:95`) — written as the N-source general
  case; **zero changes to collector-query in this whole block** (block diff touches no composer).
- Multi-source behavior covered by unit tests with fakes:
  `shouldMergeAndSortAcrossMultipleFakeSources` (`ActivityQueryComposerTest.java:36`),
  offset/limit after merge (`:56`), and the /v2 partial-failure path keeping the healthy source's
  data (`:131`).
- Real CDI wiring proven in collector-runner: `ActivityQueryPortWiringTest` asserts exactly
  Strava + Suunto instances are discovered.
- Real data path proven end-to-end: `SyncSuuntoWorkoutsJobEndToEndTest` (real client → WireMock
  fixture → mapper → upsert → H2) plus `SuuntoActivityQueryPortTest` (canonical-UUID-only exposure,
  unconnected athlete → empty, orphan row skipped — mirroring the Strava port's contract tests). ✅

### 4.2 Accepted gaps still honest — no silent workaround

- **Cross-source TSS aggregation:** no Suunto training-load table, service, or port exists (grep,
  §2.3). `GET /training-load` and `RacePerformanceComposer` therefore keep serving Strava-only
  values — the pre-#7 status quo, an honest "this source not aggregated yet", never a mixed/wrong
  number. The dangerous alternative (a second load port feeding `putIfAbsent` at
  `RacePerformanceComposer.java:117-120` and the first-port-wins loop at
  `AthleteTrainingLoadResource.java:43-49`) is exactly what was deliberately not built, and is now
  test-guarded.
- **Physical-session dedup:** still deferred exactly as ADR-002's addendum describes
  (`ActivityQueryComposer.java:28-32` javadoc). A session observed by both sources would appear
  twice in listings — an honest duplicate, not silently merged wrong data. Moot for the current
  deployment (Suunto and Strava don't observe the same sessions today).
- Per-workout `tss` + `tss_calculation_method` are persisted on `suunto_workouts` (migration 042),
  aggregation-ready for the foundational issue, consumed by nothing yet. ✅

### 4.3 ArchUnit and cross-module dependencies

- `ApiDoesNotDependOnStravaTest`: **2/2 green** in this audit's run — rule 1 api↛strava
  (`:39-50`), rule 2 read-resources→query with the two sanctioned write-path exemptions
  (`AthleteRegisterResource`, `SuuntoAthleteRegisterResource`, `:52-69`).
- Grep verification of the unruled directions — all **zero matches**:
  `import com.zensyra.collector.strava` in collector-suunto and collector-api;
  `import com.zensyra.collector.suunto` in collector-api, collector-strava, collector-query.
  Notably, `SuuntoOAuthService` lives in `collector-api/…/api/oauth/` and imports only api+core —
  the register resource needs nothing from collector-suunto.
- The symmetric rules planned in Suunto Phase 0 (api↛suunto, strava↛suunto, suunto↛strava) still
  **do not exist** — confirmed. Compensated today only by the greps above. Hardening candidate
  (F-3), not fixed here per audit constraints.

---

## Section 5 — Regression check against prior audits

- **Test count:** 328 (breakdown in §0.3), up from audit-002's 233; growth is the documented
  per-issue additions across the Suunto block plus the interim milestones. Nothing decreased;
  0 failures/errors/skips.
- **New native SQL in this block:**
  `git diff 0719ca0..origin/develop | grep '^+.*createNativeQuery'` → exactly **two**, both inside
  `CredentialSeedIT` (test code): the raw-column encryption probe and a static seed INSERT. Static
  strings, no user input, never shipped in production code. Production-code native-SQL inventory
  unchanged since audit 001/002. ✅
- **New dependencies:** block pom diff (`0719ca0..origin/develop -- '*pom.xml'`) adds:
  the new `collector-suunto` module (internal deps + Quarkus BOM artifacts only) and
  `org.wiremock:wiremock:3.9.1` **test-scope** in collector-suunto and collector-api. WireMock is
  Apache-2.0 — MIT-compatible, test-only, never distributed in artifacts; no conflict with the MIT
  licensing decision, and no interaction with the FIT-SDK constraints (the FIT dependency was never
  added — the `fit/` package doesn't exist yet). No new runtime third-party dependency. ✅
- **Prior accepted findings — unchanged:**
  - ADR-002 single-key model still accurately describes the surface: the block's only new
    `/api/v1` endpoint (`/api/v1/athletes/register/suunto`,
    `SuuntoAthleteRegisterResource.java:24`) sits behind the same `ApiKeyFilter` path prefix; the
    new admin endpoint (`POST /admin/credentials/suunto`) is behind `@AdminTokenAuth` on the shared
    `CredentialSeedResource`.
  - `JobConcurrencyGuard` still protects admin-vs-admin only (`AdminTriggerResource.java:55-71`);
    the scheduler path remains unguarded — unchanged accepted posture, now also covering the Suunto
    job identically (and made harmless for it by §2.2's unique-constraint backstop).
  - Known pre-existing local `NativeHealthCheckIT` failure under `mvn verify` (packaged jar without
    datasource env) is untouched by this block and was not triggered here (targeted `-Dit.test`
    filter); it remains its own pending item outside this scope.

---

## Prioritized findings

| # | Severity | Finding | Section | Suggested action |
|---|---|---|---|---|
| F-1 | **Low** | Issue #4 never merged via its own PR — its content landed inside #146's squash (`cdf4cd0`). Content verified complete and byte-identical; develop history clean (no local-merge artifacts). Residue: branches `feature/suunto-credential-seeding` and `feature/suunto-workouts-sync` are redundant, and #4's PR (if open) is stale. | 0.2 | Housekeeping: delete both merged-content branches, close #4's PR referencing #146. No code action. |
| F-2 | **Low** | `docs/audit/` contains only audit 002, while 002 itself and this brief reference audits 001 and 003–005 (002 even cites "audit-004/F-2"). The audit trail is partially outside the repository. | 0 / preamble | Commit the missing audit reports (or a pointer to where they live) so accepted-findings continuity is verifiable in-repo. |
| F-3 | **Low** | Symmetric ArchUnit rules (api↛suunto, strava↛suunto, suunto↛strava) planned in Suunto Phase 0 still don't exist. Today's compensation is this audit's greps (all clean) plus module-pom structure — nothing prevents a future accidental import from compiling. | 4.3 | Fold into the hardening block: extend `ApiDoesNotDependOnStravaTest` (or a sibling) with the three rules; ~15 lines, zero production impact. |
| F-4 | Info | Under the accepted #38 admin-vs-scheduler race, the losing concurrent upsert records a spurious per-athlete failure (`lastFailureAt` noise) though data stays correct and self-heals (unique-constraint backstop, §2.2). | 2.2 | Accepted-as-is (single-user deployment; race requires a manual admin trigger during the 03:30 run). Revisit only with the multi-instance/pg-advisory-lock item already noted in `JobConcurrencyGuard`'s javadoc. |
| F-5 | Info | A very large first historical sync occupies one Quartz worker for the whole throttled duration (~65 min at 20k workouts with placeholder limits). No correctness or overlap risk (would need ~430k workouts to exceed the daily cron period). | 3.2 | Informational. Re-evaluate when real Development-tier limits are read from the API Zone dashboard (raising `SUUNTO_RATELIMIT_*` shrinks the window with zero code change). |
| F-6 | Info | `SUUNTO_RATELIMIT_MAX_REQUESTS/REFILL_SECONDS` remain unconfirmed placeholders (documented as such in `.env.example`). Deliberately conservative; correctness unaffected. | 3.2 | Operational reminder: confirm the real quota in the Suunto API Zone dashboard, adjust env values. |
| F-7 | Info | Cross-source TSS aggregation and physical-session dedup remain open **by design** (deferred foundational issues, decisions recorded in #7). Verified nothing silently worked around them — the system serves honest Strava-only load and honest duplicates, never blended wrong data; the boundary is now guarded by `ActivityQueryPortWiringTest`. | 2.3 / 4.2 | Accepted scope boundary. The two follow-up issues (aggregation port + EMA composer + resource loop fixes; dedup policy) are already queued — open them as planned. |

**Nothing rated Medium, High, or Critical.** No secret reaches a log or exception; encryption at
rest re-proven against a live PostgreSQL raw column; refresh-token rotation and the expiry-margin
fix pinned by green tests; double-counting structurally impossible (natural-key upsert + unique
constraint + sum-over-rows design); the rate limiter covers every physical request including
retries and survives 429s with consistent state; the second source is genuinely additive with the
deferred boundaries intact and now test-guarded; no new runtime dependency, no new production
native SQL, no new unauthenticated surface; ArchUnit green.

**Distinction self-hosted-today vs. multi-user-future:** F-4 (spurious failure noise) and the
ADR-002 single-key model matter only when a second operator/instance appears; F-3 (symmetric
ArchUnit rules) and F-5/F-6 (rate budget) are equally relevant in both contexts but carry no
present risk.

**Zero-finding sections (checks run, came back clean):** §1.1 encryption at rest (live IT, 6/6),
§1.2 secret leakage (module-wide grep incl. api-side OAuth), §1.3 rotation persistence, §1.4
expiry-margin regression, §2.1 selection fallback chain (5 non-fixture scenarios), §2.2
double-insert/double-count (trace + constraint + idempotency tests), §2.3 deferred-port boundary,
§3.1 filter coverage incl. retries, §3.3 429/bucket state, §4.1 additive composition (unit + CDI
wiring + e2e), §4.3 cross-module imports, §5 native SQL / dependencies / prior-audit regressions.

---

*Audit only — no source files modified. This report
(`docs/audit/006-suunto-mvp-security-performance-audit.md`) is the single file created, left
uncommitted for review.*
