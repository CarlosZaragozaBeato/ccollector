# =============================================================================
# Stage 1: build
# Maven + frontend-maven-plugin (downloads Node 20 LTS internally — no Node
# required on the host machine).
# =============================================================================
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Resolve Maven dependencies before copying source so this layer is cached
# as long as no pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY collector-core/pom.xml      collector-core/
COPY collector-query/pom.xml     collector-query/
COPY collector-strava/pom.xml    collector-strava/
COPY collector-journal/pom.xml   collector-journal/
COPY collector-api/pom.xml       collector-api/
COPY collector-dashboard/pom.xml collector-dashboard/
COPY collector-runner/pom.xml    collector-runner/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Full source copy then build.
# frontend-maven-plugin runs npm ci + vite build during prepare-package and
# bakes the SPA into collector-dashboard.jar under META-INF/resources/.
# collector-dashboard/public/config.json must already contain the correct
# athleteId before this step (see README Quick Start).
COPY . .
RUN ./mvnw package -DskipTests -q

# =============================================================================
# Stage 2: runtime
# JRE only — no JDK, no Maven, no Node.
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /app/collector-runner/target/quarkus-app/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
