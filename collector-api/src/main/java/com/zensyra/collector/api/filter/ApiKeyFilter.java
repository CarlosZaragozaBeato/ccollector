package com.zensyra.collector.api.filter;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Validates the X-API-Key header on every /api/v* route.
 * The token is configured through the COLLECTOR_API_KEY environment variable.
 *
 * @ApplicationScoped is required in the native binary: without an explicit CDI
 * scope, Quarkus creates the @Provider during static initialization (build time)
 * and injects the empty build-time configuration value. At startup, the real
 * environment value differs and the application fails. @ApplicationScoped makes
 * instantiation lazy (runtime), as it is for AdminTriggerResource.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @ConfigProperty(name = "collector.api.key")
    Optional<String> apiKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("/api/v") && !path.startsWith("api/v")) {
            return;
        }

        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            ctx.abortWith(Response.status(503)
                    .entity("{\"error\":\"API key not configured\"}")
                    .build());
            return;
        }

        String provided = ctx.getHeaderString(API_KEY_HEADER);
        if (provided == null || !MessageDigest.isEqual(
                apiKey.get().getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            ctx.abortWith(Response.status(401)
                    .entity("{\"error\":\"unauthorized\"}")
                    .build());
        }
    }
}
