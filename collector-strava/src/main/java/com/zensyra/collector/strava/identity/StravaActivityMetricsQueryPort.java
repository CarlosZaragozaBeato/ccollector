package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link ActivityMetricsQueryPort}.
 *
 * <p>Resolution path is the reverse of {@link StravaActivityQueryPort}: the
 * canonical {@code ActivityId} ({@code TrainingSession.id}) is resolved to
 * its backing {@link ActivityReference} for this source, whose
 * {@code externalActivityId} is the legacy Strava activity id. That id is
 * used to find the {@code Activity} row in {@code collector-strava}, whose
 * own database PK (not its Strava id) is what
 * {@code activity_metrics.activity_id} actually references.
 *
 * <p>The resolved {@link ActivityReference#getAthleteId()} is checked
 * against the {@code athleteId} argument before anything is returned — the
 * port's contract requires this, not just this adapter's discretion. An
 * activity id that exists but belongs to a different athlete must behave
 * identically to one that does not exist at all.
 *
 * <p>Today exactly one {@link ActivityReference} backs any given
 * {@code TrainingSession} (Strava is the only connected source); this
 * adapter takes the first one found rather than assuming a list of size
 * one, so it keeps working unchanged once a second source can also back the
 * same session — see ADR-002 addendum.
 */
@ApplicationScoped
public class StravaActivityMetricsQueryPort implements ActivityMetricsQueryPort {

    private final ActivityReferenceRepository activityReferenceRepository;
    private final ActivityRepository activityRepository;
    private final ActivityMetricsRepository activityMetricsRepository;

    @Inject
    public StravaActivityMetricsQueryPort(
            ActivityReferenceRepository activityReferenceRepository,
            ActivityRepository activityRepository,
            ActivityMetricsRepository activityMetricsRepository) {
        this.activityReferenceRepository = activityReferenceRepository;
        this.activityRepository = activityRepository;
        this.activityMetricsRepository = activityMetricsRepository;
    }

    @Override
    public Optional<com.zensyra.collector.query.model.ActivityMetrics> getByActivityId(
            UUID athleteId, UUID activityId) {
        Optional<ActivityReference> reference = resolveOwnedReference(athleteId, activityId);
        if (reference.isEmpty()) {
            return Optional.empty();
        }

        Optional<com.zensyra.collector.strava.activity.Activity> stravaActivity =
                activityRepository.findByStravaId(Long.parseLong(reference.get().getExternalActivityId()));
        if (stravaActivity.isEmpty()) {
            return Optional.empty();
        }

        return activityMetricsRepository.findByActivityId(stravaActivity.get().getId())
                .map(metrics -> new com.zensyra.collector.query.model.ActivityMetrics(
                        activityId,
                        metrics.getNormalizedPower(),
                        metrics.getVariabilityIndex(),
                        metrics.getEfficiencyFactor(),
                        metrics.getIntensityFactor()
                ));
    }

    private Optional<ActivityReference> resolveOwnedReference(UUID athleteId, UUID activityId) {
        List<ActivityReference> references = activityReferenceRepository.findByTrainingSessionId(activityId);
        return references.stream()
                .filter(reference -> reference.getAthleteId().equals(athleteId))
                .findFirst();
    }
}
