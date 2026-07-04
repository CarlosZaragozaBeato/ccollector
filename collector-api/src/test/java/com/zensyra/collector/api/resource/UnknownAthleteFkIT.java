package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.support.PostgresFkTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end proof that a POST referencing an unknown {@code athleteId} returns
 * a clean 404 (not a raw 500) on all three journal write endpoints. Requires a
 * real Postgres FK, which H2 cannot enforce — so this runs against Testcontainers
 * via {@link PostgresFkTestProfile} (build-time Postgres + journal changelog).
 * The profile is scoped to this class, so every other api test keeps using H2.
 */
@QuarkusTest
@TestProfile(PostgresFkTestProfile.class)
class UnknownAthleteFkIT {

    private static final String API_KEY = "test-api-key";

    private String unknownAthlete() {
        // A syntactically valid UUID that does not exist in athlete_profiles.
        return UUID.randomUUID().toString();
    }

    @Test
    void trainingDays_unknownAthlete_returns404() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"date\":\"2025-06-01\",\"perceivedEffort\":5}")
                .when().post("/api/v1/athletes/{id}/training-days", unknownAthlete())
                .then()
                .statusCode(404)
                .body("error", is("Referenced athlete not found"));
    }

    @Test
    void healthEvents_unknownAthlete_returns404() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"Flu\"}")
                .when().post("/api/v1/athletes/{id}/health-events", unknownAthlete())
                .then()
                .statusCode(404)
                .body("error", is("Referenced athlete not found"));
    }

    @Test
    void raceResults_unknownAthlete_returns404() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"raceDate\":\"2025-06-01\",\"raceName\":\"Valencia\",\"distanceMeters\":42195.0}")
                .when().post("/api/v1/athletes/{id}/race-results", unknownAthlete())
                .then()
                .statusCode(404)
                .body("error", is("Referenced athlete not found"));
    }
}
