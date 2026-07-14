package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Field names taken verbatim from a real /v2/workouts payload (2026-07-14).
 * {@code temperature} is Kelvin (295.37 ≈ 22°C), {@code weatherIcon} uses
 * OpenWeatherMap icon codes (e.g. "01d"), {@code windDirection} degrees.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoWeatherExtensionDto(
        String type,
        String weatherIcon,
        Double temperature,
        Double windSpeed,
        Double windDirection,
        Integer humidity
) implements SuuntoWorkoutExtensionDto {}
