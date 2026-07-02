package com.zensyra.collector.strava.job;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class BackfillTrainingLoadJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    ActivityMetricsService activityMetricsService;

    @InjectMock
    TrainingLoadService trainingLoadService;

    @Inject
    BackfillTrainingLoadJob job;

    @Test
    void shouldRunStageAThenStageBForEachAthlete() {
        stubToken("12345");
        when(activityMetricsService.backfillIntensityFactors(12345L)).thenReturn(3);
        when(trainingLoadService.backfill(12345L)).thenReturn(7);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        // stage A (IF from stored NP) must commit before stage B (load recompute) reads it
        InOrder order = inOrder(activityMetricsService, trainingLoadService);
        order.verify(activityMetricsService).backfillIntensityFactors(12345L);
        order.verify(trainingLoadService).backfill(12345L);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityMetricsService, never()).backfillIntensityFactors(anyLong());
        verify(trainingLoadService, never()).backfill(anyLong());
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
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.backfill-training-load",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
