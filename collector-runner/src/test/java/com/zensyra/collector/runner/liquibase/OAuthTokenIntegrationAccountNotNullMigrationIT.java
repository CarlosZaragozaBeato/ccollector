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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthTokenIntegrationAccountNotNullMigrationIT {

    private static final List<String> CANONICAL_CHANGELOGS = List.of(
            "db/changelog/changes/028-create-athlete-profiles.yaml",
            "db/changelog/changes/029-create-integration-accounts.yaml",
            "db/changelog/changes/030-create-training-sessions.yaml",
            "db/changelog/changes/031-create-activity-references.yaml",
            "db/changelog/changes/032-link-oauth-tokens-to-integration-accounts.yaml",
            "db/changelog/changes/033-backfill-strava-canonical-data.yaml",
            "db/changelog/changes/034-enforce-oauth-tokens-integration-account-not-null.yaml"
    );

    @Test
    void shouldEnforceNotNullAfterBackfillCompletesCleanly() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("collector")
                .withUsername("collector")
                .withPassword("collector")) {
            postgres.start();

            try (Connection connection = postgres.createConnection("")) {
                createLegacySchema(connection);
                seedLegacyData(connection);

                for (String changeLog : CANONICAL_CHANGELOGS) {
                    applyChangeLog(connection, changeLog);
                }

                assertEquals(0, count(connection,
                        "select count(*) from oauth_tokens where integration_account_id is null"));

                assertThrows(SQLException.class, () -> execute(connection, """
                        insert into oauth_tokens (
                          source,
                          external_user_id,
                          access_token,
                          refresh_token,
                          expires_at,
                          created_at,
                          updated_at
                        )
                        values (
                          'STRAVA',
                          '9999',
                          'orphan-access',
                          'orphan-refresh',
                          now() + interval '1 year',
                          now(),
                          now()
                        )
                        """));
            }
        }
    }

    private void createLegacySchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table athletes (
                      strava_id bigint primary key,
                      created_at timestamptz not null,
                      updated_at timestamptz not null
                    )
                    """);
            statement.execute("""
                    create table activities (
                      strava_id bigint primary key,
                      athlete_id bigint not null references athletes(strava_id),
                      created_at timestamptz not null,
                      updated_at timestamptz not null
                    )
                    """);
            statement.execute("""
                    create table oauth_tokens (
                      id bigserial primary key,
                      source varchar(50) not null,
                      external_user_id varchar(255) not null,
                      access_token text not null,
                      refresh_token text not null,
                      expires_at timestamptz not null,
                      created_at timestamptz not null,
                      updated_at timestamptz not null,
                      unique (source, external_user_id)
                    )
                    """);
        }
    }

    private void seedLegacyData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into athletes (strava_id, created_at, updated_at)
                    values (1001, '2024-01-01T00:00:00Z', '2024-03-01T00:00:00Z')
                    """);
            statement.execute("""
                    insert into activities (strava_id, athlete_id, created_at, updated_at)
                    values (5001, 1001, '2024-03-01T00:00:00Z', '2024-03-02T00:00:00Z')
                    """);
            statement.execute("""
                    insert into oauth_tokens (
                      source,
                      external_user_id,
                      access_token,
                      refresh_token,
                      expires_at,
                      created_at,
                      updated_at
                    )
                    values (
                      'STRAVA',
                      '1001',
                      'access-1001',
                      'refresh-1001',
                      '2025-01-01T00:00:00Z',
                      '2024-01-01T00:00:00Z',
                      '2024-03-01T00:00:00Z'
                    )
                    """);
        }
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

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}


