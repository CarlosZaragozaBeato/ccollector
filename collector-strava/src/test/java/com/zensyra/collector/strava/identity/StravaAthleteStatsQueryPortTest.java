package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.AthleteStats;
import com.zensyra.collector.query.model.SportAggregate;
import com.zensyra.collector.query.model.StatsWindow;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshot;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaAthleteStatsQueryPortTest {

    @Test
    void shouldReturnEmptyWhenAthleteHasNoStravaAccount() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteStatsSnapshotRepository athleteStatsSnapshotRepository = mock(AthleteStatsSnapshotRepository.class);
        StravaAthleteStatsQueryPort port = new StravaAthleteStatsQueryPort(
                integrationAccountRepository, athleteStatsSnapshotRepository);

        UUID athleteId = UUID.randomUUID();
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of());

        Optional<AthleteStats> result = port.getLatestByAthlete(athleteId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNoSnapshotExists() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteStatsSnapshotRepository athleteStatsSnapshotRepository = mock(AthleteStatsSnapshotRepository.class);
        StravaAthleteStatsQueryPort port = new StravaAthleteStatsQueryPort(
                integrationAccountRepository, athleteStatsSnapshotRepository);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));
        when(athleteStatsSnapshotRepository.findLatestByAthleteId(111L)).thenReturn(Optional.empty());

        Optional<AthleteStats> result = port.getLatestByAthlete(athleteId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFlattenOnlySportsWithRecordedActivityCounts() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteStatsSnapshotRepository athleteStatsSnapshotRepository = mock(AthleteStatsSnapshotRepository.class);
        StravaAthleteStatsQueryPort port = new StravaAthleteStatsQueryPort(
                integrationAccountRepository, athleteStatsSnapshotRepository);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));

        AthleteStatsSnapshot snapshot = new AthleteStatsSnapshot();
        snapshot.setAthleteId(111L);
        snapshot.setSnapshotDate(LocalDate.of(2026, 6, 27));
        // Ride: both windows present.
        snapshot.setYtdRideCount(45);
        snapshot.setYtdRideDistance(1_234_500.0);
        snapshot.setYtdRideMovingTime(180_000);
        snapshot.setYtdRideElevationGain(12_000.0);
        snapshot.setAllRideCount(900);
        snapshot.setAllRideDistance(25_000_000.0);
        snapshot.setAllRideMovingTime(3_600_000);
        snapshot.setAllRideElevationGain(250_000.0);
        // Run: only all-time present, year-to-date left null on purpose.
        snapshot.setAllRunCount(120);
        snapshot.setAllRunDistance(1_500_000.0);
        snapshot.setAllRunMovingTime(540_000);
        snapshot.setAllRunElevationGain(18_000.0);
        // Swim: nothing recorded at all — both counts stay null.

        when(athleteStatsSnapshotRepository.findLatestByAthleteId(eq(111L))).thenReturn(Optional.of(snapshot));

        Optional<AthleteStats> result = port.getLatestByAthlete(athleteId);

        assertTrue(result.isPresent());
        AthleteStats stats = result.get();
        assertEquals(athleteId, stats.athleteId());
        assertEquals(LocalDate.of(2026, 6, 27), stats.snapshotDate());

        // Ride YTD + Ride all-time + Run all-time = 3 aggregates.
        // No Run YTD (null count), no Swim at all (null counts both windows).
        assertEquals(3, stats.aggregates().size());

        SportAggregate rideYtd = findAggregate(stats, "Ride", StatsWindow.YEAR_TO_DATE);
        assertEquals(45, rideYtd.activityCount());
        assertEquals(1_234_500.0, rideYtd.distanceMeters());
        assertEquals(180_000, rideYtd.movingTimeSecs());
        assertEquals(12_000.0, rideYtd.elevationGainMeters());

        SportAggregate rideAllTime = findAggregate(stats, "Ride", StatsWindow.ALL_TIME);
        assertEquals(900, rideAllTime.activityCount());

        SportAggregate runAllTime = findAggregate(stats, "Run", StatsWindow.ALL_TIME);
        assertEquals(120, runAllTime.activityCount());

        assertTrue(stats.aggregates().stream()
                .noneMatch(a -> a.sportType().equals("Run") && a.window() == StatsWindow.YEAR_TO_DATE));
        assertTrue(stats.aggregates().stream().noneMatch(a -> a.sportType().equals("Swim")));
    }

    private SportAggregate findAggregate(AthleteStats stats, String sportType, StatsWindow window) {
        return stats.aggregates().stream()
                .filter(a -> a.sportType().equals(sportType) && a.window() == window)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected aggregate for " + sportType + "/" + window + " not found"));
    }
}
