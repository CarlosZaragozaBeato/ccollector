# zensyra-collector — task runner
# Uso: just <task>
set shell := ["bash", "-c"]

# ── Dev ──────────────────────────────────────────────────────────────────────
dev:
    mkdir -p collector-core/target/classes collector-strava/target/classes
    set -a && source "${ENV_FILE:-.env.local}" && set +a && \
    mise exec -- ./mvnw quarkus:dev -pl collector-runner -am

# ── Build ────────────────────────────────────────────────────────────────────

# Compilar todos los módulos
build:
    mise exec -- ./mvnw clean package -DskipTests

# Compilar sin tests, rápido
build-fast:
    mise exec -- ./mvnw clean package -DskipTests -T 4

# Compilar binario nativo (requiere GraalVM)
native:
    mise exec -- ./mvnw clean package -Pnative -pl collector-runner -am

# ── Test ─────────────────────────────────────────────────────────────────────

# Todos los tests
test:
    mise exec -- ./mvnw test

# Tests de un módulo concreto (uso: just test-module collector-strava)
test-module module:
    mise exec -- ./mvnw test -pl {{module}}

# Tests de integración
test-integration:
    mise exec -- ./mvnw verify -pl collector-runner

# ── Utils ────────────────────────────────────────────────────────────────────

# Ver árbol de dependencias del runner
deps:
    mise exec -- ./mvnw dependency:tree -pl collector-runner

# Limpiar
clean:
    mise exec -- ./mvnw clean

# Versión de Java y Quarkus
info:
    java -version
    mise exec -- ./mvnw --version
