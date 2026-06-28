package com.zensyra.collector.strava.job;

import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.strava.api.dto.StravaAthleteStatsDto;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;

@ApplicationScoped
public class SyncAthleteStatsJob extends AbstractStravaJob {

    private static final Logger LOG = Logger.getLogger(SyncAthleteStatsJob.class);

    @Inject
    AthleteStatsSnapshotService athleteStatsSnapshotService;

    @Override
    public String jobId() {
        return "strava.sync-athlete-stats";
    }

    @Override
    public String cronExpression() {
        return "0 0 1 * * ?";
    }

    @Override
    protected boolean executeForToken(OAuthToken token, SyncContext context) {
        String externalUserId = externalUserId(token);
        try {
            Long athleteId = parseAthleteId(externalUserId);
            String accessToken = validAccessToken(token);
            StravaAthleteStatsDto dto = stravaApiClient.getAthleteStats("Bearer " + accessToken, athleteId);
            LocalDate snapshotDate = context.triggeredAt().atZone(ZoneOffset.UTC).toLocalDate();

            athleteStatsSnapshotService.upsertDailySnapshot(athleteId, snapshotDate, dto);
            LOG.infof("SyncAthleteStatsJob completed — user: '%s', snapshotDate=%s", externalUserId, snapshotDate);
        } catch (Exception e) {
            LOG.errorf(e, "Error synchronizing athlete stats for user '%s'", externalUserId);
            throw e;
        }
        return false;
    }
}
