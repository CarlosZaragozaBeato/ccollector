package com.zensyra.collector.strava.identity;

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
    void shouldReturnEmptyListWhenRepositoryHasNoRows() {
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadQueryPort port = new StravaTrainingLoadQueryPort(repo);

        UUID athleteId = UUID.randomUUID();
        LocalDate from = LocalDate.now().minusDays(30);
        when(repo.findRecentByAthleteId(athleteId, from)).thenReturn(List.of());

        List<TrainingLoad> result = port.listRecentByAthlete(athleteId, from);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldTranslateFieldsDirectlyWithCanonicalAthleteId() {
        AthleteTrainingLoadRepository repo = mock(AthleteTrainingLoadRepository.class);
        StravaTrainingLoadQueryPort port = new StravaTrainingLoadQueryPort(repo);

        UUID athleteId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 5, 28);

        AthleteTrainingLoad record = new AthleteTrainingLoad();
        record.setAthleteId(athleteId);
        record.setDate(LocalDate.of(2026, 6, 27));
        record.setTssDay(45.0);
        record.setCtl(52.1);
        record.setAtl(48.3);
        record.setTsb(3.8);

        when(repo.findRecentByAthleteId(eq(athleteId), eq(from))).thenReturn(List.of(record));

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
