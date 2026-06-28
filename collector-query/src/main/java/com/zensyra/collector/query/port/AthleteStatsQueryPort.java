package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.AthleteStats;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral port for fetching an athlete's latest aggregate
 * statistics snapshot. Never exposes a source-specific identifier or a
 * source-specific fixed sport taxonomy (see {@link AthleteStats} for why
 * aggregates are a list, not fixed columns).
 */
public interface AthleteStatsQueryPort {

    Optional<AthleteStats> getLatestByAthlete(UUID athleteId);
}
