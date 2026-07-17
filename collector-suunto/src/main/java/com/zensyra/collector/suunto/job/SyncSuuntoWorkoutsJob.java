package com.zensyra.collector.suunto.job;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutUpsertService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Fetches every athlete's Suunto workouts and upserts them into
 * {@code suunto_workouts}.
 *
 * <p>Incremental watermark: resumes from the highest stored
 * {@code lastModified} minus a 24 h safety margin (mirroring
 * {@code SyncActivitiesJob.resolveAfterEpoch}). Suunto's server-side default
 * filter is by modification time, so edited workouts re-sync too. With no
 * stored rows the {@code since} parameter is omitted entirely — the server
 * default (0) yields the full history on the first run, deliberately unlike
 * Strava's 30-day first fetch: a Suunto account's whole history is a few
 * dozen 50-workout pages, well within the rate budget.
 *
 * <p>On HTTP 429 the run aborts for ALL remaining athletes, not just the
 * current one — Suunto's quota belongs to the single per-deployment
 * subscription key, so exhausted for one means exhausted for everyone
 * (deliberate deviation from Strava's per-athlete continue). The proactive
 * {@code SuuntoRateLimiter} (#8) throttles every page request automatically,
 * making this path unlikely.
 */
@ApplicationScoped
public class SyncSuuntoWorkoutsJob extends AbstractSuuntoJob {

    private static final Logger LOG = Logger.getLogger(SyncSuuntoWorkoutsJob.class);
    private static final int PAGE_SIZE = 50;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final long WATERMARK_SAFETY_MARGIN_MS = 24L * 60 * 60 * 1000;

    @Inject
    SuuntoWorkoutRepository workoutRepository;

    @Inject
    SuuntoWorkoutUpsertService upsertService;

    @Override
    public String jobId() {
        return "suunto.sync-workouts";
    }

    @Override
    public String cronExpression() {
        // Daily, offset from Strava's hourly :00 syncs and 02:00 training load.
        // Matches Suunto's documented daily-refresh recommendation; webhooks
        // are a future issue.
        return "0 30 3 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String suuntoUser = externalUserId(token);
        String accessToken = validAccessToken(token);

        Long since = workoutRepository.findMaxLastModifiedByUser(suuntoUser)
                .map(maxLastModified -> maxLastModified - WATERMARK_SAFETY_MARGIN_MS)
                .orElse(null);

        int offset = 0;
        int totalSynced = 0;

        while (true) {
            SuuntoWorkoutListResponse response;
            try {
                response = suuntoApiClient.getWorkouts(
                        "Bearer " + accessToken, subscriptionKey(),
                        since, null, PAGE_SIZE, offset, null);
            } catch (WebApplicationException e) {
                if (e.getResponse() != null && e.getResponse().getStatus() == HTTP_TOO_MANY_REQUESTS) {
                    LOG.warnf("Suunto 429 at offset %d for user '%s' — aborting run for all remaining athletes "
                            + "(quota is per deployment)", offset, suuntoUser);
                    return true;
                }
                throw e;
            }

            if (response.error() != null) {
                throw new CollectorException("Suunto workout list returned error "
                        + response.error().code() + ": " + response.error().description());
            }

            List<SuuntoWorkoutDto> page = response.payload();
            if (page == null || page.isEmpty()) {
                break;
            }

            for (SuuntoWorkoutDto dto : page) {
                upsertService.upsert(suuntoUser, dto);
                totalSynced++;
            }

            LOG.infof("SyncSuuntoWorkoutsJob — user: '%s', offset %d, %d workouts processed",
                    suuntoUser, offset, page.size());

            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        LOG.infof("SyncSuuntoWorkoutsJob completed — user: '%s', total synchronized: %d",
                suuntoUser, totalSynced);
        return false;
    }
}
