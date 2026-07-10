package com.zensyra.collector.strava.health;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaTokenHealthCheckTest {

    // Regression guard: a fresh install with zero tokens must NOT block readiness.
    // Reverting .up() back to .status(total > 0) would make this fail and
    // reintroduce the bootstrap deadlock (seed script waits for ready → ready
    // requires tokens → tokens require seed → deadlock).
    @Test
    void zeroTokens_alwaysUp_doesNotBlockFreshInstallReadiness() {
        OAuthTokenRepository repo = mock(OAuthTokenRepository.class);
        when(repo.findAllBySource(IntegrationSource.STRAVA)).thenReturn(Collections.emptyList());

        HealthCheckResponse response = new StravaTokenHealthCheck(repo).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("strava-tokens", response.getName());
        assertEquals(0L, response.getData().get().get("total"));
        assertEquals(0L, response.getData().get().get("fresh"));
        assertEquals(0L, response.getData().get().get("needsRefresh"));
    }

    @Test
    void mixedTokens_upWithCorrectCounts() {
        OAuthToken fresh = mock(OAuthToken.class);
        when(fresh.isExpired()).thenReturn(false);

        OAuthToken expired = mock(OAuthToken.class);
        when(expired.isExpired()).thenReturn(true);

        OAuthTokenRepository repo = mock(OAuthTokenRepository.class);
        when(repo.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of(fresh, expired));

        HealthCheckResponse response = new StravaTokenHealthCheck(repo).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(2L, response.getData().get().get("total"));
        assertEquals(1L, response.getData().get().get("fresh"));
        assertEquals(1L, response.getData().get().get("needsRefresh"));
    }
}
