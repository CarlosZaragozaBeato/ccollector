package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Fallback for extension types without a dedicated DTO — retains only the
 * discriminator so callers can log/skip it. An unknown extension type in a
 * payload must never fail deserialization of the whole workout list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoGenericExtensionDto(String type) implements SuuntoWorkoutExtensionDto {}
