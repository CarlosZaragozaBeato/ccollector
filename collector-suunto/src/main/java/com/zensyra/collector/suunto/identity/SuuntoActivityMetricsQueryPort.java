package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Suunto implementation of {@link ActivityMetricsQueryPort}.
 *
 * <p>Mirrors {@link StravaActivityMetricsQueryPort}'s contract exactly —
 * canonical-UUID-in, {@link ActivityMetrics}-out, unconnected athlete →
 * empty, orphan row (reference exists but workout row is gone) → empty.
 *
 * <p>Resolution path: canonical {@code athleteId} → Suunto
 * {@link IntegrationAccount} → filter {@link ActivityReference} list by
 * both athlete ownership AND this account's id (so a Strava reference for
 * the same session is never mistaken for a Suunto reference) →
 * {@code workoutKey} ({@code externalActivityId}) →
 * {@link com.zensyra.collector.suunto.workout.SuuntoWorkout} row →
 * map four metric fields to the neutral read-model.
 */
@ApplicationScoped
public class SuuntoActivityMetricsQueryPort implements ActivityMetricsQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityReferenceRepository activityReferenceRepository;
    private final SuuntoWorkoutRepository workoutRepository;

    @Inject
    public SuuntoActivityMetricsQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityReferenceRepository activityReferenceRepository,
            SuuntoWorkoutRepository workoutRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityReferenceRepository = activityReferenceRepository;
        this.workoutRepository = workoutRepository;
    }

    @Override
    public Optional<ActivityMetrics> getByActivityId(UUID athleteId, UUID activityId) {
        Optional<IntegrationAccount> account = resolveSuuntoAccount(athleteId);
        if (account.isEmpty()) {
            return Optional.empty();
        }

        // Filter by athleteId AND integrationAccountId to avoid picking up a
        // Strava reference for the same canonical session.
        List<ActivityReference> references =
                activityReferenceRepository.findByTrainingSessionId(activityId);
        Optional<ActivityReference> reference = references.stream()
                .filter(ref -> ref.getAthleteId().equals(athleteId)
                        && ref.getIntegrationAccountId().equals(account.get().getId()))
                .findFirst();
        if (reference.isEmpty()) {
            return Optional.empty();
        }

        return workoutRepository.findByWorkoutKey(reference.get().getExternalActivityId())
                .map(workout -> new ActivityMetrics(
                        activityId,
                        workout.getNormalizedPower(),
                        workout.getVariabilityIndex(),
                        workout.getEfficiencyFactor(),
                        workout.getIntensityFactor()
                ));
    }

    private Optional<IntegrationAccount> resolveSuuntoAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(a -> a.getSource() == IntegrationSource.SUUNTO)
                .findFirst();
    }
}
