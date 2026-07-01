package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.query.model.Lap;
import com.zensyra.collector.query.port.LapQueryPort;
import com.zensyra.collector.strava.lap.ActivityLap;
import com.zensyra.collector.strava.lap.ActivityLapRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link LapQueryPort}.
 *
 * <p>Resolution path mirrors {@link StravaActivityMetricsQueryPort}: the
 * canonical {@code ActivityId} ({@code TrainingSession.id}) is resolved to
 * its backing {@link ActivityReference}, whose {@code externalActivityId} is
 * the Strava activity id used to query {@code activity_laps}.
 *
 * <p>Ownership is enforced by filtering references to those whose
 * {@code athleteId} matches the caller — an activity id that exists but
 * belongs to a different athlete returns an empty list.
 */
@ApplicationScoped
public class StravaLapQueryPort implements LapQueryPort {

    private final ActivityReferenceRepository activityReferenceRepository;
    private final ActivityLapRepository activityLapRepository;

    @Inject
    public StravaLapQueryPort(
            ActivityReferenceRepository activityReferenceRepository,
            ActivityLapRepository activityLapRepository) {
        this.activityReferenceRepository = activityReferenceRepository;
        this.activityLapRepository = activityLapRepository;
    }

    @Override
    public List<Lap> listByActivity(UUID athleteId, UUID activityId) {
        Optional<ActivityReference> reference = resolveOwnedReference(athleteId, activityId);
        if (reference.isEmpty()) {
            return List.of();
        }

        Long activityStravaId = Long.parseLong(reference.get().getExternalActivityId());
        return activityLapRepository.findByActivityStravaIdOrderByLapIndex(activityStravaId)
                .stream()
                .map(lap -> toReadModel(activityId, lap))
                .toList();
    }

    // Same pattern as StravaActivityMetricsQueryPort.resolveOwnedReference.
    private Optional<ActivityReference> resolveOwnedReference(UUID athleteId, UUID activityId) {
        return activityReferenceRepository.findByTrainingSessionId(activityId).stream()
                .filter(ref -> ref.getAthleteId().equals(athleteId))
                .findFirst();
    }

    private Lap toReadModel(UUID activityId, ActivityLap lap) {
        return new Lap(
                activityId,
                lap.getLapIndex(),
                lap.getName(),
                lap.getDistance() != null ? lap.getDistance().doubleValue() : null,
                lap.getMovingTime(),
                lap.getAverageSpeed() != null ? lap.getAverageSpeed().doubleValue() : null,
                lap.getAverageHeartrate() != null ? lap.getAverageHeartrate().doubleValue() : null,
                lap.getMaxHeartrate() != null ? lap.getMaxHeartrate().doubleValue() : null,
                lap.getTotalElevationGain() != null ? lap.getTotalElevationGain().doubleValue() : null,
                lap.getPaceZone()
        );
    }
}
