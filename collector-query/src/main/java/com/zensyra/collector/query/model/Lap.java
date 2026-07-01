package com.zensyra.collector.query.model;

import java.util.UUID;

/**
 * Provider-neutral lap read-model. Keyed by the canonical {@code ActivityId}
 * ({@code TrainingSession.id}) of the activity this lap belongs to, plus a
 * one-based {@code lapIndex} that orders laps within the activity.
 *
 * <p><strong>Deliberately omits {@code averageWatts}.</strong> Power output
 * is recorded in {@code activity_laps} but a watts-neutral concept has not
 * been defined for this read-model layer yet — the same decision was made in
 * {@link ActivityMetrics}, where NP and IF already use watts. Until a
 * consistent power model exists across sources, carrying source-specific watt
 * values through the neutral layer would be premature.
 */
public record Lap(
        UUID activityId,
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
}
