package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirror of StravaActivityQueryPortTest — plain mocks, same behavioral
 * contract: empty for unconnected athletes, parameters pushed down, rows
 * without a canonical reference skipped, and only canonical UUIDs exposed.
 */
class SuuntoActivityQueryPortTest {

    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final String SUUNTO_USER = "carloszaragozabeato";

    private final IntegrationAccountRepository accountRepository = mock(IntegrationAccountRepository.class);
    private final ActivityReferenceRepository referenceRepository = mock(ActivityReferenceRepository.class);
    private final SuuntoWorkoutRepository workoutRepository = mock(SuuntoWorkoutRepository.class);
    private final SuuntoActivityQueryPort port = new SuuntoActivityQueryPort(
            accountRepository, referenceRepository, workoutRepository);

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoSuuntoAccount() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of());

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldQueryWorkoutRepositoryWithSuuntoUserAndForwardAllParameters() {
        IntegrationAccount account = connectedSuuntoAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(workoutRepository.findPagedByUser(SUUNTO_USER, "Running", from, to, 10, 5))
                .thenReturn(List.of());

        port.listByAthlete(ATHLETE_ID, "Running", from, to, 10, 5);

        verify(workoutRepository).findPagedByUser(SUUNTO_USER, "Running", from, to, 10, 5);
    }

    @Test
    void shouldSilentlyDiscardWorkoutWithNoCanonicalReference() {
        IntegrationAccount account = connectedSuuntoAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));
        when(workoutRepository.findPagedByUser(eq(SUUNTO_USER), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of(suuntoWorkout("wk-orphan")));
        when(referenceRepository.findByIntegrationAccountIdAndExternalActivityId(account.getId(), "wk-orphan"))
                .thenReturn(Optional.empty());

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnCanonicalActivityIdAndNeverAWorkoutKey() {
        UUID canonicalActivityId = UUID.randomUUID();
        IntegrationAccount account = connectedSuuntoAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));

        SuuntoWorkout workout = suuntoWorkout("wk-1");
        workout.setSportType("Running");
        workout.setAverageWatts(261.3);
        when(workoutRepository.findPagedByUser(eq(SUUNTO_USER), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of(workout));

        ActivityReference reference = mock(ActivityReference.class);
        when(reference.getTrainingSessionId()).thenReturn(canonicalActivityId);
        when(referenceRepository.findByIntegrationAccountIdAndExternalActivityId(account.getId(), "wk-1"))
                .thenReturn(Optional.of(reference));

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertEquals(1, result.size());
        Activity activity = result.get(0);
        assertEquals(canonicalActivityId, activity.activityId());
        // Suunto list payloads carry no workout name — always null (#6)
        assertNull(activity.name());
        assertEquals("Running", activity.sportType());
        assertEquals(10000.0, activity.distanceMeters());
        assertEquals(3600, activity.movingTimeSecs());
        assertEquals(261.3, activity.averageWatts());
    }

    // --- helpers ---

    private IntegrationAccount connectedSuuntoAccount() {
        return new IntegrationAccount(ATHLETE_ID, IntegrationSource.SUUNTO, SUUNTO_USER);
    }

    private SuuntoWorkout suuntoWorkout(String workoutKey) {
        SuuntoWorkout workout = new SuuntoWorkout();
        workout.setWorkoutKey(workoutKey);
        workout.setSuuntoUser(SUUNTO_USER);
        workout.setTotalDistance(10000.0);
        workout.setMovingTimeSecs(3600);
        workout.setStartDate(Instant.parse("2026-07-13T08:12:39Z"));
        return workout;
    }
}
