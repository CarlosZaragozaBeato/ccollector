package com.zensyra.collector.api.exception;

import com.zensyra.collector.api.resource.ApiResponses;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Converts {@code UUID} path/query parameters, turning a malformed value into a
 * clean 400 instead of the JAX-RS default bare 404 (audit-001 finding #10).
 *
 * <p>A path-param conversion failure is handled by the framework as a 404 before
 * any {@code ExceptionMapper} sees it, so the fix must live in the converter
 * itself. This provider is global and applies only to {@code UUID} parameters —
 * it does not affect any other 404 behavior.
 */
@Provider
public class UuidParamConverterProvider implements ParamConverterProvider {

    private static final ParamConverter<UUID> UUID_CONVERTER = new ParamConverter<>() {
        @Override
        public UUID fromString(String value) {
            if (value == null) {
                return null;
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(ApiResponses.error(
                        Response.Status.BAD_REQUEST, "Malformed path parameter: expected a UUID"));
            }
        }

        @Override
        public String toString(UUID value) {
            return value == null ? null : value.toString();
        }
    };

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (UUID.class.equals(rawType)) {
            return (ParamConverter<T>) UUID_CONVERTER;
        }
        return null;
    }
}
