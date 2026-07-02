package com.zensyra.collector.query.model;

import java.time.Instant;
import java.time.LocalDate;

public record TrainingDaySummary(
        LocalDate date,
        Integer perceivedEffort,
        String subjectiveState,
        String notes,
        Double weightKg,
        Instant createdAt,
        Instant updatedAt
) {
}
