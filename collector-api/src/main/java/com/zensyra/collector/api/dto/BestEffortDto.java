package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.BestEffort;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public record BestEffortDto(
        UUID activityId,
        String name,
        Integer distance,
        Integer elapsedTime,
        Integer prRank
) {
    public static BestEffortDto from(BestEffort effort) {
        return new BestEffortDto(
                effort.activityId(),
                effort.name(),
                effort.distanceMeters(),
                effort.elapsedTimeSecs(),
                effort.personalRecordRank()
        );
    }
}
