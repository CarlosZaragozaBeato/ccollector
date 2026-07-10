# zensyra-collector — task runner
# Usage: just <task>
set shell := ["bash", "-c"]

# ── Setup ────────────────────────────────────────────────────────────────────

# Generate .env with auto-generated secrets and sync config.json (safe to re-run)
setup:
    bash scripts/bootstrap-env.sh

# ── Dev ──────────────────────────────────────────────────────────────────────
dev:
    mkdir -p collector-core/target/classes collector-strava/target/classes
    set -a && source "${ENV_FILE:-.env.local}" && set +a && \
    mise exec -- ./mvnw quarkus:dev -pl collector-runner -am

# ── Build ────────────────────────────────────────────────────────────────────

# Build all modules
build:
    mise exec -- ./mvnw clean package -DskipTests

# Build without tests, quickly
build-fast:
    mise exec -- ./mvnw clean package -DskipTests -T 4

# Build the native binary (requires GraalVM)
native:
    mise exec -- ./mvnw clean package -Pnative -pl collector-runner -am

# ── Test ─────────────────────────────────────────────────────────────────────

# Run all tests
test:
    mise exec -- ./mvnw test

# Tests for one module (usage: just test-module collector-strava)
test-module module:
    mise exec -- ./mvnw test -pl {{module}}

# Integration tests
test-integration:
    mise exec -- ./mvnw verify -pl collector-runner

# ── Utils ────────────────────────────────────────────────────────────────────

# Display the runner dependency tree
deps:
    mise exec -- ./mvnw dependency:tree -pl collector-runner

# Limpiar
clean:
    mise exec -- ./mvnw clean

# Java and Quarkus versions
info:
    java -version
    mise exec -- ./mvnw --version
