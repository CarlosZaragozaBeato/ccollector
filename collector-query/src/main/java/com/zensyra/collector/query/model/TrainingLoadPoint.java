package com.zensyra.collector.query.model;

import java.time.LocalDate;

/**
 * A single CTL/ATL/TSB snapshot sampled at (or near) one target date, used to
 * build the training-load context preceding a race (see
 * {@link RacePerformanceContext}).
 *
 * <p>Data gaps are represented <em>explicitly</em>, never with a substituted
 * default that could be mistaken for a real measurement:
 * <ul>
 *   <li>{@code available == true}: a daily {@code athlete_training_load} row was
 *       found within the composer's nearest-day tolerance of {@code requestedDate};
 *       {@code actualDate} is the date of the row actually used (which may differ
 *       from {@code requestedDate} when the exact day had no row), and
 *       {@code ctl}/{@code atl}/{@code tsb} carry that row's values.</li>
 *   <li>{@code available == false}: no row was found within tolerance —
 *       "insufficient data". {@code actualDate} and all three metrics are
 *       {@code null}.</li>
 * </ul>
 *
 * @param requestedDate the date the context asked for (race day, race−7, race−42)
 * @param actualDate    the date of the row actually used; {@code null} when unavailable
 * @param ctl           chronic training load at {@code actualDate}; {@code null} when unavailable
 * @param atl           acute training load at {@code actualDate}; {@code null} when unavailable
 * @param tsb           training stress balance at {@code actualDate}; {@code null} when unavailable
 * @param available     whether a row was found within tolerance
 */
public record TrainingLoadPoint(
        LocalDate requestedDate,
        LocalDate actualDate,
        Double ctl,
        Double atl,
        Double tsb,
        boolean available
) {
}
