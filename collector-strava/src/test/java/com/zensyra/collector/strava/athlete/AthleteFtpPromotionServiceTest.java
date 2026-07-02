package com.zensyra.collector.strava.athlete;

import com.zensyra.collector.core.identity.AthleteProfile;
import com.zensyra.collector.core.identity.AthleteProfileRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteFtpPromotionServiceTest {

    @InjectMock
    IntegrationAccountRepository integrationAccountRepository;

    @InjectMock
    AthleteProfileRepository athleteProfileRepository;

    @Inject
    AthleteFtpPromotionService service;

    @Test
    void shouldPromoteFtpToCanonicalProfile() {
        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "12345");
        OAuthToken token = buildToken(account.getId());
        AthleteProfile profile = new AthleteProfile();

        when(integrationAccountRepository.findByIdOptional(account.getId()))
                .thenReturn(Optional.of(account));
        when(athleteProfileRepository.findByIdOptional(athleteId))
                .thenReturn(Optional.of(profile));

        service.promoteFtp(token, 250);

        assertEquals(250, profile.getFtpWatts());
    }

    @Test
    void shouldSkipWhenFtpIsNull() {
        OAuthToken token = buildToken(UUID.randomUUID());

        assertDoesNotThrow(() -> service.promoteFtp(token, null));

        // no lookups at all — absence of power data is expected, not an error
        verify(integrationAccountRepository, never()).findByIdOptional(any());
        verify(athleteProfileRepository, never()).findByIdOptional(any());
    }

    @Test
    void shouldSkipWhenTokenHasNoIntegrationAccount() {
        OAuthToken token = buildToken(null);

        assertDoesNotThrow(() -> service.promoteFtp(token, 250));

        verify(integrationAccountRepository, never()).findByIdOptional(any());
        verify(athleteProfileRepository, never()).findByIdOptional(any());
    }

    @Test
    void shouldSkipWhenIntegrationAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        OAuthToken token = buildToken(accountId);
        when(integrationAccountRepository.findByIdOptional(accountId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.promoteFtp(token, 250));

        verify(athleteProfileRepository, never()).findByIdOptional(any());
    }

    @Test
    void shouldSkipWhenAthleteProfileNotFound() {
        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "12345");
        OAuthToken token = buildToken(account.getId());

        when(integrationAccountRepository.findByIdOptional(account.getId()))
                .thenReturn(Optional.of(account));
        when(athleteProfileRepository.findByIdOptional(athleteId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.promoteFtp(token, 250));
    }

    @Test
    void shouldLeaveFtpWattsNullWhenNoPowerData() {
        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "12345");
        OAuthToken token = buildToken(account.getId());
        AthleteProfile profile = new AthleteProfile();

        service.promoteFtp(token, null);

        assertNull(profile.getFtpWatts());
        verify(integrationAccountRepository, never()).findByIdOptional(any());
    }

    private OAuthToken buildToken(UUID integrationAccountId) {
        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.STRAVA);
        token.setExternalUserId("12345");
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setIntegrationAccountId(integrationAccountId);
        return token;
    }
}
