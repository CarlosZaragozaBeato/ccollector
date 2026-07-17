package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class ComputeTrainingLoadJobTest {

    private static final UUID UUID_12345 = UUID.fromString("00000000-0000-0000-0000-000000012345");
    private static final UUID UUID_111   = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID UUID_222   = UUID.fromString("00000000-0000-0000-0000-000000000222");

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    IntegrationAccountRepository integrationAccountRepository;

    @InjectMock
    TrainingLoadService trainingLoadService;

    @Inject
    ComputeTrainingLoadJob job;

    // --- full success ---

    @Test
    void allAthletesSucceed_jobCompletesNormally() {
        stubToken("12345");
        stubAccount("12345", UUID_12345);
        SyncContext context = buildContext();

        assertDoesNotThrow(() -> job.execute(context));

        LocalDate expectedDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();
        verify(trainingLoadService).computeAndUpsert(UUID_12345, expectedDate);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(trainingLoadService, never()).computeAndUpsert(any(UUID.class), any());
    }

    // --- partial failure (some athletes fail, some succeed) ---

    @Test
    void partialFailure_remainingAthletesStillRun_throwsPartialException() {
        OAuthToken t1 = makeToken("111");
        OAuthToken t2 = makeToken("222");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(t1, t2));
        stubAccount("111", UUID_111);
        stubAccount("222", UUID_222);

        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(UUID_111, date);

        // All tokens must be attempted even when one fails.
        assertThrows(PartialJobFailureException.class, () -> job.execute(buildContext()));
        verify(trainingLoadService).computeAndUpsert(UUID_222, date);
    }

    // --- total failure (all athletes fail) ---

    @Test
    void allAthletesFail_jobThrows() {
        OAuthToken t1 = makeToken("111");
        OAuthToken t2 = makeToken("222");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(t1, t2));
        stubAccount("111", UUID_111);
        stubAccount("222", UUID_222);

        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(UUID_111, date);
        doThrow(new RuntimeException("DB error")).when(trainingLoadService).computeAndUpsert(UUID_222, date);

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));
    }

    // --- helpers ---

    private void stubToken(String externalUserId) {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(makeToken(externalUserId)));
    }

    private void stubAccount(String externalUserId, UUID athleteId) {
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, externalUserId);
        when(integrationAccountRepository.findBySourceAndExternalUserId(IntegrationSource.STRAVA, externalUserId))
                .thenReturn(Optional.of(account));
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
