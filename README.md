# CCollector

CCollector is a Java/Quarkus data collector for endurance-sport data. It currently integrates with Strava, stores OAuth tokens and athlete data in PostgreSQL, syncs activities, activity details, laps, best efforts, gear, routes, zones, stats snapshots, and high-volume activity streams into TimescaleDB, then exposes a small secured read API for downstream products. It is a working ingestion/read-service backend, not a finished consumer application.

## Why I Built It

I built this project to understand my own running data at a lower level than the dashboards exposed by consumer apps. I wanted a backend that could pull raw training data, keep the time-series stream samples, compute basic training-load and activity metrics, and leave room for experiments with coaching analytics. The project reflects how I approach sports-tech systems: reliable ingestion first, then useful derived metrics and product-facing APIs.

## Architecture Overview

```text
[Strava API]
     |
     v
collector-strava  --->  collector-core
     |                    |
     v                    v
PostgreSQL / TimescaleDB tables and views
     ^
     |
collector-api  <---  collector-runner
```

Diagram placeholder: replace the text diagram with a module/data-flow diagram before publishing screenshots or portfolio material.

Modules:

- `collector-core`: shared infrastructure for integration credentials, encrypted OAuth tokens, sync job records, health checks, and rate limiting primitives.
- `collector-strava`: Strava REST client, transport DTOs, entities, repositories, upsert services, scheduled sync jobs, stream mapping, and derived metric computation.
- `collector-api`: REST read layer and athlete registration endpoint. Routes under `/api/v1` require `X-API-Key`.
- `collector-runner`: Quarkus application entry point, Quartz job registration, admin/dev trigger endpoints, health checks, metrics, and packaging.

Tech stack:

- Java 21
- Quarkus 3.17
- Maven multi-module build
- PostgreSQL and TimescaleDB
- Hibernate ORM Panache
- Liquibase
- Quartz
- MicroProfile REST Client and Fault Tolerance
- Micrometer Prometheus metrics
- SmallRye Health
- AES-256-GCM encryption for stored client secrets and OAuth tokens

## What Data It Collects

Current integration: Strava.

Strava endpoints used:

- `GET /api/v3/athlete`
- `GET /api/v3/athlete/zones`
- `GET /api/v3/athletes/{id}/stats`
- `GET /api/v3/athlete/activities`
- `GET /api/v3/activities/{id}`
- `GET /api/v3/activities/{id}/streams`
- `GET /api/v3/gear/{id}`
- `GET /api/v3/athletes/{id}/routes`
- `POST /oauth/token` for authorization-code exchange and token refresh

Stored entities include:

- Athletes
- Activities
- Activity laps
- Activity best efforts
- Activity streams in TimescaleDB
- Gear
- Routes
- Athlete heart-rate and power zones
- Athlete stats snapshots
- Daily training load records
- Computed activity metrics
- OAuth tokens and sync job records

The stream table stores per-sample time-series data such as elapsed time, distance, latitude/longitude, altitude, heart rate, watts, and cadence. Liquibase configures it as a TimescaleDB hypertable and adds compression/continuous aggregate support.

## How To Run It Locally

Prerequisites:

- Java 21. The repo includes `mise.toml`; run `mise install` and use `mise exec -- ...` if your shell does not activate mise automatically.
- Maven wrapper from this repo.
- PostgreSQL with TimescaleDB available locally.
- A database/schema reachable through `DB_JDBC_URL`, usually with `currentSchema=collector`.
- Strava API credentials if you want to register real athletes and sync real data.

Create a local environment file such as `.env.local`:

```dotenv
DB_USERNAME=zensyra_user
DB_PASSWORD=change-me
DB_JDBC_URL=jdbc:postgresql://localhost:5432/zensyra?currentSchema=collector
HTTP_PORT=8090
QUARKUS_LOG_LEVEL=INFO
ZENSYRA_LOG_LEVEL=DEBUG
COLLECTOR_API_KEY=dev-api-key
ADMIN_TOKEN=dev-admin-token
COLLECTOR_ENCRYPTION_KEY=<base64-encoded-32-byte-key>
STRAVA_CLIENT_ID=<strava-client-id>
STRAVA_CLIENT_SECRET=<strava-client-secret>
QUARTZ_STORE_TYPE=ram
QUARTZ_CLUSTERED=false
```

Generate an encryption key:

```bash
openssl rand -base64 32
```

Run tests:

```bash
mise exec -- ./mvnw test
```

Start the app:

```bash
set -a && source .env.local && set +a
mise exec -- ./mvnw quarkus:dev -pl collector-runner -am
```

Or, if `just` is available:

```bash
ENV_FILE=.env.local just dev
```

Check health:

```bash
curl -i http://localhost:8090/q/health/live
curl -i http://localhost:8090/q/health/ready
```

First real run:

1. Insert a `STRAVA` row into `integration_credentials` or provide it through whatever bootstrap process you use.
2. Complete Strava OAuth in a client and send the authorization code to `POST /api/v1/athletes/register`.
3. Trigger jobs manually through `/admin/trigger/{jobId}` with `X-Admin-Token`, or wait for the Quartz schedule.

What it does not do yet:

- It does not include a user-facing dashboard.
- It does not implement Strava webhooks.
- It does not yet integrate Suunto, COROS, Garmin, or TrainingPeaks.
- It does not provide production deployment automation in this repository.

## Roadmap

- Add a Suunto integration using the same collector-module pattern as Strava.
- Build an analytics dashboard for activities, streams, training load, and gear usage.
- Add Strava webhook ingestion for lower-latency activity updates.
- Replace hard-coded job cron expressions with environment-driven scheduling.
- Expand derived metrics beyond the current training-load and normalized-power calculations.
- Add clearer bootstrap tooling for credentials and first-athlete registration.
