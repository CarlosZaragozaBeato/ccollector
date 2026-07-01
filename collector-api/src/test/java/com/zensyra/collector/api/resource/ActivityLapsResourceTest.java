package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.Lap;
import com.zensyra.collector.query.port.LapQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ActivityLapsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    @InjectMock
    LapQueryPort lapQueryPort;

    @Test
    void shouldReturnLapsForActivity() {
        Lap lap1 = new Lap(ACTIVITY_ID, 0, "Lap 1", 1000.0, 240, 4.17, 145.0, 162.0, 10.0, 2);
        Lap lap2 = new Lap(ACTIVITY_ID, 1, "Lap 2", 1000.0, 235, 4.26, 148.0, 165.0, 5.0, 2);
        when(lapQueryPort.listByActivity(eq(ATHLETE_ID), eq(ACTIVITY_ID))).thenReturn(List.of(lap1, lap2));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{aId}/activities/{actId}/laps", ATHLETE_ID, ACTIVITY_ID)
                .then()
                .statusCode(200)
                .body("items.size()", is(2))
                .body("items[0].lapIndex", is(0))
                .body("items[0].name", is("Lap 1"))
                .body("items[0].distanceMeters", is(1000.0f))
                .body("items[0].movingTimeSecs", is(240))
                .body("items[1].lapIndex", is(1));
    }

    @Test
    void shouldReturnEmptyListWhenActivityNotFound() {
        when(lapQueryPort.listByActivity(any(), any())).thenReturn(List.of());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{aId}/activities/{actId}/laps", ATHLETE_ID, UUID.randomUUID())
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given()
                .when().get("/api/v1/athletes/{aId}/activities/{actId}/laps", ATHLETE_ID, ACTIVITY_ID)
                .then()
                .statusCode(401);
    }

    @Test
    void shouldNotExposeActivityId() {
        Lap lap = new Lap(ACTIVITY_ID, 0, "Lap 1", 500.0, 120, 4.17, 145.0, 162.0, 5.0, 1);
        when(lapQueryPort.listByActivity(eq(ATHLETE_ID), eq(ACTIVITY_ID))).thenReturn(List.of(lap));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{aId}/activities/{actId}/laps", ATHLETE_ID, ACTIVITY_ID)
                .then()
                .statusCode(200)
                .body("items[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("activityId")));
    }
}
