package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.StravaApiClient;
import com.zensyra.collector.strava.api.dto.StravaAthleteZonesDto;
import com.zensyra.collector.strava.athletezone.AthleteZoneUpsertService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncAthleteZonesJobTest {

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    @RestClient
    StravaApiClient stravaApiClient;

    @InjectMock
    AthleteZoneUpsertService athleteZoneUpsertService;

    @Inject
    SyncAthleteZonesJob job;

    @Test
    void shouldReplaceZonesForValidToken() {
        stubToken("12345");
        StravaAthleteZonesDto dto = buildZonesDto();
        when(stravaApiClient.getAthleteZones(anyString())).thenReturn(dto);

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(athleteZoneUpsertService).replaceZones(eq(12345L), eq(dto));
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.STRAVA))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(buildContext()));

        verify(stravaApiClient, never()).getAthleteZones(anyString());
        verify(athleteZoneUpsertService, never()).replaceZones(any(), any());
    }

    @Test
    void shouldPropagateWhenApiFails() {
        stubToken("12345");
        when(stravaApiClient.getAthleteZones(anyString()))
                .thenThrow(new RuntimeException("Strava down"));

        assertThrows(RuntimeException.class, () -> job.execute(buildContext()));
        verify(athleteZoneUpsertService, never()).replaceZones(any(), any());
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
        when(tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId))
                .thenReturn("test-access-token");
    }

    private StravaAthleteZonesDto buildZonesDto() {
        StravaAthleteZonesDto dto = new StravaAthleteZonesDto();

        StravaAthleteZonesDto.Zone hrZone = new StravaAthleteZonesDto.Zone();
        hrZone.setMin(120);
        hrZone.setMax(150);
        StravaAthleteZonesDto.ZoneSet heartRate = new StravaAthleteZonesDto.ZoneSet();
        heartRate.setZones(List.of(hrZone));
        dto.setHeartRate(heartRate);

        StravaAthleteZonesDto.Zone powerZone = new StravaAthleteZonesDto.Zone();
        powerZone.setMin(150);
        powerZone.setMax(200);
        StravaAthleteZonesDto.ZoneSet power = new StravaAthleteZonesDto.ZoneSet();
        power.setZones(List.of(powerZone));
        dto.setPower(power);

        return dto;
    }

    private SyncContext buildContext() {
        return new SyncContext(
                "strava.sync-athlete-zones",
                IntegrationSource.STRAVA,
                Instant.now(),
                null);
    }
}
