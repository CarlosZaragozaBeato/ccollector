package com.zensyra.collector.strava.lap;

import com.zensyra.collector.strava.api.dto.StravaLapDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ActivityLapUpsertService {

    private static final Logger LOG = Logger.getLogger(ActivityLapUpsertService.class);

    @Inject
    ActivityLapRepository lapRepository;

    @Transactional
    public void upsertLaps(Long activityStravaId, List<StravaLapDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        long deleted = lapRepository.deleteByActivityStravaId(activityStravaId);
        if (deleted > 0) {
            LOG.debugf("ActivityLapUpsertService: deleted %d previous laps for activity %d",
                    deleted, activityStravaId);
        }

        for (StravaLapDto dto : dtos) {
            ActivityLap lap = new ActivityLap();
            lap.setActivityStravaId(activityStravaId);
            lap.setLapIndex(dto.getLapIndex() != null ? dto.getLapIndex() : 0);
            lap.setName(dto.getName());
            lap.setElapsedTime(dto.getElapsedTime());
            lap.setMovingTime(dto.getMovingTime());
            lap.setSplit(dto.getSplit());
            lap.setPaceZone(dto.getPaceZone());
            lap.setStartIndex(dto.getStartIndex());
            lap.setEndIndex(dto.getEndIndex());

            if (dto.getStartDate() != null) {
                lap.setStartDate(Instant.parse(dto.getStartDate()));
            }
            if (dto.getDistance() != null) {
                lap.setDistance(BigDecimal.valueOf(dto.getDistance()));
            }
            if (dto.getAverageSpeed() != null) {
                lap.setAverageSpeed(BigDecimal.valueOf(dto.getAverageSpeed()));
            }
            if (dto.getMaxSpeed() != null) {
                lap.setMaxSpeed(BigDecimal.valueOf(dto.getMaxSpeed()));
            }
            if (dto.getAverageHeartrate() != null) {
                lap.setAverageHeartrate(BigDecimal.valueOf(dto.getAverageHeartrate()));
            }
            if (dto.getMaxHeartrate() != null) {
                lap.setMaxHeartrate(BigDecimal.valueOf(dto.getMaxHeartrate()));
            }
            if (dto.getAverageWatts() != null) {
                lap.setAverageWatts(BigDecimal.valueOf(dto.getAverageWatts()));
            }
            if (dto.getTotalElevationGain() != null) {
                lap.setTotalElevationGain(BigDecimal.valueOf(dto.getTotalElevationGain()));
            }

            lapRepository.persist(lap);
        }

        LOG.debugf("ActivityLapUpsertService: inserted %d laps for activity %d",
                dtos.size(), activityStravaId);
    }
}
