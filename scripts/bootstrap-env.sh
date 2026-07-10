#!/usr/bin/env bash
# =============================================================================
# CCollector — bootstrap-env.sh
#
# Generates a complete .env file and syncs collector-dashboard/public/config.json
# with auto-generated secrets and sensible defaults. Safe to re-run: values
# already set in .env are never overwritten.
#
# Usage:
#   ./scripts/bootstrap-env.sh
#   STRAVA_CLIENT_ID=<id> STRAVA_CLIENT_SECRET=<secret> ./scripts/bootstrap-env.sh
#
# Strava credentials can also be entered interactively when prompted.
# They are written to .env as a handoff for scripts/seed-strava-credentials.sh
# (issue #49); the application itself loads them from the database, not from env.
#
# Referenced from README.md — Quick Start.
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env"
ENV_EXAMPLE="${REPO_ROOT}/.env.example"
CONFIG_JSON="${REPO_ROOT}/collector-dashboard/public/config.json"
CONFIG_JSON_EXAMPLE="${REPO_ROOT}/collector-dashboard/public/config.json.example"

# ── Helpers ──────────────────────────────────────────────────────────────────

# Print to stderr so it doesn't pollute any captured output.
info()  { printf '\033[0;32m[bootstrap]\033[0m %s\n' "$*" >&2; }
warn()  { printf '\033[0;33m[bootstrap]\033[0m %s\n' "$*" >&2; }
error() { printf '\033[0;31m[bootstrap]\033[0m %s\n' "$*" >&2; }

# Return the current value of KEY in .env, or empty string if absent/placeholder.
get_existing() {
    local key="$1"
    local val
    val=$(grep -E "^${key}=" "${ENV_FILE}" 2>/dev/null | head -1 | cut -d= -f2-)
    if [[ -n "$val" && "$val" != "CHANGE_ME" ]]; then
        printf '%s' "$val"
    fi
}

# Write KEY=VALUE to .env, replacing the line if the key already exists.
set_value() {
    local key="$1"
    local value="$2"
    if grep -qE "^${key}=" "${ENV_FILE}" 2>/dev/null; then
        # Replace existing line in-place (portable: write temp then move).
        local tmp
        tmp=$(mktemp)
        sed "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}" > "${tmp}"
        mv "${tmp}" "${ENV_FILE}"
    else
        printf '%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}"
    fi
}

generate_secret() {
    openssl rand -base64 32
}

# ── Strava credential collection (fail-fast) ─────────────────────────────────

# Accept via leading env vars, existing .env values, or interactive prompt.
# Never echo secrets to the terminal; read -s suppresses input display.

collect_strava_client_id() {
    # 1. caller set it in the environment
    if [[ -n "${STRAVA_CLIENT_ID:-}" ]]; then
        printf '%s' "${STRAVA_CLIENT_ID}"
        return
    fi
    # 2. already in .env as a real value
    local existing
    existing=$(get_existing STRAVA_CLIENT_ID)
    if [[ -n "$existing" ]]; then
        printf '%s' "$existing"
        return
    fi
    # 3. prompt
    if [[ -t 0 ]]; then
        printf '\n\033[0;33mStrava Client ID (numeric) — get it from https://www.strava.com/settings/api\033[0m\n' >&2
        printf 'See README.md → "Step 1: Create a Strava API application"\n' >&2
        read -r -p "STRAVA_CLIENT_ID: " strava_id </dev/tty
        printf '%s' "$strava_id"
    else
        echo "" >&2
        error "STRAVA_CLIENT_ID is required but was not provided."
        error "These credentials cannot be auto-generated."
        error "Get them from your Strava API application:"
        error "  → https://www.strava.com/settings/api"
        error "See README.md: 'Step 1 — Create a Strava API application'"
        error ""
        error "Re-run with:"
        error "  STRAVA_CLIENT_ID=<id> STRAVA_CLIENT_SECRET=<secret> ./scripts/bootstrap-env.sh"
        exit 1
    fi
}

collect_strava_client_secret() {
    if [[ -n "${STRAVA_CLIENT_SECRET:-}" ]]; then
        printf '%s' "${STRAVA_CLIENT_SECRET}"
        return
    fi
    local existing
    existing=$(get_existing STRAVA_CLIENT_SECRET)
    if [[ -n "$existing" ]]; then
        printf '%s' "$existing"
        return
    fi
    if [[ -t 0 ]]; then
        printf '\033[0;33mStrava Client Secret — same page as above\033[0m\n' >&2
        read -r -s -p "STRAVA_CLIENT_SECRET: " strava_secret </dev/tty
        printf '\n' >&2
        printf '%s' "$strava_secret"
    else
        echo "" >&2
        error "STRAVA_CLIENT_SECRET is required but was not provided."
        error "Get it from: https://www.strava.com/settings/api"
        error ""
        error "Re-run with:"
        error "  STRAVA_CLIENT_ID=<id> STRAVA_CLIENT_SECRET=<secret> ./scripts/bootstrap-env.sh"
        exit 1
    fi
}

# ── Main ─────────────────────────────────────────────────────────────────────

info "CCollector — environment bootstrap"
info "Target: ${ENV_FILE}"

# Initialise .env from example if it doesn't exist yet.
if [[ ! -f "${ENV_FILE}" ]]; then
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    info "Created .env from .env.example"
fi

# Collect Strava credentials first (fail-fast before doing any generation work).
strava_client_id=$(collect_strava_client_id)
strava_client_secret=$(collect_strava_client_secret)

if [[ -z "$strava_client_id" ]]; then
    error "STRAVA_CLIENT_ID cannot be empty."
    error "Get it from: https://www.strava.com/settings/api"
    error "See README.md: 'Step 1 — Create a Strava API application'"
    exit 1
fi
if [[ -z "$strava_client_secret" ]]; then
    error "STRAVA_CLIENT_SECRET cannot be empty."
    error "Get it from: https://www.strava.com/settings/api"
    exit 1
fi

# ── Per-variable resolution ───────────────────────────────────────────────────

# DB_USERNAME — fixed sensible default, no entropy needed.
if [[ -z "$(get_existing DB_USERNAME)" ]]; then
    set_value DB_USERNAME ccollector
    info "DB_USERNAME → ccollector (default)"
else
    info "DB_USERNAME → (kept existing)"
fi

# DB_PASSWORD — auto-generate.
if [[ -z "$(get_existing DB_PASSWORD)" ]]; then
    set_value DB_PASSWORD "$(generate_secret)"
    info "DB_PASSWORD → (generated)"
else
    info "DB_PASSWORD → (kept existing)"
fi

# DB_JDBC_URL — sensible localhost default; docker-compose overrides this at runtime.
if [[ -z "$(get_existing DB_JDBC_URL)" ]]; then
    set_value DB_JDBC_URL "jdbc:postgresql://localhost:5432/collector?currentSchema=collector"
    info "DB_JDBC_URL → jdbc:postgresql://localhost:5432/collector?currentSchema=collector (default; docker-compose overrides this automatically)"
else
    info "DB_JDBC_URL → (kept existing)"
fi

# POSTGRES_DB — fixed, already has a real default in .env.example.
if [[ -z "$(get_existing POSTGRES_DB)" ]]; then
    set_value POSTGRES_DB collector
    info "POSTGRES_DB → collector (default)"
else
    info "POSTGRES_DB → (kept existing)"
fi

# COLLECTOR_ENCRYPTION_KEY — auto-generate.
if [[ -z "$(get_existing COLLECTOR_ENCRYPTION_KEY)" ]]; then
    set_value COLLECTOR_ENCRYPTION_KEY "$(generate_secret)"
    info "COLLECTOR_ENCRYPTION_KEY → (generated)"
else
    info "COLLECTOR_ENCRYPTION_KEY → (kept existing)"
fi

# COLLECTOR_API_KEY — auto-generate, then dual-write to config.json.
if [[ -z "$(get_existing COLLECTOR_API_KEY)" ]]; then
    api_key="$(generate_secret)"
    set_value COLLECTOR_API_KEY "$api_key"
    info "COLLECTOR_API_KEY → (generated)"
else
    api_key="$(get_existing COLLECTOR_API_KEY)"
    info "COLLECTOR_API_KEY → (kept existing)"
fi

# ADMIN_TOKEN — auto-generate.
if [[ -z "$(get_existing ADMIN_TOKEN)" ]]; then
    set_value ADMIN_TOKEN "$(generate_secret)"
    info "ADMIN_TOKEN → (generated)"
else
    info "ADMIN_TOKEN → (kept existing)"
fi

# HTTP_PORT — fixed default.
if [[ -z "$(get_existing HTTP_PORT)" ]]; then
    set_value HTTP_PORT 8080
    info "HTTP_PORT → 8080 (default)"
else
    info "HTTP_PORT → (kept existing)"
fi

# QUARKUS_LOG_LEVEL — fixed default.
if [[ -z "$(get_existing QUARKUS_LOG_LEVEL)" ]]; then
    set_value QUARKUS_LOG_LEVEL INFO
    info "QUARKUS_LOG_LEVEL → INFO (default)"
else
    info "QUARKUS_LOG_LEVEL → (kept existing)"
fi

# ZENSYRA_LOG_LEVEL — fixed default.
if [[ -z "$(get_existing ZENSYRA_LOG_LEVEL)" ]]; then
    set_value ZENSYRA_LOG_LEVEL DEBUG
    info "ZENSYRA_LOG_LEVEL → DEBUG (default)"
else
    info "ZENSYRA_LOG_LEVEL → (kept existing)"
fi

# QUARTZ_STORE_TYPE — fixed default.
if [[ -z "$(get_existing QUARTZ_STORE_TYPE)" ]]; then
    set_value QUARTZ_STORE_TYPE ram
    info "QUARTZ_STORE_TYPE → ram (default)"
else
    info "QUARTZ_STORE_TYPE → (kept existing)"
fi

# QUARTZ_CLUSTERED — fixed default.
if [[ -z "$(get_existing QUARTZ_CLUSTERED)" ]]; then
    set_value QUARTZ_CLUSTERED false
    info "QUARTZ_CLUSTERED → false (default)"
else
    info "QUARTZ_CLUSTERED → (kept existing)"
fi

# STRAVA_STREAMS_BATCH_SIZE — fixed default.
if [[ -z "$(get_existing STRAVA_STREAMS_BATCH_SIZE)" ]]; then
    set_value STRAVA_STREAMS_BATCH_SIZE 10
    info "STRAVA_STREAMS_BATCH_SIZE → 10 (default)"
else
    info "STRAVA_STREAMS_BATCH_SIZE → (kept existing)"
fi

# STRAVA_STREAMS_RETRY_LIMIT — fixed default.
if [[ -z "$(get_existing STRAVA_STREAMS_RETRY_LIMIT)" ]]; then
    set_value STRAVA_STREAMS_RETRY_LIMIT 3
    info "STRAVA_STREAMS_RETRY_LIMIT → 3 (default)"
else
    info "STRAVA_STREAMS_RETRY_LIMIT → (kept existing)"
fi

# STRAVA_CLIENT_ID / STRAVA_CLIENT_SECRET — handoff values for seed-strava-credentials.sh (#49).
if [[ -z "$(get_existing STRAVA_CLIENT_ID)" ]]; then
    set_value STRAVA_CLIENT_ID "$strava_client_id"
    info "STRAVA_CLIENT_ID → (set)"
else
    info "STRAVA_CLIENT_ID → (kept existing)"
fi

if [[ -z "$(get_existing STRAVA_CLIENT_SECRET)" ]]; then
    set_value STRAVA_CLIENT_SECRET "$strava_client_secret"
    info "STRAVA_CLIENT_SECRET → (set)"
else
    info "STRAVA_CLIENT_SECRET → (kept existing)"
fi

# ── config.json dual-write ────────────────────────────────────────────────────

if [[ ! -f "${CONFIG_JSON}" ]]; then
    cp "${CONFIG_JSON_EXAMPLE}" "${CONFIG_JSON}"
    info "Created collector-dashboard/public/config.json from config.json.example"
fi

# Update apiKey in-place. The athleteId field is left untouched.
sed -i "s|\"apiKey\":.*|\"apiKey\": \"${api_key}\"|" "${CONFIG_JSON}"
info "collector-dashboard/public/config.json → apiKey synced with COLLECTOR_API_KEY"

# ── Summary ───────────────────────────────────────────────────────────────────

printf '\n'
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "Bootstrap complete. Non-secret values:"
info "  DB_USERNAME              = $(get_existing DB_USERNAME)"
info "  DB_JDBC_URL              = $(get_existing DB_JDBC_URL)"
info "  POSTGRES_DB              = $(get_existing POSTGRES_DB)"
info "  HTTP_PORT                = $(get_existing HTTP_PORT)"
info "  QUARKUS_LOG_LEVEL        = $(get_existing QUARKUS_LOG_LEVEL)"
info "  ZENSYRA_LOG_LEVEL        = $(get_existing ZENSYRA_LOG_LEVEL)"
info "  QUARTZ_STORE_TYPE        = $(get_existing QUARTZ_STORE_TYPE)"
info "  QUARTZ_CLUSTERED         = $(get_existing QUARTZ_CLUSTERED)"
info "  STRAVA_STREAMS_BATCH_SIZE= $(get_existing STRAVA_STREAMS_BATCH_SIZE)"
info "  STRAVA_STREAMS_RETRY_LIMIT=$(get_existing STRAVA_STREAMS_RETRY_LIMIT)"
info "  STRAVA_CLIENT_ID         = $(get_existing STRAVA_CLIENT_ID)"
info ""
info "Secret values (DB_PASSWORD, COLLECTOR_ENCRYPTION_KEY, COLLECTOR_API_KEY,"
info "  ADMIN_TOKEN, STRAVA_CLIENT_SECRET) are set but not shown here."
info ""
info "Next steps:"
info "  1. docker compose up --build"
info "  2. Run scripts/seed-strava-credentials.sh to load Strava credentials"
info "     into the database via POST /admin/credentials/strava"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
