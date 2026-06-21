package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.port.ActivityQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}