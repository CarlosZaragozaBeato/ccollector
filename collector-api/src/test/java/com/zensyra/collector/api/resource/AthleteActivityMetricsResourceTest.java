package com.zensyra.collector.api.resource;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteActivityMetricsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final Long ATHLETE_ID = 12345L;
    private static final Long ACTIVITY_STRAVA_ID = 9001L;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    ActivityMetricsRepository activityMetricsRepository;

    @InjectMock
    @RestClient
    com.zensyra.collector.strava.api.StravaApiClient stravaApiClient;

    @Test
    void shouldReturnActivityMetrics() {
        Activity activity = new Activity();
        activity.setId(77L);
        activity.setAthleteId(ATHLETE_ID);
        activity.setStravaId(ACTIVITY_STRAVA_ID);

        ActivityMetrics metrics = new ActivityMetrics();
        metrics.setActivityId(77L);
        metrics.setNormalizedPower(new BigDecimal("250.50"));
        metrics.setVariabilityIndex(new BigDecimal("1.0500"));
        metrics.setEfficiencyFactor(new BigDecimal("1.6120"));
        metrics.setIntensityFactor(new BigDecimal("0.9100"));

        when(activityRepository.findByAthleteIdAndStravaId(ATHLETE_ID, ACTIVITY_STRAVA_ID))
                .thenReturn(Optional.of(activity));
        when(activityMetricsRepository.findByActivityId(77L)).thenReturn(Optional.of(metrics));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_STRAVA_ID)
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("activityId", is(77))
                .body("activityStravaId", is(9001))
                .body("normalizedPower", is(250.50f))
                .body("variabilityIndex", is(1.05f))
                .body("efficiencyFactor", is(1.612f))
                .body("intensityFactor", is(0.91f));
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
    void shouldReturn404WhenActivityNotFound() {
        when(activityRepository.findByAthleteIdAndStravaId(anyLong(), anyLong())).thenReturn(Optional.empty());

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_STRAVA_ID)
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(404);
    }

    @Test
    void shouldReturn404WhenMetricsMissing() {
        Activity activity = new Activity();
        activity.setId(77L);
        activity.setAthleteId(ATHLETE_ID);
        activity.setStravaId(ACTIVITY_STRAVA_ID);

        when(activityRepository.findByAthleteIdAndStravaId(ATHLETE_ID, ACTIVITY_STRAVA_ID))
                .thenReturn(Optional.of(activity));
        when(activityMetricsRepository.findByActivityId(77L)).thenReturn(Optional.empty());

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("activityId", ACTIVITY_STRAVA_ID)
                .when().get("/api/v1/athletes/{id}/activity-metrics", ATHLETE_ID)
                .then()
                .statusCode(404);
    }
}
