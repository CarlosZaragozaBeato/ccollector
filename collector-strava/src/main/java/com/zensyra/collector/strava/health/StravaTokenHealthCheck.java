package com.zensyra.collector.strava.health;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;

/**
 * Readiness check that verifies at least one Strava OAuth token
 * exists.  Expired access tokens are still usable because
 * {@code OAuthTokenService} refreshes them on demand using the
 * refresh token, which is always present (NOT NULL in schema).
 * The system can only not sync when there are no tokens at all.
 */
@Readiness
@ApplicationScoped
public final class StravaTokenHealthCheck implements HealthCheck {

    /** Name reported in the health response. */
    private static final String CHECK_NAME = "strava-tokens";

    /** Repository used to load OAuth tokens. */
    private final OAuthTokenRepository tokenRepository;

    /**
     * CDI constructor injection.
     *
     * @param repo the OAuth token repository
     */
    @Inject
    public StravaTokenHealthCheck(final OAuthTokenRepository repo) {
        this.tokenRepository = repo;
    }

    /**
     * Counts total Strava tokens and reports how many have a
     * fresh access token vs. how many need a refresh.
     * Status is UP as long as at least one token exists,
     * because every token carries a valid refresh token.
     *
     * @return UP when at least one token exists, DOWN otherwise
     */
    @Override
    @Transactional
    public HealthCheckResponse call() {
        List<OAuthToken> tokens =
            tokenRepository.findAllBySource(IntegrationSource.STRAVA);

        long total = tokens.size();
        long fresh = tokens.stream()
            .filter(t -> !t.isExpired())
            .count();
        long needsRefresh = total - fresh;

        return HealthCheckResponse.named(CHECK_NAME)
            .withData("total", total)
            .withData("fresh", fresh)
            .withData("needsRefresh", needsRefresh)
            .status(total > 0)
            .build();
    }
}
