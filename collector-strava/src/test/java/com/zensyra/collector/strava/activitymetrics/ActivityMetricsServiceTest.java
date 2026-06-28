package com.zensyra.collector.strava.activitymetrics;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.identity.StravaActivityIdentityService;
import com.zensyra.collector.strava.stream.ActivityStreamRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityMetricsServiceTest {

    @Test
    void shouldResolveCanonicalReferenceBeforePersistingMetrics() {
        ActivityRepository activities = mock(ActivityRepository.class);
        ActivityStreamRepository streams = mock(ActivityStreamRepository.class);
        ActivityMetricsRepository metrics = mock(ActivityMetricsRepository.class);
        StravaActivityIdentityService activityIdentity = mock(StravaActivityIdentityService.class);

        ActivityMetricsService service = new ActivityMetricsService();
        service.activityRepository = activities;
        service.streamRepository = streams;
        service.metricsRepository = metrics;
        service.activityIdentityService = activityIdentity;

        Activity activity = new Activity();
        activity.setId(100L);
        activity.setAthleteId(42L);
        activity.setStravaId(999L);
        activity.setAverageWatts(BigDecimal.valueOf(200));

        when(activities.findActivitiesNeedingMetricsComputation(42L)).thenReturn(List.of(activity));
        when(streams.findWattsByActivityIdOrdered(100L)).thenReturn(Collections.nCopies(30, 200));
        when(metrics.findByIdOptional(100L)).thenReturn(Optional.empty());

        service.computeAndUpsert(42L);

        verify(activityIdentity).resolveOrCreateReference(42L, 999L);
        verify(metrics).persist(any(ActivityMetrics.class));
    }
}
