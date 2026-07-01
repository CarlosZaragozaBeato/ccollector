package com.zensyra.collector.query.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-period aggregate of an athlete's training load metrics.
 *
 * <p>{@code totalTss} is the sum of daily TSS estimates for every day in the
 * period. The underlying estimate uses
 * {@code (moving_time_seconds / 3600) × IF² × 100} with IF = 0.75 (no
 * power meter required) — see {@code TrainingLoadService} for the derivation.
 *
 * <p>{@code ctlEnd}, {@code atlEnd}, {@code tsbEnd} are the CTL/ATL/TSB
 * snapshots recorded on the <em>last day with data</em> within the period,
 * which approximates the end-of-period fitness, fatigue, and form state.
 * Periods with no training rows return nulls for these three fields.
 */
public record TrainingLoadSummary(
        UUID athleteId,
        LocalDate periodStart,
        Granularity granularity,
        Double totalTss,
        Double ctlEnd,
        Double atlEnd,
        Double tsbEnd
) {}
