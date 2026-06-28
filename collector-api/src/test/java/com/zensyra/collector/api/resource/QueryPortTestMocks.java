package com.zensyra.collector.api.resource;

import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import com.zensyra.collector.query.port.AthleteStatsQueryPort;
import com.zensyra.collector.query.port.BestEffortQueryPort;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import static org.mockito.Mockito.mock;

/**
 * Registers Mockito mocks as real CDI beans for the four
 * {@code collector-query} ports that have no implementation in
 * {@code collector-api}'s own module — their only implementation lives in
 * {@code collector-strava}, which {@code collector-api} no longer depends
 * on (see Issue A-2).
 *
 * <p>{@code @InjectMock} can substitute an existing bean, but it cannot
 * mock an interface with zero CDI implementations: there is nothing for
 * Quarkus to substitute. This producer supplies that missing bean for the
 * test context only — it never runs outside tests because
 * {@link io.quarkus.test.Mock} beans are excluded from the production
 * build. {@link ActivityMetricsQueryPort} needs no entry here:
 * {@code AthleteActivitiesResourceTest} mocks the concrete
 * {@code ActivityQueryComposer} instead, which {@code @InjectMock} can
 * already substitute without this workaround.
 */
public class QueryPortTestMocks {

    @Mock
    @ApplicationScoped
    ActivityMetricsQueryPort activityMetricsQueryPort() {
        return mock(ActivityMetricsQueryPort.class);
    }

    @Mock
    @ApplicationScoped
    BestEffortQueryPort bestEffortQueryPort() {
        return mock(BestEffortQueryPort.class);
    }

    @Mock
    @ApplicationScoped
    AthleteStatsQueryPort athleteStatsQueryPort() {
        return mock(AthleteStatsQueryPort.class);
    }

    @Mock
    @ApplicationScoped
    TrainingLoadQueryPort trainingLoadQueryPort() {
        return mock(TrainingLoadQueryPort.class);
    }
}
