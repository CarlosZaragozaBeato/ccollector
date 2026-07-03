package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.RaceResultSummary;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RegisterForReflection
public record RaceResultDto(
        UUID id,
        LocalDate raceDate,
        String raceName,
        Double distanceMeters,
        Integer goalFinishTime,
        Integer actualFinishTime,
        Integer position,
        String notes,
        UUID linkedActivityId,
        Instant createdAt,
        Instant updatedAt
) {
    public static RaceResultDto from(RaceResultSummary summary) {
        return new RaceResultDto(
                summary.id(),
                summary.raceDate(),
                summary.raceName(),
                summary.distanceMeters(),
                summary.goalFinishTime(),
                summary.actualFinishTime(),
                summary.position(),
                summary.notes(),
                summary.linkedActivityId(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }
}
