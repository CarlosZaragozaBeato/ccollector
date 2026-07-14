package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One intensity zone: seconds spent in the zone and its lower boundary
 * (bpm for heartRate, m/s for speed, watts for power).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoZoneDto(Double totalTime, Double lowerLimit) {}
