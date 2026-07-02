package com.zensyra.collector.api.resource;

import com.zensyra.collector.journal.service.TrainingDayService;
import com.zensyra.collector.query.model.TrainingDaySummary;
import com.zensyra.collector.query.port.TrainingDayQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
class TrainingDayResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @InjectMock
    TrainingDayQueryPort trainingDayQueryPort;

    @InjectMock
    TrainingDayService trainingDayService;

    @Test
    void shouldReturnTrainingDaysForDateRange() {
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        TrainingDaySummary summary = new TrainingDaySummary(
                LocalDate.of(2025, 6, 1), 7, "GOOD", "Felt strong", 70.5, now, now);
        when(trainingDayQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any()))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-01")
                .queryParam("to", "2025-06-30")
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].date", is("2025-06-01"))
                .body("[0].perceivedEffort", is(7))
                .body("[0].subjectiveState", is("GOOD"))
                .body("[0].notes", is("Felt strong"))
                .body("[0].weightKg", is(70.5f));
    }

    @Test
    void shouldReturnEmptyListWhenNoData() {
        when(trainingDayQueryPort.findByAthlete(any(), any(), any())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "not-a-date")
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400WhenFromIsAfterTo() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-30")
                .queryParam("to", "2025-06-01")
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn201OnCreate() {
        Instant now = Instant.parse("2025-06-15T08:00:00Z");
        TrainingDaySummary summary = new TrainingDaySummary(
                LocalDate.of(2025, 6, 15), 8, "EXCELLENT", "Best run of the year", 69.8, now, now);
        when(trainingDayService.upsert(
                eq(ATHLETE_ID),
                eq(LocalDate.of(2025, 6, 15)),
                eq(8),
                eq("EXCELLENT"),
                eq("Best run of the year"),
                eq(69.8)))
                .thenReturn(summary);

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "date": "2025-06-15",
                          "perceivedEffort": 8,
                          "subjectiveState": "EXCELLENT",
                          "notes": "Best run of the year",
                          "weightKg": 69.8
                        }
                        """)
                .when().post("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(201)
                .body("date", is("2025-06-15"))
                .body("perceivedEffort", is(8))
                .body("subjectiveState", is("EXCELLENT"))
                .body("weightKg", is(69.8f));
    }

    @Test
    void shouldReturn400ForMissingDate() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "perceivedEffort": 5
                        }
                        """)
                .when().post("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400ForInvalidRpe() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "date": "2025-06-15",
                          "perceivedEffort": 11
                        }
                        """)
                .when().post("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400ForInvalidSubjectiveState() {
        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "date": "2025-06-15",
                          "subjectiveState": "AMAZING"
                        }
                        """)
                .when().post("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldNotExposeAthleteId() {
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        TrainingDaySummary summary = new TrainingDaySummary(
                LocalDate.of(2025, 6, 1), 6, "NEUTRAL", null, null, now, now);
        when(trainingDayQueryPort.findByAthlete(any(), any(), any())).thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("[0]", not(hasKey("athleteId")));
    }
}
