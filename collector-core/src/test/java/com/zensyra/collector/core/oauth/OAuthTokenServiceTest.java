package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
