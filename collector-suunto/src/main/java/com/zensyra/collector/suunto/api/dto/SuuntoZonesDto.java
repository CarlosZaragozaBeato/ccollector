package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The three intensity-zone dimensions inside IntensityExtension.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoZonesDto(
        SuuntoZoneGroupDto heartRate,
        SuuntoZoneGroupDto speed,
        SuuntoZoneGroupDto power
) {}
