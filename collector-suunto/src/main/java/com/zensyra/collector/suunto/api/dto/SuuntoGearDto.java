package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recording device metadata inside SummaryExtension (e.g. the watch).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoGearDto(
        String manufacturer,
        String name,
        String displayName,
        String serialNumber,
        String softwareVersion,
        String hardwareVersion,
        String productType
) {}
