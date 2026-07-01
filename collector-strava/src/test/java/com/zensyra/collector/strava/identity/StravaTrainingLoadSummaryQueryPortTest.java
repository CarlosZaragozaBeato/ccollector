package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.TrainingLoadSummary;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoad;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaTrainingLoadSummaryQueryPortTest {

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoStravaAccount() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadSummaryQueryPort port = new StravaTrainingLoadSummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of());

        List<TrainingLoadSummary> result = port.listByAthlete(
                athleteId, LocalDate.now().minusWeeks(4), LocalDate.now(), Granularity.WEEKLY);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAggregateDailyRowsIntoWeeklyBuckets() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadSummaryQueryPort port = new StravaTrainingLoadSummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "42");
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of(account));

        // Mon 2025-01-06 and Wed 2025-01-08 — same ISO week; Mon 2025-01-13 — next week
        AthleteTrainingLoad mon = load(6, 80.0, 55.0, 40.0, 15.0);
        AthleteTrainingLoad wed = load(8, 60.0, 56.0, 45.0, 11.0);
        AthleteTrainingLoad nextMon = load(13, 70.0, 57.0, 42.0, 15.0);
        when(repo.findByAthleteIdAndDateRange(eq(42L), any(), any()))
                .thenReturn(List.of(mon, wed, nextMon));

        List<TrainingLoadSummary> result = port.listByAthlete(
                athleteId, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 14), Granularity.WEEKLY);

        assertEquals(2, result.size());

        TrainingLoadSummary week1 = result.get(0);
        assertEquals(LocalDate.of(2025, 1, 6), week1.periodStart());  // ISO Monday
        assertEquals(Granularity.WEEKLY, week1.granularity());
        assertEquals(140.0, week1.totalTss(), 0.001);                  // 80 + 60
        assertEquals(56.0, week1.ctlEnd(), 0.001);                     // last day of week (Wed)
        assertEquals(45.0, week1.atlEnd(), 0.001);
        assertEquals(11.0, week1.tsbEnd(), 0.001);

        TrainingLoadSummary week2 = result.get(1);
        assertEquals(LocalDate.of(2025, 1, 13), week2.periodStart());
        assertEquals(70.0, week2.totalTss(), 0.001);
        assertEquals(athleteId, week2.athleteId());
    }

    @Test
    void shouldAggregateDailyRowsIntoMonthlyBuckets() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadSummaryQueryPort port = new StravaTrainingLoadSummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "99");
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of(account));

        AthleteTrainingLoad jan15 = load(15, 50.0, 60.0, 50.0, 10.0);   // January
        AthleteTrainingLoad jan28 = load(28, 70.0, 62.0, 48.0, 14.0);   // January
        AthleteTrainingLoad feb03 = load(32, 40.0, 63.0, 46.0, 17.0);   // February (day 32 = Feb 1 + 1)
        when(repo.findByAthleteIdAndDateRange(eq(99L), any(), any()))
                .thenReturn(List.of(jan15, jan28, feb03));

        List<TrainingLoadSummary> result = port.listByAthlete(
                athleteId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 28), Granularity.MONTHLY);

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2025, 1, 1), result.get(0).periodStart());
        assertEquals(120.0, result.get(0).totalTss(), 0.001);           // 50 + 70
        assertEquals(LocalDate.of(2025, 2, 1), result.get(1).periodStart());
        assertEquals(40.0, result.get(1).totalTss(), 0.001);
    }

    // --- helpers ---

    private AthleteTrainingLoad load(int dayOfYear, double tss, double ctl, double atl, double tsb) {
        AthleteTrainingLoad row = new AthleteTrainingLoad();
        row.setDate(LocalDate.ofYearDay(2025, dayOfYear));
        row.setTssDay(tss);
        row.setCtl(ctl);
        row.setAtl(atl);
        row.setTsb(tsb);
        return row;
    }
}
