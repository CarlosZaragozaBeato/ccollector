package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import com.zensyra.collector.strava.stream.ActivityStreamSyncResult;
import com.zensyra.collector.strava.stream.ActivityStreamSyncService;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SyncActivityStreamsJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncActivityStreamsJob.class);
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final List<String> STREAM_KEYS = List.of(
            "time",
            "distance",
            "latlng",
            "altitude",
            "heartrate",
            "watts",
            "cadence"
    );

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityStreamSyncService activityStreamSyncService;

    @Inject
    StravaCollectorMetrics metrics;

    @Inject
    StravaRateLimiter rateLimiter;

    @ConfigProperty(name = "strava.streams.batch-size", defaultValue = "10")
    int batchSize;

    @ConfigProperty(name = "strava.streams.retry-limit", defaultValue = "3")
    int retryLimit;

    @Override
    public String jobId() {
        return "strava.sync-activity-streams";
    }

    @Override
    public String cronExpression() {
        return "0 45 * * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        return syncStreamsForUser(token.getExternalUserId());
    }

    private boolean syncStreamsForUser(String externalUserId) {
        String accessToken = tokenService.getValidToken(IntegrationSource.STRAVA, externalUserId);
        Long athleteStravaId = parseAthleteId(externalUserId);
        List<Activity> pendingActivities = activityRepository
                .findPendingStreamActivitiesByAthleteId(athleteStravaId, retryLimit, batchSize);

        if (pendingActivities.isEmpty()) {
            LOG.infof("SyncActivityStreamsJob: no hay actividades pendientes para '%s'", externalUserId);
            return false;
        }

        for (Activity activity : pendingActivities) {
            boolean aborted = syncSingleActivity(accessToken, activity);
            if (aborted) {
                return true;
            }
        }

        return false;
    }

    private boolean syncSingleActivity(String accessToken, Activity activity) {
        Long activityStravaId = activity.getStravaId();
        int nextAttempt = (activity.getStreamsSyncAttempts() != null ? activity.getStreamsSyncAttempts() : 0) + 1;
        MDC.put("activityId", activityStravaId);
        MDC.put("attempt", nextAttempt);

        Timer.Sample sample = metrics.startActivityStreamSyncTimer();
        activityStreamSyncService.markRequested(activityStravaId, Instant.now());

        try {
            Map<String, StravaActivityStreamDto> streams = fetchStreams(accessToken, activityStravaId);
            ActivityStreamSyncResult result = activityStreamSyncService.replaceStreams(activityStravaId, streams);
            metrics.incrementActivityStreamsSynced();
            metrics.incrementActivityStreamRowsWritten(result.rowsWritten());
            MDC.put("streamRows", result.rowsWritten());
            MDC.put("status", result.status().name());
            LOG.infof("Streams sincronizados activityId=%d streamRows=%d attempt=%d status=%s",
                    activityStravaId, result.rowsWritten(), nextAttempt, result.status().name());
            return false;
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == HTTP_TOO_MANY_REQUESTS) {
                metrics.incrementRateLimitHits();
                metrics.incrementActivityStreamSyncErrors();
                MDC.put("status", "FAILED");
                LOG.warnf("Strava 429 activityId=%d attempt=%d status=FAILED abortingBatch=true",
                        activityStravaId, nextAttempt);
                return true;
            }
            metrics.incrementActivityStreamSyncErrors();
            activityStreamSyncService.markFailure(activityStravaId, e.getMessage());
            MDC.put("status", "FAILED");
            LOG.warnf("Error sincronizando streams activityId=%d attempt=%d status=FAILED error=%s",
                    activityStravaId, nextAttempt, e.getMessage());
            return false;
        } catch (Exception e) {
            metrics.incrementActivityStreamSyncErrors();
            activityStreamSyncService.markFailure(activityStravaId, e.getMessage());
            MDC.put("status", "FAILED");
            LOG.warnf("Error sincronizando streams activityId=%d attempt=%d status=FAILED error=%s",
                    activityStravaId, nextAttempt, e.getMessage());
            return false;
        } finally {
            metrics.stopActivityStreamSyncTimer(sample);
            MDC.remove("activityId");
            MDC.remove("attempt");
            MDC.remove("streamRows");
            MDC.remove("status");
        }
    }

    private Map<String, StravaActivityStreamDto> fetchStreams(String accessToken, Long activityStravaId) {
        rateLimiter.acquire();
        Map<String, StravaActivityStreamDto> response = stravaApiClient.getActivityStreams(
                "Bearer " + accessToken, activityStravaId, String.join(",", STREAM_KEYS), true
        );
        return response != null ? response : Map.of();
    }
}
