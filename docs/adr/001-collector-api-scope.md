# ADR-001: `collector-api` Scope as a Multi-Integration API

- Status: Accepted
- Date: 2026-06-20

## Context

`collector-api` is the read API consumed by downstream services. In the current
implementation, its REST resources query `collector-strava` repositories and
entities directly. This couples the public contract, query model, and module
composition to one specific integration.

The product must be able to add sources such as Suunto without requiring
consumers to select a source or rewrite the presentation layer. Approval of the
Suunto API does not affect this decision: decoupling is a prerequisite for every
additional integration.

The API must answer domain questions, such as activities over a period,
high-load weeks, and performance metrics. It must not expose Strava-specific
structures or concepts.

## Options Considered

### Option A: Generic Multi-Integration API

Define provider-neutral read models and query ports. `collector-api` resources
depend exclusively on those ports, and their DTOs are built from the read
models. Each integration supplies or populates the implementation required for
the queries.

### Option B: Keep a Strava-Specific API

Retain `collector-api`'s direct dependency on `collector-strava` repositories
and entities, then create separate provider-specific resources or contracts in
the future.

### Option C: Normalize Only in the Presentation Layer

Retain provider-specific repositories in `collector-api` and hide their differences
with mappers in the REST resources.

## Decision

We adopt **Option A: Generic Multi-Integration API**.

`collector-api` will expose source-agnostic domain read models. Its contract
will be organized around read capabilities, including:

- activities over a period;
- high-load weeks and training load;
- performance metrics;
- statistics and best efforts where a common semantic exists.

`collector-api` **must not depend on an integration's repositories, entities,
or DTOs**. In particular, it will not inject `collector-strava` repositories.
It will depend on neutral read models and query interfaces (ports) located in a
shared stable module—expected to be `collector-core`, or a dedicated read module
if its size warrants one. Provider-specific implementations remain in their
integration module.

A source that cannot supply a datum must not force its own model into the API.
The read-model implementation will represent this as either explicit absence or
an unsupported capability, according to the query contract.

## Consequences

### Benefits

- Adding Suunto or another provider does not require redesigning API consumers
  or duplicating REST resources by integration.
- The public contract expresses the training domain, not Strava's persistence
  schema or terminology.
- REST resources can be tested against read ports without starting or knowing a
  specific integration.
- Integrations retain responsibility for translating their data into the common
  semantics.

### Costs and Constraints

- Common semantics, units, time periods, provenance, and missing-data handling
  must be defined before generalizing each current endpoint.
- Some Strava-specific capabilities will have no generic representation. They
  remain outside `collector-api` until there is a clear multi-source use case
  and contract.
- Decoupling requires incremental migration of current resources: first
  introduce neutral ports and read models, then replace `collector-strava`
  repository injections, and finally remove `collector-api`'s Maven dependency
  on `collector-strava`.
- During the transition, the Strava module may implement the neutral ports, but
  the dependency must be composed by `collector-runner`, rather than from
  `collector-api` to Strava.

## Compliance Criterion

The decision is implemented when `collector-api` has no compile-time dependency
on integration modules and all its read resources use only provider-neutral
ports and read models.
