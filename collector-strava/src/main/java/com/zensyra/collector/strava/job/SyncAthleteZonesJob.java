package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
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
        String externalUserId = token.getExternalUserId();
        try {
            String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
            StravaAthleteZonesDto dto = stravaApiClient.getAthleteZones("Bearer " + accessToken);
            Long athleteStravaId = parseAthleteId(externalUserId);

            athleteZoneUpsertService.replaceZones(athleteStravaId, dto);
            int hrZones = dto != null ? dto.getHeartRateZones().size() : 0;
            int powerZones = dto != null ? dto.getPowerZones().size() : 0;
            LOG.infof("SyncAthleteZonesJob completado — usuario: '%s', hrZones=%d, powerZones=%d",
                    externalUserId, hrZones, powerZones);
        } catch (Exception e) {
            LOG.errorf(e, "Error sincronizando athlete zones para usuario '%s'", externalUserId);
            throw e;
        }
        return false;
    }
}
