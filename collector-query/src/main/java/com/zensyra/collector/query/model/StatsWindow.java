package com.zensyra.collector.query.model;

/**
 * The time window a {@link SportAggregate} covers. Closed to these two
 * values deliberately — unlike {@code sportType}, which is free text,
 * "year to date" and "all time" are generic temporal concepts that make
 * sense regardless of source, not a Strava-specific taxonomy.
 */
public enum StatsWindow {
    YEAR_TO_DATE,
    ALL_TIME
}
