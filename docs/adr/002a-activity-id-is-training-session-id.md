# ADR-002 Addendum: `TrainingSession.id` Is the Canonical `ActivityId`

- Status: Accepted
- Date: 2026-06-21
- Amends: ADR-002 (`TrainingSession` as the Cross-Source Aggregate Above `ActivityReference`)
- Triggered by: design work for `collector-query` (decoupling `collector-api` from `collector-strava`)

## Context

ADR-002 introduced `TrainingSession` as the canonical aggregate above
`ActivityReference`, specifically to give a single physical training event a
stable identity independent of how many sources observed it. ADR-002 did not,
however, say which of the two identities — `TrainingSession.id` or
`ActivityReference.id` — is the public `ActivityId` that `collector-query` and
`collector-api` will expose to consumers.

This cannot be deferred to implementation time. The shape of every read-model,
query port, and Strava adapter in the upcoming `collector-query` module
depends on this answer:

- If `ActivityId = ActivityReference.id`, the API's notion of "an activity" is
  inherently a single source's observation. A consumer asking "give me
  activity X" gets one source's data, by construction, even after Suunto is
  connected.
- If `ActivityId = TrainingSession.id`, the API's notion of "an activity" is
  the physical event. A consumer asking "give me activity X" must be served
  by a component that knows how to resolve, for that one event, which
  source(s) observed it and how to reconcile their data — which is precisely
  the "single consultation service that composes the adapters" the
  `collector-query` issue already asks for.

Picking the first option now and migrating to the second later is not a
clean follow-up; it is a breaking change to every public identity the API has
ever returned, the moment Suunto produces its first overlapping observation.

## Decision

**`ActivityId` is `TrainingSession.id`.** `ActivityReference.id` is never
exposed outside `collector-core` and its source-specific adapters. It is an
internal correlation key between an `IntegrationAccount` and one source's
external activity, not a public identity.

Consequences for `collector-query` design, fixed by this decision:

- The `Activity` read-model returned by query ports is keyed by
  `TrainingSession.id`. It is the output of composing zero or more
  per-source observations (`ActivityReference` plus whatever adapter-specific
  data each source attaches), not a direct projection of any one source's
  table.
- With a single active source (Strava, today), composition is a trivial
  case: one `TrainingSession`, one backing `ActivityReference`, no
  reconciliation needed. This is not a simplification to special-case; the
  composer must be written as the general N-source case from the start, with
  N currently equal to one. Special-casing "only Strava" now is exactly the
  kind of provider-specific leakage ADR-001 already forbids.
- Pagination, filtering, and sorting in query ports operate over
  `TrainingSession` rows (joined to `AthleteProfile` for ownership), never
  over `ActivityReference` rows directly.
- The Strava adapter inside `collector-strava` is responsible for resolving
  its own `ActivityReference` and the `TrainingSession` it belongs to, and
  for supplying whatever per-source data (distance, pace, power, etc.) the
  read-model needs from Strava's tables. It must never return an
  `ActivityReference.id` to `collector-query` as if it were the activity's
  identity.

## Consequences

### Benefits

- The public contract never has to change shape when a second source starts
  reporting overlapping activities — composition was already the rule, not
  the exception, from the first version of `collector-query`.
- `/api/v2`'s `ActivityId` is stable for the lifetime of the physical training
  event it represents, regardless of how many sources later attach
  observations to it.

### Costs and Constraints

- The composer must be designed and tested against the N-source case even
  though only one source exists today. The "one source" test case in the
  composer's test plan is a specific instance of the general case, not a
  separate, simpler code path.
- Any read-model field that can legitimately differ between sources for the
  same `TrainingSession` (e.g., distance reported by a GPS watch vs. a phone)
  needs an explicit reconciliation rule once a second source exists. This ADR
  does not define that rule — it is covered by the cross-source matching ADR
  required at the start of phase 1C (see ADR-002's compliance criterion) and,
  on the read side, by the composer's own conflict-resolution policy, to be
  defined when the composer is implemented.

## Compliance Criterion

This decision is implemented when every query port and read-model in
`collector-query` is keyed by `TrainingSession.id`, no port or DTO anywhere
in `collector-query` or `collector-api` accepts or returns an
`ActivityReference.id`, and the composer's test suite includes a multi-source
composition case even before a second source adapter exists in production
(it may use a fake or in-memory second adapter for that test).