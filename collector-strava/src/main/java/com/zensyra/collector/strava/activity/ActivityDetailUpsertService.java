package com.zensyra.collector.strava.activity;

import com.zensyra.collector.strava.api.dto.StravaActivityDetailDto;
import com.zensyra.collector.strava.besteffort.ActivityBestEffortUpsertService;
import com.zensyra.collector.strava.lap.ActivityLapUpsertService;
import com.zensyra.collector.strava.metrics.StravaCollectorMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class ActivityDetailUpsertService {

    private static final Logger LOG = Logger.getLogger(ActivityDetailUpsertService.class);

    @Inject
    private ActivityRepository activityRepository;

    @Inject
    private ActivityLapUpsertService activityLapUpsertService;

    @Inject
    private ActivityBestEffortUpsertService activityBestEffortUpsertService;

    @Inject
    private StravaCollectorMetrics metrics;


    @Transactional
    public void upsert(StravaActivityDetailDto dto) {

        activityRepository.findByStravaId(dto.getId()).ifPresentOrElse(
                activity -> {
                    activity.setCalories(dto.getCalories());
                    activity.setDescription(dto.getDescription());
                    activity.setDeviceName(dto.getDeviceName());

                    if (dto.getPerceivedExertion() != null) {
                        activity.setPerceivedExertion(BigDecimal.valueOf(dto.getPerceivedExertion()));
                    }
                    activityRepository.persist(activity);

                    if (!dto.getLaps().isEmpty()) {
                        activityLapUpsertService.upsertLaps(dto.getId(), dto.getLaps());
                        metrics.incrementLapsSynced(dto.getLaps().size());
                    }

                    if (!dto.getBestEfforts().isEmpty()) {
                        activityBestEffortUpsertService.upsertBestEfforts(activity, dto.getBestEfforts());
                    }

                    metrics.incrementActivitiesSynced();
                    LOG.debugf("Activity detail upserted — stravaId: %d", dto.getId());
                },
                () -> LOG.warnf("Activity detail: no activity found with stravaId=%d", dto.getId())
        );
    }
}
