package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.BestEffort;
import com.zensyra.collector.query.port.BestEffortQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteBestEffortsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    BestEffortQueryPort bestEffortQueryPort;

    @Test
    void shouldReturnBestEfforts() {
        UUID activityId = UUID.randomUUID();
        BestEffort effort = new BestEffort(activityId, "10k", 10000, 2400, 1);
        when(bestEffortQueryPort.listTopByAthlete(eq(ATHLETE_ID), anyInt())).thenReturn(List.of(effort));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("limit", 5)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("limit", is(5))
                .body("items", hasSize(1))
                .body("items[0].activityId", is(activityId.toString()))
                .body("items[0].name", is("10k"))
                .body("items[0].distance", is(10000))
                .body("items[0].prRank", is(1));
    }

    @Test
    void shouldNotExposeIsKomOrAnyStravaSpecificField() {
        BestEffort effort = new BestEffort(UUID.randomUUID(), "10k", 10000, 2400, 1);
        when(bestEffortQueryPort.listTopByAthlete(eq(ATHLETE_ID), anyInt())).thenReturn(List.of(effort));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("isKom")))
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("activityStravaId")));
    }

    @Test
    void shouldReturn400WhenLimitOutOfRange() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("limit", 99)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn401WithoutKey() {
        given()
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturnEmptyListWhenNoEfforts() {
        when(bestEffortQueryPort.listTopByAthlete(any(), anyInt())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }
}
