package com.zensyra.collector.suunto.workout;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.identity.SuuntoActivityIdentityService;
import com.zensyra.collector.suunto.mapping.SuuntoTrainingStress;
import com.zensyra.collector.suunto.mapping.SuuntoWorkoutMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Upserts one Suunto workout into {@code suunto_workouts}. Existence is
 * checked via the natural key ({@code workoutKey}), never a pre-initialized
 * id field (#36) — a re-sync updates the same row, so the per-workout TSS
 * contribution can never be counted twice.
 *
 * <p>Unlike Strava's {@code ActivityUpsertService} (which resolves the
 * canonical reference after persisting), the identity is resolved FIRST:
 * the #6 mapper needs the canonical {@code ActivityId} as input, and an
 * unregistered Suunto user should fail before any row is written. Same
 * transaction either way, so the observable outcome is identical.
 */
@ApplicationScoped
public class SuuntoWorkoutUpsertService {

    private static final Logger LOG = Logger.getLogger(SuuntoWorkoutUpsertService.class);

    @Inject
    SuuntoWorkoutRepository workoutRepository;

    @Inject
    SuuntoWorkoutMapper workoutMapper;

    @Inject
    SuuntoActivityIdentityService activityIdentityService;

    @Transactional
    public void upsert(String suuntoUser, SuuntoWorkoutDto dto) {
        ActivityReference reference =
                activityIdentityService.resolveOrCreateReference(suuntoUser, dto.workoutKey());
        UUID canonicalActivityId = reference.getTrainingSessionId();

        Activity activity = workoutMapper.toActivity(canonicalActivityId, dto);
        ActivityMetrics metrics = workoutMapper.toActivityMetrics(canonicalActivityId, dto);
        SuuntoTrainingStress stress = workoutMapper.selectTrainingStress(dto).orElse(null);

        SuuntoWorkout workout = workoutRepository.findByWorkoutKey(dto.workoutKey())
                .orElseGet(SuuntoWorkout::new);

        workout.setWorkoutKey(dto.workoutKey());
        workout.setSuuntoUser(suuntoUser);
        workout.setActivityId(dto.activityId());
        workout.setSportType(activity.sportType());
        workout.setTotalDistance(activity.distanceMeters());
        workout.setMovingTimeSecs(activity.movingTimeSecs());
        workout.setStartDate(activity.startDate());
        workout.setTotalElevationGain(activity.totalElevationGain());
        workout.setAverageHeartrate(activity.averageHeartrate());
        workout.setAverageWatts(activity.averageWatts());
        workout.setNormalizedPower(metrics.normalizedPower());
        workout.setVariabilityIndex(metrics.variabilityIndex());
        workout.setEfficiencyFactor(metrics.efficiencyFactor());
        workout.setIntensityFactor(metrics.intensityFactor());
        workout.setTss(stress != null ? stress.trainingStressScore() : null);
        workout.setTssCalculationMethod(stress != null ? stress.calculationMethod() : null);
        workout.setLastModified(dto.lastModified());

        workoutRepository.persist(workout);

        LOG.debugf("Suunto workout upserted — workoutKey: '%s', user: '%s'",
                dto.workoutKey(), suuntoUser);
    }
}
