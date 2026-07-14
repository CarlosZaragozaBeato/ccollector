package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope of every Suunto Cloud API list response: the workouts are wrapped
 * in {@code payload}, never returned as a raw array. {@code metadata.until}
 * is the cursor for incremental fetching.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoWorkoutListResponse(
        SuuntoErrorDto error,
        List<SuuntoWorkoutDto> payload,
        SuuntoWorkoutListMetadata metadata
) {}
