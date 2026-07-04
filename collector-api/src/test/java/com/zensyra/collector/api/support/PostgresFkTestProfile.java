package com.zensyra.collector.api.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Build-time config for the FK integration test: switches the datasource to
 * PostgreSQL and runs the real journal changelog. These are build-time
 * properties, so they cannot come from the (runtime) test resource — a dedicated
 * profile triggers a separate Quarkus augmentation with them, leaving every
 * other api test on its default H2 configuration untouched. The dynamic
 * container URL is supplied at runtime by {@link PostgresFkTestResource}.
 */
public class PostgresFkTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.hibernate-orm.database.generation", "none",
                "quarkus.liquibase.enabled", "true",
                "quarkus.liquibase.migrate-at-start", "true",
                "quarkus.liquibase.change-log", "db/changelog/db.changelog-journal.yaml",
                "quarkus.devservices.enabled", "false"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(PostgresFkTestResource.class));
    }
}
