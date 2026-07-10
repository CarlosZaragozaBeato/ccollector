#!/usr/bin/env bash
# =============================================================================
# CCollector — seed-strava-credentials.sh
#
# Reads STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET, ADMIN_TOKEN, and HTTP_PORT
# from .env, waits for the app to be ready, then calls
# POST /admin/credentials/strava to seed the credentials into the database
# through JPA AES-GCM encryption (issue #47).
#
# Usage:
#   ./scripts/seed-strava-credentials.sh
#
# Run AFTER:
#   scripts/bootstrap-env.sh    — generates .env with all required values
#   docker compose up --build   — starts the app (Liquibase migrations run
#                                  automatically on first boot)
#
# Idempotent: safe to re-run. The endpoint always overwrites the existing
# credential row (delete-then-insert via Panache bulk DELETE, which never
# invokes the JPA @Convert decryptor, so it is safe whether the existing
# row is encrypted or plaintext).
#
# Secret discipline:
#   - ADMIN_TOKEN is written to a curl config file (-K) so it never appears
#     as a command-line argument visible in `ps aux`.
#   - STRAVA_CLIENT_SECRET is written to a temp file (--data @file) for the
#     same reason. printf is a bash builtin and therefore never appears as a
#     separate process in `ps`.
#   - All temp files are mode 600 and removed via a trap on exit.
#   - Neither STRAVA_CLIENT_SECRET nor ADMIN_TOKEN appear in terminal output.
#
# Dependencies: bash, curl (standard on macOS and most Linux distros).
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env"

# ── Helpers (matching bootstrap-env.sh conventions) ──────────────────────────

info()  { printf '\033[0;32m[seed]\033[0m %s\n' "$*" >&2; }
warn()  { printf '\033[0;33m[seed]\033[0m %s\n' "$*" >&2; }
error() { printf '\033[0;31m[seed]\033[0m %s\n' "$*" >&2; }

# Read KEY from .env, return empty if absent or still CHANGE_ME.
read_env() {
    local key="$1"
    local val
    val=$(grep -E "^${key}=" "${ENV_FILE}" 2>/dev/null | head -1 | cut -d= -f2-)
    if [[ -n "$val" && "$val" != "CHANGE_ME" ]]; then
        printf '%s' "$val"
    fi
}

# ── Pre-flight checks ─────────────────────────────────────────────────────────

if [[ ! -f "${ENV_FILE}" ]]; then
    error ".env not found at ${ENV_FILE}."
    error "Run scripts/bootstrap-env.sh first to generate it."
    exit 1
fi

admin_token=$(read_env ADMIN_TOKEN)
strava_client_id=$(read_env STRAVA_CLIENT_ID)
strava_client_secret=$(read_env STRAVA_CLIENT_SECRET)
http_port=$(read_env HTTP_PORT)
http_port="${http_port:-8080}"

missing=()
[[ -z "$admin_token" ]]          && missing+=("ADMIN_TOKEN")
[[ -z "$strava_client_id" ]]     && missing+=("STRAVA_CLIENT_ID")
[[ -z "$strava_client_secret" ]] && missing+=("STRAVA_CLIENT_SECRET")

if [[ "${#missing[@]}" -gt 0 ]]; then
    error "The following required values are missing or still set to CHANGE_ME in .env:"
    for m in "${missing[@]}"; do
        error "  - $m"
    done
    error "Run scripts/bootstrap-env.sh first."
    exit 1
fi

info "CCollector — Strava credential seeding"
info "Endpoint: http://localhost:${http_port}/admin/credentials/strava"
info "Client ID: ${strava_client_id}"

# ── Wait for app readiness ────────────────────────────────────────────────────
# Poll /q/health/ready (Quarkus SmallRye Health readiness endpoint).
# The app service in docker-compose.yml has no explicit healthcheck, so we
# poll directly. 5-minute total timeout with 5-second intervals.

HEALTH_URL="http://localhost:${http_port}/q/health/ready"
WAIT_INTERVAL=5
WAIT_MAX_SECONDS=300
WAIT_ATTEMPTS=$(( WAIT_MAX_SECONDS / WAIT_INTERVAL ))

info "Waiting for app readiness at ${HEALTH_URL} (timeout: ${WAIT_MAX_SECONDS}s)..."

attempt=0
while [[ $attempt -lt $WAIT_ATTEMPTS ]]; do
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 \
                "${HEALTH_URL}" 2>/dev/null || echo "000")
    if [[ "$http_code" == "200" ]]; then
        info "App is ready."
        break
    fi
    attempt=$(( attempt + 1 ))
    if [[ $attempt -ge $WAIT_ATTEMPTS ]]; then
        printf '\n' >&2
        error "Timed out after ${WAIT_MAX_SECONDS}s — app did not become ready."
        error "Ensure the stack is running:  docker compose up --build"
        error "Check readiness manually:     curl -s ${HEALTH_URL}"
        exit 1
    fi
    printf '\r\033[0;32m[seed]\033[0m Attempt %d/%d — app not ready yet (HTTP %s), retrying in %ds...' \
        "$attempt" "$WAIT_ATTEMPTS" "$http_code" "$WAIT_INTERVAL" >&2
    sleep "$WAIT_INTERVAL"
done
printf '\n' >&2

# ── Seed credentials via curl ─────────────────────────────────────────────────
#
# Security approach — keeping secrets out of `ps` args:
#
#   ADMIN_TOKEN  → written to a curl config file, passed via -K.
#                  `ps` shows "-K /tmp/tmpXXX", not the token value.
#
#   STRAVA_CLIENT_SECRET → written to a temp file, passed via --data @file.
#                  `ps` shows "--data @/tmp/tmpYYY", not the secret.
#                  printf is a bash builtin (no subprocess → nothing in `ps`).
#
#   All temp files: mode 600, removed via trap regardless of exit path.

tmp_cfg=$(mktemp)
tmp_body=$(mktemp)
tmp_resp=$(mktemp)
chmod 600 "$tmp_cfg" "$tmp_body" "$tmp_resp"
trap 'rm -f "$tmp_cfg" "$tmp_body" "$tmp_resp"' EXIT INT TERM

# Write curl config (printf = bash builtin, never a separate process).
printf 'header = "X-Admin-Token: %s"\n' "$admin_token" > "$tmp_cfg"

# Write JSON body.
# Strava credentials from the developer portal are a numeric ID and a
# lowercase hex string — no characters requiring JSON escaping.
printf '{"clientId":"%s","clientSecret":"%s"}' \
    "$strava_client_id" "$strava_client_secret" > "$tmp_body"

SEED_URL="http://localhost:${http_port}/admin/credentials/strava"

http_status=$(curl -s \
    -K "$tmp_cfg" \
    -X POST \
    -H "Content-Type: application/json" \
    --data "@$tmp_body" \
    -o "$tmp_resp" \
    -w "%{http_code}" \
    "${SEED_URL}")

response_body=$(cat "$tmp_resp")

# ── Result ────────────────────────────────────────────────────────────────────

if [[ "$http_status" =~ ^2 ]]; then
    info "Credentials seeded successfully (HTTP ${http_status})."
    info "Response: ${response_body}"
    printf '\n' >&2
    info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    info "Strava credentials are now encrypted and stored in the database."
    info "Client ID: ${strava_client_id}"
    info "Client secret: (stored encrypted — never echoed)"
    info ""
    info "Next step — authorize the athlete and register:"
    info "  See README.md → 'Step 4: Authorize the athlete account'"
    info "  Then: POST /api/v1/athletes/register with the OAuth code"
    info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    printf '\n' >&2
    error "Seeding failed (HTTP ${http_status})."
    error "Response: ${response_body}"
    case "$http_status" in
        401) error "ADMIN_TOKEN in .env does not match the running app's admin.token config." ;;
        503) error "Admin endpoint not configured — app may not have loaded ADMIN_TOKEN from .env." ;;
        400) error "Request body rejected as invalid — check STRAVA_CLIENT_ID and STRAVA_CLIENT_SECRET in .env." ;;
        000) error "Could not connect to ${SEED_URL} — is the app running? (docker compose up --build)" ;;
    esac
    exit 1
fi
