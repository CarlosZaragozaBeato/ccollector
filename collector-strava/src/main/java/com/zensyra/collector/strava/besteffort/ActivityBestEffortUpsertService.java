package com.zensyra.collector.strava.besteffort;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.api.dto.StravaBestEffortDto;
import com.zensyra.collector.strava.identity.StravaActivityIdentityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ActivityBestEffortUpsertService {

    private static final Logger LOG = Logger.getLogger(ActivityBestEffortUpsertService.class);

    @Inject
    ActivityBestEffortRepository repository;

    @Inject
    StravaActivityIdentityService activityIdentityService;

    @Transactional
    public void upsertBestEfforts(Activity activity, List<StravaBestEffortDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        Long activityStravaId = activity.getStravaId();
        activityIdentityService.resolveOrCreateReference(activity.getAthleteId(), activityStravaId);

        long deleted = repository.deleteByActivityStravaId(activityStravaId);
        if (deleted > 0) {
            LOG.debugf("ActivityBestEffortUpsertService: deleted %d previous best efforts for activity %d",
                    deleted, activityStravaId);
        }

        for (StravaBestEffortDto dto : dtos) {
            if (dto.getName() == null) continue;

            ActivityBestEffort effort = new ActivityBestEffort();
            effort.setActivityStravaId(activityStravaId);
            effort.setName(dto.getName());
            effort.setDistance(dto.getDistance());
            effort.setElapsedTime(dto.getElapsedTime());
            effort.setIsKom(dto.getIsKom() != null ? dto.getIsKom() : false);
            effort.setPrRank(dto.getPrRank());
            repository.persist(effort);
        }

        LOG.debugf("ActivityBestEffortUpsertService: inserted %d best efforts for activity %d",
                dtos.size(), activityStravaId);
    }
}
