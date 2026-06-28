package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.UUID;

@ApplicationScoped
public class OAuthTokenService {

    private static final Logger LOG = Logger.getLogger(OAuthTokenService.class);

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    Instance<TokenRefresher> refreshers;

    @Transactional
    public String getValidToken(UUID integrationAccountId) {
        IntegrationAccount account = integrationAccountRepository.findByIdOptional(integrationAccountId)
                .orElseThrow(() -> new TokenRefreshException(
                        "No integration account found: " + integrationAccountId
                ));
        OAuthToken token = tokenRepository.findByIntegrationAccountId(integrationAccountId)
                .orElseThrow(() -> new TokenRefreshException(
                        "No token found for integration account " + integrationAccountId
                ));

        return refreshIfNeeded(token, account.getSource(), account.getExternalUserId());
    }

    /**
     * Compatibility entry point for legacy tokens that have not yet been linked
     * to an integration account. New callers must use {@link #getValidToken(UUID)}.
     */
    @Deprecated
    @Transactional
    public String getValidToken(IntegrationSource source, String externalUserId) {
        return integrationAccountRepository.findBySourceAndExternalUserId(source, externalUserId)
                .flatMap(account -> tokenRepository.findByIntegrationAccountId(account.getId())
                        .map(token -> refreshIfNeeded(
                                token,
                                account.getSource(),
                                account.getExternalUserId())))
                .orElseGet(() -> refreshLegacyToken(source, externalUserId));
    }

    private String refreshLegacyToken(IntegrationSource source, String externalUserId) {
        OAuthToken token = tokenRepository.findBySourceAndUser(source, externalUserId)
                .orElseThrow(() -> new TokenRefreshException(
                        "No token found for source %s and user %s".formatted(source, externalUserId)
                ));
        return refreshIfNeeded(token, source, externalUserId);
    }

    private String refreshIfNeeded(OAuthToken token, IntegrationSource source, String externalUserId) {
        if (!token.isExpired()){
            return token.getAccessToken();
        }
        TokenRefresher refresher = findRefresher(source);
        TokenRefreshResult result;
        try {
            result = refresher.refresh(token.getRefreshToken());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TokenRefreshException("Token refresh failed for source " + source, e);
        }

        token.setAccessToken(result.accessToken());
        token.setRefreshToken(result.refreshToken());
        token.setExpiresAt(result.expiresAt());

        LOG.infof("Token refreshed for source '%s', user '%s', expires: %s", source, externalUserId, result.expiresAt());

        return token.getAccessToken();
    }

    private TokenRefresher findRefresher(IntegrationSource source){
        for (TokenRefresher refresher: refreshers){
            if(refresher.source() == source){
                return refresher;
            }
        }
        throw new TokenRefreshException("No TokenRefresher found for source: "+ source);
    }

}
