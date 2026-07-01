package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.PeriodSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for listing an athlete's training aggregated by
 * calendar period (week or month). Implementations derive these totals from
 * the raw activity data synced for their source; they never expose a
 * source-specific identifier.
 *
 * @param athleteId   canonical {@code AthleteProfile.id}
 * @param from        inclusive start date of the range to query
 * @param to          inclusive end date of the range to query
 * @param granularity {@link Granularity#WEEKLY} or {@link Granularity#MONTHLY}
 */
public interface ActivitySummaryQueryPort {

    List<PeriodSummary> listByAthlete(UUID athleteId, LocalDate from, LocalDate to, Granularity granularity);
}
