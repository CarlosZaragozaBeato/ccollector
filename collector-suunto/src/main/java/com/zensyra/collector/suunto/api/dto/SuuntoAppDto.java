package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A SuuntoPlus app that ran during the workout, with its summary outputs.
 * Output schemas vary per app type — modeled generically, never per-app.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoAppDto(
        String name,
        String id,
        List<SuuntoAppSummaryOutputDto> summaryOutputs
) {}
