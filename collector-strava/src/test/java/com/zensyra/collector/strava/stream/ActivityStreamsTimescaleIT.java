package com.zensyra.collector.strava.stream;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import com.zensyra.collector.strava.athlete.Athlete;
import com.zensyra.collector.strava.athlete.AthleteRepository;
import com.zensyra.collector.strava.job.SyncActivityStreamsJob;
import com.zensyra.collector.strava.support.TimescaleDbTestResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = TimescaleDbTestResource.class, restrictToAnnotatedClass = true)
class ActivityStreamsTimescaleIT {

    @Inject
    DataSource dataSource;

    @Inject
    AthleteRepository athleteRepository;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityStreamRepository activityStreamRepository;

    @Inject
    ActivityStreamSyncService activityStreamSyncService;

    @Inject
    SyncActivityStreamsJob syncActivityStreamsJob;

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @Test
    void liquibaseShouldCreateHypertable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet hypertableRs = statement.executeQuery(
                     "select count(*) from timescaledb_information.hypertables where hypertable_name = 'activity_streams'")) {
            assertTrue(hypertableRs.next());
            assertEquals(1, hypertableRs.getInt(1));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet compressionRs = statement.executeQuery(
                     "select compression_enabled from timescaledb_information.hypertables where hypertable_name = 'activity_streams'")) {
            assertTrue(compressionRs.next());
            assertTrue(compressionRs.getBoolean("compression_enabled"));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet compressionPolicyRs = statement.executeQuery(
                     "select count(*) from timescaledb_information.jobs " +
                             "where hypertable_name = 'activity_streams' and proc_name = 'policy_compression'")) {
            assertTrue(compressionPolicyRs.next());
            assertEquals(1, compressionPolicyRs.getInt(1));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet caggRs = statement.executeQuery(
                     "select count(*) from timescaledb_information.continuous_aggregates " +
                             "where view_name = 'cagg_activity_streams_hr_power_5m'")) {
            assertTrue(caggRs.next());
            assertEquals(1, caggRs.getInt(1));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet caggPolicyRs = statement.executeQuery(
                     "select count(*) " +
                             "from timescaledb_information.jobs j " +
                             "join timescaledb_information.continuous_aggregates c " +
                             "  on c.materialization_hypertable_schema = j.hypertable_schema " +
                             " and c.materialization_hypertable_name = j.hypertable_name " +
                             "where c.view_name = 'cagg_activity_streams_hr_power_5m' " +
                             "  and j.proc_name = 'policy_refresh_continuous_aggregate'")) {
            assertTrue(caggPolicyRs.next());
            assertEquals(1, caggPolicyRs.getInt(1));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet columnsRs = statement.executeQuery(
                     "select count(*) from information_schema.columns " +
                             "where table_name = 'activities' and column_name in " +
                             "('streams_synced_at','streams_sync_status','streams_sync_attempts','streams_last_error','streams_last_requested_at')")) {
            assertTrue(columnsRs.next());
            assertEquals(5, columnsRs.getInt(1));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet pkColumnsRs = statement.executeQuery("""
                     select kcu.column_name
                     from information_schema.table_constraints tc
                     join information_schema.key_column_usage kcu
                       on tc.constraint_name = kcu.constraint_name
                      and tc.table_schema = kcu.table_schema
                     where tc.table_schema = 'public'
                       and tc.table_name = 'activity_streams'
                       and tc.constraint_type = 'PRIMARY KEY'
                     order by kcu.ordinal_position
                     """)) {
            List<String> pkColumns = new ArrayList<>();
            while (pkColumnsRs.next()) {
                pkColumns.add(pkColumnsRs.getString("column_name"));
            }
            assertEquals(List.of("activity_id", "time", "elapsed_seconds"), pkColumns);
        }
    }

    @Test
    @TestTransaction
    void persistenceShouldBeIdempotent() {
        Activity activity = seedActivity(3001L, 2001L);
        Map<String, StravaActivityStreamDto> streams = buildStreamsByType();

        activityStreamSyncService.replaceStreams(3001L, streams);
        activityStreamSyncService.replaceStreams(3001L, streams);

        assertEquals(2, activityStreamRepository.countByActivityId(activity.getId()));
        Activity updated = activityRepository.findByStravaId(3001L).orElseThrow();
        assertEquals(StreamSyncStatus.SYNCED, updated.getStreamsSyncStatus());
    }

    @Test
    @TestTransaction
    void jobShouldSyncStreamsEndToEndWithMockedStrava() throws Exception {
        Activity activity = seedActivity(3002L, 2002L);
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId("2002");
        token.setAccessToken("access");
        token.setRefreshToken("refresh");
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, "2002")).thenReturn("access");
        when(stravaApiClient.getActivityStreams(anyString(), eq(3002L), anyString(), anyBoolean()))
                .thenReturn(buildStreamsByType());

        assertDoesNotThrow(() -> syncActivityStreamsJob.execute(new SyncContext(
                "strava.sync-activity-streams",
                IntegrationSource.STRAVA,
                Instant.now(),
                null
        )));

        assertEquals(2, activityStreamRepository.countByActivityId(activity.getId()));
        Activity updated = activityRepository.findByStravaId(3002L).orElseThrow();
        assertEquals(StreamSyncStatus.SYNCED, updated.getStreamsSyncStatus());
        assertTrue(updated.getStreamsLastRequestedAt() != null);
    }

    private Activity seedActivity(Long activityStravaId, Long athleteStravaId) {
        Athlete athlete = athleteRepository.findByStravaId(athleteStravaId).orElseGet(() -> {
            Athlete created = new Athlete();
            created.setStravaId(athleteStravaId);
            created.setUsername("athlete-" + athleteStravaId);
            athleteRepository.persist(created);
            return created;
        });

        Activity activity = new Activity();
        activity.setStravaId(activityStravaId);
        activity.setAthleteId(athlete.getStravaId());
        activity.setName("Test Activity");
        activity.setType("Run");
        activity.setSportType("Run");
        activity.setDistance(java.math.BigDecimal.valueOf(5000));
        activity.setMovingTime(1200);
        activity.setElapsedTime(1200);
        activity.setStartDate(Instant.parse("2026-03-20T10:00:00Z"));
        activityRepository.persist(activity);
        return activity;
    }

    private Map<String, StravaActivityStreamDto> buildStreamsByType() {
        return Map.of(
                "time", stream(JsonNodeFactory.instance.arrayNode().add(0).add(5)),
                "distance", stream(JsonNodeFactory.instance.arrayNode().add(0.0).add(25.0)),
                "latlng", stream(JsonNodeFactory.instance.arrayNode()
                        .add(JsonNodeFactory.instance.arrayNode().add(41.0).add(2.0))
                        .add(JsonNodeFactory.instance.arrayNode().add(41.1).add(2.1))),
                "altitude", stream(JsonNodeFactory.instance.arrayNode().add(8.0).add(10.0)),
                "heartrate", stream(JsonNodeFactory.instance.arrayNode().add(140).add(145)),
                "watts", stream(JsonNodeFactory.instance.arrayNode().add(210).add(215)),
                "cadence", stream(JsonNodeFactory.instance.arrayNode().add(84).add(86))
        );
    }

    private StravaActivityStreamDto stream(com.fasterxml.jackson.databind.JsonNode data) {
        StravaActivityStreamDto dto = new StravaActivityStreamDto();
        dto.setData(data);
        return dto;
    }
}
