package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.TrainingLoadSummary;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;

/**
 * REST response record for one period's training load aggregate.
 *
 * <p>TSS: {@code (moving_time_seconds / 3600) × IF² × 100}, using each activity's
 * real intensity factor IF = NP / FTP when power data and athlete FTP are available,
 * falling back to a fixed IF = 0.75 only when an activity has no real IF. This matches
 * the estimate used by {@code TrainingLoadService} when populating
 * {@code athlete_training_load.tss_day}. {@code totalTss} is the sum of
 * {@code tss_day} for every day in the period.
 *
 * <p>{@code ctlEnd}, {@code atlEnd}, {@code tsbEnd} are CTL/ATL/TSB on the
 * last day with data in the period; null when the period has no training rows.
 *
 * <p>{@code athleteId} is intentionally omitted — it is already in the URL.
 */
@RegisterForReflection
public record TrainingLoadSummaryDto(
        LocalDate periodStart,
        String granularity,
        Double totalTss,
        Double ctlEnd,
        Double atlEnd,
        Double tsbEnd
) {
    public static TrainingLoadSummaryDto from(TrainingLoadSummary summary) {
        return new TrainingLoadSummaryDto(
                summary.periodStart(),
                summary.granularity().name(),
                summary.totalTss(),
                summary.ctlEnd(),
                summary.atlEnd(),
                summary.tsbEnd()
        );
    }
}
