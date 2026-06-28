package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.TrainingLoad;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for listing an athlete's recent daily training load
 * snapshots, from the given date onward.
 */
public interface TrainingLoadQueryPort {

    List<TrainingLoad> listRecentByAthlete(UUID athleteId, LocalDate from);
}
