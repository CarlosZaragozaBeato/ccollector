package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Both values arrive as JSON strings (not numbers) — kept faithful here;
 * parsing them is the caller's concern. {@code until} is the epoch-millisecond
 * cursor to pass as {@code since} on the next incremental fetch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoWorkoutListMetadata(String workoutcount, String until) {}
