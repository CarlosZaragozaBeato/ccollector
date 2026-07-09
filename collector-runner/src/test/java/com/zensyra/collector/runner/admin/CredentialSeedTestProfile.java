package com.zensyra.collector.runner.admin;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Switches the runner's datasource from H2 to a real TimescaleDB container for the
 * credential seed integration test. Build-time properties (db-kind, Liquibase settings)
 * live here because they trigger a separate Quarkus augmentation — they cannot come from
 * the runtime test resource. The full master changelog is required because starting the
 * runner application brings in all modules (including strava health checks) that query
 * tables beyond just integration_credentials.
 */
public class CredentialSeedTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.hibernate-orm.database.generation", "none",
                "quarkus.liquibase.enabled", "true",
                "quarkus.liquibase.migrate-at-start", "true",
                "quarkus.liquibase.change-log", "db/changelog/db.changelog-master.yaml",
                "quarkus.devservices.enabled", "false"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(CredentialSeedTestResource.class));
    }
}
