package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.RacePerformanceContext;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for one race together with its preceding training-load context —
 * CTL/ATL/TSB on race day and at the two standard PMC look-back points (7 and 42
 * days before). Reuses {@link RaceResultDto} for the race so a race is shaped
 * identically here and on the {@code /race-results} endpoint.
 */
@RegisterForReflection
public record RacePerformanceDto(
        RaceResultDto race,
        TrainingLoadPointDto atRaceDate,
        TrainingLoadPointDto at7DaysBefore,
        TrainingLoadPointDto at42DaysBefore
) {
    public static RacePerformanceDto from(RacePerformanceContext context) {
        return new RacePerformanceDto(
                RaceResultDto.from(context.race()),
                TrainingLoadPointDto.from(context.atRaceDate()),
                TrainingLoadPointDto.from(context.at7DaysBefore()),
                TrainingLoadPointDto.from(context.at42DaysBefore())
        );
    }
}
