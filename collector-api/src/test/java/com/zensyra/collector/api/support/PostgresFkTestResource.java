package com.zensyra.collector.api.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Stands up a real PostgreSQL for the FK-violation integration test. Mirrors
 * {@code collector-strava}'s TimescaleDbTestResource (same image and
 * Testcontainers version) for consistency.
 *
 * <p>{@code athlete_profiles} is the FK target of the journal tables, but its
 * migration (028) lives in collector-runner, which is not on collector-api's
 * classpath. So we create a minimal {@code athlete_profiles} here, then let
 * Quarkus run the journal changelog (035/037/038 — on the classpath via
 * collector-journal) which adds the real FK constraints against it.
 */
public class PostgresFkTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        DockerImageName image = DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                .asCompatibleSubstituteFor("postgres");
        container = new PostgreSQLContainer<>(image)
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector");
        container.start();

        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {
            // FK target — minimal shape sufficient for the FK reference.
            statement.execute(
                    "CREATE TABLE athlete_profiles ("
                            + "id UUID PRIMARY KEY, "
                            + "ftp_watts INTEGER, "
                            + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                            + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare FK target for test container", e);
        }

        // Runtime-only config — the dynamic container URL. Build-time config
        // (db-kind, liquibase change-log, hibernate generation) lives in
        // PostgresFkTestProfile because those cannot be set at runtime.
        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        properties.put("quarkus.datasource.username", container.getUsername());
        properties.put("quarkus.datasource.password", container.getPassword());
        return properties;
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
