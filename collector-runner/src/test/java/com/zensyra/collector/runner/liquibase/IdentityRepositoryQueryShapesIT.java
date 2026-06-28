package com.zensyra.collector.runner.liquibase;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies, at the SQL level, the two query patterns backing
 * {@code IntegrationAccountRepository.findByAthleteId} and
 * {@code ActivityReferenceRepository.findByTrainingSessionId}.
 *
 * <p>These repository methods are not exercised here through Panache
 * directly. {@code @QuarkusTest} with {@code @QuarkusTestResource} resolves
 * Testcontainers through Quarkus's own deployment classloader, which is
 * governed by the quarkus-bom independently of any version pinned in this
 * project's own {@code <dependencies>} — so the explicitly-pinned, working
 * {@code org.testcontainers:postgresql} version this project relies on for
 * Docker API compatibility (see root POM, {@code testcontainers.version})
 * is bypassed in that path. Plain JDBC integration tests, as used
 * throughout this class and {@link CanonicalIdentitySchemaMigrationIT}, are
 * not subject to that classloader and use the pinned version correctly.
 *
 * <p>Each method under test is a single-line JPQL query with no
 * intervening business logic, so verifying the equivalent SQL against a
 * real schema gives equivalent confidence without depending on Quarkus's
 * test-resource machinery.
 */
class IdentityRepositoryQueryShapesIT {

    private static final List<String> IDENTITY_CHANGELOGS = List.of(
            "db/changelog/changes/028-create-athlete-profiles.yaml",
            "db/changelog/changes/029-create-integration-accounts.yaml",
            "db/changelog/changes/030-create-training-sessions.yaml",
            "db/changelog/changes/031-create-activity-references.yaml"
    );

    @Test
    void findByAthleteIdShouldReturnOnlyAccountsOwnedByThatAthlete() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                for (String changeLog : IDENTITY_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                UUID athleteId = UUID.randomUUID();
                UUID otherAthleteId = UUID.randomUUID();
                UUID ownAccountId = UUID.randomUUID();
                UUID otherAccountId = UUID.randomUUID();

                execute(connection, """
                        insert into athlete_profiles (id, created_at, updated_at)
                        values
                          ('%s', now(), now()),
                          ('%s', now(), now())
                        """.formatted(athleteId, otherAthleteId));
                execute(connection, """
                        insert into integration_accounts (id, athlete_id, source, external_user_id, created_at, updated_at)
                        values
                          ('%s', '%s', 'STRAVA', '111', now(), now()),
                          ('%s', '%s', 'STRAVA', '222', now(), now())
                        """.formatted(ownAccountId, athleteId, otherAccountId, otherAthleteId));

                // Equivalent to IntegrationAccountRepository.findByAthleteId(athleteId).
                List<UUID> result = queryIds(connection, """
                        select id from integration_accounts where athlete_id = '%s'
                        """.formatted(athleteId));

                assertEquals(1, result.size());
                assertEquals(ownAccountId, result.get(0));
            }
        }
    }

    @Test
    void findByAthleteIdShouldReturnEmptyForAthleteWithNoConnectedAccounts() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                for (String changeLog : IDENTITY_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                List<UUID> result = queryIds(connection, """
                        select id from integration_accounts where athlete_id = '%s'
                        """.formatted(UUID.randomUUID()));

                assertTrue(result.isEmpty());
            }
        }
    }

    @Test
    void findByTrainingSessionIdShouldReturnTheBackingObservation() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                for (String changeLog : IDENTITY_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                UUID athleteId = UUID.randomUUID();
                UUID accountId = UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID referenceId = UUID.randomUUID();

                execute(connection, """
                        insert into athlete_profiles (id, created_at, updated_at)
                        values ('%s', now(), now())
                        """.formatted(athleteId));
                execute(connection, """
                        insert into integration_accounts (id, athlete_id, source, external_user_id, created_at, updated_at)
                        values ('%s', '%s', 'STRAVA', '111', now(), now())
                        """.formatted(accountId, athleteId));
                execute(connection, """
                        insert into training_sessions (id, athlete_id, created_at, updated_at)
                        values ('%s', '%s', now(), now())
                        """.formatted(sessionId, athleteId));
                execute(connection, """
                        insert into activity_references (
                          id, athlete_id, training_session_id, integration_account_id, external_activity_id, created_at, updated_at
                        )
                        values ('%s', '%s', '%s', '%s', '999', now(), now())
                        """.formatted(referenceId, athleteId, sessionId, accountId));

                // Equivalent to ActivityReferenceRepository.findByTrainingSessionId(sessionId).
                List<UUID> result = queryIds(connection, """
                        select id from activity_references where training_session_id = '%s'
                        """.formatted(sessionId));

                assertEquals(1, result.size());
                assertEquals(referenceId, result.get(0));
            }
        }
    }

    @Test
    void findByTrainingSessionIdShouldReturnEmptyWhenNoObservationExistsYet() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                for (String changeLog : IDENTITY_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                List<UUID> result = queryIds(connection, """
                        select id from activity_references where training_session_id = '%s'
                        """.formatted(UUID.randomUUID()));

                assertTrue(result.isEmpty());
            }
        }
    }

    private void applyChangeLog(Connection connection, String changeLog) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(changeLog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts());
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<UUID> queryIds(Connection connection, String sql) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ids.add(UUID.fromString(resultSet.getString(1)));
            }
        }
        return ids;
    }
}
