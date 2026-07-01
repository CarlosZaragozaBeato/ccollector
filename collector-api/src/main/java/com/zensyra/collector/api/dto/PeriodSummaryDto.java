package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.PeriodSummary;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;

@RegisterForReflection
public record PeriodSummaryDto(
        LocalDate periodStart,
        String granularity,
        Integer numActivities,
        Double totalDistanceMeters,
        Integer totalMovingTimeSecs,
        Double totalElevationGainMeters
) {
    public static PeriodSummaryDto from(PeriodSummary summary) {
        return new PeriodSummaryDto(
                summary.periodStart(),
                summary.granularity().name(),
                summary.numActivities(),
                summary.totalDistanceMeters(),
                summary.totalMovingTimeSecs(),
                summary.totalElevationGainMeters()
        );
    }
}
