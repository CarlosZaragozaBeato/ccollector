package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaActivityDetailDto;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncActivityDetailJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @Inject
    SyncActivityDetailJob job;

    @Test
    void shouldContinueOnSingleActivityError() throws Exception {
        stubToken("12345");

        when(activityRepository.findStravaIdsWithoutCaloriesByAthleteIdSince(
                eq(12345L), any(), anyInt()))
                .thenReturn(List.of(1L, 2L));

        when(stravaApiClient.getActivityDetail(anyString(), eq(1L)))
                .thenThrow(new InternalServerErrorException("Strava down"));
        when(stravaApiClient.getActivityDetail(anyString(), eq(2L)))
                .thenReturn(buildDetailDto(2L));

        assertDoesNotThrow(() -> job.execute(buildContext()));
    }

    @Test
    void shouldAbortBatchOn429() throws Exception {
        stubToken("12345");

        when(activityRepository.findStravaIdsWithoutCaloriesByAthleteIdSince(
                eq(12345L), any(), anyInt()))
                .thenReturn(List.of(1L, 2L));

        when(stravaApiClient.getActivityDetail(anyString(), any()))
                .thenThrow(new ClientErrorException(429));

        assertDoesNotThrow(() -> job.execute(buildContext()));
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));
    }

    // --- helpers ---

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

    private StravaActivityDetailDto buildDetailDto(Long stravaId) {
        var dto = new StravaActivityDetailDto();
        dto.setId(stravaId);
        dto.setCalories(350);
        dto.setDescription("Test activity");
        return dto;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-activity-detail",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
