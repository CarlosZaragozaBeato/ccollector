package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Fields from the official APIM sample for {@code SummaryExtension}.
 * Temperatures are Kelvin, times are seconds. {@code feeling} is Suunto's
 * 1–5 self-assessment scale.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoSummaryExtensionDto(
        String type,
        Double pte,
        Integer feeling,
        Double avgTemperature,
        Double maxTemperature,
        Double peakEpoc,
        Double avgPower,
        Double maxPower,
        Double avgCadence,
        Double maxCadence,
        Double ascentTime,
        Double descentTime,
        Double performanceLevel
) implements SuuntoWorkoutExtensionDto {}
