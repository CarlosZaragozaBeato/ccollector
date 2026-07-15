package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthTokenServiceTest {

    @Test
    void shouldResolveTokenUsingIntegrationAccount() {
        OAuthTokenRepository tokens = mock(OAuthTokenRepository.class);
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        OAuthTokenService service = service(tokens, accounts);

        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(),
                IntegrationSource.STRAVA,
                "canonical-athlete"
        );
        OAuthToken token = validToken();
        token.setIntegrationAccountId(account.getId());
        when(accounts.findByIdOptional(account.getId())).thenReturn(Optional.of(account));
        when(tokens.findByIntegrationAccountId(account.getId())).thenReturn(Optional.of(token));

        assertEquals("access-token", service.getValidToken(account.getId()));

        verify(accounts).findByIdOptional(account.getId());
        verify(tokens).findByIntegrationAccountId(account.getId());
    }

    @Test
    void shouldUseLegacyFieldsWhenTokenHasNotBeenLinkedYet() {
        OAuthTokenRepository tokens = mock(OAuthTokenRepository.class);
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        OAuthTokenService service = service(tokens, accounts);

        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(),
                IntegrationSource.STRAVA,
                "legacy-athlete"
        );
        OAuthToken token = validToken();
        when(accounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "legacy-athlete"))
                .thenReturn(Optional.of(account));
        when(tokens.findByIntegrationAccountId(account.getId())).thenReturn(Optional.empty());
        when(tokens.findBySourceAndUser(IntegrationSource.STRAVA, "legacy-athlete"))
                .thenReturn(Optional.of(token));

        assertEquals("access-token", service.getValidToken(IntegrationSource.STRAVA, "legacy-athlete"));

        verify(tokens).findBySourceAndUser(IntegrationSource.STRAVA, "legacy-athlete");
    }

    @Test
    void shouldPersistRotatedRefreshTokenAfterRefresh() throws Exception {
        // Suunto rotates the refresh_token on every refresh (verified live):
        // the entity must end up holding the NEW refresh token, or the next
        // refresh would replay the invalidated one and fail silently.
        OAuthTokenRepository tokens = mock(OAuthTokenRepository.class);
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        OAuthTokenService service = service(tokens, accounts);

        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(),
                IntegrationSource.SUUNTO,
                "suunto-user"
        );
        OAuthToken token = validToken();
        token.setRefreshToken("rotated-out-refresh");
        token.setExpiresAt(Instant.now().minusSeconds(3600)); // force a refresh
        token.setIntegrationAccountId(account.getId());
        when(accounts.findByIdOptional(account.getId())).thenReturn(Optional.of(account));
        when(tokens.findByIntegrationAccountId(account.getId())).thenReturn(Optional.of(token));

        Instant rotatedExpiry = Instant.now().plusSeconds(86400);
        TokenRefresher refresher = mock(TokenRefresher.class);
        when(refresher.source()).thenReturn(IntegrationSource.SUUNTO);
        when(refresher.refresh("rotated-out-refresh"))
                .thenReturn(new TokenRefreshResult("new-access", "rotated-in-refresh", rotatedExpiry));

        @SuppressWarnings("unchecked")
        Instance<TokenRefresher> refreshers = mock(Instance.class);
        when(refreshers.iterator()).thenReturn(List.of(refresher).iterator());
        service.refreshers = refreshers;

        assertEquals("new-access", service.getValidToken(account.getId()));

        // The core assertion: the rotated refresh token was written back to the
        // managed entity — not just access_token/expires_at.
        assertEquals("rotated-in-refresh", token.getRefreshToken());
        assertEquals("new-access", token.getAccessToken());
        assertEquals(rotatedExpiry, token.getExpiresAt());
    }

    private OAuthTokenService service(
            OAuthTokenRepository tokens,
            IntegrationAccountRepository accounts) {
        OAuthTokenService service = new OAuthTokenService();
        service.tokenRepository = tokens;
        service.integrationAccountRepository = accounts;
        return service;
    }

    private OAuthToken validToken() {
        OAuthToken token = new OAuthToken();
        token.setAccessToken("access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
