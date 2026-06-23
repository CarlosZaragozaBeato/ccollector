package com.zensyra.collector.core.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides a real PostgreSQL instance to {@code @QuarkusTest} classes in this
 * module, bypassing Quarkus Dev Services entirely.
 *
 * <p>Dev Services resolves Testcontainers transitively through the Quarkus
 * BOM, which can lag behind the explicit {@code org.testcontainers:postgresql}
 * version this project pins (see {@code testcontainers.version} in the root
 * POM) and fails to negotiate a Docker API version against recent Docker
 * Engines. This resource manager starts the container directly using the
 * known-working pinned version instead.
 *
 * <p>{@code quarkus.datasource.db-kind} is declared statically in this
 * module's test {@code application.properties}, because Hibernate ORM
 * resolves the persistence unit descriptor at build time and needs to know
 * a datasource of this kind exists before this class ever runs. Only the
 * dynamic connection details (host, port, credentials) — known only once
 * the container has actually started — are injected here.
 *
 * <p>Usage: annotate the test class with
 * {@code @QuarkusTestResource(PostgresTestResource.class)}.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "postgres:16-alpine";

    private PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector");
        container.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.username", container.getUsername());
        config.put("quarkus.datasource.password", container.getPassword());
        config.put("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        return config;
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
