package com.zensyra.collector.query.port;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface TrainingStressContributionPort {

    /**
     * Returns TSS contributions keyed by local date (UTC) for the given
     * athlete over [from, to] inclusive. Missing dates carry no entry —
     * absent key means zero contribution from this source on that day.
     * The EMA loop in {@code TrainingLoadService} handles absent keys via
     * {@code Map.getOrDefault(day, 0.0)}.
     */
    Map<LocalDate, Double> contributionsForAthlete(UUID athleteId, LocalDate from, LocalDate to);
}
