package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaGearDto;
import com.zensyra.collector.strava.athlete.Athlete;
import com.zensyra.collector.strava.athlete.AthleteRepository;
import com.zensyra.collector.strava.gear.GearUpsertService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncGearJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    AthleteRepository athleteRepository;

    @InjectMock
    GearUpsertService gearUpsertService;

    @Inject
    SyncGearJob job;

    @Test
    void shouldUpsertGearForValidToken() throws Exception {
        stubToken("12345");
        stubAthlete(12345L);
        when(activityRepository.findDistinctGearIdsByAthleteId(12345L))
                .thenReturn(List.of("b123456"));
        StravaGearDto dto = buildGearDto("b123456");
        when(stravaApiClient.getGear(anyString(), eq("b123456"))).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(gearUpsertService).upsert(eq(dto), eq(12345L));
    }

    @Test
    void shouldContinueWhenGearFetchReturns404() throws Exception {
        stubToken("12345");
        stubAthlete(12345L);
        when(activityRepository.findDistinctGearIdsByAthleteId(12345L))
                .thenReturn(List.of("b999999"));
        when(stravaApiClient.getGear(anyString(), eq("b999999")))
                .thenThrow(new NotFoundException("gear not found"));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(gearUpsertService, never()).upsert(any(), anyLong());
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));
    }

    @Test
    void shouldContinueWhenFirstGearFailsAndSecondSucceeds() throws Exception {
        stubToken("12345");
        stubAthlete(12345L);
        when(activityRepository.findDistinctGearIdsByAthleteId(12345L))
                .thenReturn(List.of("b999999", "b123456"));
        when(stravaApiClient.getGear(anyString(), eq("b999999")))
                .thenThrow(new RuntimeException("Strava down"));
        StravaGearDto dto = buildGearDto("b123456");
        when(stravaApiClient.getGear(anyString(), eq("b123456"))).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(gearUpsertService, times(1)).upsert(eq(dto), eq(12345L));
    }

    @Test
    void shouldFallbackToExternalUserIdWhenAthleteIsMissingInRepository() throws Exception {
        stubToken("12345");
        when(athleteRepository.findByStravaId(12345L)).thenReturn(Optional.empty());
        when(activityRepository.findDistinctGearIdsByAthleteId(12345L))
                .thenReturn(List.of("b123456"));
        StravaGearDto dto = buildGearDto("b123456");
        when(stravaApiClient.getGear(anyString(), eq("b123456"))).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(gearUpsertService).upsert(eq(dto), eq(12345L));
    }

    @Test
    void shouldPropagateWhenTokenResolutionFails() throws Exception {
        OAuthToken token = buildToken("12345");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, "12345"))
                .thenThrow(new RuntimeException("token refresh failed"));

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));

        verify(stravaApiClient, never()).getGear(anyString(), anyString());
        verify(gearUpsertService, never()).upsert(any(), anyLong());
    }

    // --- helpers ---

    private void stubToken(String externalUserId) throws Exception {
        OAuthToken token = buildToken(externalUserId);

        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId))
                .thenReturn("test-access-token");
    }

    private OAuthToken buildToken(String externalUserId) {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId(externalUserId);
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private void stubAthlete(Long stravaId) {
        Athlete athlete = new Athlete();
        athlete.setStravaId(stravaId);
        when(athleteRepository.findByStravaId(stravaId)).thenReturn(Optional.of(athlete));
    }

    private StravaGearDto buildGearDto(String id) {
        StravaGearDto dto = new StravaGearDto();
        dto.setId(id);
        dto.setName("Test Bike");
        return dto;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-gear",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
