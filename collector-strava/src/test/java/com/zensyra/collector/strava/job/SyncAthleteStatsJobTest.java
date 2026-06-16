package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaAthleteStatsDto;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncAthleteStatsJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @InjectMock
    AthleteStatsSnapshotService athleteStatsSnapshotService;

    @Inject
    SyncAthleteStatsJob job;

    @Test
    void shouldUpsertDailySnapshotForValidToken() {
        stubToken("12345");
        SyncContext context = buildContext();
        StravaAthleteStatsDto dto = new StravaAthleteStatsDto();
        when(stravaApiClient.getAthleteStats(anyString(), anyLong())).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(context));

        LocalDate snapshotDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();
        verify(athleteStatsSnapshotService).upsertDailySnapshot(eq(12345L), eq(snapshotDate), eq(dto));
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(stravaApiClient, never()).getAthleteStats(anyString(), anyLong());
        verify(athleteStatsSnapshotService, never()).upsertDailySnapshot(anyLong(), any(), any());
    }

    @Test
    void shouldPropagateWhenApiFails() {
        stubToken("12345");
        when(stravaApiClient.getAthleteStats(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Strava down"));

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));
        verify(athleteStatsSnapshotService, never()).upsertDailySnapshot(anyLong(), any(), any());
    }

    private void stubToken(String externalUserId) {
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

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-athlete-stats",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
