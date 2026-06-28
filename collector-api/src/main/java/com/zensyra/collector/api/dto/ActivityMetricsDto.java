package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.ActivityMetrics;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivityMetricsDto(
        UUID activityId,
        BigDecimal normalizedPower,
        BigDecimal variabilityIndex,
        BigDecimal efficiencyFactor,
        BigDecimal intensityFactor
) {
    public static ActivityMetricsDto from(ActivityMetrics metrics) {
        return new ActivityMetricsDto(
                metrics.activityId(),
                metrics.normalizedPower(),
                metrics.variabilityIndex(),
                metrics.efficiencyFactor(),
                metrics.intensityFactor()
        );
    }
}
