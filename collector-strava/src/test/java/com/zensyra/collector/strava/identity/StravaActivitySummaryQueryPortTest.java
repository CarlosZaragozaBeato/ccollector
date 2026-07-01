package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.PeriodSummary;
import com.zensyra.collector.strava.summary.ActivitySummaryRow;
import com.zensyra.collector.strava.summary.ActivitySummaryViewRepository;
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

class StravaActivitySummaryQueryPortTest {

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoStravaAccount() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivitySummaryViewRepository repo = mock(ActivitySummaryViewRepository.class);
        StravaActivitySummaryQueryPort port = new StravaActivitySummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of());

        List<PeriodSummary> result = port.listByAthlete(
                athleteId, LocalDate.now().minusWeeks(4), LocalDate.now(), Granularity.WEEKLY);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldTranslateRowsToReadModelWithCanonicalAthleteId() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivitySummaryViewRepository repo = mock(ActivitySummaryViewRepository.class);
        StravaActivitySummaryQueryPort port = new StravaActivitySummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "555");
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of(account));

        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        ActivitySummaryRow row = new ActivitySummaryRow(
                LocalDate.of(2025, 1, 6), 3, 42000.0, 14400, 350.0);
        when(repo.findByAthleteId(eq(555L), eq(from), eq(to), eq(Granularity.WEEKLY)))
                .thenReturn(List.of(row));

        List<PeriodSummary> result = port.listByAthlete(athleteId, from, to, Granularity.WEEKLY);

        assertEquals(1, result.size());
        PeriodSummary summary = result.get(0);
        assertEquals(athleteId, summary.athleteId());
        assertEquals(LocalDate.of(2025, 1, 6), summary.periodStart());
        assertEquals(Granularity.WEEKLY, summary.granularity());
        assertEquals(3, summary.numActivities());
        assertEquals(42000.0, summary.totalDistanceMeters());
        assertEquals(14400, summary.totalMovingTimeSecs());
        assertEquals(350.0, summary.totalElevationGainMeters());
    }

    @Test
    void shouldPassGranularityToRepository() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivitySummaryViewRepository repo = mock(ActivitySummaryViewRepository.class);
        StravaActivitySummaryQueryPort port = new StravaActivitySummaryQueryPort(accounts, repo);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "777");
        when(accounts.findByAthleteId(athleteId)).thenReturn(List.of(account));
        when(repo.findByAthleteId(eq(777L), any(), any(), eq(Granularity.MONTHLY))).thenReturn(List.of());

        List<PeriodSummary> result = port.listByAthlete(
                athleteId, LocalDate.now().minusMonths(6), LocalDate.now(), Granularity.MONTHLY);

        assertTrue(result.isEmpty());
    }
}
