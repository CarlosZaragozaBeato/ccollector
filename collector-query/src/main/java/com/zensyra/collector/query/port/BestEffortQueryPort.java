package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.BestEffort;

import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for listing an athlete's top personal-best efforts,
 * ranked by {@code personalRecordRank} within their own history. Never
 * exposes a source-specific identifier or a source-specific social ranking
 * (see {@link BestEffort} for why {@code isKom} has no place here).
 */
public interface BestEffortQueryPort {

    List<BestEffort> listTopByAthlete(UUID athleteId, int limit);
}
