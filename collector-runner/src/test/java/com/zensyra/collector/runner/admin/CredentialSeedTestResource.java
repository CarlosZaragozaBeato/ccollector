package com.zensyra.collector.runner.admin;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts a TimescaleDB container for the credential seed IT. TimescaleDB is required
 * because the full master changelog (which the runner application depends on to start
 * cleanly) includes hypertable creation via the strava changelog.
 */
public class CredentialSeedTestResource implements QuarkusTestResourceLifecycleManager {

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
            // Mirror docker/init-schema.sql: the application schema must exist so that
            // Liquibase (with default search_path "$user",public) creates tables there,
            // matching the schema qualifiers used in the strava TimescaleDB migrations.
            statement.execute("CREATE SCHEMA IF NOT EXISTS collector");
            statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare TimescaleDB test container", e);
        }

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
