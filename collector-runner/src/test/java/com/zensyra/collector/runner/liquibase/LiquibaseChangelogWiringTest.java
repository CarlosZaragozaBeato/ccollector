package com.zensyra.collector.runner.liquibase;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseChangelogWiringTest {

    @Test
    void shouldExposeActivityStreamsChangelogsOnRunnerClasspath() throws Exception {
        assertNotNull(resource("db/changelog/db.changelog-master.yaml"));
        assertNotNull(resource("db/changelog/db.changelog-strava.yaml"));
        assertNotNull(resource("db/changelog/changes/012-create-activity-streams.yaml"));
        assertNotNull(resource("db/changelog/changes/013-expand-activities-stream-sync.yaml"));
    }

    @Test
    void shouldIncludeActivityStreamsChangesetsInLiquibaseChain() throws Exception {
        String master = resourceText("db/changelog/db.changelog-master.yaml");
        String strava = resourceText("db/changelog/db.changelog-strava.yaml");

        assertTrue(master.contains("file: db/changelog/db.changelog-strava.yaml"));
        assertTrue(strava.contains("file: db/changelog/changes/012-create-activity-streams.yaml"));
        assertTrue(strava.contains("file: db/changelog/changes/013-expand-activities-stream-sync.yaml"));
        assertTrue(!master.contains("includeAll"));
        assertTrue(!strava.contains("includeAll"));
    }

    private InputStream resource(String path) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    private String resourceText(String path) throws Exception {
        try (InputStream inputStream = resource(path)) {
            assertNotNull(inputStream, "Missing resource: " + path);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
