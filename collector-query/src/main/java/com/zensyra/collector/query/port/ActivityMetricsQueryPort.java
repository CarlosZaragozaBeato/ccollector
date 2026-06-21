package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.ActivityMetrics;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral port for fetching computed metrics of a single canonical
 * activity. {@code activityId} is always {@code TrainingSession.id} — never
 * a source-specific activity ID.
 */
public interface ActivityMetricsQueryPort {

    Optional<ActivityMetrics> getByActivityId(UUID activityId);
}