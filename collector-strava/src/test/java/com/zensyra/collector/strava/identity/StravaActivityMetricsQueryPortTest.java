package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaActivityMetricsQueryPortTest {

    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final UUID CANONICAL_ACTIVITY_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String EXTERNAL_ACTIVITY_ID = "777";
    private static final Long STRAVA_DB_PK = 42L;

    private final ActivityReferenceRepository referenceRepository = mock(ActivityReferenceRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ActivityMetricsRepository metricsRepository = mock(ActivityMetricsRepository.class);
    private final StravaActivityMetricsQueryPort port = new StravaActivityMetricsQueryPort(
            referenceRepository, activityRepository, metricsRepository);

    @Test
    void shouldReturnEmptyWhenNoActivityReferenceExists() {
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenActivityBelongsToDifferentAthlete() {
        UUID otherAthleteId = UUID.randomUUID();
        ActivityReference ref = new ActivityReference(
                otherAthleteId, CANONICAL_ACTIVITY_ID, ACCOUNT_ID, EXTERNAL_ACTIVITY_ID);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenReferenceExistsButStravaActivityIsGone() {
        ActivityReference ref = new ActivityReference(
                ATHLETE_ID, CANONICAL_ACTIVITY_ID, ACCOUNT_ID, EXTERNAL_ACTIVITY_ID);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));
        when(activityRepository.findByStravaId(Long.parseLong(EXTERNAL_ACTIVITY_ID)))
                .thenReturn(Optional.empty());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenActivityExistsButMetricsNotYetComputed() {
        ActivityReference ref = new ActivityReference(
                ATHLETE_ID, CANONICAL_ACTIVITY_ID, ACCOUNT_ID, EXTERNAL_ACTIVITY_ID);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));

        com.zensyra.collector.strava.activity.Activity stravaActivity =
                new com.zensyra.collector.strava.activity.Activity();
        stravaActivity.setId(STRAVA_DB_PK);
        stravaActivity.setStravaId(Long.parseLong(EXTERNAL_ACTIVITY_ID));
        when(activityRepository.findByStravaId(Long.parseLong(EXTERNAL_ACTIVITY_ID)))
                .thenReturn(Optional.of(stravaActivity));
        when(metricsRepository.findByActivityId(STRAVA_DB_PK)).thenReturn(Optional.empty());

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnReadModelWithCanonicalActivityIdAndAllFourMetricFields() {
        ActivityReference ref = new ActivityReference(
                ATHLETE_ID, CANONICAL_ACTIVITY_ID, ACCOUNT_ID, EXTERNAL_ACTIVITY_ID);
        when(referenceRepository.findByTrainingSessionId(CANONICAL_ACTIVITY_ID)).thenReturn(List.of(ref));

        com.zensyra.collector.strava.activity.Activity stravaActivity =
                new com.zensyra.collector.strava.activity.Activity();
        stravaActivity.setId(STRAVA_DB_PK);
        stravaActivity.setStravaId(Long.parseLong(EXTERNAL_ACTIVITY_ID));
        when(activityRepository.findByStravaId(Long.parseLong(EXTERNAL_ACTIVITY_ID)))
                .thenReturn(Optional.of(stravaActivity));

        com.zensyra.collector.strava.activitymetrics.ActivityMetrics metricsEntity =
                new com.zensyra.collector.strava.activitymetrics.ActivityMetrics();
        metricsEntity.setActivityId(STRAVA_DB_PK);
        metricsEntity.setNormalizedPower(BigDecimal.valueOf(250));
        metricsEntity.setVariabilityIndex(BigDecimal.valueOf(1.05));
        metricsEntity.setEfficiencyFactor(BigDecimal.valueOf(1.10));
        metricsEntity.setIntensityFactor(BigDecimal.valueOf(0.85));
        when(metricsRepository.findByActivityId(STRAVA_DB_PK)).thenReturn(Optional.of(metricsEntity));

        Optional<ActivityMetrics> result = port.getByActivityId(ATHLETE_ID, CANONICAL_ACTIVITY_ID);

        assertTrue(result.isPresent());
        ActivityMetrics metrics = result.get();
        // activityId must be the canonical TrainingSession UUID, not the Strava DB PK.
        assertEquals(CANONICAL_ACTIVITY_ID, metrics.activityId());
        assertEquals(BigDecimal.valueOf(250), metrics.normalizedPower());
        assertEquals(BigDecimal.valueOf(1.05), metrics.variabilityIndex());
        assertEquals(BigDecimal.valueOf(1.10), metrics.efficiencyFactor());
        assertEquals(BigDecimal.valueOf(0.85), metrics.intensityFactor());
    }
}
