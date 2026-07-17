package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.port.TrainingStressContributionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates per-day TSS contributions from all registered
 * {@link TrainingStressContributionPort} implementations using
 * {@link Double#sum} — never {@code putIfAbsent}, which would silently
 * discard a second source's training load on the same day.
 *
 * <p>The returned map contains only dates with at least one contribution;
 * the EMA loop in {@code TrainingLoadService} handles missing dates via
 * {@code Map.getOrDefault(day, 0.0)}.
 */
@ApplicationScoped
public class DailyTssComposer {

    private final Instance<TrainingStressContributionPort> ports;

    @Inject
    public DailyTssComposer(Instance<TrainingStressContributionPort> ports) {
        this.ports = ports;
    }

    public Map<LocalDate, Double> aggregate(UUID athleteId, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> result = new HashMap<>();
        for (TrainingStressContributionPort port : ports) {
            port.contributionsForAthlete(athleteId, from, to)
                    .forEach((date, tss) -> result.merge(date, tss, Double::sum));
        }
        return result;
    }
}
