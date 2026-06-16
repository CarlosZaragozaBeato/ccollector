package com.zensyra.collector.api.resource;

import com.zensyra.collector.strava.trainingload.AthleteTrainingLoad;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteTrainingLoadResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final Long ATHLETE_ID = 12345L;

    @InjectMock
    AthleteTrainingLoadRepository trainingLoadRepository;

    @InjectMock
    @RestClient
    com.zensyra.collector.strava.api.StravaApiClient stravaApiClient;

    @Test
    void shouldReturnTrainingLoadItems() {
        AthleteTrainingLoad r = buildRecord(LocalDate.now(), 45.0, 52.1, 48.3, 3.8);
        when(trainingLoadRepository.findRecentByAthleteId(anyLong(), any()))
                .thenReturn(List.of(r));

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
        when(trainingLoadRepository.findRecentByAthleteId(anyLong(), any()))
                .thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    private AthleteTrainingLoad buildRecord(LocalDate date, double tss, double ctl, double atl, double tsb) {
        AthleteTrainingLoad r = new AthleteTrainingLoad();
        r.setAthleteId(ATHLETE_ID);
        r.setDate(date);
        r.setTssDay(tss);
        r.setCtl(ctl);
        r.setAtl(atl);
        r.setTsb(tsb);
        return r;
    }
}
