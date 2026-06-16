package com.zensyra.collector.api.dto;

import com.zensyra.collector.strava.trainingload.AthleteTrainingLoad;
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
    public static TrainingLoadDto from(AthleteTrainingLoad r) {
        return new TrainingLoadDto(r.getDate(), r.getTssDay(), r.getCtl(), r.getAtl(), r.getTsb());
    }
}
