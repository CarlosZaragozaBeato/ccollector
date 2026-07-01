package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.PeriodSummary;
import com.zensyra.collector.query.port.ActivitySummaryQueryPort;
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
class ActivitySummaryResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    ActivitySummaryQueryPort activitySummaryQueryPort;

    @Test
    void shouldReturnWeeklySummaryItems() {
        PeriodSummary summary = new PeriodSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 6), Granularity.WEEKLY,
                3, 42000.0, 14400, 350.0);
        when(activitySummaryQueryPort.listByAthlete(eq(ATHLETE_ID), any(), any(), eq(Granularity.WEEKLY)))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "weekly")
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("granularity", is("WEEKLY"))
                .body("items.size()", is(1))
                .body("items[0].numActivities", is(3))
                .body("items[0].totalDistanceMeters", is(42000.0f))
                .body("items[0].totalMovingTimeSecs", is(14400))
                .body("items[0].granularity", is("WEEKLY"));
    }

    @Test
    void shouldReturnMonthlySummaryItems() {
        PeriodSummary summary = new PeriodSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 1), Granularity.MONTHLY,
                12, 180000.0, 54000, 1200.0);
        when(activitySummaryQueryPort.listByAthlete(eq(ATHLETE_ID), any(), any(), eq(Granularity.MONTHLY)))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "monthly")
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("granularity", is("MONTHLY"))
                .body("items[0].granularity", is("MONTHLY"))
                .body("items[0].numActivities", is(12));
    }

    @Test
    void shouldReturn400ForInvalidGranularity() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("granularity", "quarterly")
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400WhenFromIsAfterTo() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-01")
                .queryParam("to", "2025-01-01")
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("from", "not-a-date")
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturnEmptyListWhenNoData() {
        when(activitySummaryQueryPort.listByAthlete(any(), any(), any(), any())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void shouldNotExposeAthleteId() {
        PeriodSummary summary = new PeriodSummary(
                ATHLETE_ID, LocalDate.of(2025, 1, 6), Granularity.WEEKLY,
                1, 10000.0, 3600, 100.0);
        when(activitySummaryQueryPort.listByAthlete(any(), any(), any(), any()))
                .thenReturn(List.of(summary));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities/summary", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("athleteId")));
    }
}
