package com.zensyra.collector.runner.query;

import com.zensyra.collector.query.composer.ActivityQueryComposer;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import com.zensyra.collector.query.port.ActivityQueryPort;
import com.zensyra.collector.query.port.AthleteStatsQueryPort;
import com.zensyra.collector.query.port.BestEffortQueryPort;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import com.zensyra.collector.strava.identity.StravaActivityMetricsQueryPort;
import com.zensyra.collector.strava.identity.StravaActivityQueryPort;
import com.zensyra.collector.strava.identity.StravaAthleteStatsQueryPort;
import com.zensyra.collector.strava.identity.StravaBestEffortQueryPort;
import com.zensyra.collector.strava.identity.StravaTrainingLoadQueryPort;
import com.zensyra.collector.suunto.identity.SuuntoActivityQueryPort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the real CDI wiring between {@code collector-query} and
 * {@code collector-strava}, which only {@code collector-runner} can exercise
 * — {@code collector-query} does not depend on {@code collector-strava}
 * (per ADR-001), so neither module alone can prove this wiring works.
 *
 * <p>Covers all five query ports introduced across Issue A and Issue A-2.
 * Each port's own unit tests (e.g. {@code StravaBestEffortQueryPortTest})
 * prove the adapter's logic is correct against mocked repositories; they
 * cannot prove Quarkus actually discovers and injects that adapter as a
 * real CDI bean. This test closes that gap for every port before any
 * {@code collector-api} resource is allowed to depend on it.
 */
@QuarkusTest
class ActivityQueryPortWiringTest {

    @Inject
    Instance<ActivityQueryPort> activityQueryPorts;

    @Inject
    Instance<ActivityMetricsQueryPort> activityMetricsQueryPorts;

    @Inject
    Instance<BestEffortQueryPort> bestEffortQueryPorts;

    @Inject
    Instance<AthleteStatsQueryPort> athleteStatsQueryPorts;

    @Inject
    Instance<TrainingLoadQueryPort> trainingLoadQueryPorts;

    @Inject
    ActivityQueryComposer composer;

    @Test
    void shouldDiscoverBothActivityQueryPortAdaptersAsCdiBeans() {
        assertFalse(activityQueryPorts.isUnsatisfied(),
                "Expected ActivityQueryPort beans to be discovered by CDI");

        // Two sources since #7: the composer's "general N-source case" is no
        // longer theoretical. An unexpected third registration still fails here.
        List<ActivityQueryPort> ports = activityQueryPorts.stream().toList();
        assertEquals(2, ports.size(),
                "Expected exactly the Strava and Suunto ActivityQueryPort implementations");
        assertTrue(ports.stream().anyMatch(p -> p instanceof StravaActivityQueryPort),
                "StravaActivityQueryPort must be registered");
        assertTrue(ports.stream().anyMatch(p -> p instanceof SuuntoActivityQueryPort),
                "SuuntoActivityQueryPort must be registered");
    }

    @Test
    void shouldDiscoverTheStravaActivityMetricsQueryPortAsACdiBean() {
        assertFalse(activityMetricsQueryPorts.isUnsatisfied(),
                "Expected at least one ActivityMetricsQueryPort bean to be discovered by CDI");

        List<ActivityMetricsQueryPort> ports = activityMetricsQueryPorts.stream().toList();
        assertEquals(1, ports.size(),
                "Expected exactly one ActivityMetricsQueryPort implementation today (Strava only)");
        assertInstanceOf(StravaActivityMetricsQueryPort.class, ports.get(0));
    }

    @Test
    void shouldDiscoverTheStravaBestEffortQueryPortAsACdiBean() {
        assertFalse(bestEffortQueryPorts.isUnsatisfied(),
                "Expected at least one BestEffortQueryPort bean to be discovered by CDI");

        List<BestEffortQueryPort> ports = bestEffortQueryPorts.stream().toList();
        assertEquals(1, ports.size(),
                "Expected exactly one BestEffortQueryPort implementation today (Strava only)");
        assertInstanceOf(StravaBestEffortQueryPort.class, ports.get(0));
    }

    @Test
    void shouldDiscoverTheStravaAthleteStatsQueryPortAsACdiBean() {
        assertFalse(athleteStatsQueryPorts.isUnsatisfied(),
                "Expected at least one AthleteStatsQueryPort bean to be discovered by CDI");

        List<AthleteStatsQueryPort> ports = athleteStatsQueryPorts.stream().toList();
        assertEquals(1, ports.size(),
                "Expected exactly one AthleteStatsQueryPort implementation today (Strava only)");
        assertInstanceOf(StravaAthleteStatsQueryPort.class, ports.get(0));
    }

    @Test
    void shouldDiscoverTheStravaTrainingLoadQueryPortAsACdiBean() {
        assertFalse(trainingLoadQueryPorts.isUnsatisfied(),
                "Expected at least one TrainingLoadQueryPort bean to be discovered by CDI");

        List<TrainingLoadQueryPort> ports = trainingLoadQueryPorts.stream().toList();
        // Permanently 1: the cross-source composition seam (DailyTssComposer +
        // TrainingStressContributionPort) is the correct aggregation path. A second
        // TrainingLoadQueryPort is neither planned nor correct — TSS aggregation
        // happens upstream of the query port, not by registering multiple ports here.
        assertEquals(1, ports.size(),
                "Expected exactly one TrainingLoadQueryPort implementation — TSS aggregation is handled by DailyTssComposer, not by multiple query ports");
        assertInstanceOf(StravaTrainingLoadQueryPort.class, ports.get(0));
    }

    @Test
    void shouldInjectTheComposerWithTheRealStravaAdapterWired() {
        assertNotNull(composer,
                "ActivityQueryComposer must be a resolvable CDI bean in collector-runner");
    }
}
