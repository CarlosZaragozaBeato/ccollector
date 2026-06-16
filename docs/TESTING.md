# Testing Strategy

## Test Pyramid

Use the smallest useful test for each behavior:

- Unit tests for pure mapping, metrics calculations, token logic, and small services.
- Quarkus tests for REST resources, scheduler wiring, repository behavior, and cross-module integration.
- Testcontainers-backed integration tests for database-specific behavior such as TimescaleDB hypertables, Liquibase, and SQL views.

## Feature Test Checklist

For each new feature, add or update tests at the same time as the implementation.

### API Feature

- Resource test for success response.
- Resource test for empty/not-found behavior.
- Resource test for auth failure.
- DTO mapping test when mapping is non-trivial.
- Repository/service test if filtering, pagination, or sorting changes.

### Strava Sync Feature

- Job test for happy path.
- Job test for pagination when applicable.
- Job test for rate-limit or recoverable API failure.
- Upsert service test for insert/update behavior.
- Mapper test for API DTO to persistence model conversion.

### Database Feature

- Liquibase changelog wiring test.
- Repository test for important queries.
- Integration test when behavior depends on PostgreSQL or TimescaleDB features.

### Security Feature

- Auth success and failure tests.
- Negative tests for missing or malformed headers.
- Tests that verify secrets are not logged or returned in API responses.

## Commands

Run the full test suite:

```bash
./mvnw test
```

Run one module:

```bash
./mvnw test -pl collector-strava
```

Run the runner and required dependencies:

```bash
./mvnw test -pl collector-runner --also-make
```

## Naming

- Use method names that describe behavior, not implementation.
- Prefer `should...When...` for behavior tests.
- Keep test fixtures local unless multiple tests genuinely share the same setup.

## Pull Request Rule

A feature PR should not be considered complete until its acceptance criteria are represented in tests or explicitly documented as manual verification.
