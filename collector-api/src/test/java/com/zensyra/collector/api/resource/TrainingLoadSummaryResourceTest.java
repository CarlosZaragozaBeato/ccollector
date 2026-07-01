package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.TrainingLoadSummary;
import com.zensyra.collector.query.port.TrainingLoadSummaryQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class TrainingLoadSummaryResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    TrainingLoadSummaryQueryPort trainingLoadSummaryQueryPort;

    @Test
    void shouldReturnWeeklySummaryItems() {
        TrainingLoadSummary summary = new TrainingLoadSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 6), Granularity.WEEKLY,
                140.0, 56.0, 45.0, 11.0);
        when(trainingLoadSummaryQueryPort.listByAthlete(eq(ATHLETE_ID), any(), any(), eq(Granularity.WEEKLY)))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "weekly")
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("granularity", is("WEEKLY"))
                .body("items.size()", is(1))
                .body("items[0].granularity", is("WEEKLY"))
                .body("items[0].totalTss", is(140.0f))
                .body("items[0].ctlEnd", is(56.0f))
                .body("items[0].atlEnd", is(45.0f))
                .body("items[0].tsbEnd", is(11.0f));
    }

    @Test
    void shouldReturnMonthlySummaryItems() {
        TrainingLoadSummary summary = new TrainingLoadSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 1), Granularity.MONTHLY,
                480.0, 62.0, 48.0, 14.0);
        when(trainingLoadSummaryQueryPort.listByAthlete(eq(ATHLETE_ID), any(), any(), eq(Granularity.MONTHLY)))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "monthly")
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("granularity", is("MONTHLY"))
                .body("items[0].granularity", is("MONTHLY"))
                .body("items[0].totalTss", is(480.0f));
    }

    @Test
    void shouldReturn400ForInvalidGranularity() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "quarterly")
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400WhenFromIsAfterTo() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-01")
                .queryParam("to", "2025-01-01")
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "not-a-date")
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturnEmptyListWhenNoData() {
        when(trainingLoadSummaryQueryPort.listByAthlete(any(), any(), any(), any())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void shouldNotExposeAthleteId() {
        TrainingLoadSummary summary = new TrainingLoadSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 6), Granularity.WEEKLY,
                80.0, 55.0, 40.0, 15.0);
        when(trainingLoadSummaryQueryPort.listByAthlete(any(), any(), any(), any()))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-load/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items[0]", not(hasKey("athleteId")));
    }
}
