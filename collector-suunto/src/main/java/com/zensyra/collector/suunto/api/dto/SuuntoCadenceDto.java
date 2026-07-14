package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root-level cadence summary in integer steps-per-something units (real
 * sample: max=84, avg=82). Deliberately distinct from SummaryExtension's
 * {@code avgCadence}/{@code maxCadence}, which are small doubles (1.363/1.4)
 * in different units — do not conflate the two shapes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoCadenceDto(Integer max, Integer avg) {}
