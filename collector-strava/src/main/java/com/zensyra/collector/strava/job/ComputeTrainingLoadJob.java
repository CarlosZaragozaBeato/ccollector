package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.trainingload.TrainingLoadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;

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
        String externalUserId = token.getExternalUserId();
        LocalDate targetDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();
        try {
            Long athleteId = parseAthleteId(externalUserId);
            trainingLoadService.computeAndUpsert(athleteId, targetDate);
            LOG.infof("ComputeTrainingLoadJob completado — usuario: '%s', date=%s", externalUserId, targetDate);
        } catch (Exception e) {
            LOG.errorf(e, "Error calculando training load para usuario '%s'", externalUserId);
        }
        return false;
    }
}
