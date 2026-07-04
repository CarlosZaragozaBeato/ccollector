package com.zensyra.collector.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * A malformed (non-UUID) {@code athleteId} path parameter must return a clean
 * 400 (audit-001 finding #10), not the framework's default bare 404. This path
 * fails at parameter conversion — before any DB access — so it runs on H2.
 */
@QuarkusTest
class MalformedUuidPathTest {

    private static final String API_KEY = "test-api-key";

    @Test
    void malformedUuid_onGet_returns400() {
        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/not-a-uuid/health-events")
                .then()
                .statusCode(400)
                .body("error", is("Malformed path parameter: expected a UUID"));
    }

    @Test
    void malformedUuid_onPost_returns400() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"raceDate\":\"2025-06-01\",\"raceName\":\"x\",\"distanceMeters\":1000.0}")
                .when().post("/api/v1/athletes/not-a-uuid/race-results")
                .then()
                .statusCode(400)
                .body("error", is("Malformed path parameter: expected a UUID"));
    }
}
