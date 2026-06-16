package com.zensyra.collector.api.resource;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteActivitiesResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final Long ATHLETE_ID = 12345L;

    @InjectMock
    ActivityRepository activityRepository;

    @InjectMock
    @RestClient
    com.zensyra.collector.strava.api.StravaApiClient stravaApiClient;

    @Test
    void shouldReturnActivitiesWithValidKey() {
        Activity a = buildActivity(9001L, "Morning Ride", "Ride", 25000.0, 3600);
        when(activityRepository.findPagedByAthleteId(anyLong(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(a));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("items[0].stravaId", is(9001))
                .body("items[0].name", is("Morning Ride"))
                .body("items[0].sportType", is("Ride"))
                .body("page", is(0))
                .body("size", is(20));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturn401WithWrongKey() {
        given()
                .header("X-API-Key", "wrong-key")
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturn400WhenSizeOutOfRange() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("size", 200)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturnEmptyListWhenNoActivities() {
        when(activityRepository.findPagedByAthleteId(anyLong(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    private Activity buildActivity(Long stravaId, String name, String type, double distanceM, int movingTime) {
        Activity a = new Activity();
        a.setStravaId(stravaId);
        a.setAthleteId(ATHLETE_ID);
        a.setName(name);
        a.setSportType(type);
        a.setDistance(BigDecimal.valueOf(distanceM));
        a.setMovingTime(movingTime);
        a.setStartDate(Instant.now());
        return a;
    }
}
