package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Post-exercise heart-rate recovery inside SummaryExtension. {@code drop} is
 * the bpm delta (negative = HR fell), real sample: comparisonLevel="Normal",
 * drop=-27, level="Low".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoHeartRateRecoveryDto(
        String comparisonLevel,
        Integer drop,
        String level
) {}
