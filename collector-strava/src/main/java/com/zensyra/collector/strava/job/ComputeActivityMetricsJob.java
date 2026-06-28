package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ComputeActivityMetricsJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(ComputeActivityMetricsJob.class);

    @Inject
    ActivityMetricsService activityMetricsService;

    @Override
    public String jobId() {
        return "strava.compute-activity-metrics";
    }

    @Override
    public String cronExpression() {
        return "0 0 3 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        try {
            Long athleteId = parseAthleteId(externalUserId);
            activityMetricsService.computeAndUpsert(athleteId);
            LOG.infof("ComputeActivityMetricsJob completed — user: '%s'", externalUserId);
        } catch (Exception e) {
            LOG.errorf(e, "Error calculating activity metrics for user '%s'", externalUserId);
        }
        return false;
    }
}
