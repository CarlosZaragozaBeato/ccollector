package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.PreRaceSubjectiveState;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for the pre-race subjective-state aggregate. Mirrors
 * {@link PreRaceSubjectiveState} field-for-field; {@code available} makes a data
 * gap explicit (when {@code false}, {@code entryCount} is 0 and the metrics are
 * {@code null} rather than a substituted "neutral"/zero).
 */
@RegisterForReflection
public record PreRaceSubjectiveStateDto(
        int entryCount,
        Double averagePerceivedEffort,
        String dominantSubjectiveState,
        boolean available
) {
    public static PreRaceSubjectiveStateDto from(PreRaceSubjectiveState state) {
        return new PreRaceSubjectiveStateDto(
                state.entryCount(),
                state.averagePerceivedEffort(),
                state.dominantSubjectiveState(),
                state.available()
        );
    }
}
