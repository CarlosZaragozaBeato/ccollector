package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Geographic position as returned by the real API: {@code x} is longitude,
 * {@code y} is latitude (confirmed against a real workout recorded in Spain:
 * x=-3.34, y=39.62). Used for startPosition/stopPosition/centerPosition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoPositionDto(Double x, Double y) {}
