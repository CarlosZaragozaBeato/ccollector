package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@QuarkusTest
class ComputeActivityMetricsJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    IntegrationAccountRepository integrationAccountRepository;

    @InjectMock
    ActivityMetricsService activityMetricsService;

    @Inject
    ComputeActivityMetricsJob job;

    @Test
    void shouldComputeForEachAthleteToken() {
        stubToken("42");
        SyncContext context = buildContext();

        assertDoesNotThrow(() -> job.execute(context));

        verify(activityMetricsService).computeAndUpsert(42L);
    }

    @Test
    void shouldUseExternalUserIdFromLinkedIntegrationAccount() {
        IntegrationAccount account = new IntegrationAccount(
                java.util.UUID.randomUUID(),
                IntegrationSource.STRAVA,
                "42"
        );
        OAuthToken token = makeToken("999");
        token.setIntegrationAccountId(account.getId());
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of(token));
        when(integrationAccountRepository.findByIdOptional(account.getId())).thenReturn(Optional.of(account));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityMetricsService).computeAndUpsert(42L);
        verify(activityMetricsService, never()).computeAndUpsert(999L);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityMetricsService, never()).computeAndUpsert(anyLong());
    }

    @Test
    void shouldContinueRemainingAthletesWhenOneFails() {
        OAuthToken t1 = makeToken("111");
        OAuthToken t2 = makeToken("222");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(t1, t2));

        doThrow(new RuntimeException("DB error")).when(activityMetricsService).computeAndUpsert(111L);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(activityMetricsService).computeAndUpsert(222L);
    }

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
                "strava.compute-activity-metrics",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
