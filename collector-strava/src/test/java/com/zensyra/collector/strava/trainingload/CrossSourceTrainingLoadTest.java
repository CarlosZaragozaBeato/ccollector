package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.query.composer.DailyTssComposer;
import com.zensyra.collector.query.port.TrainingStressContributionPort;
import jakarta.enterprise.inject.Instance;
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
import static org.mockito.Mockito.when;

/**
 * Proves that PR-A + PR-B together deliver the cross-source TSS-summing
 * feature, not just each half in isolation.
 *
 * <p>Wires a real {@link DailyTssComposer} with TWO mock
 * {@link TrainingStressContributionPort} adapters (one acting as "Strava",
 * one as "Suunto"), then drives {@link TrainingLoadService} through
 * {@code computeAndUpsert} and asserts that:
 * <ul>
 *   <li>the upserted {@code tssDay} is the sum of both sources' contributions
 *       on the same calendar date, and
 *   <li>the EMA values (CTL, ATL) are computed from that combined TSS, not
 *       from one source alone.
 * </ul>
 */
class CrossSourceTrainingLoadTest {

    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Test
    @SuppressWarnings("unchecked")
    void sameDayStravaAndSuuntoContributionsAreSummedBeforeEma() {
        LocalDate target = LocalDate.of(2026, 6, 15);
        LocalDate windowStart = target.minusDays(TrainingLoadService.WINDOW_DAYS - 1);

        TrainingStressContributionPort stravaPort = mock(TrainingStressContributionPort.class);
        TrainingStressContributionPort suuntoPort = mock(TrainingStressContributionPort.class);

        // Strava contributes 50 TSS, Suunto contributes 75 TSS — same calendar date.
        when(stravaPort.contributionsForAthlete(eq(ATHLETE_ID), eq(windowStart), eq(target)))
                .thenReturn(Map.of(target, 50.0));
        when(suuntoPort.contributionsForAthlete(eq(ATHLETE_ID), eq(windowStart), eq(target)))
                .thenReturn(Map.of(target, 75.0));

        // Wire DailyTssComposer with both ports via a mocked CDI Instance.
        Instance<TrainingStressContributionPort> portsInstance = mock(Instance.class);
        // Each call to the for-each loop re-fetches the iterator — thenAnswer
        // ensures a fresh iterator is returned every time.
        when(portsInstance.iterator()).thenAnswer(inv -> List.of(stravaPort, suuntoPort).iterator());

        DailyTssComposer composer = new DailyTssComposer(portsInstance);

        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        AthleteTrainingLoad stored = new AthleteTrainingLoad();
        when(repo.findByAthleteAndDate(eq(ATHLETE_ID), any())).thenReturn(Optional.of(stored));

        TrainingLoadService service = new TrainingLoadService();
        service.trainingLoadRepository = repo;
        service.dailyTssComposer = composer;

        service.computeAndUpsert(ATHLETE_ID, target);

        // Combined TSS on target day: 50 + 75 = 125
        assertEquals(125.0, stored.getTssDay(), 1e-9,
                "tssDay must be the sum of Strava and Suunto contributions, not one source alone");

        // With a 90-day window and TSS only on the last day, the EMA formula gives:
        //   CTL: 0 * (1 - 1/42) + 125 * (1/42) = 125/42
        //   ATL: 0 * (1 - 1/7)  + 125 * (1/7)  = 125/7
        double expectedCtl = 125.0 / TrainingLoadService.CTL_DAYS;
        double expectedAtl = 125.0 / TrainingLoadService.ATL_DAYS;
        assertEquals(expectedCtl, stored.getCtl(), 1e-9,
                "CTL must be computed from the combined TSS, not one source's contribution alone");
        assertEquals(expectedAtl, stored.getAtl(), 1e-9,
                "ATL must be computed from the combined TSS, not one source's contribution alone");
        assertEquals(expectedCtl - expectedAtl, stored.getTsb(), 1e-9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void composerReturnsEmptyMapWhenBothPortsHaveNoData() {
        LocalDate target = LocalDate.of(2026, 6, 15);
        LocalDate windowStart = target.minusDays(TrainingLoadService.WINDOW_DAYS - 1);

        TrainingStressContributionPort stravaPort = mock(TrainingStressContributionPort.class);
        TrainingStressContributionPort suuntoPort = mock(TrainingStressContributionPort.class);
        when(stravaPort.contributionsForAthlete(eq(ATHLETE_ID), eq(windowStart), eq(target)))
                .thenReturn(Map.of());
        when(suuntoPort.contributionsForAthlete(eq(ATHLETE_ID), eq(windowStart), eq(target)))
                .thenReturn(Map.of());

        Instance<TrainingStressContributionPort> portsInstance = mock(Instance.class);
        when(portsInstance.iterator()).thenAnswer(inv -> List.of(stravaPort, suuntoPort).iterator());

        DailyTssComposer composer = new DailyTssComposer(portsInstance);
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        AthleteTrainingLoad stored = new AthleteTrainingLoad();
        when(repo.findByAthleteAndDate(eq(ATHLETE_ID), any())).thenReturn(Optional.of(stored));

        TrainingLoadService service = new TrainingLoadService();
        service.trainingLoadRepository = repo;
        service.dailyTssComposer = composer;

        service.computeAndUpsert(ATHLETE_ID, target);

        // No TSS from any source → tssDay=0, CTL=ATL=TSB=0
        assertEquals(0.0, stored.getTssDay(), 1e-9);
        assertEquals(0.0, stored.getCtl(), 1e-9);
        assertEquals(0.0, stored.getAtl(), 1e-9);
    }
}
