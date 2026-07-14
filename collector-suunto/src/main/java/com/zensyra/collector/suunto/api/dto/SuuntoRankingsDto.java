package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Route rankings attached to a workout. Only {@code totalTimeOnRouteRanking}
 * has been observed in real payloads; other ranking kinds Suunto may add are
 * tolerated and ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoRankingsDto(SuuntoRankingDto totalTimeOnRouteRanking) {}
