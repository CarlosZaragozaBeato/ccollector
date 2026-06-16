package com.zensyra.collector.core.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness check that verifies database connectivity.
 */
@Readiness
@ApplicationScoped
public final class DatabaseHealthCheck implements HealthCheck {

    /** Name reported in the health response. */
    private static final String CHECK_NAME = "database";

    @Inject
    EntityManager entityManager;

    @Override
    public HealthCheckResponse call() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return HealthCheckResponse
                .named(CHECK_NAME)
                .up()
                .build();
        } catch (Exception e) {
            return HealthCheckResponse
                .named(CHECK_NAME)
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
