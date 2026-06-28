package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoad;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaTrainingLoadQueryPortTest {

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoStravaAccount() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository athleteTrainingLoadRepository = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadQueryPort port = new StravaTrainingLoadQueryPort(
                integrationAccountRepository, athleteTrainingLoadRepository);

        UUID athleteId = UUID.randomUUID();
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of());

        List<TrainingLoad> result = port.listRecentByAthlete(athleteId, LocalDate.now().minusDays(30));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoRecordsExist() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository athleteTrainingLoadRepository = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadQueryPort port = new StravaTrainingLoadQueryPort(
                integrationAccountRepository, athleteTrainingLoadRepository);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));
        when(athleteTrainingLoadRepository.findRecentByAthleteId(eq(111L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        List<TrainingLoad> result = port.listRecentByAthlete(athleteId, LocalDate.now().minusDays(30));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldTranslateFieldsDirectlyWithCanonicalAthleteId() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        AthleteTrainingLoadRepository athleteTrainingLoadRepository = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadQueryPort port = new StravaTrainingLoadQueryPort(
                integrationAccountRepository, athleteTrainingLoadRepository);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));

        AthleteTrainingLoad stravaRecord = new AthleteTrainingLoad();
        stravaRecord.setAthleteId(111L);
        stravaRecord.setDate(LocalDate.of(2026, 6, 27));
        stravaRecord.setTssDay(45.0);
        stravaRecord.setCtl(52.1);
        stravaRecord.setAtl(48.3);
        stravaRecord.setTsb(3.8);

        LocalDate from = LocalDate.of(2026, 5, 28);
        when(athleteTrainingLoadRepository.findRecentByAthleteId(eq(111L), eq(from)))
                .thenReturn(List.of(stravaRecord));

        List<TrainingLoad> result = port.listRecentByAthlete(athleteId, from);

        assertEquals(1, result.size());
        TrainingLoad load = result.get(0);
        assertEquals(athleteId, load.athleteId());
        assertEquals(LocalDate.of(2026, 6, 27), load.date());
        assertEquals(45.0, load.tssDay());
        assertEquals(52.1, load.ctl());
        assertEquals(48.3, load.atl());
        assertEquals(3.8, load.tsb());
    }
}
