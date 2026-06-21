package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.port.ActivityQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolves an athlete's activities by composing every registered
 * {@link ActivityQueryPort} implementation — one per connected integration
 * source. Written as the general N-source case per the ADR-002 addendum,
 * even though only the Strava adapter is registered today.
 *
 * <p>Each registered port is queried independently and results are merged
 * by start date. With a single registered port this degenerates to a
 * pass-through; that is the expected one-source instance of the general
 * algorithm, not a separate code path.
 *
 * <p>This composer does not yet implement cross-source deduplication or
 * conflict resolution for genuinely overlapping observations of the same
 * {@code TrainingSession} — that policy is explicitly deferred (see
 * ADR-002 addendum, "Costs and Constraints") until a second source adapter
 * exists and a real conflict case can be observed and tested against.
 */
@ApplicationScoped
public class ActivityQueryComposer {

    private final Instance<ActivityQueryPort> registeredPorts;

    @Inject
    public ActivityQueryComposer(Instance<ActivityQueryPort> registeredPorts) {
        this.registeredPorts = registeredPorts;
    }

    /**
     * Lists activities for the given athlete across every registered source,
     * merged and sorted by {@code startDate} descending, then paged.
     *
     * <p>Filtering by sport type and date range is pushed down to each
     * source's port; offset/limit paging is applied once, after the merge,
     * so a page boundary is consistent regardless of how many sources
     * contributed to it.
     */
    public List<Activity> listByAthlete(
            UUID athleteId,
            String sportType,
            Instant from,
            Instant to,
            int offset,
            int limit) {
        List<Activity> merged = new ArrayList<>();
        for (ActivityQueryPort port : registeredPorts) {
            // Each source is asked for up to offset + limit rows so the merge
            // step has enough candidates to produce a correct page without
            // requiring sources to know about each other's result counts.
            merged.addAll(port.listByAthlete(athleteId, sportType, from, to, 0, offset + limit));
        }

        merged.sort(Comparator.comparing(Activity::startDate, Comparator.nullsLast(Comparator.reverseOrder())));

        int fromIndex = Math.min(offset, merged.size());
        int toIndex = Math.min(offset + limit, merged.size());
        return merged.subList(fromIndex, toIndex);
    }
}