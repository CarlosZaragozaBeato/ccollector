package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Field names taken verbatim from a real /v2/workouts payload (2026-07-14).
 * {@code vo2Max} arrives as an integer, {@code estimatedVo2Max} as a double —
 * both shapes kept faithful.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoFitnessExtensionDto(
        String type,
        Integer maxHeartRate,
        Integer vo2Max,
        Double estimatedVo2Max,
        Integer fitnessAge
) implements SuuntoWorkoutExtensionDto {}
