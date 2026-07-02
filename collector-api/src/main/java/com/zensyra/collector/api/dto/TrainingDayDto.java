package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.TrainingDaySummary;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.LocalDate;

@RegisterForReflection
public record TrainingDayDto(
        LocalDate date,
        Integer perceivedEffort,
        String subjectiveState,
        String notes,
        Double weightKg,
        Instant createdAt,
        Instant updatedAt
) {
    public static TrainingDayDto from(TrainingDaySummary summary) {
        return new TrainingDayDto(
                summary.date(),
                summary.perceivedEffort(),
                summary.subjectiveState(),
                summary.notes(),
                summary.weightKg(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }
}
