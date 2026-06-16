package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityDetailUpsertService;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.dto.StravaActivityDetailDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class SyncActivityDetailJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncActivityDetailJob.class);

    private static final int BATCH_SIZE = 50;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityDetailUpsertService activityDetailUpsertService;

    @Inject
    StravaCollectorMetrics metrics;

    @Inject
    StravaRateLimiter rateLimiter;

    @Override
    public String jobId() {
        return "strava.sync-activity-detail";
    }

    @Override
    public String cronExpression() {
        return "0 30 * * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        syncDetailForUser(token.getExternalUserId());
        return false;
    }

    private void syncDetailForUser(String externalUserId) {
        try {
            String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
            Long athleteStravaId = parseAthleteId(externalUserId);

            Instant startOfYear = LocalDate.now(ZoneOffset.UTC)
                    .withDayOfYear(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();

            List<Long> pendingStravaIds = activityRepository
                    .findStravaIdsWithoutCaloriesByAthleteIdSince(
                            athleteStravaId, startOfYear, BATCH_SIZE);

            if (pendingStravaIds.isEmpty()) {
                LOG.infof("SyncActivityDetailJob: no hay actividades pendientes para '%s'", externalUserId);
                return;
            }

            int enriched = 0;
            for (Long stravaId : pendingStravaIds) {
                rateLimiter.acquire();

                Timer.Sample sample = metrics.startActivityDetailTimer();
                try {
                    StravaActivityDetailDto dto = stravaApiClient.getActivityDetail(
                            "Bearer " + accessToken, stravaId
                    );
                    activityDetailUpsertService.upsert(dto);
                    metrics.incrementActivitiesSynced();
                    enriched++;

                } catch (WebApplicationException e) {
                    if (e.getResponse().getStatus() == HTTP_TOO_MANY_REQUESTS) {
                        metrics.incrementRateLimitHits();
                        LOG.warnf("Rate limit de Strava alcanzado tras %d actividades — abortando batch", enriched);
                        break;
                    }
                    metrics.incrementActivitiesFailed();
                    LOG.warnf("No se pudo enriquecer actividad stravaId=%d: %s",
                            stravaId, e.getMessage());
                } catch (Exception e) {
                    metrics.incrementActivitiesFailed();
                    LOG.warnf("No se pudo enriquecer actividad stravaId=%d: %s",
                            stravaId, e.getMessage());
                } finally {
                    metrics.stopActivityDetailTimer(sample);
                }
            }

            LOG.infof("SyncActivityDetailJob completado — usuario: '%s', enriquecidas: %d/%d",
                    externalUserId, enriched, pendingStravaIds.size());

        } catch (Exception e) {
            LOG.errorf(e, "Error en SyncActivityDetailJob para usuario '%s'", externalUserId);
            throw e;
        }
    }
}
