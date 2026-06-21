package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaAthleteDto;
import com.zensyra.collector.strava.athlete.AthleteUpsertService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncAthleteJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    IntegrationAccountRepository integrationAccountRepository;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @InjectMock
    AthleteUpsertService athleteUpsertService;

    @Inject
    SyncAthleteJob job;

    @Test
    void shouldUpsertAthleteForValidToken() throws Exception {
        stubToken("12345");
        StravaAthleteDto dto = buildAthleteDto(12345L);
        when(stravaApiClient.getAthlete(anyString())).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(athleteUpsertService).upsert(dto);
    }

    @Test
    void shouldResolveCanonicalAccountAndRefreshByAccountId() throws Exception {
        IntegrationAccount account = new IntegrationAccount(
                java.util.UUID.randomUUID(),
                IntegrationSource.STRAVA,
                "canonical-athlete"
        );
        OAuthToken token = buildToken("legacy-athlete");
        token.setIntegrationAccountId(account.getId());

        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA)).thenReturn(List.of(token));
        when(integrationAccountRepository.findByIdOptional(account.getId())).thenReturn(Optional.of(account));
        when(tokenService.getValidToken(account.getId())).thenReturn("canonical-access-token");
        StravaAthleteDto dto = buildAthleteDto(12345L);
        when(stravaApiClient.getAthlete("Bearer canonical-access-token")).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(tokenService).getValidToken(account.getId());
        verify(athleteUpsertService).upsert(dto);
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));
    }

    @Test
    void shouldUpsertForEachTokenWhenMultipleTokensExist() throws Exception {
        OAuthToken token1 = buildToken("12345");
        OAuthToken token2 = buildToken("67890");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(token1, token2));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, "12345"))
                .thenReturn("access-1");
        when(tokenService.getValidToken(IntegrationSource.STRAVA, "67890"))
                .thenReturn("access-2");
        when(stravaApiClient.getAthlete("Bearer access-1"))
                .thenReturn(buildAthleteDto(12345L));
        when(stravaApiClient.getAthlete("Bearer access-2"))
                .thenReturn(buildAthleteDto(67890L));

        assertDoesNotThrow(() -> job.execute(buildContext()));

        ArgumentCaptor<StravaAthleteDto> captor = ArgumentCaptor.forClass(StravaAthleteDto.class);
        verify(athleteUpsertService, times(2)).upsert(captor.capture());
        List<Long> ids = new ArrayList<>();
        for (StravaAthleteDto dto : captor.getAllValues()) {
            ids.add(dto.getId());
        }
        assertEquals(List.of(12345L, 67890L), ids);
    }

    @Test
    void shouldPropagateWhenGetAthleteFails() throws Exception {
        stubToken("12345");
        when(stravaApiClient.getAthlete(anyString()))
                .thenThrow(new RuntimeException("Strava down"));

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));

        verify(athleteUpsertService, never()).upsert(any());
    }

    @Test
    void shouldPropagateWhenTokenResolutionFails() throws Exception {
        OAuthToken token = buildToken("12345");
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(List.of(token));
        when(tokenService.getValidToken(IntegrationSource.STRAVA, "12345"))
                .thenThrow(new RuntimeException("token refresh failed"));

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));

        verify(stravaApiClient, never()).getAthlete(anyString());
        verify(athleteUpsertService, never()).upsert(any());
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

    private StravaAthleteDto buildAthleteDto(Long id) {
        StravaAthleteDto dto = new StravaAthleteDto();
        dto.setId(id);
        dto.setUsername("athlete-" + id);
        return dto;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-athlete",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
