package com.zensyra.collector.api.v2.resource;

import com.zensyra.collector.query.composer.ActivityQueryComposer;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.QueryResult;
import com.zensyra.collector.query.model.SourceFailure;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteActivitiesResourceV2Test {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    ActivityQueryComposer activityQueryComposer;

    @Test
    void shouldReturnCompleteResultWhenAllSourcesSucceed() {
        UUID activityId = UUID.randomUUID();
        Activity activity = buildActivity(activityId, "Morning Ride", "Ride");
        QueryResult<Activity> complete = QueryResult.complete(List.of(activity));
        when(activityQueryComposer.listByAthleteWithFailures(
                eq(ATHLETE_ID), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(complete);

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v2/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("partial", is(false))
                .body("failures", hasSize(0))
                .body("items.size()", is(1))
                .body("items[0].activityId", is(activityId.toString()))
                .body("items[0].name", is("Morning Ride"));
    }

    @Test
    void shouldReturn200WithPartialTrueWhenASourceFails() {
        QueryResult<Activity> partial = new QueryResult<>(
                List.of(), List.of(new SourceFailure("StravaActivityQueryPort", "Strava down")));
        when(activityQueryComposer.listByAthleteWithFailures(
                eq(ATHLETE_ID), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(partial);

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v2/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("partial", is(true))
                .body("failures", hasSize(1))
                .body("failures[0].source", is("StravaActivityQueryPort"))
                .body("failures[0].reason", is("Strava down"))
                .body("items", hasSize(0));
    }

    @Test
    void shouldKeepSucceedingSourceDataAndStillReportPartial() {
        UUID activityId = UUID.randomUUID();
        Activity activity = buildActivity(activityId, "Evening Run", "Run");
        QueryResult<Activity> partial = new QueryResult<>(
                List.of(activity), List.of(new SourceFailure("SomeOtherSource", "timeout")));
        when(activityQueryComposer.listByAthleteWithFailures(
                eq(ATHLETE_ID), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(partial);

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v2/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("partial", is(true))
                .body("items.size()", is(1))
                .body("items[0].name", is("Evening Run"));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v2/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturn400WhenSizeOutOfRange() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("size", 200)
                .when().get("/api/v2/athletes/{id}/activities", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    private Activity buildActivity(UUID activityId, String name, String sportType) {
        return new Activity(
                activityId,
                name,
                sportType,
                10000.0,
                3600,
                Instant.now(),
                null,
                null,
                null
        );
    }
}
