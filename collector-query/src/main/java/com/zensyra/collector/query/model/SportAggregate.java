package com.zensyra.collector.query.model;

/**
 * Aggregate totals for one sport, within one {@link StatsWindow}.
 *
 * <p>{@code sportType} is free text deliberately, not a closed enum of
 * Ride/Run/Swim. Strava's own stats schema hard-codes exactly three sports
 * as fixed columns; baking that same assumption into the canonical
 * read-model would break the moment a source reports a sport Strava does
 * not, or omits one Strava always has. A list of aggregates, one per
 * (sportType, window) pair actually reported, has no such ceiling — see
 * the Issue A-2 design note on why {@code AthleteStats} is shaped this way.
 */
public record SportAggregate(
        String sportType,
        StatsWindow window,
        Integer activityCount,
        Double distanceMeters,
        Integer movingTimeSecs,
        Double elevationGainMeters
) {
}
