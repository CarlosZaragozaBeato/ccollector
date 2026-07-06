package com.zensyra.collector.query.model;

/**
 * A race result together with the athlete's training-load context around it —
 * CTL/ATL/TSB on race day and at the two standard Performance Management Chart
 * look-back points before it (7 days for short-term fatigue, 42 days for chronic
 * fitness). Closes UC-1 (PMC taper analysis) from
 * {@code docs/design/003-extended-domain-model.md §3}.
 *
 * <p>This is a provider-neutral composition of two sources — the race comes from
 * the journal, the load snapshots from the training-load model — assembled by
 * {@code RacePerformanceComposer}. Each {@link TrainingLoadPoint} independently
 * flags whether its data was actually available, so a race with sync gaps (or no
 * training-load history at all) yields a well-formed context with explicit
 * "insufficient data" points rather than throwing or inventing numbers.
 *
 * @param race            the race result (embeds {@link RaceResultSummary} verbatim)
 * @param atRaceDate      CTL/ATL/TSB on race day
 * @param at7DaysBefore   CTL/ATL/TSB 7 days before the race (short-term fatigue)
 * @param at42DaysBefore  CTL/ATL/TSB 42 days before the race (chronic fitness)
 */
public record RacePerformanceContext(
        RaceResultSummary race,
        TrainingLoadPoint atRaceDate,
        TrainingLoadPoint at7DaysBefore,
        TrainingLoadPoint at42DaysBefore
) {
}
