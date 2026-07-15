package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One summary value produced by a SuuntoPlus app. Kept generic: each app
 * defines its own ids/formats, so no per-app semantics are modeled here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoAppSummaryOutputDto(
        String format,
        String id,
        String name,
        String postfix,
        Double summaryValue
) {}
