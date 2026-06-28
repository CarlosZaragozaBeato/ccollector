package com.zensyra.collector.query.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral athlete statistics snapshot: a list of per-sport,
 * per-window aggregates (see {@link SportAggregate}), instead of Strava's
 * fixed Ride/Run/Swim × year-to-date/all-time column layout.
 *
 * <p>Deliberately omits Strava's {@code biggestRideDistance} and
 * {@code biggestClimbElevationGain}. These are standalone personal records
 * with no time window and no relationship to the aggregate totals here —
 * conceptually closer to {@link BestEffort} than to an aggregate. They are
 * left out of this read-model rather than forced in; if a consumer needs
 * them, they belong in their own contract, decided when that need is
 * concrete rather than carried along here "just in case."
 */
public record AthleteStats(
        UUID athleteId,
        LocalDate snapshotDate,
        List<SportAggregate> aggregates
) {
}
