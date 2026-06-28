package com.zensyra.collector.api.v2.resource;

import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * {@code /v2}'s own error-response helper. Not a reuse of
 * {@code com.zensyra.collector.api.resource.ApiResponses} — that class is
 * package-private to {@code /v1}'s resource package and stays that way
 * rather than being widened just for this. Duplicating five lines here is
 * cheaper than loosening an encapsulation boundary that was deliberate.
 */
final class ApiResponsesV2 {

    private ApiResponsesV2() {
    }

    static Response error(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("error", message)).build();
    }
}
