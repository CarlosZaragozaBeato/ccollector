package com.zensyra.collector.api.dto;

import com.zensyra.collector.strava.activity.Activity;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record ActivityDto(
        Long stravaId,
        String name,
        String sportType,
        Double distanceMeters,
        Integer movingTimeSecs,
        Instant startDate,
        Double totalElevationGain,
        Double averageHeartrate,
        Double averageWatts
) {
    public static ActivityDto from(Activity a) {
        return new ActivityDto(
                a.getStravaId(),
                a.getName(),
                a.getSportType(),
                a.getDistance() != null ? a.getDistance().doubleValue() : null,
                a.getMovingTime(),
                a.getStartDate(),
                a.getTotalElevationGain() != null ? a.getTotalElevationGain().doubleValue() : null,
                a.getAverageHeartrate() != null ? a.getAverageHeartrate().doubleValue() : null,
                a.getAverageWatts() != null ? a.getAverageWatts().doubleValue() : null
        );
    }
}
