package com.zensyra.collector.query.port;

import com.zensyra.collector.query.model.HealthEventSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HealthEventQueryPort {

    List<HealthEventSummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to);
}
