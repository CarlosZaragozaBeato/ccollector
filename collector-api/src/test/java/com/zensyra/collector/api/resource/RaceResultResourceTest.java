package com.zensyra.collector.api.resource;

import com.zensyra.collector.journal.service.RaceResultService;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.port.RaceResultQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class RaceResultResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RACE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ACTIVITY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @InjectMock
    RaceResultQueryPort raceResultQueryPort;

    @InjectMock
    RaceResultService raceResultService;

    private RaceResultSummary sample(UUID linkedActivityId) {
        Instant now = Instant.parse("2025-05-01T10:00:00Z");
        return new RaceResultSummary(
                RACE_ID, LocalDate.of(2025, 4, 27), "Valencia Marathon", 42195.0,
                10800, 10530, 342, "PR!", linkedActivityId, now, now);
    }

    // ---- POST: create ----

    @Test
    void shouldCreateRaceResultAndReturn201() {
        when(raceResultService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sample(ACTIVITY_ID));

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"Valencia Marathon\","
                        + "\"distanceMeters\":42195.0,\"goalFinishTime\":10800,\"actualFinishTime\":10530,"
                        + "\"position\":342,\"notes\":\"PR!\","
                        + "\"linkedActivityId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\"}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(201)
                .body("id", is(RACE_ID.toString()))
                .body("raceDate", is("2025-04-27"))
                .body("raceName", is("Valencia Marathon"))
                .body("distanceMeters", is(42195.0f))
                .body("goalFinishTime", is(10800))
                .body("actualFinishTime", is(10530))
                .body("position", is(342))
                .body("linkedActivityId", is(ACTIVITY_ID.toString()))
                .body("$", not(hasKey("athleteId")));
    }

    @Test
    void shouldCreateWithNullLinkedActivityAndOptionalFields() {
        when(raceResultService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sample(null));

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"Valencia Marathon\","
                        + "\"distanceMeters\":42195.0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(201)
                .body("linkedActivityId", is(nullValue()));
    }

    // ---- POST: 7 validation paths ----

    @Test
    void shouldReturn400WhenRaceDateMissing() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceName\":\"X\",\"distanceMeters\":1000.0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'raceDate' is required"));
    }

    @Test
    void shouldReturn400WhenRaceNameMissingOrBlank() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"distanceMeters\":1000.0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'raceName' is required"));

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"  \",\"distanceMeters\":1000.0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'raceName' is required"));
    }

    @Test
    void shouldReturn400WhenDistanceMissing() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"X\"}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'distanceMeters' is required"));
    }

    @Test
    void shouldReturn400WhenDistanceNotPositive() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"X\",\"distanceMeters\":0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'distanceMeters' must be positive"));
    }

    @Test
    void shouldReturn400WhenGoalFinishTimeNotPositive() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"X\",\"distanceMeters\":1000.0,"
                        + "\"goalFinishTime\":-5}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'goalFinishTime' must be positive"));
    }

    @Test
    void shouldReturn400WhenActualFinishTimeNotPositive() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"X\",\"distanceMeters\":1000.0,"
                        + "\"actualFinishTime\":0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'actualFinishTime' must be positive"));
    }

    @Test
    void shouldReturn400WhenPositionNotPositive() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"raceDate\":\"2025-04-27\",\"raceName\":\"X\",\"distanceMeters\":1000.0,"
                        + "\"position\":0}")
                .when().post("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'position' must be positive"));
    }

    // ---- GET ----

    @Test
    void shouldReturnRaceResultsForDateRange() {
        when(raceResultQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any()))
                .thenReturn(List.of(sample(ACTIVITY_ID)));

        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-01-01").queryParam("to", "2025-12-31")
                .when().get("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].raceName", is("Valencia Marathon"))
                .body("[0]", not(hasKey("athleteId")));
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given().header("X-API-Key", API_KEY).queryParam("from", "nope")
                .when().get("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)"));
    }

    @Test
    void shouldReturn400WhenFromAfterTo() {
        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-12-31").queryParam("to", "2025-01-01")
                .when().get("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'from' must not be after 'to'"));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given().when().get("/api/v1/athletes/{id}/race-results", ATHLETE_ID)
                .then().statusCode(401);
    }

    @Test
    void getDefaultRangeIsTwelveMonthsNotThree() {
        when(raceResultQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any())).thenReturn(List.of());

        given().header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/race-results", ATHLETE_ID).then().statusCode(200);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(raceResultQueryPort).findByAthlete(eq(ATHLETE_ID), from.capture(), to.capture());

        // Default window is exactly 12 months back — the confirmed deviation from
        // the 3-month default used by TrainingDay/HealthEvent.
        assertEquals(to.getValue().minusMonths(12), from.getValue());
        assertNotEquals(to.getValue().minusMonths(3), from.getValue());
    }
}
