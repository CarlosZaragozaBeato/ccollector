package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.dto.StravaRouteDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import com.zensyra.collector.strava.route.RouteUpsertService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SyncRoutesJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncRoutesJob.class);
    private static final int PER_PAGE = 200;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    @Inject
    RouteUpsertService routeUpsertService;

    @Inject
    StravaCollectorMetrics metrics;

    @Inject
    StravaRateLimiter rateLimiter;

    @Override
    public String jobId() {
        return "strava.sync-routes";
    }

    @Override
    public String cronExpression() {
        return "0 0 4 ? * MON";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = token.getExternalUserId();
        try {
            Long athleteId = parseAthleteId(externalUserId);
            String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
            String bearer = "Bearer " + accessToken;
            int page = 1;
            int totalSynced = 0;

            while (true) {
                rateLimiter.acquire();
                List<StravaRouteDto> routes;
                try {
                    routes = stravaApiClient.getRoutes(bearer, athleteId, PER_PAGE, page);
                } catch (WebApplicationException e) {
                    if (e.getResponse() != null && e.getResponse().getStatus() == HTTP_TOO_MANY_REQUESTS) {
                        metrics.incrementRateLimitHits();
                        LOG.warnf("Strava 429 en página %d — abortando paginación para usuario '%s'",
                                page, externalUserId);
                        return true;
                    }
                    throw e;
                }

                if (routes.isEmpty()) break;

                for (StravaRouteDto dto : routes) {
                    routeUpsertService.upsert(dto);
                    totalSynced++;
                }

                LOG.infof("SyncRoutesJob — usuario: '%s', página %d, %d rutas procesadas",
                        externalUserId, page, routes.size());

                if (routes.size() < PER_PAGE) break;

                page++;
            }

            LOG.infof("SyncRoutesJob completado — usuario: '%s', total sincronizadas: %d",
                    externalUserId, totalSynced);

        } catch (Exception e) {
            LOG.errorf(e, "Error sincronizando rutas para usuario '%s'", externalUserId);
            throw e;
        }
        return false;
    }
}
