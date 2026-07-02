package com.zensyra.collector.strava.activitymetrics;

import com.zensyra.collector.core.identity.AthleteProfile;
import com.zensyra.collector.core.identity.AthleteProfileRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.identity.StravaActivityIdentityService;
import com.zensyra.collector.strava.stream.ActivityStreamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityMetricsServiceTest {

    private static final Long STRAVA_ATHLETE_ID = 42L;
    private static final UUID CANONICAL_ATHLETE_ID = UUID.randomUUID();

    @Test
    void shouldResolveCanonicalReferenceBeforePersistingMetrics() {
        Fixture f = new Fixture();
        f.stubActivityWithPower();
        f.stubNoFtp();

        f.service.computeAndUpsert(STRAVA_ATHLETE_ID);

        verify(f.activityIdentity).resolveOrCreateReference(STRAVA_ATHLETE_ID, 999L);
        verify(f.metrics).persist(any(ActivityMetrics.class));
    }

    @Test
    void shouldComputeIntensityFactorWhenFtpAndNpPresent() {
        Fixture f = new Fixture();
        f.stubActivityWithPower();
        f.stubFtp(250);

        ActivityMetrics persisted = f.captureAfterCompute();

        // NP of a flat 200 W stream is 200; IF = 200 / 250 = 0.8
        assertNotNull(persisted.getNormalizedPower());
        assertEquals(0, persisted.getIntensityFactor().compareTo(new BigDecimal("0.8")));
    }

    @Test
    void shouldLeaveIntensityFactorNullWhenFtpMissing() {
        Fixture f = new Fixture();
        f.stubActivityWithPower();
        f.stubNoFtp();

        ActivityMetrics persisted = f.captureAfterCompute();

        assertNotNull(persisted.getNormalizedPower());
        assertNull(persisted.getIntensityFactor());
    }

    @Test
    void shouldLeaveIntensityFactorNullWhenFtpIsZero() {
        Fixture f = new Fixture();
        f.stubActivityWithPower();
        f.stubFtp(0);

        ActivityMetrics persisted = f.captureAfterCompute();

        assertNull(persisted.getIntensityFactor());
    }

    @Test
    void shouldLeaveIntensityFactorNullWhenNoCanonicalAccount() {
        Fixture f = new Fixture();
        f.stubActivityWithPower();
        when(f.integrationAccounts.findBySourceAndExternalUserId(
                IntegrationSource.STRAVA, String.valueOf(STRAVA_ATHLETE_ID)))
                .thenReturn(Optional.empty());

        ActivityMetrics persisted = f.captureAfterCompute();

        assertNull(persisted.getIntensityFactor());
    }

    @Test
    void shouldNotPersistMetricsWhenNoPowerData() {
        Fixture f = new Fixture();
        Activity activity = buildActivity();
        when(f.activities.findActivitiesNeedingMetricsComputation(STRAVA_ATHLETE_ID))
                .thenReturn(List.of(activity));
        // fewer than the 30-second rolling window → NP cannot be computed
        when(f.streams.findWattsByActivityIdOrdered(100L)).thenReturn(Collections.nCopies(10, 200));
        f.stubFtp(250);

        f.service.computeAndUpsert(STRAVA_ATHLETE_ID);

        verify(f.metrics, org.mockito.Mockito.never()).persist(any(ActivityMetrics.class));
    }

    // --- helpers ---

    private static Activity buildActivity() {
        Activity activity = new Activity();
        activity.setId(100L);
        activity.setAthleteId(STRAVA_ATHLETE_ID);
        activity.setStravaId(999L);
        activity.setAverageWatts(BigDecimal.valueOf(200));
        return activity;
    }

    /**
     * Bundles the service under test with its mocked collaborators and the
     * common stubbing helpers used across the intensity-factor cases.
     */
    private static final class Fixture {
        final ActivityRepository activities = mock(ActivityRepository.class);
        final ActivityStreamRepository streams = mock(ActivityStreamRepository.class);
        final ActivityMetricsRepository metrics = mock(ActivityMetricsRepository.class);
        final StravaActivityIdentityService activityIdentity = mock(StravaActivityIdentityService.class);
        final IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        final AthleteProfileRepository athleteProfiles = mock(AthleteProfileRepository.class);
        final ActivityMetricsService service = new ActivityMetricsService();

        Fixture() {
            service.activityRepository = activities;
            service.streamRepository = streams;
            service.metricsRepository = metrics;
            service.activityIdentityService = activityIdentity;
            service.integrationAccountRepository = integrationAccounts;
            service.athleteProfileRepository = athleteProfiles;
        }

        void stubActivityWithPower() {
            Activity activity = buildActivity();
            when(activities.findActivitiesNeedingMetricsComputation(STRAVA_ATHLETE_ID))
                    .thenReturn(List.of(activity));
            when(streams.findWattsByActivityIdOrdered(100L)).thenReturn(Collections.nCopies(30, 200));
            when(metrics.findByIdOptional(100L)).thenReturn(Optional.empty());
        }

        void stubFtp(Integer ftpWatts) {
            IntegrationAccount account = new IntegrationAccount(
                    CANONICAL_ATHLETE_ID, IntegrationSource.STRAVA, String.valueOf(STRAVA_ATHLETE_ID));
            when(integrationAccounts.findBySourceAndExternalUserId(
                    IntegrationSource.STRAVA, String.valueOf(STRAVA_ATHLETE_ID)))
                    .thenReturn(Optional.of(account));
            AthleteProfile profile = new AthleteProfile();
            profile.setFtpWatts(ftpWatts);
            when(athleteProfiles.findByIdOptional(CANONICAL_ATHLETE_ID)).thenReturn(Optional.of(profile));
        }

        void stubNoFtp() {
            IntegrationAccount account = new IntegrationAccount(
                    CANONICAL_ATHLETE_ID, IntegrationSource.STRAVA, String.valueOf(STRAVA_ATHLETE_ID));
            when(integrationAccounts.findBySourceAndExternalUserId(
                    IntegrationSource.STRAVA, String.valueOf(STRAVA_ATHLETE_ID)))
                    .thenReturn(Optional.of(account));
            AthleteProfile profile = new AthleteProfile(); // ftpWatts null
            when(athleteProfiles.findByIdOptional(CANONICAL_ATHLETE_ID)).thenReturn(Optional.of(profile));
        }

        ActivityMetrics captureAfterCompute() {
            service.computeAndUpsert(STRAVA_ATHLETE_ID);
            ArgumentCaptor<ActivityMetrics> captor = ArgumentCaptor.forClass(ActivityMetrics.class);
            verify(metrics).persist(captor.capture());
            return captor.getValue();
        }
    }
}
