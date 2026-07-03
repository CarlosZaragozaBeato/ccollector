package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.RaceResultSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RaceResultQueryPort {

    List<RaceResultSummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to);
}
