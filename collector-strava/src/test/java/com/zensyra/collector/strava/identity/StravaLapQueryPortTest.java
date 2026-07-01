package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.query.model.Lap;
import com.zensyra.collector.strava.lap.ActivityLap;
import com.zensyra.collector.strava.lap.ActivityLapRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaLapQueryPortTest {

    @Test
    void shouldReturnEmptyListWhenActivityNotOwned() {
        ActivityReferenceRepository references = mock(ActivityReferenceRepository.class);
        ActivityLapRepository laps = mock(ActivityLapRepository.class);
        StravaLapQueryPort port = new StravaLapQueryPort(references, laps);

        UUID athleteId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID differentAthlete = UUID.randomUUID();

        ActivityReference ref = buildReference(differentAthlete, activityId, "987");
        when(references.findByTrainingSessionId(activityId)).thenReturn(List.of(ref));

        List<Lap> result = port.listByActivity(athleteId, activityId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoReferenceExists() {
        ActivityReferenceRepository references = mock(ActivityReferenceRepository.class);
        ActivityLapRepository laps = mock(ActivityLapRepository.class);
        StravaLapQueryPort port = new StravaLapQueryPort(references, laps);

        UUID activityId = UUID.randomUUID();
        when(references.findByTrainingSessionId(activityId)).thenReturn(List.of());

        List<Lap> result = port.listByActivity(UUID.randomUUID(), activityId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMapLapFieldsAndKeepCanonicalActivityId() {
        ActivityReferenceRepository references = mock(ActivityReferenceRepository.class);
        ActivityLapRepository laps = mock(ActivityLapRepository.class);
        StravaLapQueryPort port = new StravaLapQueryPort(references, laps);

        UUID athleteId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        ActivityReference ref = buildReference(athleteId, activityId, "12345");
        when(references.findByTrainingSessionId(activityId)).thenReturn(List.of(ref));

        ActivityLap lap = buildLap(12345L, 0, "Lap 1", 1000.0, 240, 4.17, 145.0, 162.0, 10.0, 2);
        when(laps.findByActivityStravaIdOrderByLapIndex(12345L)).thenReturn(List.of(lap));

        List<Lap> result = port.listByActivity(athleteId, activityId);

        assertEquals(1, result.size());
        Lap l = result.get(0);
        assertEquals(activityId, l.activityId());
        assertEquals(0, l.lapIndex());
        assertEquals("Lap 1", l.name());
        assertEquals(1000.0, l.distanceMeters());
        assertEquals(240, l.movingTimeSecs());
        assertEquals(4.17, l.averageSpeedMps(), 0.001);
        assertEquals(145.0, l.averageHeartrate(), 0.001);
        assertEquals(162.0, l.maxHeartrate(), 0.001);
        assertEquals(10.0, l.elevationGainMeters(), 0.001);
        assertEquals(2, l.paceZone());
    }

    // --- helpers ---

    private ActivityReference buildReference(UUID athleteId, UUID trainingSessionId, String externalActivityId) {
        return new ActivityReference(athleteId, trainingSessionId, UUID.randomUUID(), externalActivityId);
    }

    private ActivityLap buildLap(Long stravaId, int lapIndex, String name,
                                  double distM, int movingTime, double speed,
                                  double avgHr, double maxHr, double elevation, int paceZone) {
        ActivityLap lap = new ActivityLap();
        lap.setActivityStravaId(stravaId);
        lap.setLapIndex(lapIndex);
        lap.setName(name);
        lap.setDistance(BigDecimal.valueOf(distM));
        lap.setMovingTime(movingTime);
        lap.setAverageSpeed(BigDecimal.valueOf(speed));
        lap.setAverageHeartrate(BigDecimal.valueOf(avgHr));
        lap.setMaxHeartrate(BigDecimal.valueOf(maxHr));
        lap.setTotalElevationGain(BigDecimal.valueOf(elevation));
        lap.setPaceZone(paceZone);
        return lap;
    }
}
