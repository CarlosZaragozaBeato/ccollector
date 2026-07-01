package com.zensyra.collector.strava.summary;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Internal projection from the {@code weekly_activity_summary} /
 * {@code monthly_activity_summary} native query result. Not part of any
 * public API — used only within {@link ActivitySummaryViewRepository} and
 * {@link com.zensyra.collector.strava.identity.StravaActivitySummaryQueryPort}.
 */
public record ActivitySummaryRow(
        LocalDate periodStart,
        int numActivities,
        double totalDistanceMeters,
        int totalMovingTimeSecs,
        double totalElevationGainMeters
) {

    static ActivitySummaryRow fromRow(Object[] row) {
        LocalDate periodStart = ((Timestamp) row[0]).toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        int numActivities = ((Number) row[1]).intValue();
        double totalDistanceM = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        int totalMovingTimeS = row[3] != null ? ((Number) row[3]).intValue() : 0;
        double totalElevationGainM = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
        return new ActivitySummaryRow(periodStart, numActivities, totalDistanceM, totalMovingTimeS, totalElevationGainM);
    }
}
