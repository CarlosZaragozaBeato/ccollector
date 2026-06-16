# CCollector Development Guide

This guide documents the extension patterns used in `ccollector` as repeatable recipes. The goal is that adding a new sync capability or a new read endpoint feels like filling in a known scaffold, not inventing a new architecture every time.

## How To Add A New Sync Job

Use [`SyncRoutesJob`](./collector-strava/src/main/java/com/zensyra/collector/strava/job/SyncRoutesJob.java) as the reference implementation for jobs that fetch data from Strava, paginate, upsert into PostgreSQL, and expose the result later via `collector-api`.

### Current pattern

- Database schema is added with Liquibase first.
- Strava payloads are represented with a dedicated DTO under `collector-strava/api/dto`.
- Persistence is split into entity + repository + upsert service.
- The scheduled unit is a CDI bean that extends `AbstractStravaJob`.
- The default for Strava-backed jobs is to extend `AbstractStravaJob`, even when the concrete job injects extra repositories or services of its own.
- Registration is automatic through [`StravaCollector`](./collector-strava/src/main/java/com/zensyra/collector/strava/StravaCollector.java) and [`JobRegistry`](./collector-runner/src/main/java/com/zensyra/collector/runner/scheduling/JobRegistry.java).
- Tests are added at the job layer and, when relevant, at repository/upsert/resource level.

### Recipe

1. Add the Liquibase changeset.
   Create a new YAML file under `collector-strava/src/main/resources/db/changelog/changes/`.
- Follow the existing numbered sequence such as `026-create-routes.yaml`.
- Include the new file from the Strava changelog chain. The application starts from [`collector-core/src/main/resources/db/changelog/db.changelog-master.yaml`](./collector-core/src/main/resources/db/changelog/db.changelog-master.yaml), which includes the Strava changelog.
- Keep DDL idempotent when possible and add indexes needed by the future read queries now, not later.

2. Add the JPA entity and repository.
- Create the entity in the corresponding package under `collector-strava/src/main/java/com/zensyra/collector/strava/...`.
- Create a Panache repository when the job or API needs custom queries.
- If the job is an upsert flow, also add a dedicated upsert service instead of pushing write logic into the job.

3. Add or extend the Strava DTO.
- Create a transport DTO in `collector-strava/src/main/java/com/zensyra/collector/strava/api/dto/`.
- Keep it aligned with the Strava response shape. Mapping from transport DTO to entity belongs in the upsert service, not in the REST client.

4. Extend `StravaApiClient`.
- Add the new HTTP method in [`collector-strava/src/main/java/com/zensyra/collector/strava/api/StravaApiClient.java`](./collector-strava/src/main/java/com/zensyra/collector/strava/api/StravaApiClient.java).
- Match the existing conventions:
  `@Path` on the Strava resource, `@HeaderParam("Authorization")`, typed DTO return value, and `@Retry` tuned to the endpoint semantics.
- If the endpoint should stop retrying on 4xx, use `abortOn = ClientErrorException.class` as done by activity detail and streams.

5. Add the write-side service.
- Create an upsert or sync service that converts the DTO into entities and owns all persistence rules.
- Keep the job orchestration thin: token resolution, pagination, rate-limit handling, logging, metrics, and calls into the write-side service.

6. Create the job by extending `AbstractStravaJob`.
- Place the class under `collector-strava/.../job/`.
- Mark it `@ApplicationScoped`.
- Implement `jobId()`.
- Implement `cronExpression()`.
- Implement `executeForToken(OAuthToken token, SyncContext context)`.
- Reuse inherited collaborators from `AbstractStravaJob`: `tokenRepository`, `tokenService`, and `stravaApiClient`.
- Inject extra repositories or services directly in the concrete job when needed. Extending the base class does not prevent job-specific dependencies.
- Return `true` from `executeForToken(...)` only when the job should abort processing remaining tokens, for example on rate-limit exhaustion.

7. Register the job in `StravaCollector`.
- Inject the new job in [`collector-strava/src/main/java/com/zensyra/collector/strava/StravaCollector.java`](./collector-strava/src/main/java/com/zensyra/collector/strava/StravaCollector.java).
- Add it to the list returned by `jobs()`.
- No runner wiring is needed beyond that. [`JobRegistry`](./collector-runner/src/main/java/com/zensyra/collector/runner/scheduling/JobRegistry.java) auto-discovers every `DataCollector` and schedules each `SyncJob`.

8. Set the cron.
- Current codebase rule: cron lives in the job class, via `cronExpression()`.
- There is no `application.properties`-driven cron pattern in the current implementation.
- If you need environment-specific schedules, that is a separate refactor and should be documented as such instead of partially introduced in a single job.

9. Add tests.
- Add a job test following [`SyncRoutesJobTest`](./collector-strava/src/test/java/com/zensyra/collector/strava/job/SyncRoutesJobTest.java).
- Cover at least:
  happy path, pagination if applicable, rate-limit behavior if applicable, and no-token behavior.
- Add repository or service tests if the query/upsert logic is non-trivial.
- If the new schema uses Timescale-specific DDL or SQL-heavy behavior, add an integration test similar to the stream tests.

10. Expose it through `collector-api` only if the data must be read externally.
- Not every sync job needs a read endpoint.
- If the job creates data for product-facing consumption, continue with the next recipe.

### Sync job checklist

- Liquibase changeset added and included in the changelog chain.
- Entity, repository, and write-side service created.
- DTO added under `strava/api/dto`.
- `StravaApiClient` method added with retry semantics.
- New job extends `AbstractStravaJob`.
- `jobId()` and `cronExpression()` implemented.
- Job injected into `StravaCollector.jobs()`.
- Logging and rate-limit behavior covered.
- Tests added.

## How To Add A New Read Endpoint

Use [`AthleteBestEffortsResource`](./collector-api/src/main/java/com/zensyra/collector/api/resource/AthleteBestEffortsResource.java) as the reference for a read-only endpoint backed by a repository query and a thin API DTO.

### Current pattern

- Resource classes live in `collector-api/.../resource`.
- API DTOs live in `collector-api/.../dto`.
- Resources query repositories from `collector-strava` directly.
- Input validation stays in the resource.
- Mapping from entity to API DTO is explicit and local.
- Resource tests mock the repository and hit the HTTP endpoint with RestAssured.
- Error responses are built through a shared helper so the JSON shape stays uniform.

### Recipe

1. Start from the storage query.
- Add the repository method in the owning persistence module, typically under `collector-strava`.
- Keep the query narrow and shaped for the endpoint. Do not fetch broad entity graphs and filter in memory.
- Add indexes in Liquibase first if the query needs them.

2. Add the API DTO.
- Create a record under `collector-api/src/main/java/com/zensyra/collector/api/dto/`.
- Provide a `from(...)` factory when mapping from a domain entity, as in [`BestEffortDto`](./collector-api/src/main/java/com/zensyra/collector/api/dto/BestEffortDto.java).

3. Add the resource.
- Create a JAX-RS resource under `collector-api/.../resource`.
- Define the route under `/api/v1/athletes/{athleteId}/...` unless there is a stronger reason to use another aggregate root.
- Validate query params early and return `400` with a small JSON body for invalid client input.
- Use the shared error-response helper instead of hand-building `Map.of("error", ...)` in each resource.
- Call the repository, map to DTOs, and return a stable JSON shape.

4. Keep response shapes product-friendly.
- Prefer returning an object with metadata like `items`, `limit`, `page`, `size`, `days`, rather than a bare array.
- Keep naming aligned with existing endpoints so downstream proxies in `ttemplate` can stay simple.

5. Add the resource test.
- Follow [`AthleteBestEffortsResourceTest`](./collector-api/src/test/java/com/zensyra/collector/api/resource/AthleteBestEffortsResourceTest.java).
- Mock the repository.
- Cover:
  valid request, invalid parameter range, and missing API key.

### Read endpoint checklist

- Repository query added or updated.
- Supporting DB index added if needed.
- API DTO record created.
- Resource created with validation.
- JSON response shape consistent with existing endpoints.
- Resource test added.

## Cross-Review And Automation Opportunities

### Consistency review

- The write path is already consistent: Liquibase first, then entity/repository/service, then job, then optional read endpoint.
- The scheduling pattern is consistent across Strava jobs that extend `AbstractStravaJob`.
- The read API pattern is consistent across resources: validate input, query repository, map to DTO, return a small wrapper object.
- The only mismatch with the requested recipe is cron placement. In this codebase, cron is not configured in `application.properties`; it is defined in `cronExpression()` and registered by `JobRegistry`.

### Good candidates for automation

- A script or archetype for a new Strava sync job could generate:
  Liquibase changeset filename, DTO stub, entity stub, repository stub, upsert service stub, job class stub, and test stub.
- A smaller generator for a new `collector-api` read endpoint could generate:
  DTO record, resource skeleton, repository method signature, and resource test skeleton.
- The strongest return on investment is the sync-job scaffold, because it touches the most files and follows the strictest sequence.

### What should stay manual

- Query design and index design.
- Retry policy on `StravaApiClient`.
- Rate-limit and pagination behavior.
- JSON response contract choices for product-facing endpoints.
