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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalStravaBackfillMigrationIT {

    private static final List<String> CANONICAL_CHANGELOGS = List.of(
            "db/changelog/changes/028-create-athlete-profiles.yaml",
            "db/changelog/changes/029-create-integration-accounts.yaml",
            "db/changelog/changes/030-create-training-sessions.yaml",
            "db/changelog/changes/031-create-activity-references.yaml",
            "db/changelog/changes/032-link-oauth-tokens-to-integration-accounts.yaml",
            "db/changelog/changes/033-backfill-strava-canonical-data.yaml"
    );

    @Test
    void shouldBackfillCanonicalDataFromHistoricalStravaRows() throws Exception {
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

                assertEquals(3, count(connection, "select count(*) from athlete_profiles"));
                assertEquals(3, count(connection, "select count(*) from integration_accounts"));
                assertEquals(2, count(connection, "select count(*) from training_sessions"));
                assertEquals(2, count(connection, "select count(*) from activity_references"));
                assertEquals(0, count(connection,
                        "select count(*) from oauth_tokens where integration_account_id is null"));
                assertEquals(1, count(connection, """
                        select count(*)
                        from integration_accounts
                        where source = 'STRAVA'
                          and external_user_id = '1001'
                        """));
                assertEquals(1, count(connection, """
                        select count(*)
                        from integration_accounts
                        where source = 'STRAVA'
                          and external_user_id = '3003'
                        """));
                assertEquals(1, count(connection, """
                        select count(*)
                        from activity_references ar
                        join integration_accounts ia on ia.id = ar.integration_account_id
                        where ia.external_user_id = '1001'
                          and ar.external_activity_id = '5001'
                        """));
                assertEquals(0, count(connection, """
                        select count(*)
                        from (
                          select source, external_user_id
                          from integration_accounts
                          group by source, external_user_id
                          having count(*) > 1
                        ) duplicates
                        """));
                assertEquals(0, count(connection, """
                        select count(*)
                        from (
                          select integration_account_id, external_activity_id
                          from activity_references
                          group by integration_account_id, external_activity_id
                          having count(*) > 1
                        ) duplicates
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
                    values
                      (1001, '2024-01-01T00:00:00Z', '2024-03-01T00:00:00Z'),
                      (2002, '2024-02-01T00:00:00Z', '2024-04-01T00:00:00Z')
                    """);
            statement.execute("""
                    insert into activities (strava_id, athlete_id, created_at, updated_at)
                    values
                      (5001, 1001, '2024-03-01T00:00:00Z', '2024-03-02T00:00:00Z'),
                      (5002, 2002, '2024-04-01T00:00:00Z', '2024-04-02T00:00:00Z')
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
                    values
                      (
                        'STRAVA',
                        '2002',
                        'access-2002',
                        'refresh-2002',
                        '2025-01-01T00:00:00Z',
                        '2024-04-01T00:00:00Z',
                        '2024-04-02T00:00:00Z'
                      ),
                      (
                        'STRAVA',
                        '3003',
                        'access-3003',
                        'refresh-3003',
                        '2025-01-01T00:00:00Z',
                        '2024-05-01T00:00:00Z',
                        '2024-05-02T00:00:00Z'
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

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
