package com.zensyra.collector.strava.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class TimescaleDbTestResource implements QuarkusTestResourceLifecycleManager {

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
            statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare TimescaleDB test container", e);
        }

        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.datasource.db-kind", "postgresql");
        properties.put("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        properties.put("quarkus.datasource.username", container.getUsername());
        properties.put("quarkus.datasource.password", container.getPassword());
        properties.put("quarkus.hibernate-orm.database.generation", "validate");
        properties.put("quarkus.liquibase.enabled", "true");
        properties.put("quarkus.liquibase.migrate-at-start", "true");
        properties.put("quarkus.liquibase.change-log", "db/changelog/db.changelog-master.yaml");
        properties.put("quarkus.devservices.enabled", "false");
        properties.put("Retry/enabled", "false");
        return properties;
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
