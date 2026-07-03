package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.HealthEventSummary;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RegisterForReflection
public record HealthEventDto(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        String title,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static HealthEventDto from(HealthEventSummary summary) {
        return new HealthEventDto(
                summary.id(),
                summary.startDate(),
                summary.endDate(),
                summary.type(),
                summary.title(),
                summary.notes(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }
}
