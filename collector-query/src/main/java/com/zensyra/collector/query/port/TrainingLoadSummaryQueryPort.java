package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.TrainingLoadSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingLoadSummaryQueryPort {
    List<TrainingLoadSummary> listByAthlete(UUID athleteId, LocalDate from, LocalDate to, Granularity granularity);
}
