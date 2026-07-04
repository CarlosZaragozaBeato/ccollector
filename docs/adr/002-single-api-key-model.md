# ADR-002: Single API key authorization model

- Status: Accepted
- Date: 2026-07-04

## Context

CCollector is a self-hosted, single-user platform in its current deployment
model: one operator runs one instance for one athlete's data.

All `/api/v1/*` endpoints are guarded by a single shared API key sent in the
`X-API-Key` header and configured through the `COLLECTOR_API_KEY` environment
variable. Enforcement lives in `ApiKeyFilter` (`collector-api`), a
`@Priority(Priorities.AUTHENTICATION)` `ContainerRequestFilter` that guards every
path starting with `/api/v`, rejects a missing/incorrect key with `401`, returns
`503` when the key is unconfigured, and compares the provided value against the
configured one with a constant-time `MessageDigest.isEqual` check.

The key grants access to **all** athletes' data in the system. There is no
binding between a specific key and a specific `athleteId`: the filter validates a
shared secret, it does not resolve a caller identity. Consequently, any caller in
possession of the key can read or write any athlete's data simply by changing the
`{athleteId}` path parameter on endpoints such as
`/api/v1/athletes/{athleteId}/...`.

In the general (multi-user) case this is a broken-access-control pattern — an
insecure direct object reference (IDOR). This was raised as the first finding in
the Milestone 1 security & performance audit (audit 001, Section 1, finding #1)
and is carried forward as an accepted finding in the Milestone 2 audit
([audit 002](../audit/002-milestone2-security-performance-audit.md)). This ADR
records the decision to accept it for the current deployment context and defines
the exact conditions under which it must be revisited.

## Options Considered

### Option A: Single shared key, no per-athlete binding

One `COLLECTOR_API_KEY` secret guards the whole API; the filter validates the
shared secret only. Simplest to operate for a self-hosted single-user
deployment, with zero additional infrastructure — no user table, no session
model, no OAuth provider. The threat model for a single-operator instance does
not include "another authenticated user abusing another athlete's data" because
there is only one legitimate user.

### Option B: Key-to-athlete binding

Each API key maps to exactly one `athleteId`; the filter resolves the bound
athlete from the credential and rejects requests whose path `{athleteId}` does
not match. Adds complexity — key management and binding storage — with no present
benefit, because there is no multi-athlete deployment today.

### Option C: OAuth-scoped identity per athlete

Replace the shared-key model with a full multi-user authorization model in which
each athlete authenticates and receives scoped credentials. A significant amount
of scope for a problem that does not exist yet in the current deployment model.

## Decision

We adopt **Option A: single shared key, no per-athlete binding**, for the current
deployment context (self-hosted, single operator, a single athlete in practice).
We do **not** implement per-athlete authorization at this time.

This is a deliberate, time-bounded acceptance of a known risk, not an oversight.
It holds only while the deployment model remains single-user. The conditions that
end this acceptance are stated in "The Multi-User Gate" below.

## Consequences

### Benefits

- Minimal operational complexity for the intended self-hosted single-user case.
- No additional schema, and no session or identity infrastructure to build or
  maintain.
- Authentication remains a single, auditable mechanism (`ApiKeyFilter`) rather
  than a distributed set of authorization checks.

### Costs and Constraints

- The moment this platform is deployed for more than one athlete or user sharing
  a single instance, this becomes a real broken-access-control vulnerability: any
  key holder can read or write any athlete's data by changing the `{athleteId}`
  path parameter.
- **This model is NOT safe to deploy multi-tenant as-is.** The acceptance in this
  ADR is valid only for the single-user deployment model and must not be read as
  a general endorsement of the shared-key design.
- The risk is invisible in day-to-day single-user operation, which makes it easy
  to overlook when the deployment model changes — hence the explicit gate below.

## The Multi-User Gate

Before CCollector is ever opened to multiple users or athletes sharing one
deployment, **all** of the following MUST change. This gate is a hard
precondition for any multi-tenant deployment, not a recommendation.

1. **Resolve identity from the credential.** `ApiKeyFilter` (`collector-api`)
   must be extended to resolve an athlete identity from the presented credential,
   not merely validate a shared secret.
2. **Enforce ownership per request.** Every resource under
   `/api/v1/athletes/{athleteId}/...` must verify that the resolved identity
   matches the requested `{athleteId}`, returning `403` on mismatch.
3. **Choose an identity mechanism.** Adopt exactly one of:
   (a) a key→athlete binding stored in a new table, validated by the filter; or
   (b) replacement of the shared-key model entirely with OAuth-scoped identity.
4. **Cover the entire authenticated surface.** The gate applies to **all**
   journal write endpoints — `training-days`, `health-events`, `race-results` —
   and to **all** read endpoints under `/api/v1/athletes/{athleteId}/*`. No
   endpoint that takes an `{athleteId}` may be exempt.

The decision in this ADR is considered correctly superseded only when a
subsequent ADR records the multi-user authorization model and the four
conditions above are implemented and enforced across the whole
`/api/v1/athletes/{athleteId}/*` surface.
