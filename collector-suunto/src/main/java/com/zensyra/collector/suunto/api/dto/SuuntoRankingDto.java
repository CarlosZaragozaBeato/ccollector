package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single ranking entry inside {@link SuuntoRankingsDto}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoRankingDto(Integer originalRanking, Integer originalNumberOfWorkouts) {}
