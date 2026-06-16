package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.dto.StravaAthleteDto;
import com.zensyra.collector.strava.athlete.AthleteUpsertService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SyncAthleteJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncAthleteJob.class);

    @Inject
    AthleteUpsertService athleteUpsertService;

    @Override
    public String jobId() {
        return "strava.sync-athlete";
    }

    @Override
    public String cronExpression() {
        return "0 0 6 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = token.getExternalUserId();
        try {
            String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
            StravaAthleteDto dto = stravaApiClient.getAthlete("Bearer " + accessToken);
            athleteUpsertService.upsert(dto);
        } catch (Exception e) {
            LOG.errorf(e, "Error sincronizando atleta para usuario '%s'", externalUserId);
            throw e;
        }
        return false;
    }
}
