package com.zensyra.collector.runner.resource;

import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.strava.identity.StravaActivityMetricsQueryPort;
import com.zensyra.collector.suunto.identity.SuuntoActivityMetricsQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the try-all-stop-on-first-hit fix in
 * {@code AthleteActivityMetricsResource} with BOTH ports actually registered
 * in CDI — the only context where the fix is observable.
 *
 * <p>Kept in {@code collector-runner} (not {@code collector-api}) because
 * the concrete port classes are only on the classpath here; ADR-001 forbids
 * {@code collector-api} from depending on {@code collector-strava} or
 * {@code collector-suunto}.
 */
@QuarkusTest
class AthleteActivityMetricsMultiPortTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    @InjectMock
    StravaActivityMetricsQueryPort stravaPort;

    @InjectMock
    SuuntoActivityMetricsQueryPort suuntoPort;

    @BeforeEach
    void defaultEmptyAnswers() {
        // Mockito returns null by default for Optional-typed methods; empty is
        // the correct default here so the resource loop doesn't NPE.
        when(stravaPort.getByActivityId(any(), any())).thenReturn(Optional.empty());
        when(suuntoPort.getByActivityId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void suuntoOnlyActivityIsFoundEvenWhenStravaPortResolvesFirst() {
        // Strava has no metrics for this activity
        when(stravaPort.getByActivityId(ATHLETE_ID, ACTIVITY_ID)).thenReturn(Optional.empty());

        // Suunto has the metrics
        ActivityMetrics metrics = new ActivityMetrics(
                ACTIVITY_ID,
                new BigDecimal("280.00"),
                new BigDecimal("1.0500"),
                new BigDecimal("1.6200"),
                new BigDecimal("0.9200"));
        when(suuntoPort.getByActivityId(ATHLETE_ID, ACTIVITY_ID)).thenReturn(Optional.of(metrics));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("activityId", is(ACTIVITY_ID.toString()));
    }

    @Test
    void returns404WhenNeitherPortHasMetrics() {
        // both already return empty from @BeforeEach
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(404);
    }

    @Test
    void stravaActivityIsFoundWhenStravaPortHitsFirst() {
        ActivityMetrics metrics = new ActivityMetrics(
                ACTIVITY_ID,
                new BigDecimal("250.00"),
                new BigDecimal("1.0200"),
                new BigDecimal("1.5900"),
                new BigDecimal("0.8800"));
        when(stravaPort.getByActivityId(ATHLETE_ID, ACTIVITY_ID)).thenReturn(Optional.of(metrics));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_ID.toString())
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("activityId", is(ACTIVITY_ID.toString()));
    }
}
