package com.zensyra.collector.api.resource;

import com.zensyra.collector.strava.besteffort.ActivityBestEffort;
import com.zensyra.collector.strava.besteffort.ActivityBestEffortRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteBestEffortsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final Long ATHLETE_ID = 12345L;

    @InjectMock
    ActivityBestEffortRepository bestEffortRepository;

    @InjectMock
    @RestClient
    com.zensyra.collector.strava.api.StravaApiClient stravaApiClient;

    @Test
    void shouldReturnBestEfforts() {
        ActivityBestEffort effort = new ActivityBestEffort();
        effort.setActivityStravaId(9001L);
        effort.setName("10k");
        effort.setDistance(10000);
        effort.setElapsedTime(2400);
        effort.setIsKom(false);
        effort.setPrRank(1);

        when(bestEffortRepository.findTopPrsByAthleteId(anyLong(), anyInt())).thenReturn(List.of(effort));

        given()
                .header("X-API-Key", API_KEY)
                .queryParam("limit", 5)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("limit", is(5))
                .body("items", hasSize(1))
                .body("items[0].activityStravaId", is(9001))
                .body("items[0].name", is("10k"))
                .body("items[0].distance", is(10000))
                .body("items[0].prRank", is(1));
    }

    @Test
    void shouldReturn400WhenLimitOutOfRange() {
        given()
                .header("X-API-Key", API_KEY)
                .queryParam("limit", 99)
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(400);
    }

    @Test
    void shouldReturn401WithoutKey() {
        given()
                .when().get("/api/v1/athletes/{id}/best-efforts", ATHLETE_ID)
                .then()
                .statusCode(401);
    }
}
