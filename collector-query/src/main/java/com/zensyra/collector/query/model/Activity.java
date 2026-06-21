package com.zensyra.collector.query.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider-neutral activity read-model. Keyed by the canonical {@code ActivityId}
 * (which is {@code TrainingSession.id} — see ADR-002 addendum), never by any
 * source-specific identifier. Structurally cannot carry a Strava ID: that is
 * the point, not an oversight.
 */
public record Activity(
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
}