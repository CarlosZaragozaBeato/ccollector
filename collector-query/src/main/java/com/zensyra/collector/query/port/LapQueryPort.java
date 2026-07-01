package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.Lap;

import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for listing the laps of a single canonical activity.
 *
 * <p>{@code athleteId} is required for ownership validation — an activity id
 * that exists but belongs to a different athlete must return an empty list,
 * not the laps of someone else's activity. This mirrors the contract in
 * {@link ActivityMetricsQueryPort}.
 */
public interface LapQueryPort {

    List<Lap> listByActivity(UUID athleteId, UUID activityId);
}
