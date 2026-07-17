package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsService;
import com.zensyra.collector.strava.trainingload.TrainingLoadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * One-off, admin-triggered backfill that corrects historical training load with
 * the real intensity factor (#30).
 *
 * <p>Per athlete it runs two stages, each in its own transaction (invoked through
 * the CDI proxies so stage A commits before stage B reads it):
 * <ol>
 *   <li><b>Stage A</b> — populate {@code activity_metrics.intensity_factor} on
 *       historical rows that had normalized power but no IF, using the now-available
 *       FTP ({@link ActivityMetricsService#backfillIntensityFactors}). This stage
 *       still uses the Strava numeric id because it queries the strava-internal
 *       {@code activities} table directly.</li>
 *   <li><b>Stage B</b> — recompute CTL/ATL/TSB for every existing daily row so the
 *       stored values reflect the corrected TSS ({@link TrainingLoadService#backfill}).
 *       This stage uses the canonical UUID because {@code athlete_training_load} is
 *       now keyed by canonical UUID (migration 043).</li>
 * </ol>
 *
 * <p>The cron is a far-future sentinel so the scheduler never fires it
 * automatically; it is invoked only through
 * {@code POST /admin/trigger/strava.backfill-training-load}. This mirrors the
 * one-off pattern of {@code InitialHistoricalSyncJob}.
 */
@ApplicationScoped
public class BackfillTrainingLoadJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(BackfillTrainingLoadJob.class);

    @Inject
    ActivityMetricsService activityMetricsService;

    @Inject
    TrainingLoadService trainingLoadService;

    @Override
    public String jobId() {
        return "strava.backfill-training-load";
    }

    @Override
    public String cronExpression() {
        // Far-future sentinel — never auto-fires; admin-triggered only.
        return "0 0 0 1 1 ? 2099";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        Long stravaAthleteId = parseAthleteId(externalUserId);
        UUID canonicalAthleteId = resolveCanonicalAthleteId(externalUserId);

        int ifPopulated = activityMetricsService.backfillIntensityFactors(stravaAthleteId);
        int rowsRecomputed = trainingLoadService.backfill(canonicalAthleteId);

        LOG.infof("BackfillTrainingLoadJob completed — user: '%s', IF populated on %d rows, "
                + "%d daily training-load rows recomputed", externalUserId, ifPopulated, rowsRecomputed);
        return false;
    }
}
