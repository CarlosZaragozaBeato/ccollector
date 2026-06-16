package com.zensyra.collector.strava.activity;

import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;

@ApplicationScoped
public class ActivityUpsertService {

    private static final Logger LOG = Logger.getLogger(ActivityUpsertService.class);

    @Inject
    ActivityRepository activityRepository;

    @Transactional
    public void upsert(StravaActivityDto dto) {
        Activity activity = activityRepository.findByStravaId(dto.getId())
                .orElseGet(Activity::new);

        activity.setStravaId(dto.getId());
        activity.setAthleteId(dto.getAthleteId());
        activity.setName(dto.getName());
        activity.setType(dto.getType());
        activity.setSportType(dto.getSportType());
        activity.setDistance(BigDecimal.valueOf(dto.getDistance()));
        activity.setMovingTime(dto.getMovingTime());
        activity.setElapsedTime(dto.getElapsedTime());
        activity.setTrainer(dto.isTrainer());
        activity.setCommute(dto.isCommute());
        activity.setManual(dto.isManual());
        activity.setPrivateActivity(dto.isPrivateActivity());
        activity.setFlagged(dto.isFlagged());
        activity.setGearId(dto.getGearId());
        activity.setTimezone(dto.getTimezone());
        activity.setStartLatlng(dto.getStartLatlngAsString());
        activity.setEndLatlng(dto.getEndLatlngAsString());

        if (dto.getStartDate() != null) {
            activity.setStartDate(Instant.parse(dto.getStartDate()));
        }
        if (dto.getTotalElevationGain() != null) {
            activity.setTotalElevationGain(BigDecimal.valueOf(dto.getTotalElevationGain()));
        }
        if (dto.getAverageSpeed() != null) {
            activity.setAverageSpeed(BigDecimal.valueOf(dto.getAverageSpeed()));
        }
        if (dto.getMaxSpeed() != null) {
            activity.setMaxSpeed(BigDecimal.valueOf(dto.getMaxSpeed()));
        }
        if (dto.getAverageHeartrate() != null) {
            activity.setAverageHeartrate(BigDecimal.valueOf(dto.getAverageHeartrate()));
        }
        if (dto.getMaxHeartrate() != null) {
            activity.setMaxHeartrate(BigDecimal.valueOf(dto.getMaxHeartrate()));
        }
        if (dto.getAverageWatts() != null) {
            activity.setAverageWatts(BigDecimal.valueOf(dto.getAverageWatts()));
        }
        if (dto.getKilojoules() != null) {
            activity.setKilojoules(BigDecimal.valueOf(dto.getKilojoules()));
        }
        if (dto.getSufferScore() != null) {
            activity.setSufferScore(dto.getSufferScore());
        }

        activityRepository.persist(activity);

        LOG.debugf("Activity upserted — stravaId: %d, nombre: '%s'", dto.getId(), dto.getName());
    }
}
