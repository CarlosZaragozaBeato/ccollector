package com.zensyra.collector.query.model;

/**
 * The time granularity of a {@link PeriodSummary}.
 *
 * <p>Modelled as an explicit enum — the same design choice as
 * {@link StatsWindow} — because "weekly" and "monthly" are generic
 * calendar concepts that make sense regardless of source, unlike a
 * raw string that could carry any value a caller constructs.
 */
public enum Granularity {
    WEEKLY,
    MONTHLY
}
