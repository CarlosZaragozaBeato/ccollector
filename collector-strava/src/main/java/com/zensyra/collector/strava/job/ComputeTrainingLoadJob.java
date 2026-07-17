package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.trainingload.TrainingLoadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
public class ComputeTrainingLoadJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(ComputeTrainingLoadJob.class);

    @Inject
    TrainingLoadService trainingLoadService;

    @Override
    public String jobId() {
        return "strava.compute-training-load";
    }

    @Override
    public String cronExpression() {
        return "0 0 2 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        LocalDate targetDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();
        UUID athleteId = resolveCanonicalAthleteId(externalUserId);
        trainingLoadService.computeAndUpsert(athleteId, targetDate);
        LOG.infof("ComputeTrainingLoadJob completed — user: '%s', date=%s", externalUserId, targetDate);
        return false;
    }
}
