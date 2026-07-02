package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingLoadServiceTest {

    private static final Long ATHLETE_ID = 42L;

    @Test
    void estimateTssUsesRealIntensityFactorWhenPresent() {
        Fixture f = new Fixture();
        Activity activity = activity(1L, 3600); // 1 hour
        // IF = 0.90 → TSS = 1 × 0.90² × 100 = 81.0
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.of(metricsWithIf(new BigDecimal("0.9000"))));

        assertEquals(81.0, f.service.estimateTss(activity), 1e-9);
    }

    @Test
    void estimateTssFallsBackToPointSevenFiveWhenIntensityFactorNull() {
        Fixture f = new Fixture();
        Activity activity = activity(1L, 3600); // 1 hour
        // metrics row exists but IF is null → fallback 0.75 → TSS = 1 × 0.75² × 100 = 56.25
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.of(metricsWithIf(null)));

        assertEquals(56.25, f.service.estimateTss(activity), 1e-9);
    }

    @Test
    void estimateTssFallsBackToPointSevenFiveWhenNoMetricsRow() {
        Fixture f = new Fixture();
        Activity activity = activity(1L, 3600);
        when(f.metrics.findByActivityId(1L)).thenReturn(Optional.empty());

        assertEquals(56.25, f.service.estimateTss(activity), 1e-9);
    }

    @Test
    void backfillRecomputesOnlyExistingDatesAndNeverCreatesNewRows() {
        Fixture f = new Fixture();
        LocalDate d1 = LocalDate.of(2026, 6, 1);
        LocalDate d2 = LocalDate.of(2026, 6, 15);
        when(f.trainingLoad.findDatesByAthleteId(ATHLETE_ID)).thenReturn(List.of(d1, d2));
        // computeAndUpsert reads activities per window and upserts an EXISTING row
        when(f.activities.findByAthleteIdAndDateRange(eq(ATHLETE_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(f.trainingLoad.findByAthleteAndDate(eq(ATHLETE_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(new AthleteTrainingLoad())); // row already exists

        int recomputed = f.service.backfill(ATHLETE_ID);

        assertEquals(2, recomputed);
        // one recompute per existing date, and never a persist of a new row
        verify(f.trainingLoad).findByAthleteAndDate(ATHLETE_ID, d1);
        verify(f.trainingLoad).findByAthleteAndDate(ATHLETE_ID, d2);
        verify(f.trainingLoad, org.mockito.Mockito.never()).persist(org.mockito.ArgumentMatchers.any(AthleteTrainingLoad.class));
    }

    // --- helpers ---

    private static Activity activity(Long id, int movingTimeSeconds) {
        Activity a = new Activity();
        a.setId(id);
        a.setAthleteId(ATHLETE_ID);
        a.setMovingTime(movingTimeSeconds);
        a.setStartDate(Instant.now().atZone(ZoneOffset.UTC).toInstant());
        return a;
    }

    private static ActivityMetrics metricsWithIf(BigDecimal intensityFactor) {
        ActivityMetrics m = new ActivityMetrics();
        m.setIntensityFactor(intensityFactor);
        return m;
    }

    private static final class Fixture {
        final ActivityRepository activities = mock(ActivityRepository.class);
        final AthleteTrainingLoadRepository trainingLoad = mock(AthleteTrainingLoadRepository.class);
        final ActivityMetricsRepository metrics = mock(ActivityMetricsRepository.class);
        final TrainingLoadService service = new TrainingLoadService();

        Fixture() {
            service.activityRepository = activities;
            service.trainingLoadRepository = trainingLoad;
            service.metricsRepository = metrics;
        }
    }
}
