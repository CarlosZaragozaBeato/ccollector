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
- `collector-dashboard`: React/Vite SPA compiled at build time and served as static resources by Quarkus. No runtime Node.js dependency.
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
- A Strava API application (see [Connect Your Strava Account](#connect-your-strava-account) below).

### Environment variables

Copy `.env.example` to `.env.local` and fill in the required values:

```bash
cp .env.example .env.local
```

The file is annotated with which variables are required and which have working
defaults. Never commit `.env.local` — it is in `.gitignore`.

Required variables:

| Variable | Description |
|----------|-------------|
| `DB_USERNAME` | PostgreSQL user |
| `DB_PASSWORD` | PostgreSQL password |
| `DB_JDBC_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/zensyra?currentSchema=collector` |
| `COLLECTOR_ENCRYPTION_KEY` | AES-256-GCM key (32 bytes, base64). Generate: `openssl rand -base64 32` |
| `COLLECTOR_API_KEY` | Secret for the read API (`X-API-Key` header on `/api/v1` routes) |
| `ADMIN_TOKEN` | Secret for admin routes (`X-Admin-Token` header on `/admin` routes) |

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
curl -i http://localhost:8080/q/health/live
curl -i http://localhost:8080/q/health/ready
```

### Dashboard

The dashboard is a React SPA served at `/dashboard/` by Quarkus.

**1. Configure your athlete ID and API key**

```bash
cp collector-dashboard/public/config.json.example \
   collector-dashboard/public/config.json
```

Edit `collector-dashboard/public/config.json`:

```json
{
  "athleteId": "<your-athlete-uuid>",
  "apiKey":    "<value of COLLECTOR_API_KEY>"
}
```

The `athleteId` is the UUID stored in the `athletes` table for your Strava account.
`config.json` is gitignored — it never leaves your machine.

**2. Build (includes frontend)**

```bash
./mvnw package -DskipTests
```

The `frontend-maven-plugin` downloads Node 20, runs `npm ci` and `vite build`, and
packages the built assets into `collector-dashboard.jar` under `META-INF/resources/dashboard/`.
Quarkus picks them up automatically.

To skip the frontend build during development iteration:

```bash
./mvnw package -DskipTests -P skip-frontend
```

**3. Open the dashboard**

```
http://localhost:8080/dashboard/
```

The dashboard has three tabs:
- **Fitness Trends** — CTL / ATL / TSB over time (last 30 / 60 / 90 days)
- **Weekly Load** — accumulated TSS per week for the last 12 weeks
- **Recent Activities** — last 20 activities

**Swagger UI** is available at `http://localhost:8080/q/swagger-ui`.

---

## Connect Your Strava Account

Follow these steps from a clean install. You need a running app instance and a
Strava API application before registering an athlete.

### Step 1 — Create a Strava API application

1. Log in at [strava.com](https://www.strava.com) with the account you want to use as the API owner (this does not have to be the athlete account you will sync).
2. Go to **Settings → My API Application**: [strava.com/settings/api](https://www.strava.com/settings/api).
3. Fill in the form:
   - **Application name**: anything, e.g. `CCollector Dev`.
   - **Category**: choose the closest option, e.g. `Training Analysis`.
   - **Club**: leave empty.
   - **Website**: any valid URL, e.g. `http://localhost`.
   - **Authorization Callback Domain**: for local development use `localhost`. For a deployed instance use your domain, e.g. `collector.yourdomain.com`.
4. Click **Create** (or **Update** if the app already exists).
5. Note the **Client ID** (a number) and the **Client Secret** (a long hex string) shown on the page. You will need both in Step 3.

### Step 2 — Start the application

Ensure your `.env.local` is complete and start the app:

```bash
ENV_FILE=.env.local just dev
# or:
set -a && source .env.local && set +a
mise exec -- ./mvnw quarkus:dev -pl collector-runner -am
```

Wait until the health endpoint returns `UP`:

```bash
curl -s http://localhost:8080/q/health/ready | jq .status
```

### Step 3 — Seed the Strava credentials into the database

The Client ID and Secret are stored in the `integration_credentials` table,
encrypted at rest with `COLLECTOR_ENCRYPTION_KEY`. Insert them via psql:

```sql
-- Connect to your database first, then:
INSERT INTO integration_credentials (source, client_id, client_secret)
VALUES ('STRAVA', '<your-client-id>', '<your-client-secret>');
```

> The `client_secret` column is encrypted by the application layer using
> AES-256-GCM. The plain-text value you insert here is encrypted on the
> first write and is never stored in clear text.

Verify the row was created:

```sql
SELECT id, source, client_id FROM integration_credentials WHERE source = 'STRAVA';
```

### Step 4 — Authorize the athlete account

Build the Strava authorization URL and open it in a browser. Replace
`<client-id>` with your numeric Client ID and `<redirect-uri>` with a URI
that Strava will redirect back to after the athlete approves access (it must
match the callback domain you configured in Step 1):

```
https://www.strava.com/oauth/authorize
  ?client_id=<client-id>
  &redirect_uri=<redirect-uri>
  &response_type=code
  &approval_prompt=auto
  &scope=read,activity:read_all,profile:read_all
```

Example for local development (redirect to localhost, code will be in the
browser address bar — no server needs to be listening on that URI for this
step):

```
https://www.strava.com/oauth/authorize?client_id=12345&redirect_uri=http://localhost/callback&response_type=code&approval_prompt=auto&scope=read,activity:read_all,profile:read_all
```

After the athlete clicks **Authorize**, Strava redirects to your callback URI
with a `code` query parameter:

```
http://localhost/callback?state=&code=abc123xyz&scope=read,activity:read_all,profile:read_all
```

Copy the value of `code` from the address bar.

### Step 5 — Register the athlete

Call the register endpoint with the code and the same redirect URI you used in
Step 4:

```bash
curl -s -X POST http://localhost:8080/api/v1/athletes/register \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <COLLECTOR_API_KEY>" \
  -d '{
    "code": "<paste-code-here>",
    "redirectUri": "<redirect-uri>"
  }' | jq .
```

A successful response looks like:

```json
{
  "athleteId": "a1b2c3d4-...",
  "created": true,
  "expiresAt": "2026-07-01T12:00:00Z"
}
```

The athlete's OAuth tokens are now stored, encrypted, in the `oauth_tokens`
table. The app will refresh them automatically before they expire.

### Step 6 — Trigger the initial sync

Jobs run on the Quartz schedule automatically, but you can also trigger them
immediately through the admin endpoint:

```bash
# List available jobs
curl -s http://localhost:8080/admin/jobs \
  -H "X-Admin-Token: <ADMIN_TOKEN>" | jq .

# Trigger the initial historical sync
curl -s -X POST http://localhost:8080/admin/trigger/strava.initial-historical-sync \
  -H "X-Admin-Token: <ADMIN_TOKEN>"

# Or trigger a standard activity sync
curl -s -X POST http://localhost:8080/admin/trigger/strava.sync-activities \
  -H "X-Admin-Token: <ADMIN_TOKEN>"
```

After the sync completes, query your activities:

```bash
curl -s "http://localhost:8080/api/v1/athletes/<athleteId>/activities?size=5" \
  -H "X-API-Key: <COLLECTOR_API_KEY>" | jq .
```

---

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
