package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.dto.StravaAthleteZonesDto;
import com.zensyra.collector.strava.athletezone.AthleteZoneUpsertService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SyncAthleteZonesJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncAthleteZonesJob.class);

    @Inject
    AthleteZoneUpsertService athleteZoneUpsertService;

    @Override
    public String jobId() {
        return "strava.sync-athlete-zones";
    }

    @Override
    public String cronExpression() {
        return "0 15 6 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        try {
            String accessToken = validAccessToken(token);
            StravaAthleteZonesDto dto = stravaApiClient.getAthleteZones("Bearer " + accessToken);
            Long athleteStravaId = parseAthleteId(externalUserId);

            athleteZoneUpsertService.replaceZones(athleteStravaId, dto);
            int hrZones = dto != null ? dto.getHeartRateZones().size() : 0;
            int powerZones = dto != null ? dto.getPowerZones().size() : 0;
            LOG.infof("SyncAthleteZonesJob completed — user: '%s', hrZones=%d, powerZones=%d",
                    externalUserId, hrZones, powerZones);
        } catch (Exception e) {
            LOG.errorf(e, "Error synchronizing athlete zones for user '%s'", externalUserId);
            throw e;
        }
        return false;
    }
}
