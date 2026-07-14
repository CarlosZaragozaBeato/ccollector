package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Faithful, source-specific representation of one workout as returned by
 * {@code GET /v2/workouts}. Only fields confirmed against the real API (live
 * responses + the official APIM schema) are mapped; everything else is
 * tolerated and ignored. Mapping to CCollector's neutral models is issue #6.
 *
 * <p>{@code startTime}/{@code stopTime} are epoch milliseconds. The
 * {@code tss} object is deliberately NOT mapped yet: its field names are
 * undocumented and will be taken verbatim from a real sanitized response
 * before being added — never guessed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoWorkoutDto(
        Long workoutId,
        Integer activityId,
        Long startTime,
        Long stopTime,
        Double totalTime,
        Double totalDistance,
        Double totalAscent,
        Double totalDescent,
        Double maxSpeed,
        String workoutKey,
        List<SuuntoWorkoutExtensionDto> extensions
) {}
