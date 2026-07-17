package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.query.composer.DailyTssComposer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingLoadServiceTest {

    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void backfillRecomputesOnlyExistingDatesAndNeverCreatesNewRows() {
        Fixture f = new Fixture();
        LocalDate d1 = LocalDate.of(2026, 6, 1);
        LocalDate d2 = LocalDate.of(2026, 6, 15);
        when(f.trainingLoad.findDatesByAthleteId(ATHLETE_ID)).thenReturn(List.of(d1, d2));
        when(f.composer.aggregate(eq(ATHLETE_ID), any(), any())).thenReturn(Map.of());
        when(f.trainingLoad.findByAthleteAndDate(eq(ATHLETE_ID), any()))
                .thenReturn(Optional.of(new AthleteTrainingLoad()));

        int recomputed = f.service.backfill(ATHLETE_ID);

        assertEquals(2, recomputed);
        verify(f.trainingLoad).findByAthleteAndDate(ATHLETE_ID, d1);
        verify(f.trainingLoad).findByAthleteAndDate(ATHLETE_ID, d2);
        verify(f.trainingLoad, never()).persist(any(AthleteTrainingLoad.class));
    }

    @Test
    void computeAndUpsertPersistsNewRowWhenNoneExists() {
        Fixture f = new Fixture();
        LocalDate target = LocalDate.of(2026, 6, 15);
        LocalDate windowStart = target.minusDays(TrainingLoadService.WINDOW_DAYS - 1);
        when(f.composer.aggregate(ATHLETE_ID, windowStart, target)).thenReturn(Map.of(target, 100.0));
        when(f.trainingLoad.findByAthleteAndDate(ATHLETE_ID, target)).thenReturn(Optional.empty());

        f.service.computeAndUpsert(ATHLETE_ID, target);

        verify(f.trainingLoad).persist(any(AthleteTrainingLoad.class));
    }

    @Test
    void computeAndUpsertUpdatesExistingRowWithoutPersist() {
        Fixture f = new Fixture();
        LocalDate target = LocalDate.of(2026, 6, 15);
        LocalDate windowStart = target.minusDays(TrainingLoadService.WINDOW_DAYS - 1);
        when(f.composer.aggregate(ATHLETE_ID, windowStart, target)).thenReturn(Map.of());
        AthleteTrainingLoad existing = new AthleteTrainingLoad();
        when(f.trainingLoad.findByAthleteAndDate(ATHLETE_ID, target)).thenReturn(Optional.of(existing));

        f.service.computeAndUpsert(ATHLETE_ID, target);

        verify(f.trainingLoad, never()).persist(any(AthleteTrainingLoad.class));
    }

    // --- helpers ---

    private static final class Fixture {
        final AthleteTrainingLoadRepository trainingLoad = mock(AthleteTrainingLoadRepository.class);
        final DailyTssComposer composer = mock(DailyTssComposer.class);
        final TrainingLoadService service = new TrainingLoadService();

        Fixture() {
            service.trainingLoadRepository = trainingLoad;
            service.dailyTssComposer = composer;
        }
    }
}
