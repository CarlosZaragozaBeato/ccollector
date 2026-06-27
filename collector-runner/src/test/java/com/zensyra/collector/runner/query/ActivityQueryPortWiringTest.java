package com.zensyra.collector.runner.query;

import com.zensyra.collector.query.composer.ActivityQueryComposer;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import com.zensyra.collector.query.port.ActivityQueryPort;
import com.zensyra.collector.strava.identity.StravaActivityMetricsQueryPort;
import com.zensyra.collector.strava.identity.StravaActivityQueryPort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the real CDI wiring between {@code collector-query} and
 * {@code collector-strava}, which only {@code collector-runner} can exercise
 * — {@code collector-query} does not depend on {@code collector-strava}
 * (per ADR-001), so neither module alone can prove this wiring works.
 *
 * <p>{@link com.zensyra.collector.query.composer.ActivityQueryComposerTest}
 * proves the composer's merge logic is correct using a hand-written
 * {@code Instance<T>} stand-in; it cannot prove Quarkus actually discovers
 * and injects {@link StravaActivityQueryPort} as a real CDI bean. This test
 * closes that gap before any {@code collector-api} resource is migrated to
 * depend on the composer — if the wiring were broken here, a resource-level
 * test would only show an empty result with no obvious cause.
 */
@QuarkusTest
class ActivityQueryPortWiringTest {

    @Inject
    Instance<ActivityQueryPort> activityQueryPorts;

    @Inject
    Instance<ActivityMetricsQueryPort> activityMetricsQueryPorts;

    @Inject
    ActivityQueryComposer composer;

    @Test
    void shouldDiscoverTheStravaActivityQueryPortAsACdiBean() {
        assertFalse(activityQueryPorts.isUnsatisfied(),
                "Expected at least one ActivityQueryPort bean to be discovered by CDI");

        List<ActivityQueryPort> ports = activityQueryPorts.stream().toList();
        assertEquals(1, ports.size(),
                "Expected exactly one ActivityQueryPort implementation today (Strava only)");
        assertInstanceOf(StravaActivityQueryPort.class, ports.get(0));
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
    void shouldInjectTheComposerWithTheRealStravaAdapterWired() {
        assertNotNull(composer,
                "ActivityQueryComposer must be a resolvable CDI bean in collector-runner");
    }
}
