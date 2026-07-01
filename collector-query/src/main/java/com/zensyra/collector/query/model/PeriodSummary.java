package com.zensyra.collector.query.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider-neutral aggregate of an athlete's training for one calendar period
 * (week or month). Derived from the system's own TimescaleDB continuous
 * aggregates, not from values a source reports — any connected source that
 * provides raw activity data can back this model once it has an adapter.
 *
 * <p>{@code granularity} follows the {@link StatsWindow} pattern: typed as an
 * enum rather than a free string so consumers can branch on it without parsing.
 */
public record PeriodSummary(
        UUID athleteId,
        LocalDate periodStart,
        Granularity granularity,
        Integer numActivities,
        Double totalDistanceMeters,
        Integer totalMovingTimeSecs,
        Double totalElevationGainMeters
) {
}
