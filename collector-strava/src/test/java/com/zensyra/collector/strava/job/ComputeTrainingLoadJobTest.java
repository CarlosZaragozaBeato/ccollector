package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.PartialJobFailureException;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.trainingload.TrainingLoadService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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
import static org.mockito.Mockito.*;

@QuarkusTest
class ComputeTrainingLoadJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    TrainingLoadService trainingLoadService;

    @Inject
    ComputeTrainingLoadJob job;

    // --- full success ---

    @Test
    void allAthletesSucceed_jobCompletesNormally() {
        stubToken("12345");
        SyncContext context = buildContext();

        assertDoesNotThrow(() -> job.execute(context));

        LocalDate expectedDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();
        verify(trainingLoadService).computeAndUpsert(12345L, expectedDate);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(trainingLoadService, never()).computeAndUpsert(anyLong(), any());
    }

    // --- partial failure (some athletes fail, some succeed) ---

    @Test
    void partialFailure_remainingAthletesStillRun_throwsPartialException() {
        OAuthToken t1 = makeToken("111");
        OAuthToken t2 = makeToken("222");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(t1, t2));

        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(111L, date);

        // All tokens must be attempted even when one fails.
        assertThrows(PartialJobFailureException.class, () -> job.execute(buildContext()));
        verify(trainingLoadService).computeAndUpsert(222L, date);
    }

    // --- total failure (all athletes fail) ---

    // RED before fix: the job swallowed all exceptions and returned normally,
    // so SyncJobExecutor called markSuccess() — incorrectly reporting SUCCESS.
    // GREEN after fix: the job propagates a RuntimeException, so
    // SyncJobExecutor calls markFailure() — correctly reporting FAILED.
    @Test
    void allAthletesFail_jobThrows() {
        OAuthToken t1 = makeToken("111");
        OAuthToken t2 = makeToken("222");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(t1, t2));

        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(111L, date);
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(222L, date);

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));
    }

    // --- helpers ---

    private void stubToken(String externalUserId) {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(makeToken(externalUserId)));
    }

    private OAuthToken makeToken(String externalUserId) {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId(externalUserId);
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.compute-training-load",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
