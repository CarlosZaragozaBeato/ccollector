package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaRouteDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.route.RouteUpsertService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
class SyncRoutesJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    RouteUpsertService routeUpsertService;

    @InjectMock
    StravaCollectorMetrics metrics;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @Inject
    SyncRoutesJob job;

    @Test
    void shouldUpsertAllRoutesWhenSinglePageReturned() {
        stubToken("12345");
        List<StravaRouteDto> routes = Collections.nCopies(5, new StravaRouteDto());
        when(stravaApiClient.getRoutes(anyString(), anyLong(), eq(200), eq(1))).thenReturn(routes);

        job.execute(buildContext());

        verify(routeUpsertService, times(5)).upsert(any());
        verify(metrics, never()).incrementRateLimitHits();
    }

    @Test
    void shouldPaginateUntilEmptyPage() {
        stubToken("12345");
        List<StravaRouteDto> fullPage = Collections.nCopies(200, new StravaRouteDto());
        when(stravaApiClient.getRoutes(anyString(), anyLong(), eq(200), eq(1))).thenReturn(fullPage);
        when(stravaApiClient.getRoutes(anyString(), anyLong(), eq(200), eq(2))).thenReturn(List.of());

        job.execute(buildContext());

        verify(routeUpsertService, times(200)).upsert(any());
        verify(stravaApiClient, times(1)).getRoutes(anyString(), anyLong(), anyInt(), eq(1));
        verify(stravaApiClient, times(1)).getRoutes(anyString(), anyLong(), anyInt(), eq(2));
    }

    @Test
    void shouldIncrementMetricAndAbortOn429() {
        stubToken("12345");
        List<StravaRouteDto> fullPage = Collections.nCopies(200, new StravaRouteDto());
        when(stravaApiClient.getRoutes(anyString(), anyLong(), eq(200), eq(1))).thenReturn(fullPage);
        when(stravaApiClient.getRoutes(anyString(), anyLong(), eq(200), eq(2)))
                .thenThrow(new ClientErrorException(429));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(metrics, times(1)).incrementRateLimitHits();
        verify(routeUpsertService, times(200)).upsert(any());
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of());

        job.execute(buildContext());

        verify(routeUpsertService, never()).upsert(any());
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

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-routes",
                IntegrationSource.STRAVA,
                Instant.now(),
                null
        );
    }
}
