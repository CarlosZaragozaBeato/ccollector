package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.stream.ActivityStreamSyncResult;
import com.zensyra.collector.strava.stream.ActivityStreamSyncService;
import com.zensyra.collector.strava.stream.StreamSyncStatus;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncActivityStreamsJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    ActivityStreamSyncService activityStreamSyncService;

    @InjectMock
    StravaCollectorMetrics metrics;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @Inject
    SyncActivityStreamsJob job;

    @Test
    void shouldProcessSuccessfulBatch() throws Exception {
        stubMetrics();
        stubToken("12345");
        Activity activity = buildActivity(1L, 1001L);

        when(activityRepository.findPendingStreamActivitiesByAthleteId(eq(12345L), anyInt(), anyInt()))
                .thenReturn(List.of(activity));
        when(activityStreamSyncService.replaceStreams(eq(1001L), any()))
                .thenReturn(new ActivityStreamSyncResult(StreamSyncStatus.SYNCED, 2));
        when(stravaApiClient.getActivityStreams(anyString(), eq(1001L), anyString(), anyBoolean()))
                .thenReturn(Map.of());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityStreamSyncService).markRequested(eq(1001L), any());
        verify(activityStreamSyncService).replaceStreams(eq(1001L), any());
        verify(stravaApiClient, times(1)).getActivityStreams(
                anyString(), eq(1001L), eq("time,distance,latlng,altitude,heartrate,watts,cadence"), eq(true));
    }

    @Test
    void shouldMarkFailureAndContinueOnSingleActivityError() throws Exception {
        stubMetrics();
        stubToken("12345");
        Activity activity = buildActivity(1L, 1001L);

        when(activityRepository.findPendingStreamActivitiesByAthleteId(eq(12345L), anyInt(), anyInt()))
                .thenReturn(List.of(activity));
        when(stravaApiClient.getActivityStreams(anyString(), eq(1001L), anyString(), eq(true)))
                .thenThrow(new InternalServerErrorException("boom"));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityStreamSyncService).markFailure(eq(1001L), anyString());
    }

    @Test
    void shouldAbortBatchOn429() throws Exception {
        stubMetrics();
        stubToken("12345");
        Activity first = buildActivity(1L, 1001L);
        Activity second = buildActivity(2L, 1002L);

        when(activityRepository.findPendingStreamActivitiesByAthleteId(eq(12345L), anyInt(), anyInt()))
                .thenReturn(List.of(first, second));
        when(stravaApiClient.getActivityStreams(anyString(), eq(1001L), anyString(), eq(true)))
                .thenThrow(new ClientErrorException(429));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(stravaApiClient, never()).getActivityStreams(anyString(), eq(1002L), anyString(), eq(true));
    }

    private void stubMetrics() {
        when(metrics.startActivityStreamSyncTimer()).thenReturn(Timer.start());
    }

    private void stubToken(String externalUserId) throws Exception {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId(externalUserId);
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId))
                .thenReturn("test-access-token");
    }

    private Activity buildActivity(Long id, Long stravaId) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setStravaId(stravaId);
        activity.setStreamsSyncAttempts(0);
        return activity;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-activity-streams",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
