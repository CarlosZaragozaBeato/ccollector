package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Training Stress Score entry. Appears twice on a workout: as the single
 * {@code tss} object (Suunto's preferred method for that workout) and inside
 * {@code tssList}, one entry per calculation method. Observed
 * {@code calculationMethod} values: HR, POWER, PACE, MET.
 *
 * <p>{@code intensityFactor}, {@code normalizedPower} and
 * {@code averageGradeAdjustedPace} are null for methods that don't use them
 * (e.g. MET has neither, HR has no intensityFactor).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoTssDto(
        String calculationMethod,
        Double trainingStressScore,
        Double intensityFactor,
        Double normalizedPower,
        Double averageGradeAdjustedPace
) {}
