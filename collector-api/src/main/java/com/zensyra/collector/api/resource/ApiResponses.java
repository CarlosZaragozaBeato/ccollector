package com.zensyra.collector.api.resource;

import jakarta.ws.rs.core.Response;

import java.util.Map;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static Response error(Response.Status status, String message) {
        return Response.status(status)
                .entity(Map.of("error", message))
                .build();
    }
}
