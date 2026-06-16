package com.zensyra.collector.api.dto;

import com.zensyra.collector.strava.besteffort.ActivityBestEffort;

public record BestEffortDto(
        Long activityStravaId,
        String name,
        Integer distance,
        Integer elapsedTime,
        Boolean isKom,
        Integer prRank
) {
    public static BestEffortDto from(ActivityBestEffort effort) {
        return new BestEffortDto(
                effort.getActivityStravaId(),
                effort.getName(),
                effort.getDistance(),
                effort.getElapsedTime(),
                effort.getIsKom(),
                effort.getPrRank()
        );
    }
}
