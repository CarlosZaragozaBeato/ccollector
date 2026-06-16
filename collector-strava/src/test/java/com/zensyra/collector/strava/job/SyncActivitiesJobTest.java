package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activity.ActivityUpsertService;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncActivitiesJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    ActivityUpsertService activityUpsertService;

    @InjectMock
    StravaCollectorMetrics metrics;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @Inject
    SyncActivitiesJob job;

    @Test
    void shouldProcess200ActivitiesWhenSecondPageIsEmpty() {
        stubToken("12345");
        when(activityRepository.findMaxStartDateByAthleteId(12345L)).thenReturn(Optional.empty());

        List<StravaActivityDto> fullPage = Collections.nCopies(200, new StravaActivityDto());
        when(stravaApiClient.getActivities(anyString(), anyLong(), anyInt(), eq(1))).thenReturn(fullPage);
        when(stravaApiClient.getActivities(anyString(), anyLong(), anyInt(), eq(2))).thenReturn(List.of());

        job.execute(buildContext(null));

        verify(activityUpsertService, times(200)).upsert(any());
        verify(stravaApiClient, times(1)).getActivities(anyString(), anyLong(), anyInt(), eq(1));
        verify(stravaApiClient, times(1)).getActivities(anyString(), anyLong(), anyInt(), eq(2));
        verify(metrics, never()).incrementRateLimitHits();
    }

    @Test
    void shouldCalculateAfterParameterFromLastActivityWhenLastRunExists() {
        stubToken("12345");
        Instant lastRunAt = Instant.parse("2026-03-20T10:00:00Z");
        Instant lastActivityStart = Instant.parse("2026-03-21T12:34:56Z");
        long expectedAfter = lastActivityStart.minusSeconds(24 * 60 * 60).getEpochSecond();

        when(activityRepository.findMaxStartDateByAthleteId(12345L))
                .thenReturn(Optional.of(lastActivityStart));
        when(stravaApiClient.getActivities(anyString(), anyLong(), anyInt(), eq(1))).thenReturn(List.of());

        job.execute(buildContext(lastRunAt));

        ArgumentCaptor<Long> afterCaptor = ArgumentCaptor.forClass(Long.class);
        verify(stravaApiClient).getActivities(anyString(), afterCaptor.capture(), eq(200), eq(1));
        verify(activityUpsertService, never()).upsert(any());
        assertEquals(expectedAfter, afterCaptor.getValue());
    }

    @Test
    void shouldNotFailAndIncrementMetricOn429InSecondPage() {
        stubToken("12345");
        when(activityRepository.findMaxStartDateByAthleteId(12345L)).thenReturn(Optional.empty());

        List<StravaActivityDto> fullPage = Collections.nCopies(200, new StravaActivityDto());
        when(stravaApiClient.getActivities(anyString(), anyLong(), anyInt(), eq(1))).thenReturn(fullPage);
        when(stravaApiClient.getActivities(anyString(), anyLong(), anyInt(), eq(2)))
                .thenThrow(new ClientErrorException(429));

        assertDoesNotThrow(() -> job.execute(buildContext(null)));

        verify(metrics, times(1)).incrementRateLimitHits();
        verify(activityUpsertService, times(200)).upsert(any());
        verify(stravaApiClient, times(1)).getActivities(anyString(), anyLong(), anyInt(), eq(2));
    }

    private void stubToken(String externalUserId) {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId(externalUserId);
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId)).thenReturn("test-access-token");
    }

    private SyncContext buildContext(Instant lastRunAt) {
        return new SyncContext(
                "strava.sync-activities",
                IntegrationSource.STRAVA,
                Instant.now(),
                lastRunAt
        );
    }
}
