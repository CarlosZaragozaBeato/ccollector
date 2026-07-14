package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Field names taken verbatim from a real /v2/workouts payload (2026-07-14).
 *
 * <p>{@code physiologicalThresholds} and {@code overallIntensity} were null
 * in the real sample, so their shape is unconfirmed — kept loosely typed as
 * {@link Object} until a real non-null value is observed; do not narrow them
 * on a guess.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoIntensityExtensionDto(
        String type,
        SuuntoZonesDto zones,
        Object physiologicalThresholds,
        Object overallIntensity
) implements SuuntoWorkoutExtensionDto {}
