package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.Activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for listing an athlete's activities. Implementations
 * (one per connected source, composed by {@code ActivityQueryComposer}) must
 * accept and return only canonical identifiers and read-models — never a
 * source-specific entity, repository, or ID.
 */
public interface ActivityQueryPort {

    /**
     * Lists activities for the given canonical athlete, paged and optionally
     * filtered by sport type and start-date range.
     *
     * @param athleteId canonical {@code AthleteProfile.id}
     * @param sportType optional case-insensitive sport type filter, or {@code null}
     * @param from      optional inclusive lower bound on {@code startDate}, or {@code null}
     * @param to        optional exclusive upper bound on {@code startDate}, or {@code null}
     * @param offset    zero-based row offset
     * @param limit     maximum number of rows to return
     */
    List<Activity> listByAthlete(
            UUID athleteId,
            String sportType,
            Instant from,
            Instant to,
            int offset,
            int limit);
}