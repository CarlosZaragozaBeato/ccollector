package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The five zones of one intensity dimension (heartRate/speed/power).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoZoneGroupDto(
        SuuntoZoneDto zone1,
        SuuntoZoneDto zone2,
        SuuntoZoneDto zone3,
        SuuntoZoneDto zone4,
        SuuntoZoneDto zone5
) {}
