package com.zensyra.collector.api.resource;

import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshot;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
class AthleteStatsResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final Long ATHLETE_ID = 12345L;

    @InjectMock
    AthleteStatsSnapshotRepository snapshotRepository;

    @InjectMock
    @RestClient
    com.zensyra.collector.strava.api.StravaApiClient stravaApiClient;

    @Test
    void shouldReturnLatestStats() {
        AthleteStatsSnapshot snap = buildSnapshot(ATHLETE_ID, LocalDate.now(), 45, 1234500.0, 18);
        when(snapshotRepository.findLatestByAthleteId(anyLong())).thenReturn(Optional.of(snap));

        given()
                .header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/stats", ATHLETE_ID)
                .then()
                .statusCode(200)
                .body("athleteId", is(ATHLETE_ID.intValue()))
                .body("ytdRideCount", is(45))
                .body("ytdRideDistanceKm", is(1234.5f));
    }

    @Test
    void shouldReturn404WhenNoSnapshot() {
        when(snapshotRepository.findLatestByAthleteId(anyLong())).thenReturn(Optional.empty());

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

    private AthleteStatsSnapshot buildSnapshot(Long athleteId, LocalDate date,
                                               int ytdRideCount, double ytdRideDistanceM,
                                               int allRideCount) {
        AthleteStatsSnapshot s = new AthleteStatsSnapshot();
        s.setAthleteId(athleteId);
        s.setSnapshotDate(date);
        s.setYtdRideCount(ytdRideCount);
        s.setYtdRideDistance(ytdRideDistanceM);
        s.setAllRideCount(allRideCount);
        return s;
    }
}
