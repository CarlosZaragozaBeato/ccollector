package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.TrainingDaySummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingDayQueryPort {

    List<TrainingDaySummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to);
}
