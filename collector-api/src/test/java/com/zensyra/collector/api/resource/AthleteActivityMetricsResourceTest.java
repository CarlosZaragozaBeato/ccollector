package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteActivityMetricsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    @InjectMock
    ActivityMetricsQueryPort activityMetricsQueryPort;

    @Test
    void shouldReturnActivityMetrics() {
        ActivityMetrics metrics = new ActivityMetrics(
                ACTIVITY_ID,
                new BigDecimal("250.50"),
                new BigDecimal("1.0500"),
                new BigDecimal("1.6120"),
                new BigDecimal("0.9100")
        );
        when(activityMetricsQueryPort.getByActivityId(eq(ATHLETE_ID), eq(ACTIVITY_ID)))
                .thenReturn(Optional.of(metrics));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("activityId", is(ACTIVITY_ID.toString()))
                .body("normalizedPower", is(250.50f))
                .body("variabilityIndex", is(1.05f))
                .body("efficiencyFactor", is(1.612f))
                .body("intensityFactor", is(0.91f));
    }

    @Test
    void shouldNotExposeAnyStravaSpecificField() {
        ActivityMetrics metrics = new ActivityMetrics(
                ACTIVITY_ID, new BigDecimal("250.50"), new BigDecimal("1.05"),
                new BigDecimal("1.61"), new BigDecimal("0.91"));
        when(activityMetricsQueryPort.getByActivityId(eq(ATHLETE_ID), eq(ACTIVITY_ID)))
                .thenReturn(Optional.of(metrics));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("activityStravaId")));
    }

    @Test
    void shouldReturn400WhenActivityIdMissing() {
        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn404WhenActivityDoesNotBelongToAthlete() {
        // The port itself is responsible for ownership — a query for an
        // activity that exists but belongs to a different athlete must
        // come back empty, identically to one that does not exist at all.
        when(activityMetricsQueryPort.getByActivityId(eq(ATHLETE_ID), eq(ACTIVITY_ID)))
                .thenReturn(Optional.empty());

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(404);
    }

    @Test
    void shouldReturn404WhenMetricsMissing() {
        when(activityMetricsQueryPort.getByActivityId(any(), any())).thenReturn(Optional.empty());

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(404);
    }
}
