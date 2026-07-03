package com.zensyra.collector.query.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-model for one health event. {@code athleteId} is intentionally omitted —
 * it is implicit from the request URL. {@code id} is included because a
 * HealthEvent has no natural key (multiple events can start on the same date),
 * so clients need it to reference a specific event.
 */
public record HealthEventSummary(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        String title,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
