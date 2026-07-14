package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root-level heart-rate summary. The real payload carries the same values
 * under two namings ({@code workoutMaxHR}/{@code hrmax} both = workout max,
 * {@code workoutAvgHR}/{@code avg} both = workout avg, {@code userMaxHR}/
 * {@code max} both = the athlete's configured max HR); all six are kept
 * faithful — picking a canonical one is issue #6's job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoHrDataDto(
        Integer workoutMaxHR,
        Integer workoutAvgHR,
        Integer userMaxHR,
        Integer hrmax,
        Integer avg,
        Integer max
) {}
