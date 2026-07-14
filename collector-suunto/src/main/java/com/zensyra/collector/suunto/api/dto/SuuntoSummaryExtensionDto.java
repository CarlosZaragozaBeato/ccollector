package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Field names taken verbatim from a real /v2/workouts payload (2026-07-14),
 * superset of the official APIM sample. Temperatures are Kelvin, times are
 * seconds. {@code feeling} is Suunto's 1–5 self-assessment scale.
 *
 * <p>Fields that were null in the real sample are boxed and nullable; the
 * ones whose shape is unconfirmed because only null has ever been observed
 * ({@code workoutType}, {@code weather}, {@code tags}, {@code additionalGears},
 * {@code exerciseId}, {@code teamSportId}) are kept loosely typed as
 * {@link Object} — do not narrow them on a guess.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoSummaryExtensionDto(
        String type,
        Double avgSpeed,
        Double avgPower,
        Double maxPower,
        Double avgVerticalOscillation,
        Double avgStrideLength,
        Double avgGroundContactTime,
        Double avgCadence,
        Double maxCadence,
        Double ascent,
        Double descent,
        Double ascentTime,
        Double descentTime,
        Double pte,
        Double peakEpoc,
        Double performanceLevel,
        Double recoveryTime,
        Object weather,
        Double minTemperature,
        Double avgTemperature,
        Double maxTemperature,
        Object workoutType,
        Integer feeling,
        Object tags,
        SuuntoGearDto gear,
        Object additionalGears,
        Object exerciseId,
        List<SuuntoAppDto> apps,
        Integer repetitionCount,
        Double lacticThHr,
        Double avgAscentSpeed,
        Double maxAscentSpeed,
        Double avgDescentSpeed,
        Double maxDescentSpeed,
        Double avgDistancePerStroke,
        Double fatConsumption,
        Double carbohydrateConsumption,
        Double avgLeftGroundContactBalance,
        Double avgRightGroundContactBalance,
        Double lacticThPace,
        Double avgFlightTime,
        Double avgContactTimeRatio,
        Object teamSportId,
        SuuntoHeartRateRecoveryDto heartRateRecovery,
        Double finalEndurance,
        Double minimumEndurance,
        Double curEnduranceDistance,
        Double minEnduranceDistance,
        Boolean enduranceValid
) implements SuuntoWorkoutExtensionDto {}
