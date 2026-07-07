package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.PreRaceSubjectiveState;
import com.zensyra.collector.query.model.RacePerformanceContext;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.model.TrainingDaySummary;
import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.query.model.TrainingLoadPoint;
import com.zensyra.collector.query.port.RaceResultQueryPort;
import com.zensyra.collector.query.port.TrainingDayQueryPort;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link RacePerformanceComposer}: stubbed ports, no CDI,
 * no DB, no identity bridging (the training-load port encapsulates that). Covers
 * the training-load cases the issue calls out — complete data at both look-back
 * windows, a within-tolerance data gap, and a race with no training load at all —
 * plus multiplicity/ordering, and the pre-race subjective-state aggregation
 * (full / partial / zero diary coverage, and the most-recent tie-break).
 */
class RacePerformanceComposerTest {

    private static final UUID ATHLETE = UUID.randomUUID();
    private static final LocalDate RACE_DATE = LocalDate.parse("2026-06-01");
    private static final LocalDate SEVEN_BEFORE = RACE_DATE.minusDays(7);   // 2026-05-25
    private static final LocalDate FORTY_TWO_BEFORE = RACE_DATE.minusDays(42); // 2026-04-20

    @Test
    void completeData_allThreePointsAvailableAtExactDates() {
        RaceResultSummary race = race(RACE_DATE, "Marathon");
        FakeRaceResultQueryPort races = new FakeRaceResultQueryPort(List.of(race));
        FakeTrainingLoadQueryPort load = new FakeTrainingLoadQueryPort(List.of(
                load(RACE_DATE, 80.0, 90.0, -10.0),
                load(SEVEN_BEFORE, 78.0, 60.0, 18.0),
                load(FORTY_TWO_BEFORE, 55.0, 50.0, 5.0)
        ));
        RacePerformanceComposer composer = composer(races, load);

        List<RacePerformanceContext> result =
                composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE);

        assertEquals(1, result.size());
        RacePerformanceContext ctx = result.get(0);
        assertEquals(race, ctx.race());

        assertAvailable(ctx.atRaceDate(), RACE_DATE, RACE_DATE, 80.0, 90.0, -10.0);
        assertAvailable(ctx.at7DaysBefore(), SEVEN_BEFORE, SEVEN_BEFORE, 78.0, 60.0, 18.0);
        assertAvailable(ctx.at42DaysBefore(), FORTY_TWO_BEFORE, FORTY_TWO_BEFORE, 55.0, 50.0, 5.0);

        // No diary entries supplied → subjective signal is explicitly unavailable.
        assertSubjectiveUnavailable(ctx.preRaceSubjectiveState());
    }

    @Test
    void withinToleranceGap_usesNearestRow_preferringEarlierOnTie() {
        // No row on the exact race−7 day (2026-05-25). Rows exist 2 days either
        // side; the earlier one must win the tie.
        LocalDate twoBefore = SEVEN_BEFORE.minusDays(2); // 2026-05-23
        LocalDate twoAfter = SEVEN_BEFORE.plusDays(2);   // 2026-05-27
        FakeRaceResultQueryPort races = new FakeRaceResultQueryPort(List.of(race(RACE_DATE, "Marathon")));
        FakeTrainingLoadQueryPort load = new FakeTrainingLoadQueryPort(List.of(
                load(RACE_DATE, 80.0, 90.0, -10.0),
                load(twoBefore, 70.0, 65.0, 5.0),   // should be chosen (earlier on tie)
                load(twoAfter, 71.0, 66.0, 5.0),
                load(FORTY_TWO_BEFORE, 55.0, 50.0, 5.0)
        ));
        RacePerformanceComposer composer = composer(races, load);

        TrainingLoadPoint at7 = composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE)
                .get(0).at7DaysBefore();

        // requestedDate stays the exact target; actualDate reveals the substitution.
        assertAvailable(at7, SEVEN_BEFORE, twoBefore, 70.0, 65.0, 5.0);
    }

    @Test
    void gapBeyondTolerance_reportedAsInsufficientNotApproximated() {
        // Nearest row to race−42 is 4 days away — outside the ±3 tolerance.
        FakeRaceResultQueryPort races = new FakeRaceResultQueryPort(List.of(race(RACE_DATE, "Marathon")));
        FakeTrainingLoadQueryPort load = new FakeTrainingLoadQueryPort(List.of(
                load(RACE_DATE, 80.0, 90.0, -10.0),
                load(FORTY_TWO_BEFORE.minusDays(4), 55.0, 50.0, 5.0)
        ));
        RacePerformanceComposer composer = composer(races, load);

        RacePerformanceContext ctx =
                composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE).get(0);

        assertTrue(ctx.atRaceDate().available());
        assertInsufficient(ctx.at42DaysBefore(), FORTY_TWO_BEFORE);
    }

    @Test
    void noTrainingLoadAtAll_returnsContextWithAllPointsInsufficient_doesNotThrow() {
        FakeRaceResultQueryPort races = new FakeRaceResultQueryPort(List.of(race(RACE_DATE, "Marathon")));
        FakeTrainingLoadQueryPort emptyLoad = new FakeTrainingLoadQueryPort(List.of());
        RacePerformanceComposer composer = composer(races, emptyLoad);

        List<RacePerformanceContext> result =
                composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE);

        assertEquals(1, result.size());
        RacePerformanceContext ctx = result.get(0);
        assertEquals(RACE_DATE, ctx.race().raceDate());
        assertInsufficient(ctx.atRaceDate(), RACE_DATE);
        assertInsufficient(ctx.at7DaysBefore(), SEVEN_BEFORE);
        assertInsufficient(ctx.at42DaysBefore(), FORTY_TWO_BEFORE);
    }

    @Test
    void multipleRaces_returnsOneContextEach_newestFirst() {
        RaceResultSummary older = race(RACE_DATE.minusMonths(2), "Half");
        RaceResultSummary newer = race(RACE_DATE, "Marathon");
        FakeRaceResultQueryPort races = new FakeRaceResultQueryPort(List.of(older, newer));
        FakeTrainingLoadQueryPort load = new FakeTrainingLoadQueryPort(List.of());
        RacePerformanceComposer composer = composer(races, load);

        List<RacePerformanceContext> result =
                composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE);

        assertEquals(2, result.size());
        assertEquals(newer, result.get(0).race());
        assertEquals(older, result.get(1).race());
    }

    @Test
    void noRacesInRange_returnsEmpty() {
        RacePerformanceComposer composer = composer(
                new FakeRaceResultQueryPort(List.of()),
                new FakeTrainingLoadQueryPort(List.of()));

        assertTrue(composer.composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE).isEmpty());
    }

    // --- pre-race subjective state --------------------------------------

    @Test
    void subjectiveState_fullCoverage_averagesRpeAndPicksDominantState() {
        RaceResultSummary race = race(RACE_DATE, "Marathon");
        // Window is [RACE_DATE−7, RACE_DATE−1] = [2026-05-25, 2026-05-31], inclusive.
        // The 05-25 entry proves the lower bound is inclusive; the 05-24 entry
        // (RACE_DATE−8) proves it is excluded — if it counted, the average and the
        // dominant state would both change.
        FakeTrainingDayQueryPort diary = new FakeTrainingDayQueryPort(List.of(
                td(RACE_DATE.minusDays(1), 4, "GOOD"),        // 2026-05-31
                td(RACE_DATE.minusDays(3), 3, "EXCELLENT"),   // 2026-05-29
                td(RACE_DATE.minusDays(7), 5, "GOOD"),        // 2026-05-25 (lower bound)
                td(RACE_DATE.minusDays(8), 10, "BAD")         // 2026-05-24 (excluded)
        ));
        RacePerformanceComposer composer = composer(
                new FakeRaceResultQueryPort(List.of(race)), new FakeTrainingLoadQueryPort(List.of()), diary);

        PreRaceSubjectiveState subjective = composer
                .composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE)
                .get(0).preRaceSubjectiveState();

        assertTrue(subjective.available());
        assertEquals(3, subjective.entryCount());
        assertEquals(4.0, subjective.averagePerceivedEffort()); // (4+3+5)/3
        assertEquals("GOOD", subjective.dominantSubjectiveState()); // 2×GOOD > 1×EXCELLENT
    }

    @Test
    void subjectiveState_partialCoverage_degradesEachMetricIndependently() {
        RaceResultSummary race = race(RACE_DATE, "Marathon");
        // One entry has an RPE but no state; another has a state but no RPE.
        FakeTrainingDayQueryPort diary = new FakeTrainingDayQueryPort(List.of(
                td(RACE_DATE.minusDays(2), 6, null),      // RPE only
                td(RACE_DATE.minusDays(4), null, "POOR")  // state only
        ));
        RacePerformanceComposer composer = composer(
                new FakeRaceResultQueryPort(List.of(race)), new FakeTrainingLoadQueryPort(List.of()), diary);

        PreRaceSubjectiveState subjective = composer
                .composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE)
                .get(0).preRaceSubjectiveState();

        assertTrue(subjective.available());
        assertEquals(2, subjective.entryCount());
        assertEquals(6.0, subjective.averagePerceivedEffort()); // only the RPE-bearing entry
        assertEquals("POOR", subjective.dominantSubjectiveState()); // only the state-bearing entry
    }

    @Test
    void subjectiveState_zeroCoverage_returnsUnavailable_doesNotThrow() {
        RaceResultSummary race = race(RACE_DATE, "Marathon");
        // Entries exist, but on race day (excluded) and one day before the window
        // (RACE_DATE−8, excluded) — nothing lands inside [RACE_DATE−7, RACE_DATE−1].
        FakeTrainingDayQueryPort diary = new FakeTrainingDayQueryPort(List.of(
                td(RACE_DATE, 9, "BAD"),
                td(RACE_DATE.minusDays(8), 2, "EXCELLENT")
        ));
        RacePerformanceComposer composer = composer(
                new FakeRaceResultQueryPort(List.of(race)), new FakeTrainingLoadQueryPort(List.of()), diary);

        RacePerformanceContext ctx = composer
                .composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE).get(0);

        assertSubjectiveUnavailable(ctx.preRaceSubjectiveState());
    }

    @Test
    void subjectiveState_dominantStateTie_brokenByMostRecentEntry() {
        RaceResultSummary race = race(RACE_DATE, "Marathon");
        // GOOD and POOR each appear once; the more recent entry (POOR, closer to
        // race day) wins the tie.
        FakeTrainingDayQueryPort diary = new FakeTrainingDayQueryPort(List.of(
                td(RACE_DATE.minusDays(6), null, "GOOD"),  // 2026-05-26
                td(RACE_DATE.minusDays(1), null, "POOR")   // 2026-05-31 (most recent)
        ));
        RacePerformanceComposer composer = composer(
                new FakeRaceResultQueryPort(List.of(race)), new FakeTrainingLoadQueryPort(List.of()), diary);

        PreRaceSubjectiveState subjective = composer
                .composeForAthlete(ATHLETE, RACE_DATE.minusMonths(6), RACE_DATE)
                .get(0).preRaceSubjectiveState();

        assertTrue(subjective.available());
        assertEquals(2, subjective.entryCount());
        assertNull(subjective.averagePerceivedEffort()); // no RPE recorded
        assertEquals("POOR", subjective.dominantSubjectiveState());
    }

    // --- helpers ---------------------------------------------------------

    private static RacePerformanceComposer composer(RaceResultQueryPort races, TrainingLoadQueryPort load) {
        // Most tests exercise only training-load composition; no diary entries.
        return composer(races, load, new FakeTrainingDayQueryPort(List.of()));
    }

    private static RacePerformanceComposer composer(RaceResultQueryPort races, TrainingLoadQueryPort load,
                                                    TrainingDayQueryPort days) {
        return new RacePerformanceComposer(FakeInstance.of(races), FakeInstance.of(load), FakeInstance.of(days));
    }

    private static RaceResultSummary race(LocalDate date, String name) {
        return new RaceResultSummary(
                UUID.randomUUID(), date, name, 42195.0, 10800, 10500, 3, null, null, null, null);
    }

    private static TrainingDaySummary td(LocalDate date, Integer rpe, String state) {
        return new TrainingDaySummary(date, rpe, state, null, null, null, null);
    }

    private static TrainingLoad load(LocalDate date, double ctl, double atl, double tsb) {
        return new TrainingLoad(ATHLETE, date, 50.0, ctl, atl, tsb);
    }

    private static void assertAvailable(TrainingLoadPoint point, LocalDate requested, LocalDate actual,
                                        double ctl, double atl, double tsb) {
        assertTrue(point.available(), "expected available point");
        assertEquals(requested, point.requestedDate());
        assertEquals(actual, point.actualDate());
        assertEquals(ctl, point.ctl());
        assertEquals(atl, point.atl());
        assertEquals(tsb, point.tsb());
    }

    private static void assertInsufficient(TrainingLoadPoint point, LocalDate requested) {
        assertFalse(point.available(), "expected insufficient-data point");
        assertEquals(requested, point.requestedDate());
        assertNull(point.actualDate());
        assertNull(point.ctl());
        assertNull(point.atl());
        assertNull(point.tsb());
    }

    private static void assertSubjectiveUnavailable(PreRaceSubjectiveState subjective) {
        assertFalse(subjective.available(), "expected unavailable subjective state");
        assertEquals(0, subjective.entryCount());
        assertNull(subjective.averagePerceivedEffort());
        assertNull(subjective.dominantSubjectiveState());
    }

    private static final class FakeRaceResultQueryPort implements RaceResultQueryPort {
        private final List<RaceResultSummary> races;

        private FakeRaceResultQueryPort(List<RaceResultSummary> races) {
            this.races = races;
        }

        @Override
        public List<RaceResultSummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to) {
            return races;
        }
    }

    private static final class FakeTrainingLoadQueryPort implements TrainingLoadQueryPort {
        private final List<TrainingLoad> load;

        private FakeTrainingLoadQueryPort(List<TrainingLoad> load) {
            this.load = load;
        }

        @Override
        public List<TrainingLoad> listRecentByAthlete(UUID athleteId, LocalDate from) {
            return load;
        }
    }

    private static final class FakeTrainingDayQueryPort implements TrainingDayQueryPort {
        private final List<TrainingDaySummary> days;

        private FakeTrainingDayQueryPort(List<TrainingDaySummary> days) {
            this.days = days;
        }

        @Override
        public List<TrainingDaySummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to) {
            return days;
        }
    }
}
