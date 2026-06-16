package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityUpsertService;
import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class InitialHistoricalSyncJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(InitialHistoricalSyncJob.class);
    private static final int PER_PAGE = 200;
    private static final long AFTER_EPOCH = 0L;

    @Inject
    ActivityUpsertService activityUpsertService;

    @Inject
    StravaRateLimiter rateLimiter;

    @Override
    public String jobId() {
        return "strava.initial-historical-sync";
    }

    @Override
    public String cronExpression() {
        return "0 0 0 1 1 ? 2099";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        if (context.lastRunAt() != null) {
            LOG.info("InitialHistoricalSyncJob: carga histórica ya ejecutada — omitiendo");
            return true;
        }
        syncHistoryForUser(token.getExternalUserId());
        return false;
    }

    private void syncHistoryForUser(String externalUserId) {
        LOG.infof("InitialHistoricalSyncJob: iniciando carga histórica para usuario '%s'", externalUserId);

        String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
        int page = 1;
        int totalSynced = 0;

        while (true) {
            rateLimiter.acquire();
            List<StravaActivityDto> dtos = stravaApiClient.getActivities(
                    "Bearer " + accessToken, AFTER_EPOCH, PER_PAGE, page
            );

            if (dtos.isEmpty()) {
                LOG.infof("InitialHistoricalSyncJob: página %d vacía — carga completa", page);
                break;
            }

            for (StravaActivityDto dto : dtos) {
                activityUpsertService.upsert(dto);
                totalSynced++;
            }

            LOG.infof("InitialHistoricalSyncJob — usuario: '%s', página %d, %d actividades procesadas (total: %d)",
                    externalUserId, page, dtos.size(), totalSynced);

            if (dtos.size() < PER_PAGE) break;

            page++;
        }

        LOG.infof("InitialHistoricalSyncJob completado — usuario: '%s', total histórico: %d actividades",
                externalUserId, totalSynced);
    }
}
