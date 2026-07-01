package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.Lap;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record LapDto(
        Integer lapIndex,
        String name,
        Double distanceMeters,
        Integer movingTimeSecs,
        Double averageSpeedMps,
        Double averageHeartrate,
        Double maxHeartrate,
        Double elevationGainMeters,
        Integer paceZone
) {
    public static LapDto from(Lap lap) {
        return new LapDto(
                lap.lapIndex(),
                lap.name(),
                lap.distanceMeters(),
                lap.movingTimeSecs(),
                lap.averageSpeedMps(),
                lap.averageHeartrate(),
                lap.maxHeartrate(),
                lap.elevationGainMeters(),
                lap.paceZone()
        );
    }
}
