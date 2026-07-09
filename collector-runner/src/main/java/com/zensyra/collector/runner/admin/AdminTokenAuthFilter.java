package com.zensyra.collector.runner.admin;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

@Provider
@AdminTokenAuth
@Priority(Priorities.AUTHENTICATION)
public class AdminTokenAuthFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "admin.token")
    Optional<String> adminToken;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (adminToken.isEmpty() || adminToken.get().isBlank()) {
            ctx.abortWith(Response.status(503)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", "admin endpoint not configured"))
                    .build());
            return;
        }
        String token = ctx.getHeaderString("X-Admin-Token");
        if (token == null || !MessageDigest.isEqual(
                adminToken.get().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            ctx.abortWith(Response.status(401)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", "unauthorized"))
                    .build());
        }
    }
}
