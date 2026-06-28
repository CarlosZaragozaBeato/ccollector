package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StravaActivityQueryPortTest {

    private static final UUID ATHLETE_ID = UUID.randomUUID();
    private static final String STRAVA_EXTERNAL_USER_ID = "98765";

    private final IntegrationAccountRepository accountRepository = mock(IntegrationAccountRepository.class);
    private final ActivityReferenceResolver referenceResolver = mock(ActivityReferenceResolver.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final StravaActivityQueryPort port = new StravaActivityQueryPort(
            accountRepository, referenceResolver, activityRepository);

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoStravaAccount() {
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of());

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldQueryActivityRepositoryWithStravaAthleteIdAndForwardAllParameters() {
        IntegrationAccount account = connectedStravaAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-01T00:00:00Z");
        when(activityRepository.findPagedByAthleteId(
                Long.parseLong(STRAVA_EXTERNAL_USER_ID), "Run", from, to, 10, 5))
                .thenReturn(List.of());

        port.listByAthlete(ATHLETE_ID, "Run", from, to, 10, 5);

        verify(activityRepository).findPagedByAthleteId(
                Long.parseLong(STRAVA_EXTERNAL_USER_ID), "Run", from, to, 10, 5);
    }

    @Test
    void shouldSilentlyDiscardActivityWithNoCanonicalReference() {
        IntegrationAccount account = connectedStravaAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));

        com.zensyra.collector.strava.activity.Activity stravaActivity = stravaActivity(1001L, 555L);
        when(activityRepository.findPagedByAthleteId(
                eq(Long.parseLong(STRAVA_EXTERNAL_USER_ID)), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of(stravaActivity));
        when(referenceResolver.resolveCanonicalActivityId(account.getId(), "555")).thenReturn(null);

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnCanonicalActivityIdAndNeverStravaId() {
        UUID canonicalActivityId = UUID.randomUUID();
        IntegrationAccount account = connectedStravaAccount();
        when(accountRepository.findByAthleteId(ATHLETE_ID)).thenReturn(List.of(account));

        com.zensyra.collector.strava.activity.Activity stravaActivity = stravaActivity(1001L, 555L);
        stravaActivity.setName("Morning Run");
        stravaActivity.setSportType("Run");

        when(activityRepository.findPagedByAthleteId(
                eq(Long.parseLong(STRAVA_EXTERNAL_USER_ID)), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of(stravaActivity));
        when(referenceResolver.resolveCanonicalActivityId(account.getId(), "555"))
                .thenReturn(canonicalActivityId);

        List<Activity> result = port.listByAthlete(ATHLETE_ID, null, null, null, 0, 20);

        assertEquals(1, result.size());
        Activity activity = result.get(0);
        // Canonical id must be the TrainingSession UUID, not a Strava numeric id.
        assertEquals(canonicalActivityId, activity.activityId());
        assertEquals("Morning Run", activity.name());
        assertEquals("Run", activity.sportType());
    }

    // --- helpers ---

    private IntegrationAccount connectedStravaAccount() {
        return new IntegrationAccount(ATHLETE_ID, IntegrationSource.STRAVA, STRAVA_EXTERNAL_USER_ID);
    }

    private com.zensyra.collector.strava.activity.Activity stravaActivity(Long dbId, Long stravaId) {
        com.zensyra.collector.strava.activity.Activity a =
                new com.zensyra.collector.strava.activity.Activity();
        a.setId(dbId);
        a.setStravaId(stravaId);
        a.setDistance(BigDecimal.valueOf(10000));
        a.setMovingTime(3600);
        a.setStartDate(Instant.parse("2025-03-01T08:00:00Z"));
        return a;
    }
}
