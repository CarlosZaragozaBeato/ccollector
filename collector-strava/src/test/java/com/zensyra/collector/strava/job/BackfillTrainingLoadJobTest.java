package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsService;
import com.zensyra.collector.strava.trainingload.TrainingLoadService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class BackfillTrainingLoadJobTest {

    private static final UUID ATHLETE_UUID = UUID.fromString("00000000-0000-0000-0000-000000012345");

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    IntegrationAccountRepository integrationAccountRepository;

    @InjectMock
    ActivityMetricsService activityMetricsService;

    @InjectMock
    TrainingLoadService trainingLoadService;

    @Inject
    BackfillTrainingLoadJob job;

    @Test
    void shouldRunStageAThenStageBForEachAthlete() {
        stubToken("12345");
        stubAccount("12345", ATHLETE_UUID);
        when(activityMetricsService.backfillIntensityFactors(12345L)).thenReturn(3);
        when(trainingLoadService.backfill(ATHLETE_UUID)).thenReturn(7);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        // Stage A (IF from stored NP) must commit before stage B (load recompute) reads it.
        InOrder order = inOrder(activityMetricsService, trainingLoadService);
        order.verify(activityMetricsService).backfillIntensityFactors(12345L);
        order.verify(trainingLoadService).backfill(ATHLETE_UUID);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityMetricsService, never()).backfillIntensityFactors(any(Long.class));
        verify(trainingLoadService, never()).backfill(any(UUID.class));
    }

    // --- helpers ---

    private void stubToken(String externalUserId) {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId(externalUserId);
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(java.util.List.of(token));
    }

    private void stubAccount(String externalUserId, UUID athleteId) {
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, externalUserId);
        when(integrationAccountRepository.findBySourceAndExternalUserId(IntegrationSource.STRAVA, externalUserId))
                .thenReturn(Optional.of(account));
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.backfill-training-load",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
