package com.zensyra.collector.runner.health;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Corre contra el binario nativo ya compilado — solo se ejecuta con -Pnative (mvn verify).
 * Ignorado completamente en mvn test (JVM mode).
 *
 * Verifica que el binario nativo arranca y responde correctamente a los health checks.
 */
@QuarkusIntegrationTest
class NativeHealthCheckIT {

    @Test
    void shouldBeAlive() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }

    @Test
    void shouldBeReady() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("checks.find { it.name == 'database' }.status", is("UP"))
            .body("checks.find { it.name == 'job-registry' }.status", is("UP"));
    }

    @Test
    void shouldExposeMetrics() {
        given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("strava_rate_limiter_available_permits"));
    }
}
