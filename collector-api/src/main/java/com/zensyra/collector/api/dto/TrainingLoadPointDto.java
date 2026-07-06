package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.TrainingLoadPoint;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;

/**
 * Response DTO for one CTL/ATL/TSB snapshot in the race-performance context.
 * Mirrors {@link TrainingLoadPoint} field-for-field; {@code available} makes a
 * data gap explicit (when {@code false}, {@code actualDate} and the metrics are
 * {@code null} rather than substituted with a default).
 */
@RegisterForReflection
public record TrainingLoadPointDto(
        LocalDate requestedDate,
        LocalDate actualDate,
        Double ctl,
        Double atl,
        Double tsb,
        boolean available
) {
    public static TrainingLoadPointDto from(TrainingLoadPoint point) {
        return new TrainingLoadPointDto(
                point.requestedDate(),
                point.actualDate(),
                point.ctl(),
                point.atl(),
                point.tsb(),
                point.available()
        );
    }
}
