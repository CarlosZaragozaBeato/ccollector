package com.zensyra.collector.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Regression test for the silent create-data-loss bug in TrainingDayService,
 * discovered by the FK integration test while verifying #36.
 *
 * <p>The service derived create-vs-update from {@code td.getId() == null}, but
 * the entity id is pre-initialized ({@code UUID.randomUUID()}) so it is never
 * null — {@code persist()} was skipped and a POST for a brand-new
 * {@code (athleteId, date)} returned 201 without writing anything. Before the
 * fix, the GET below returned {@code []} (red); after deriving {@code isNew}
 * from the lookup, the row is persisted and the GET returns it (green). Uses
 * the real service + query port against H2 (no mocks) so the round-trip is real.
 */
@QuarkusTest
class TrainingDayPersistenceTest {

    private static final String API_KEY = "test-api-key";

    @Test
    void postForNewAthleteDate_actuallyPersistsTheRow() {
        UUID athlete = UUID.randomUUID();

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"date\":\"2025-06-15\",\"perceivedEffort\":6,\"notes\":\"regression\"}")
                .when().post("/api/v1/athletes/{id}/training-days", athlete)
                .then()
                .statusCode(201);

        // The row must actually exist — before the isNew fix this returned [].
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-01")
                .queryParam("to", "2025-06-30")
                .when().get("/api/v1/athletes/{id}/training-days", athlete)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].date", is("2025-06-15"))
                .body("[0].perceivedEffort", is(6))
                .body("[0].notes", is("regression"));
    }

    @Test
    void secondPostForSameAthleteDate_updatesInPlace_noDuplicate() {
        UUID athlete = UUID.randomUUID();
        String post = "{\"date\":\"2025-07-01\",\"perceivedEffort\":%d}";

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body(String.format(post, 4))
                .when().post("/api/v1/athletes/{id}/training-days", athlete)
                .then().statusCode(201);

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body(String.format(post, 9))
                .when().post("/api/v1/athletes/{id}/training-days", athlete)
                .then().statusCode(201);

        // Still one row (upsert), with the updated value.
        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-07-01").queryParam("to", "2025-07-01")
                .when().get("/api/v1/athletes/{id}/training-days", athlete)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].perceivedEffort", is(9));
    }
}
