package com.zensyra.collector.query.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-model for one race result. {@code athleteId} is intentionally omitted —
 * it is implicit from the request URL. {@code id} is included because a
 * RaceResult has no natural key (an athlete may race more than once on the same
 * date), so clients need it to reference a specific result.
 */
public record RaceResultSummary(
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
}
