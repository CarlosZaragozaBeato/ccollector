package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.strava.api.StravaApiClient;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

public abstract class AbstractStravaJob implements SyncJob {

    private static final Logger LOG = Logger.getLogger(AbstractStravaJob.class);

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    OAuthTokenService tokenService;

    @Inject
    @RestClient
    StravaApiClient stravaApiClient;

    @Override
    public IntegrationSource source() {
        return IntegrationSource.STRAVA;
    }

    @Override
    public final void execute(SyncContext context) {
        List<OAuthToken> tokens = tokenRepository.findAllBySource(IntegrationSource.STRAVA);
        if (tokens.isEmpty()) {
            LOG.infof("%s: no hay tokens de Strava — omitiendo ejecución", getClass().getSimpleName());
            return;
        }
        for (OAuthToken token : tokens) {
            if (executeForToken(token, context)) {
                break;
            }
        }
    }

    /**
     * Execute job logic for a single authenticated user.
     *
     * @return true to abort processing of remaining tokens (e.g., on rate-limit hit), false to continue
     */
    protected abstract boolean executeForToken(OAuthToken token, SyncContext context);

    protected Long parseAthleteId(String externalUserId) {
        try {
            return Long.parseLong(externalUserId);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid externalUserId (not a number): " + externalUserId, e);
        }
    }
}
