package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@code StravaActivityMetricsQueryPortTest}'s contract tests:
 * unconnected athlete → empty, no reference → empty, orphan workout
 * (reference exists but workout row is gone) → empty, happy path → all
 * four metric fields with canonical activityId.
 */
class SuuntoActivityMetricsQueryPortTest {

    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final UUID CANONICAL_ACTIVITY_ID = UUID.randomUUID();
    private static final String WORKOUT_KEY = "workout-abc-123";

    // Account is created once so its auto-generated id is stable across tests
    // that need to build ActivityReference with the matching integrationAccountId.
    private final IntegrationAccount suuntoAccount =
            new IntegrationAccount(ATHLETE_ID, IntegrationSource.SUUNTO, "carloszaragozabeato");

    private final IntegrationAccountRepository accountRepository = mock(IntegrationAccountRepository.class);
    private final ActivityReferenceRepository referenceRepository = mock(ActivityReferenceRepository.class);
    private final SuuntoWorkoutRepository workoutRepository = mock(SuuntoWorkoutRepository.class);
    private final SuuntoActivityMetricsQueryPort port = new SuuntoActivityMetricsQueryPort(
            accountRepository, referenceRepository, workoutRepository);

    @Test
    void shouldReturnEmptyWhenAthleteHasNoSuuntoAccount() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNoReferenceExistsForSuuntoAccount() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(suuntoAccount));
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenReferenceExistsButWorkoutIsGone() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(suuntoAccount));
        ActivityReference ref = new ActivityReference(
                ATHLETE_ID, CANONICAL_ACTIVITY_ID, suuntoAccount.getId(), WORKOUT_KEY);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));
        when(workoutRepository.findByWorkoutKey(WORKOUT_KEY)).thenReturn(Optional.empty());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenReferenceExistsButBelongsToDifferentAthlete() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(suuntoAccount));
        UUID otherAthlete = UUID.randomUUID();
        ActivityReference ref = new ActivityReference(
                otherAthlete, CANONICAL_ACTIVITY_ID, suuntoAccount.getId(), WORKOUT_KEY);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnReadModelWithCanonicalActivityIdAndAllFourMetricFields() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(suuntoAccount));
        ActivityReference ref = new ActivityReference(
                ATHLETE_ID, CANONICAL_ACTIVITY_ID, suuntoAccount.getId(), WORKOUT_KEY);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));

        SuuntoWorkout workout = new SuuntoWorkout();
        workout.setWorkoutKey(WORKOUT_KEY);
        workout.setNormalizedPower(new BigDecimal("280.00"));
        workout.setVariabilityIndex(new BigDecimal("1.0500"));
        workout.setEfficiencyFactor(new BigDecimal("1.6200"));
        workout.setIntensityFactor(new BigDecimal("0.9200"));
        when(workoutRepository.findByWorkoutKey(WORKOUT_KEY)).thenReturn(Optional.of(workout));

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isPresent());
        ActivityMetrics metrics = result.get();
        // activityId must be the canonical TrainingSession UUID, not the workout key
        assertEquals(CANONICAL_ACTIVITY_ID, metrics.activityId());
        assertEquals(new BigDecimal("280.00"), metrics.normalizedPower());
        assertEquals(new BigDecimal("1.0500"), metrics.variabilityIndex());
        assertEquals(new BigDecimal("1.6200"), metrics.efficiencyFactor());
        assertEquals(new BigDecimal("0.9200"), metrics.intensityFactor());
    }
}
