package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.QueryResult;
import com.zensyra.collector.query.model.SourceFailure;
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
     *
     * <p>Frozen for the {@code /v1} contract: if any registered source
     * throws, that exception propagates and the whole request fails, exactly
     * as it always has. Do not add failure tolerance here — that is what
     * {@link #listByAthleteWithFailures} is for. Changing this method's
     * behavior would silently change {@code /v1}'s contract.
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
            merged.addAll(port.listByAthlete(athleteId, sportType, from, to, 0, offset + limit));
        }
        return sortAndPage(merged, offset, limit);
    }

    /**
     * Same composition as {@link #listByAthlete}, except a source that
     * throws is recorded as a {@link SourceFailure} and skipped, rather than
     * failing the whole request. Used by {@code /v2}, which surfaces partial
     * results explicitly via {@link QueryResult#isPartial()} instead of
     * letting one failing source take down every other source's data along
     * with it.
     *
     * <p>With today's single registered source, a failure here means the
     * result is always either fully complete or fully empty-with-a-failure
     * — there is no "some activities present, source X failed" case yet.
     * That case becomes real, and testable, only once a second source
     * exists.
     */
    public QueryResult<Activity> listByAthleteWithFailures(
            UUID athleteId,
            String sportType,
            Instant from,
            Instant to,
            int offset,
            int limit) {
        List<Activity> merged = new ArrayList<>();
        List<SourceFailure> failures = new ArrayList<>();
        for (ActivityQueryPort port : registeredPorts) {
            try {
                merged.addAll(port.listByAthlete(athleteId, sportType, from, to, 0, offset + limit));
            } catch (RuntimeException e) {
                failures.add(new SourceFailure(
                        port.getClass().getSimpleName(),
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        return new QueryResult<>(sortAndPage(merged, offset, limit), failures);
    }

    private List<Activity> sortAndPage(List<Activity> merged, int offset, int limit) {
        merged.sort(Comparator.comparing(Activity::startDate, Comparator.nullsLast(Comparator.reverseOrder())));
        int fromIndex = Math.min(offset, merged.size());
        int toIndex = Math.min(offset + limit, merged.size());
        return merged.subList(fromIndex, toIndex);
    }
}
