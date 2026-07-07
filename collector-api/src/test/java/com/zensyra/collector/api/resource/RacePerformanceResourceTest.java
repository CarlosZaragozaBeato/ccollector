package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.composer.RacePerformanceComposer;
import com.zensyra.collector.query.model.PreRaceSubjectiveState;
import com.zensyra.collector.query.model.RacePerformanceContext;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.model.TrainingLoadPoint;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class RacePerformanceResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RACE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @InjectMock
    RacePerformanceComposer racePerformanceComposer;

    private RacePerformanceContext sampleContext() {
        Instant now = Instant.parse("2025-05-01T10:00:00Z");
        RaceResultSummary race = new RaceResultSummary(
                RACE_ID, LocalDate.of(2025, 4, 27), "Valencia Marathon", 42195.0,
                10800, 10530, 342, "PR!", null, now, now);
        TrainingLoadPoint atRaceDate = new TrainingLoadPoint(
                LocalDate.of(2025, 4, 27), LocalDate.of(2025, 4, 27), 80.0, 90.0, -10.0, true);
        TrainingLoadPoint at7 = new TrainingLoadPoint(
                LocalDate.of(2025, 4, 20), LocalDate.of(2025, 4, 20), 78.0, 60.0, 18.0, true);
        // 42-day point has no row within tolerance — explicit "insufficient data".
        TrainingLoadPoint at42 = new TrainingLoadPoint(
                LocalDate.of(2025, 3, 16), null, null, null, null, false);
        // Diary entries recorded in the taper week: avg RPE 4.5, dominant "GOOD".
        PreRaceSubjectiveState subjective = new PreRaceSubjectiveState(2, 4.5, "GOOD", true);
        return new RacePerformanceContext(race, atRaceDate, at7, at42, subjective);
    }

    @Test
    void shouldReturnRacePerformanceContextForDateRange() {
        when(racePerformanceComposer.composeForAthlete(eq(ATHLETE_ID), any(), any()))
                .thenReturn(List.of(sampleContext()));

        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-01-01").queryParam("to", "2025-12-31")
                .when().get("/api/v1/athletes/{id}/race-performance", ATHLETE_ID)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].race.raceName", is("Valencia Marathon"))
                // race is shaped like /race-results (no athleteId leaked)
                .body("[0].race", not(hasKey("athleteId")))
                .body("[0].atRaceDate.ctl", is(80.0f))
                .body("[0].atRaceDate.available", is(true))
                .body("[0].at7DaysBefore.actualDate", is("2025-04-20"))
                .body("[0].at7DaysBefore.available", is(true))
                // gap is explicit: unavailable point serializes null metrics, not 0
                .body("[0].at42DaysBefore.available", is(false))
                .body("[0].at42DaysBefore.ctl", is(nullValue()))
                .body("[0].at42DaysBefore.actualDate", is(nullValue()))
                // additive pre-race subjective-state aggregate
                .body("[0].preRaceSubjectiveState.available", is(true))
                .body("[0].preRaceSubjectiveState.entryCount", is(2))
                .body("[0].preRaceSubjectiveState.averagePerceivedEffort", is(4.5f))
                .body("[0].preRaceSubjectiveState.dominantSubjectiveState", is("GOOD"));
    }

    @Test
    void shouldReturnEmptyListForAthleteWithNoRaces() {
        when(racePerformanceComposer.composeForAthlete(eq(ATHLETE_ID), any(), any()))
                .thenReturn(List.of());

        given().header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/race-performance", ATHLETE_ID)
                .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given().header("X-API-Key", API_KEY).queryParam("from", "nope")
                .when().get("/api/v1/athletes/{id}/race-performance", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)"));
    }

    @Test
    void shouldReturn400WhenFromAfterTo() {
        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-12-31").queryParam("to", "2025-01-01")
                .when().get("/api/v1/athletes/{id}/race-performance", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'from' must not be after 'to'"));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given().when().get("/api/v1/athletes/{id}/race-performance", ATHLETE_ID)
                .then().statusCode(401);
    }
}
