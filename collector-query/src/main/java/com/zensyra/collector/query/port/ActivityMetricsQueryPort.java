package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.ActivityMetrics;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral port for fetching computed metrics of a single canonical
 * activity. {@code activityId} is always {@code TrainingSession.id} — never
 * a source-specific activity ID.
 *
 * <p>{@code athleteId} is required, not merely advisory: implementations
 * must verify the resolved activity actually belongs to this athlete and
 * return {@link Optional#empty()} otherwise, never the metrics of an
 * activity belonging to someone else. This mirrors
 * {@link ActivityQueryPort#listByAthlete}, which already scopes every
 * result to a single athlete — ownership validation belongs in this
 * contract, not duplicated by every caller that happens to remember to
 * check it.
 */
public interface ActivityMetricsQueryPort {

    Optional<ActivityMetrics> getByActivityId(UUID athleteId, UUID activityId);
}
