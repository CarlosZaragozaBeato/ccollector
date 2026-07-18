package com.zensyra.collector.suunto.trainingload;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuuntoTrainingStressContributionPortTest {

    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final String SUUNTO_USER = "carloszaragozabeato";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 15);

    @Test
    void sameDayWorkoutsAreSummed() {
        Fixture f = new Fixture();
        stubAccount(f);
        SuuntoWorkout w1 = workout(TEST_DATE, 80.0);
        SuuntoWorkout w2 = workout(TEST_DATE, 45.5);
        when(f.workouts.findByUserAndDateRange(eq(SUUNTO_USER), any(), any()))
                .thenReturn(List.of(w1, w2));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_ID, TEST_DATE, TEST_DATE);

        assertEquals(125.5, result.get(TEST_DATE), 1e-9);
    }

    @Test
    void nullTssWorkoutDoesNotContribute() {
        Fixture f = new Fixture();
        stubAccount(f);
        SuuntoWorkout withTss = workout(TEST_DATE, 60.0);
        SuuntoWorkout nullTss = workout(TEST_DATE, null);
        when(f.workouts.findByUserAndDateRange(eq(SUUNTO_USER), any(), any()))
                .thenReturn(List.of(withTss, nullTss));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_ID, TEST_DATE, TEST_DATE);

        // Only the non-null TSS workout contributes — null is not treated as 0
        assertEquals(60.0, result.get(TEST_DATE), 1e-9);
        assertEquals(1, result.size());
    }

    @Test
    void allNullTssWorkoutsYieldEmptyMap() {
        Fixture f = new Fixture();
        stubAccount(f);
        when(f.workouts.findByUserAndDateRange(eq(SUUNTO_USER), any(), any()))
                .thenReturn(List.of(workout(TEST_DATE, null), workout(TEST_DATE, null)));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_ID, TEST_DATE, TEST_DATE);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMapWhenAthleteHasNoSuuntoAccount() {
        Fixture f = new Fixture();
        when(f.accounts.findByAthleteId(ATHLETE_ID)).thenReturn(List.of());

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_ID, TEST_DATE, TEST_DATE);

        assertTrue(result.isEmpty());
    }

    // --- helpers ---

    private void stubAccount(Fixture f) {
        IntegrationAccount account = new IntegrationAccount(ATHLETE_ID, IntegrationSource.SUUNTO, SUUNTO_USER);
        when(f.accounts.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));
    }

    private static SuuntoWorkout workout(LocalDate date, Double tss) {
        SuuntoWorkout w = new SuuntoWorkout();
        w.setSuuntoUser(SUUNTO_USER);
        w.setStartDate(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        w.setTss(tss);
        return w;
    }

    private static final class Fixture {
        final IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        final SuuntoWorkoutRepository workouts = mock(SuuntoWorkoutRepository.class);
        final SuuntoTrainingStressContributionPort port =
                new SuuntoTrainingStressContributionPort(accounts, workouts);
    }
}
