package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.Activity;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

@RegisterForReflection
public record ActivityDto(
        UUID activityId,
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
                a.activityId(),
                a.name(),
                a.sportType(),
                a.distanceMeters(),
                a.movingTimeSecs(),
                a.startDate(),
                a.totalElevationGain(),
                a.averageHeartrate(),
                a.averageWatts()
        );
    }
}
