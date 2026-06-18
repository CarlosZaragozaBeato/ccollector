# PROJECT_CONTEXT - CCollector

## Purpose

CCollector is a sports-data ingestion and read-service backend. It currently integrates with Strava, stores raw and enriched training data in PostgreSQL/TimescaleDB, computes a small set of derived metrics, and exposes secured REST endpoints for downstream consumers.

This is not a full athlete-facing product yet. There is no dashboard, no webhook ingestion, and no production deployment automation in this repository.

## Current State

- Working Strava OAuth token registration and refresh.
- Working scheduled Strava sync jobs.
- Working persistence layer for athletes, activities, laps, streams, gear, routes, zones, stats snapshots, best efforts, training load, and activity metrics.
- Working read API under `/api/v1`, protected by `X-API-Key`.
- Working admin job trigger/list endpoints under `/admin`, protected by `X-Admin-Token`.
- Liquibase-managed schema, including TimescaleDB hypertable/continuous aggregate support for activity streams.
- README has been rewritten for public/portfolio use.

## Stack

- Java 21
- Quarkus 3.17
- Maven multi-module project
- PostgreSQL and TimescaleDB
- Hibernate ORM Panache
- Liquibase
- Quartz
- MicroProfile REST Client and Fault Tolerance
- Micrometer Prometheus metrics
- SmallRye Health
- JSON logging
- AES-256-GCM JPA attribute encryption for credentials and OAuth tokens

## Module Structure

```text
zensyra-collector/
|-- collector-core/      shared infrastructure: credentials, OAuth tokens, sync abstractions, health, rate limits
|-- collector-strava/    Strava client, DTOs, entities, repositories, upsert services, jobs, metrics computation
|-- collector-suunto/    Suunto client, DTOs, entities, repositories, upsert services, jobs, metrics computation
|-- collector-api/       secured read API and athlete registration endpoint
`-- collector-runner/    Quarkus runtime, Quartz registration, admin/dev triggers, health, metrics, packaging
```

## Architecture Rules

- `collector-core` must not depend on other internal modules.
- `collector-strava` depends on `collector-core`.
- `collector-suunto` depends on `collector-core`.
- `collector-api` depends on `collector-core` and `collector-strava` because the read resources query Strava repositories directly.
- `collector-runner` aggregates all modules and is the runnable Quarkus application.
- A new vendor integration should be added as a new `collector-[vendor]` module.
- Shared infrastructure belongs in `collector-core`; vendor-specific persistence and jobs belong in the vendor module.

## Public API

Routes under `/api/v1` require `X-API-Key`.

- `POST /api/v1/athletes/register`: exchanges a Strava OAuth authorization code and stores the encrypted token.
- `GET /api/v1/athletes/{athleteId}/activities`: paginated activity summaries, with optional type/date filters.
- `GET /api/v1/athletes/{athleteId}/stats`: latest athlete stats snapshot.
- `GET /api/v1/athletes/{athleteId}/training-load`: recent daily training-load records.
- `GET /api/v1/athletes/{athleteId}/best-efforts`: best efforts with PR ranking.
- `GET /api/v1/athletes/{athleteId}/activity-metrics?activityId=...`: computed activity metrics for one activity.

## Scheduled Jobs

Jobs are registered dynamically by `collector-runner` from `DataCollector` implementations.

- `strava.sync-athlete`
- `strava.sync-athlete-zones`
- `strava.sync-athlete-stats`
- `strava.sync-activities`
- `strava.sync-activity-detail`
- `strava.sync-activity-streams`
- `strava.sync-gear`
- `strava.sync-routes`
- `strava.initial-historical-sync`
- `strava.compute-training-load`
- `strava.compute-activity-metrics`

Cron expressions currently live in each job class. They are not configuration-driven yet.

## Data Model Summary

Core tables:

- `integration_credentials`
- `oauth_tokens`
- `sync_job_records`

Strava tables:

- `athletes`
- `activities`
- `activity_laps`
- `activity_streams`
- `gears`
- `routes`
- `athlete_zones`
- `athlete_stats_snapshots`
- `activity_best_efforts`
- `activity_metrics`
- `athlete_training_load`

There are also SQL analytics views and TimescaleDB continuous aggregates defined by Liquibase. Not all of them are exposed through `collector-api`.

## Development Commands

```bash
mise install
mise exec -- java -version
mise exec -- ./mvnw test
mise exec -- ./mvnw clean package -DskipTests
ENV_FILE=.env.local just dev
```

## Known Gaps

- No Suunto, COROS, Garmin, or TrainingPeaks integration yet.
- No user-facing analytics dashboard yet.
- No Strava webhook support yet.
- No production deployment pipeline in this repository.
- Initial historical sync is effectively manual because its cron is set far in the future.
- Some derived metrics are intentionally simple: training load uses estimated TSS with a fixed intensity factor.
- Cron configuration should eventually move from Java classes to environment/config properties.
