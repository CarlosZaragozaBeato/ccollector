# CCollector

CCollector is a Java/Quarkus data collector for endurance-sport data. It currently integrates with Strava, stores OAuth tokens and athlete data in PostgreSQL, syncs activities, activity details, laps, best efforts, gear, routes, zones, stats snapshots, and high-volume activity streams into TimescaleDB, then exposes a small secured read API for downstream products. It is a working ingestion/read-service backend, not a finished consumer application.

## Quick Start

Bring up a full local stack (TimescaleDB + app) with a single command.
The DB schema is created automatically by Liquibase on first boot — no manual SQL steps.

**Prerequisites:**
- Docker v24+ with the Compose plugin — included in [Docker Desktop](https://docs.docker.com/desktop/);
  for Linux, install [Docker Engine](https://docs.docker.com/engine/install/) +
  the [Compose plugin](https://docs.docker.com/compose/install/).
  Use `docker compose` (v2), not the legacy `docker-compose` (v1).
- Git
- No JDK, Node.js, or Maven required — the multi-stage Dockerfile handles the full build.

```bash
# 1. Clone
git clone https://github.com/CarlosZaragozaBeato/ccollector.git
cd ccollector

# 2. Create a Strava API application at https://www.strava.com/settings/api
#    Note the Client ID (a number) and Client Secret shown on that page —
#    you need them in the next step.
#    See "Connect Your Strava Account → Step 1" below for the full walkthrough.

# 3. Generate .env and config.json with all auto-generated secrets
STRAVA_CLIENT_ID=<your-client-id> STRAVA_CLIENT_SECRET=<your-client-secret> \
  bash scripts/bootstrap-env.sh
# Interactive alternative (prompts for Strava credentials):
#   bash scripts/bootstrap-env.sh
# With just:
#   just setup

# 4. Build and start
docker compose up --build

# 5. Seed Strava credentials into the database
#    (the script waits for the app to be ready, then calls the admin API)
bash scripts/seed-strava-credentials.sh
# With just:   just seed
```

Open **http://localhost:8080/dashboard/** once the stack is running.
Swagger UI is at **http://localhost:8080/q/swagger-ui**.

> **Dashboard athleteId — two-step process.**
> `config.json` is baked into the JAR at build time, so the dashboard shows a
> placeholder UUID until you rebuild after Strava registration.
> `apiKey` is written automatically by `bootstrap-env.sh` — no manual editing needed.
>
> **Step A — First run:** leave `athleteId` as the placeholder and complete the
> Strava setup below (seed credentials → authorize athlete → register).
> The `POST /api/v1/athletes/register` response contains your real `athleteId`.
>
> **Step B — After registration:** edit `collector-dashboard/public/config.json`
> and set `athleteId` to the UUID from the register response, then run:
> ```bash
> docker compose up --build
> ```
> You must use `--build` (not just `docker compose up`) — without it, Docker
> reuses the cached image and the dashboard still shows the old placeholder.

Follow **[Connect Your Strava Account](#connect-your-strava-account)** for the
full credential and registration walkthrough.

---

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
5. Note the **Client ID** (a number) and the **Client Secret** (a long hex string) shown on the page. You will need both for the bootstrap script (Quick Start step 3) and for Step 2 below.

### Step 2 — Seed Strava credentials into the database

With the stack running, load the Client ID and Secret into the database through
the admin API. The script waits for the app to be ready automatically:

```bash
bash scripts/seed-strava-credentials.sh
# or: just seed
```

The script reads `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `ADMIN_TOKEN`
from `.env` (written there by `bootstrap-env.sh`) and calls
`POST /admin/credentials/strava`, which stores the secret encrypted with
AES-256-GCM. It is safe to re-run if credentials change.

### Step 3 — Authorize the athlete account

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

### Step 4 — Register the athlete

Call the register endpoint with the code and the same redirect URI you used in
Step 3:

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

### Step 5 — Trigger the initial sync

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

## Troubleshooting

**`db` service fails healthcheck / app exits immediately**
The `pg_isready` check timed out. Usually means `DB_USERNAME` or `DB_PASSWORD` in `.env`
don't match the values PostgreSQL was initialised with. Stop the stack, remove the
data volume (`docker compose down -v`), correct the values, and start again.

**Liquibase fails with "schema collector does not exist"**
`docker/init-schema.sql` was not mounted into the `db` container. Verify the file exists
and that the volume mount in `docker-compose.yml` is intact. Remove the data volume and
restart: `docker compose down -v && docker compose up --build`.

**Dashboard shows placeholder UUID (all-zeros athleteId)**
`config.json` was not updated with your real `athleteId` after Strava registration, or
`docker compose up --build` was not re-run after editing it. Edit
`collector-dashboard/public/config.json` with the UUID from the register response, then
run `docker compose up --build` (the `--build` flag is required — without it Docker
reuses the cached image and the old config.json stays baked in).

**`seed-strava-credentials.sh` times out waiting for the app**
The script polls `/q/health/ready` for up to 5 minutes. If it times out, the app
has not started successfully. Run `docker compose logs app` to inspect errors,
confirm `docker compose up --build` has fully finished, and verify `HTTP_PORT`
in `.env` matches the port the app is bound to.

**`seed-strava-credentials.sh` exits with 401 or 503**
A 401 means the `ADMIN_TOKEN` in `.env` does not match the value the running app
loaded at startup. Stop and restart the stack (`docker compose down && docker compose up --build`)
so the app picks up the current `.env`. A 503 means the app started without
`ADMIN_TOKEN` configured at all — re-run `bootstrap-env.sh` to generate the
missing value, then restart.

**Port 8080 already in use**
Set `HTTP_PORT=<other-port>` in `.env` (e.g. `HTTP_PORT=8090`) and restart.

**`docker compose up --build` is slow the first time**
Expected — Maven resolves dependencies, the frontend-maven-plugin downloads Node 20 LTS,
and `npm ci` fetches packages. The full first build takes roughly 3–4 minutes. Subsequent
builds are faster because Docker caches the dependency resolution layer.

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
