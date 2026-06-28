package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.composer.ActivityQueryComposer;
import com.zensyra.collector.query.model.Activity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteActivitiesResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    ActivityQueryComposer activityQueryComposer;

    @Test
    void shouldReturnActivitiesWithValidKey() {
        UUID activityId = UUID.randomUUID();
        Activity activity = buildActivity(activityId, "Morning Ride", "Ride", 25000.0, 3600);
        when(activityQueryComposer.listByAthlete(
                eq(ATHLETE_ID), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(activity));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("items[0].activityId", is(activityId.toString()))
                .body("items[0].name", is("Morning Ride"))
                .body("items[0].sportType", is("Ride"))
                .body("page", is(0))
                .body("size", is(20));
    }

    @Test
    void shouldNotExposeAnyStravaSpecificField() {
        UUID activityId = UUID.randomUUID();
        Activity activity = buildActivity(activityId, "Morning Ride", "Ride", 25000.0, 3600);
        when(activityQueryComposer.listByAthlete(
                eq(ATHLETE_ID), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(activity));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("stravaId")));
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
        when(activityQueryComposer.listByAthlete(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    private Activity buildActivity(
            UUID activityId, String name, String sportType, double distanceMeters, int movingTimeSecs) {
        return new Activity(
                activityId,
                name,
                sportType,
                distanceMeters,
                movingTimeSecs,
                Instant.now(),
                null,
                null,
                null
        );
    }
}
