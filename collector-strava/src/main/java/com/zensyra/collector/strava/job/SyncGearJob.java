package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.dto.StravaGearDto;
import com.zensyra.collector.strava.athlete.Athlete;
import com.zensyra.collector.strava.athlete.AthleteRepository;
import com.zensyra.collector.strava.gear.GearUpsertService;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SyncGearJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncGearJob.class);

    @Inject
    ActivityRepository activityRepository;

    @Inject
    AthleteRepository athleteRepository;

    @Inject
    GearUpsertService gearUpsertService;

    @Inject
    StravaRateLimiter rateLimiter;

    @Override
    public String jobId() {
        return "strava.sync-gear";
    }

    @Override
    public String cronExpression() {
        return "0 0 7 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        syncGearForUser(token.getExternalUserId());
        return false;
    }

    private void syncGearForUser(String externalUserId) {
        try {
            String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);

            // Obtener athleteId desde BD usando el externalUserId (stravaId del atleta)
            Long athleteStravaId = parseAthleteId(externalUserId);
            Long athleteId = athleteRepository.findByStravaId(athleteStravaId)
                    .map(Athlete::getStravaId)
                    .orElse(athleteStravaId);

            // Obtener gear_ids distintos de las actividades de este atleta
            List<String> gearIds = activityRepository
                    .findDistinctGearIdsByAthleteId(athleteStravaId);

            if (gearIds.isEmpty()) {
                LOG.infof("SyncGearJob: no hay gear_ids en actividades para usuario '%s'", externalUserId);
                return;
            }

            int synced = 0;
            for (String gearId : gearIds) {
                 try {
                    rateLimiter.acquire();
                    StravaGearDto dto = stravaApiClient.getGear("Bearer " + accessToken, gearId);
                    gearUpsertService.upsert(dto, athleteId);
                    synced++;
                } catch (Exception e) {
                    LOG.warnf("No se pudo sincronizar gear '%s': %s", gearId, e.getMessage());
                }
            }

            LOG.infof("SyncGearJob completado — usuario: '%s', gears sincronizados: %d", externalUserId, synced);

        } catch (Exception e) {
            LOG.errorf(e, "Error en SyncGearJob para usuario '%s'", externalUserId);
            throw e;
        }
    }
}
