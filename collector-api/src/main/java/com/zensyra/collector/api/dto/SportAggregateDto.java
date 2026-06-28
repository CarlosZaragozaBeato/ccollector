package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.SportAggregate;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SportAggregateDto(
        String sportType,
        String window,
        Integer activityCount,
        Double distanceMeters,
        Integer movingTimeSecs,
        Double elevationGainMeters
) {
    public static SportAggregateDto from(SportAggregate aggregate) {
        return new SportAggregateDto(
                aggregate.sportType(),
                aggregate.window().name(),
                aggregate.activityCount(),
                aggregate.distanceMeters(),
                aggregate.movingTimeSecs(),
                aggregate.elevationGainMeters()
        );
    }
}
