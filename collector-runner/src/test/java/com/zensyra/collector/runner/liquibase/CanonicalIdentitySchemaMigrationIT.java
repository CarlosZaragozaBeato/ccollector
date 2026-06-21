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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalIdentitySchemaMigrationIT {

    private static final List<String> IDENTITY_CHANGELOGS = List.of(
            "db/changelog/changes/028-create-athlete-profiles.yaml",
            "db/changelog/changes/029-create-integration-accounts.yaml",
            "db/changelog/changes/030-create-training-sessions.yaml",
            "db/changelog/changes/031-create-activity-references.yaml"
    );

    @Test
    void shouldCreateCanonicalIdentityTablesWithConstraints() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                for (String changeLog : IDENTITY_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                assertCoreTablesAndConstraintsExist(connection);
                assertCoreIndexesExist(connection);
                assertNoRedundantUniqueIndex(connection);
                assertIdentityConstraintsAreEnforced(connection);
            }
        }
    }

    private void assertCoreTablesAndConstraintsExist(Connection connection) throws Exception {
        assertEquals(1, count(connection, "select count(*) from information_schema.tables "
                + "where table_name = 'athlete_profiles'"));
        assertEquals(1, count(connection, "select count(*) from information_schema.tables "
                + "where table_name = 'integration_accounts'"));
        assertEquals(1, count(connection, "select count(*) from information_schema.tables "
                + "where table_name = 'training_sessions'"));
        assertEquals(1, count(connection, "select count(*) from information_schema.tables "
                + "where table_name = 'activity_references'"));

        assertTrue(constraintExists(connection, "pk_athlete_profiles"));
        assertTrue(constraintExists(connection, "pk_integration_accounts"));
        assertTrue(constraintExists(connection, "fk_integration_accounts_athlete_id"));
        assertTrue(constraintExists(connection, "uq_integration_accounts_source_external_user"));
        assertTrue(constraintExists(connection, "pk_training_sessions"));
        assertTrue(constraintExists(connection, "fk_training_sessions_athlete_id"));
        assertTrue(constraintExists(connection, "pk_activity_references"));
        assertTrue(constraintExists(connection, "fk_activity_references_athlete_id"));
        assertTrue(constraintExists(connection, "fk_activity_references_training_session_id"));
        assertTrue(constraintExists(connection, "fk_activity_references_integration_account_id"));
        assertTrue(constraintExists(connection, "uq_activity_references_account_external_activity"));
    }

    private void assertCoreIndexesExist(Connection connection) throws Exception {
        assertTrue(indexExists(connection, "idx_integration_accounts_athlete_id"));
        assertTrue(indexExists(connection, "idx_training_sessions_athlete_id"));
        assertTrue(indexExists(connection, "idx_activity_references_athlete_id"));
        assertTrue(indexExists(connection, "idx_activity_references_training_session_id"));
    }

    /**
     * uq_integration_accounts_source_external_user already creates its own backing
     * unique index in PostgreSQL. There must be no separate, manually-created index
     * duplicating the same column pair — see 029-create-integration-accounts.yaml.
     */
    private void assertNoRedundantUniqueIndex(Connection connection) throws Exception {
        assertEquals(0, count(connection,
                "select count(*) from pg_indexes "
                        + "where tablename = 'integration_accounts' "
                        + "and indexname = 'idx_integration_accounts_source_external_user'"));
        assertEquals(1, count(connection,
                "select count(*) from pg_indexes "
                        + "where tablename = 'integration_accounts' "
                        + "and indexdef like '%source%external_user_id%'"));
    }

    private void assertIdentityConstraintsAreEnforced(Connection connection) throws Exception {
        execute(connection, """
                insert into athlete_profiles (id, created_at, updated_at)
                values
                  ('11111111-1111-1111-1111-111111111111', now(), now()),
                  ('22222222-2222-2222-2222-222222222222', now(), now())
                """);
        execute(connection, """
                insert into integration_accounts (
                  id,
                  athlete_id,
                  source,
                  external_user_id,
                  created_at,
                  updated_at
                )
                values (
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                  '11111111-1111-1111-1111-111111111111',
                  'STRAVA',
                  '1001',
                  now(),
                  now()
                )
                """);

        assertEquals("ACTIVE", text(connection, """
                select connection_status
                from integration_accounts
                where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
                """));

        assertDoesNotThrow(() -> execute(connection, """
                insert into integration_accounts (
                  id,
                  athlete_id,
                  source,
                  external_user_id,
                  created_at,
                  updated_at
                )
                values (
                  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                  '11111111-1111-1111-1111-111111111111',
                  'STRAVA',
                  '1002',
                  now(),
                  now()
                )
                """));
        assertThrows(SQLException.class, () -> execute(connection, """
                insert into integration_accounts (
                  id,
                  athlete_id,
                  source,
                  external_user_id,
                  created_at,
                  updated_at
                )
                values (
                  '33333333-3333-3333-3333-333333333333',
                  '22222222-2222-2222-2222-222222222222',
                  'STRAVA',
                  '1001',
                  now(),
                  now()
                )
                """));
        assertThrows(SQLException.class, () -> execute(connection, """
                insert into integration_accounts (
                  id,
                  athlete_id,
                  source,
                  external_user_id,
                  connection_status,
                  created_at,
                  updated_at
                )
                values (
                  '44444444-4444-4444-4444-444444444444',
                  '11111111-1111-1111-1111-111111111111',
                  'STRAVA',
                  '1003',
                  'BROKEN',
                  now(),
                  now()
                )
                """));

        execute(connection, """
                insert into training_sessions (id, athlete_id, created_at, updated_at)
                values (
                  'cccccccc-cccc-cccc-cccc-cccccccccccc',
                  '11111111-1111-1111-1111-111111111111',
                  now(),
                  now()
                )
                """);
        execute(connection, """
                insert into activity_references (
                  id,
                  athlete_id,
                  training_session_id,
                  integration_account_id,
                  external_activity_id,
                  created_at,
                  updated_at
                )
                values (
                  'dddddddd-dddd-dddd-dddd-dddddddddddd',
                  '11111111-1111-1111-1111-111111111111',
                  'cccccccc-cccc-cccc-cccc-cccccccccccc',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                  '5001',
                  now(),
                  now()
                )
                """);

        assertDoesNotThrow(() -> execute(connection, """
                insert into activity_references (
                  id,
                  athlete_id,
                  training_session_id,
                  integration_account_id,
                  external_activity_id,
                  created_at,
                  updated_at
                )
                values (
                  'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
                  '11111111-1111-1111-1111-111111111111',
                  'cccccccc-cccc-cccc-cccc-cccccccccccc',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                  '5002',
                  now(),
                  now()
                )
                """));
        assertThrows(SQLException.class, () -> execute(connection, """
                insert into activity_references (
                  id,
                  athlete_id,
                  training_session_id,
                  integration_account_id,
                  external_activity_id,
                  created_at,
                  updated_at
                )
                values (
                  'ffffffff-ffff-ffff-ffff-ffffffffffff',
                  '11111111-1111-1111-1111-111111111111',
                  'cccccccc-cccc-cccc-cccc-cccccccccccc',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                  '5001',
                  now(),
                  now()
                )
                """));
        assertThrows(SQLException.class, () -> execute(connection, """
                insert into activity_references (
                  id,
                  athlete_id,
                  training_session_id,
                  integration_account_id,
                  external_activity_id,
                  created_at,
                  updated_at
                )
                values (
                  '99999999-9999-9999-9999-999999999999',
                  '99999999-9999-9999-9999-999999999999',
                  'cccccccc-cccc-cccc-cccc-cccccccccccc',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                  '5003',
                  now(),
                  now()
                )
                """));
    }

    private void applyChangeLog(Connection connection, String changeLog) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(changeLog, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean constraintExists(Connection connection, String constraintName) throws Exception {
        return count(connection, "select count(*) from pg_constraint where conname = '" + constraintName + "'") == 1;
    }

    private boolean indexExists(Connection connection, String indexName) throws Exception {
        return count(connection, "select count(*) from pg_indexes where indexname = '" + indexName + "'") == 1;
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String text(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}


