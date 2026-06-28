package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.QueryResult;
import com.zensyra.collector.query.model.SourceFailure;
import com.zensyra.collector.query.port.ActivityQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityQueryComposerTest {

    @Test
    void shouldPassThroughSingleSourceUnchanged() {
        UUID athleteId = UUID.randomUUID();
        Activity activity1 = activityAt(Instant.parse("2026-06-10T08:00:00Z"));
        Activity activity2 = activityAt(Instant.parse("2026-06-12T08:00:00Z"));
        FakeActivityQueryPort onlySource = new FakeActivityQueryPort(List.of(activity1, activity2));
        ActivityQueryComposer composer = new ActivityQueryComposer(
                FakeInstance.of(onlySource));

        List<Activity> result = composer.listByAthlete(athleteId, null, null, null, 0, 10);

        // Most recent first.
        assertEquals(List.of(activity2, activity1), result);
    }

    @Test
    void shouldMergeAndSortAcrossMultipleFakeSources() {
        UUID athleteId = UUID.randomUUID();
        Activity stravaOldest = activityAt(Instant.parse("2026-06-01T08:00:00Z"));
        Activity stravaNewest = activityAt(Instant.parse("2026-06-15T08:00:00Z"));
        Activity suuntoMiddle = activityAt(Instant.parse("2026-06-10T08:00:00Z"));

        FakeActivityQueryPort stravaLikeSource =
                new FakeActivityQueryPort(List.of(stravaOldest, stravaNewest));
        FakeActivityQueryPort suuntoLikeSource =
                new FakeActivityQueryPort(List.of(suuntoMiddle));

        ActivityQueryComposer composer = new ActivityQueryComposer(
                FakeInstance.of(stravaLikeSource, suuntoLikeSource));

        List<Activity> result = composer.listByAthlete(athleteId, null, null, null, 0, 10);

        assertEquals(List.of(stravaNewest, suuntoMiddle, stravaOldest), result);
    }

    @Test
    void shouldApplyOffsetAndLimitAfterMerging() {
        UUID athleteId = UUID.randomUUID();
        Activity a1 = activityAt(Instant.parse("2026-06-01T00:00:00Z"));
        Activity a2 = activityAt(Instant.parse("2026-06-02T00:00:00Z"));
        Activity a3 = activityAt(Instant.parse("2026-06-03T00:00:00Z"));
        Activity a4 = activityAt(Instant.parse("2026-06-04T00:00:00Z"));

        FakeActivityQueryPort sourceA = new FakeActivityQueryPort(List.of(a1, a3));
        FakeActivityQueryPort sourceB = new FakeActivityQueryPort(List.of(a2, a4));

        ActivityQueryComposer composer = new ActivityQueryComposer(
                FakeInstance.of(sourceA, sourceB));

        // Newest-first order is a4, a3, a2, a1. Page 1 (offset=1, limit=2) -> a3, a2.
        List<Activity> result = composer.listByAthlete(athleteId, null, null, null, 1, 2);

        assertEquals(List.of(a3, a2), result);
    }

    @Test
    void shouldReturnEmptyWhenNoSourcesRegistered() {
        UUID athleteId = UUID.randomUUID();
        ActivityQueryComposer composer = new ActivityQueryComposer(FakeInstance.of());

        List<Activity> result = composer.listByAthlete(athleteId, null, null, null, 0, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void listByAthleteShouldPropagateExceptionFromAFailingSource() {
        // The /v1 contract: a failing source still fails the whole request.
        // This must never change — listByAthleteWithFailures exists
        // specifically so this method's behavior doesn't have to.
        UUID athleteId = UUID.randomUUID();
        FailingActivityQueryPort failingSource = new FailingActivityQueryPort("Strava down");
        ActivityQueryComposer composer = new ActivityQueryComposer(FakeInstance.of(failingSource));

        assertThrows(RuntimeException.class,
                () -> composer.listByAthlete(athleteId, null, null, null, 0, 10));
    }

    @Test
    void listByAthleteWithFailuresShouldReturnCompleteResultWhenAllSourcesSucceed() {
        UUID athleteId = UUID.randomUUID();
        Activity activity = activityAt(Instant.parse("2026-06-10T08:00:00Z"));
        FakeActivityQueryPort source = new FakeActivityQueryPort(List.of(activity));
        ActivityQueryComposer composer = new ActivityQueryComposer(FakeInstance.of(source));

        QueryResult<Activity> result = composer.listByAthleteWithFailures(
                athleteId, null, null, null, 0, 10);

        assertFalse(result.isPartial());
        assertEquals(List.of(activity), result.data());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void listByAthleteWithFailuresShouldRecordFailureAndReturnEmptyWhenOnlySourceFails() {
        UUID athleteId = UUID.randomUUID();
        FailingActivityQueryPort failingSource = new FailingActivityQueryPort("Strava down");
        ActivityQueryComposer composer = new ActivityQueryComposer(FakeInstance.of(failingSource));

        QueryResult<Activity> result = composer.listByAthleteWithFailures(
                athleteId, null, null, null, 0, 10);

        assertTrue(result.isPartial());
        assertTrue(result.data().isEmpty());
        assertEquals(1, result.failures().size());
        SourceFailure failure = result.failures().get(0);
        assertEquals("FailingActivityQueryPort", failure.sourceName());
        assertEquals("Strava down", failure.reason());
    }

    @Test
    void listByAthleteWithFailuresShouldKeepSucceedingSourceDataWhenAnotherSourceFails() {
        UUID athleteId = UUID.randomUUID();
        Activity activity = activityAt(Instant.parse("2026-06-10T08:00:00Z"));
        FakeActivityQueryPort succeedingSource = new FakeActivityQueryPort(List.of(activity));
        FailingActivityQueryPort failingSource = new FailingActivityQueryPort("timeout");
        ActivityQueryComposer composer = new ActivityQueryComposer(
                FakeInstance.of(succeedingSource, failingSource));

        QueryResult<Activity> result = composer.listByAthleteWithFailures(
                athleteId, null, null, null, 0, 10);

        assertTrue(result.isPartial());
        assertEquals(List.of(activity), result.data());
        assertEquals(1, result.failures().size());
        assertEquals("timeout", result.failures().get(0).reason());
    }

    private static Activity activityAt(Instant startDate) {
        return new Activity(
                UUID.randomUUID(),
                "Test activity",
                "Run",
                10000.0,
                3600,
                startDate,
                100.0,
                150.0,
                200.0
        );
    }

    /**
     * Minimal stand-in for a per-source {@link ActivityQueryPort}. Used here
     * to exercise the composer's merge logic against a second, non-Strava
     * source before any real second adapter exists — see ADR-002 addendum.
     */
    private static final class FakeActivityQueryPort implements ActivityQueryPort {
        private final List<Activity> activities;

        private FakeActivityQueryPort(List<Activity> activities) {
            this.activities = activities;
        }

        @Override
        public List<Activity> listByAthlete(
                UUID athleteId, String sportType, Instant from, Instant to, int offset, int limit) {
            int fromIndex = Math.min(offset, activities.size());
            int toIndex = Math.min(offset + limit, activities.size());
            return activities.subList(fromIndex, toIndex);
        }
    }

    /**
     * A source that always throws, simulating a connectivity failure or
     * timeout against a real integration, to exercise both the strict
     * ({@code listByAthlete}) and tolerant ({@code listByAthleteWithFailures})
     * composition paths.
     */
    private static final class FailingActivityQueryPort implements ActivityQueryPort {
        private final String message;

        private FailingActivityQueryPort(String message) {
            this.message = message;
        }

        @Override
        public List<Activity> listByAthlete(
                UUID athleteId, String sportType, Instant from, Instant to, int offset, int limit) {
            throw new RuntimeException(message);
        }
    }
}
