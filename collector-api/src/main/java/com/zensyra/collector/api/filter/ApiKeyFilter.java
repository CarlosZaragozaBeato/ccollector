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
 * Valida el header X-API-Key en todas las rutas /api/v1/*.
 * El token se configura vía la variable de entorno COLLECTOR_API_KEY.
 *
 * @ApplicationScoped es necesario en el binario nativo: sin CDI scope explícito,
 * Quarkus instancia el @Provider durante static-init (build time) e inyecta la
 * config con el valor vacío de build. Al arrancar con la env var real detecta
 * la discrepancia y falla. Con @ApplicationScoped la instanciación es lazy
 * (runtime), igual que AdminTriggerResource.
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
        if (!path.startsWith("/api/v1") && !path.startsWith("api/v1")) {
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
