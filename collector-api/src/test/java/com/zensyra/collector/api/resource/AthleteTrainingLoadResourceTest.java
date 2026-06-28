package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteTrainingLoadResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    TrainingLoadQueryPort trainingLoadQueryPort;

    @Test
    void shouldReturnTrainingLoadItems() {
        TrainingLoad load = new TrainingLoad(ATHLETE_ID, LocalDate.now(), 45.0, 52.1, 48.3, 3.8);
        when(trainingLoadQueryPort.listRecentByAthlete(eq(ATHLETE_ID), any())).thenReturn(List.of(load));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("days", is(30))
                .body("items.size()", is(1))
                .body("items[0].ctl", is(52.1f))
                .body("items[0].atl", is(48.3f))
                .body("items[0].tsb", is(3.8f));
    }

    @Test
    void shouldNotExposeAnyStravaSpecificField() {
        TrainingLoad load = new TrainingLoad(ATHLETE_ID, LocalDate.now(), 45.0, 52.1, 48.3, 3.8);
        when(trainingLoadQueryPort.listRecentByAthlete(eq(ATHLETE_ID), any())).thenReturn(List.of(load));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("athleteId")));
    }

    @Test
    void shouldReturn401WithoutKey() {
        given()
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturn400WhenDaysOutOfRange() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("days", 120)
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturnEmptyListWhenNoData() {
        when(trainingLoadQueryPort.listRecentByAthlete(any(), any())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }
}
