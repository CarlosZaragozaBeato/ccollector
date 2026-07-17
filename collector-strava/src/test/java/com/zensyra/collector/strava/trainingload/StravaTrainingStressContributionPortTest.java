package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaTrainingStressContributionPortTest {

    private static final UUID ATHLETE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final Long STRAVA_ID = 42L;
    private static final String STRAVA_ID_STR = "42";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 15);

    @Test
    void estimateTssUsesRealIntensityFactorWhenPresent() {
        Fixture f = new Fixture();
        stubAccount(f);
        Activity activity = activity(1L, 3600, TEST_DATE); // 1 hour
        when(f.activities.findByAthleteIdAndDateRange(eq(STRAVA_ID), any(), any()))
                .thenReturn(List.of(activity));
        // IF = 0.90 → TSS = 1 × 0.90² × 100 = 81.0
        when(f.metrics.findByActivityId(1L))
                .thenReturn(Optional.of(metricsWithIf(new BigDecimal("0.9000"))));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertEquals(81.0, result.get(TEST_DATE), 1e-9);
    }

    @Test
    void estimateTssFallsBackToPointSevenFiveWhenIntensityFactorNull() {
        Fixture f = new Fixture();
        stubAccount(f);
        Activity activity = activity(1L, 3600, TEST_DATE);
        when(f.activities.findByAthleteIdAndDateRange(eq(STRAVA_ID), any(), any()))
                .thenReturn(List.of(activity));
        // metrics row exists but IF is null → fallback 0.75 → TSS = 1 × 0.75² × 100 = 56.25
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.of(metricsWithIf(null)));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertEquals(56.25, result.get(TEST_DATE), 1e-9);
    }

    @Test
    void estimateTssFallsBackToPointSevenFiveWhenNoMetricsRow() {
        Fixture f = new Fixture();
        stubAccount(f);
        Activity activity = activity(1L, 3600, TEST_DATE);
        when(f.activities.findByAthleteIdAndDateRange(eq(STRAVA_ID), any(), any()))
                .thenReturn(List.of(activity));
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.empty());

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertEquals(56.25, result.get(TEST_DATE), 1e-9);
    }

    @Test
    void activitiesWithZeroMovingTimeAreExcluded() {
        Fixture f = new Fixture();
        stubAccount(f);
        Activity zeroTime = activity(1L, 0, TEST_DATE);
        when(f.activities.findByAthleteIdAndDateRange(eq(STRAVA_ID), any(), any()))
                .thenReturn(List.of(zeroTime));

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertTrue(result.isEmpty(), "Zero moving-time activities must not contribute TSS");
    }

    @Test
    void multipleActivitiesOnSameDayAreSummed() {
        Fixture f = new Fixture();
        stubAccount(f);
        // Two 1-hour activities, both falling back to IF=0.75 → each 56.25 → sum 112.5
        Activity a1 = activity(1L, 3600, TEST_DATE);
        Activity a2 = activity(2L, 3600, TEST_DATE);
        when(f.activities.findByAthleteIdAndDateRange(eq(STRAVA_ID), any(), any()))
                .thenReturn(List.of(a1, a2));
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.empty());
        when(f.metrics.findByActivityId(2L)).thenReturn(Optional.empty());

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertEquals(112.5, result.get(TEST_DATE), 1e-9);
    }

    @Test
    void shouldReturnEmptyMapWhenAthleteHasNoStravaAccount() {
        Fixture f = new Fixture();
        when(f.accounts.findByAthleteId(ATHLETE_UUID)).thenReturn(List.of());

        Map<LocalDate, Double> result = f.port.contributionsForAthlete(ATHLETE_UUID, TEST_DATE, TEST_DATE);

        assertTrue(result.isEmpty());
    }

    // --- helpers ---

    private void stubAccount(Fixture f) {
        IntegrationAccount account = new IntegrationAccount(ATHLETE_UUID, IntegrationSource.STRAVA, STRAVA_ID_STR);
        when(f.accounts.findByAthleteId(ATHLETE_UUID)).thenReturn(List.of(account));
    }

    private static Activity activity(Long id, int movingTimeSeconds, LocalDate date) {
        Activity a = new Activity();
        a.setId(id);
        a.setAthleteId(STRAVA_ID);
        a.setMovingTime(movingTimeSeconds);
        a.setStartDate(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        return a;
    }

    private static ActivityMetrics metricsWithIf(BigDecimal intensityFactor) {
        ActivityMetrics m = new ActivityMetrics();
        m.setIntensityFactor(intensityFactor);
        return m;
    }

    private static final class Fixture {
        final IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        final ActivityRepository activities = mock(ActivityRepository.class);
        final ActivityMetricsRepository metrics = mock(ActivityMetricsRepository.class);
        final StravaTrainingStressContributionPort port =
                new StravaTrainingStressContributionPort(accounts, activities, metrics);
    }
}
