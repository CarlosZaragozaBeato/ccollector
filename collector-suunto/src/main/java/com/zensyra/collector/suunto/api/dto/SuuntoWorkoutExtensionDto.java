package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic base for the {@code extensions} array, discriminated by the
 * {@code type} field. The official schema types this array as opaque
 * ({@code [{}]}), so Suunto can introduce new extension types without notice:
 * anything not explicitly registered below deserializes to
 * {@link SuuntoGenericExtensionDto} instead of breaking the sync.
 *
 * <p>FitnessExtension, IntensityExtension and WeatherExtension subtypes are
 * pending — their field names are undocumented and will be added verbatim
 * from a real sanitized response, never guessed.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = SuuntoGenericExtensionDto.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SuuntoSummaryExtensionDto.class, name = "SummaryExtension")
})
public interface SuuntoWorkoutExtensionDto {

    String type();
}
