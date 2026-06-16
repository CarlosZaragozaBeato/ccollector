package com.zensyra.collector.api.dto;

import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;

import java.math.BigDecimal;

public record ActivityMetricsDto(
        Long activityId,
        Long activityStravaId,
        BigDecimal normalizedPower,
        BigDecimal variabilityIndex,
        BigDecimal efficiencyFactor,
        BigDecimal intensityFactor
) {
    public static ActivityMetricsDto from(ActivityMetrics metrics, Long activityStravaId) {
        return new ActivityMetricsDto(
                metrics.getActivityId(),
                activityStravaId,
                metrics.getNormalizedPower(),
                metrics.getVariabilityIndex(),
                metrics.getEfficiencyFactor(),
                metrics.getIntensityFactor()
        );
    }
}
