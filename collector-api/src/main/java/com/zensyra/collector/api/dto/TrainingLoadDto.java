package com.zensyra.collector.api.dto;

import com.zensyra.collector.query.model.TrainingLoad;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;

@RegisterForReflection
public record TrainingLoadDto(
        LocalDate date,
        Double tssDay,
        Double ctl,
        Double atl,
        Double tsb
) {
    public static TrainingLoadDto from(TrainingLoad load) {
        return new TrainingLoadDto(load.date(), load.tssDay(), load.ctl(), load.atl(), load.tsb());
    }
}
