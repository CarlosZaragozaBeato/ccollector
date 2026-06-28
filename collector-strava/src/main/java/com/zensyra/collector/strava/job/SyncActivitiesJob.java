package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activity.ActivityUpsertService;
import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class SyncActivitiesJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncActivitiesJob.class);
    private static final int PER_PAGE = 200;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityUpsertService activityUpsertService;

    @Inject
    StravaCollectorMetrics metrics;

    @Inject
    StravaRateLimiter rateLimiter;

    @Override
    public String jobId() {
        return "strava.sync-activities";
    }

    @Override
    public String cronExpression() {
        return "0 0 * * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        Long athleteStravaId = parseAthleteId(externalUserId);
        long afterEpoch = resolveAfterEpoch(athleteStravaId);
        boolean isFirstRun = context.lastRunAt() == null
                && activityRepository.findMaxStartDateByAthleteId(athleteStravaId).isEmpty();
        syncActivitiesForUser(token, externalUserId, afterEpoch, isFirstRun);
        return false;
    }

    private long resolveAfterEpoch(Long athleteStravaId) {
        return activityRepository
                .findMaxStartDateByAthleteId(athleteStravaId)
                .map(lastActivity -> lastActivity.minus(24, ChronoUnit.HOURS).getEpochSecond())
                .orElseGet(() -> Instant.now().minus(30, ChronoUnit.DAYS).getEpochSecond());
    }

    private void syncActivitiesForUser(
            OAuthToken token,
            String externalUserId,
            long afterEpoch,
            boolean isFirstRun) {
        try {
            String accessToken = validAccessToken(token);
            int totalSynced = 0;

            if (isFirstRun) {
                int page = 1;
                while (true) {
                    rateLimiter.acquire();
                    List<StravaActivityDto> dtos;
                    try {
                        dtos = stravaApiClient.getActivities(
                                "Bearer " + accessToken, afterEpoch, PER_PAGE, page
                        );
                    } catch (WebApplicationException e) {
                        if (e.getResponse() != null && e.getResponse().getStatus() == HTTP_TOO_MANY_REQUESTS) {
                            metrics.incrementRateLimitHits();
                            LOG.warnf("Strava 429 on page %d — aborting pagination for user '%s'",
                                    page, externalUserId);
                            break;
                        }
                        throw e;
                    }

                    if (dtos.isEmpty()) break;

                    for (StravaActivityDto dto : dtos) {
                        activityUpsertService.upsert(dto);
                        totalSynced++;
                    }

                    LOG.infof("SyncActivitiesJob — user: '%s', page %d, %d activities processed",
                            externalUserId, page, dtos.size());

                    if (dtos.size() < PER_PAGE) break;

                    page++;
                }
            } else {
                rateLimiter.acquire();
                List<StravaActivityDto> dtos = stravaApiClient.getActivities(
                        "Bearer " + accessToken, afterEpoch, PER_PAGE, 1
                );
                for (StravaActivityDto dto : dtos) {
                    activityUpsertService.upsert(dto);
                    totalSynced++;
                }
            }

            LOG.infof("SyncActivitiesJob completed — user: '%s', total synchronized: %d",
                    externalUserId, totalSynced);

        } catch (Exception e) {
            LOG.errorf(e, "Error synchronizing activities for user '%s'", externalUserId);
            throw e;
        }
    }
}
