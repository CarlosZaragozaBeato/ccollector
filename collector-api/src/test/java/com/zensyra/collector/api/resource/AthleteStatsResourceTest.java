package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.model.AthleteStats;
import com.zensyra.collector.query.model.SportAggregate;
import com.zensyra.collector.query.model.StatsWindow;
import com.zensyra.collector.query.port.AthleteStatsQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteStatsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.randomUUID();

    @InjectMock
    AthleteStatsQueryPort athleteStatsQueryPort;

    @Test
    void shouldReturnLatestStatsAsAggregateList() {
        AthleteStats stats = new AthleteStats(
                ATHLETE_ID,
                LocalDate.of(2026, 6, 27),
                List.of(
                        new SportAggregate("Ride", StatsWindow.YEAR_TO_DATE, 45, 1_234_500.0, 180_000, 12_000.0),
                        new SportAggregate("Run", StatsWindow.ALL_TIME, 120, 1_500_000.0, 540_000, 18_000.0)
                )
        );
        when(athleteStatsQueryPort.getLatestByAthlete(eq(ATHLETE_ID))).thenReturn(Optional.of(stats));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/stats", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("athleteId", is(ATHLETE_ID.toString()))
                .body("snapshotDate", is("2026-06-27"))
                .body("aggregates", hasSize(2))
                .body("aggregates[0].sportType", is("Ride"))
                .body("aggregates[0].window", is("YEAR_TO_DATE"))
                .body("aggregates[0].activityCount", is(45))
                .body("aggregates[1].sportType", is("Run"))
                .body("aggregates[1].window", is("ALL_TIME"));
    }

    @Test
    void shouldNotExposeAnyStravaSpecificField() {
        AthleteStats stats = new AthleteStats(
                ATHLETE_ID, LocalDate.of(2026, 6, 27),
                List.of(new SportAggregate("Ride", StatsWindow.YEAR_TO_DATE, 45, 1_234_500.0, 180_000, 12_000.0)));
        when(athleteStatsQueryPort.getLatestByAthlete(eq(ATHLETE_ID))).thenReturn(Optional.of(stats));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/stats", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("biggestRideDistance")))
                .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("biggestClimbElevationGain")))
                .body("aggregates[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("ytdRideCount")));
    }

    @Test
    void shouldReturn404WhenNoSnapshot() {
        when(athleteStatsQueryPort.getLatestByAthlete(any())).thenReturn(Optional.empty());

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/stats", ATHLETE_ID)
                .then()
                .statusCode(404);
    }

    @Test
    void shouldReturn401WithoutKey() {
        given()
                .when().get("/api/v1/athletes/{id}/stats", ATHLETE_ID)
                .then()
                .statusCode(401);
    }
}
