package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.AthleteStats;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RegisterForReflection
public record AthleteStatsDto(
        UUID athleteId,
        LocalDate snapshotDate,
        List<SportAggregateDto> aggregates
) {
    public static AthleteStatsDto from(AthleteStats stats) {
        return new AthleteStatsDto(
                stats.athleteId(),
                stats.snapshotDate(),
                stats.aggregates().stream().map(SportAggregateDto::from).toList()
        );
    }
}
