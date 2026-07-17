package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.port.ActivityQueryPort;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Suunto implementation of {@link ActivityQueryPort} — the second source
 * adapter the {@code ActivityQueryComposer} was written for ("the general
 * N-source case"). Registered automatically via CDI; zero changes in
 * collector-query.
 *
 * <p>Mirrors {@code StravaActivityQueryPort}: canonical {@code AthleteId}
 * (UUID) → this athlete's Suunto {@link IntegrationAccount}; its
 * {@code externalUserId} (the Suunto {@code user} string) keys this module's
 * own {@code suunto_workouts} rows; each row translates back to its
 * canonical {@code ActivityId} through its {@link ActivityReference} —
 * never exposing a Suunto identifier to the caller. The reference lookup is
 * inlined rather than extracted to a resolver bean because this is the only
 * Suunto adapter today (Strava extracted {@code ActivityReferenceResolver}
 * once a second adapter needed it — same trigger applies here).
 */
@ApplicationScoped
public class SuuntoActivityQueryPort implements ActivityQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityReferenceRepository activityReferenceRepository;
    private final SuuntoWorkoutRepository workoutRepository;

    @Inject
    public SuuntoActivityQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityReferenceRepository activityReferenceRepository,
            SuuntoWorkoutRepository workoutRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityReferenceRepository = activityReferenceRepository;
        this.workoutRepository = workoutRepository;
    }

    @Override
    public List<Activity> listByAthlete(
            UUID athleteId,
            String sportType,
            Instant from,
            Instant to,
            int offset,
            int limit) {
        Optional<IntegrationAccount> account = resolveSuuntoAccount(athleteId);
        if (account.isEmpty()) {
            // No connected Suunto account: this source simply does not apply
            // to this athlete. Empty is correct, not an error.
            return List.of();
        }

        List<SuuntoWorkout> workouts = workoutRepository.findPagedByUser(
                account.get().getExternalUserId(), sportType, from, to, offset, limit);

        List<Activity> result = new ArrayList<>(workouts.size());
        for (SuuntoWorkout workout : workouts) {
            UUID canonicalActivityId = activityReferenceRepository
                    .findByIntegrationAccountIdAndExternalActivityId(
                            account.get().getId(), workout.getWorkoutKey())
                    .map(ActivityReference::getTrainingSessionId)
                    .orElse(null);
            if (canonicalActivityId == null) {
                // Every row synced through SuuntoWorkoutUpsertService has a
                // reference (identity is resolved before persisting), so this
                // should not happen — but skipping is safer than exposing a
                // null canonical id, same policy as StravaActivityQueryPort.
                continue;
            }
            result.add(toReadModel(canonicalActivityId, workout));
        }
        return result;
    }

    // Same fetch-all-and-filter approach as StravaActivityQueryPort — see
    // the rationale there; revisit only if profiling says so.
    private Optional<IntegrationAccount> resolveSuuntoAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.SUUNTO)
                .findFirst();
    }

    private Activity toReadModel(UUID canonicalActivityId, SuuntoWorkout workout) {
        return new Activity(
                canonicalActivityId,
                // Suunto's list payload carries no workout name (#6)
                null,
                workout.getSportType(),
                workout.getTotalDistance(),
                workout.getMovingTimeSecs(),
                workout.getStartDate(),
                workout.getTotalElevationGain(),
                workout.getAverageHeartrate(),
                workout.getAverageWatts()
        );
    }
}
