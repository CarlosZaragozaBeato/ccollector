package com.zensyra.collector.suunto.mapping;

import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.suunto.api.dto.SuuntoSummaryExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoTssDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps a source-specific {@link SuuntoWorkoutDto} to the provider-neutral
 * read-models in collector-query. Pure translation — no persistence, no
 * identity resolution: the caller (#7's sync job) supplies the canonical
 * {@code ActivityId} ({@code TrainingSession.id}).
 *
 * <p>Suunto already reports its own TSS/IF/NP per workout (all four
 * calculation methods in {@code tssList}), so unlike the Strava pipeline
 * nothing is recomputed from streams or FTP here: the values are selected by
 * {@link #selectTrainingStress} and mapped straight through. Only
 * variabilityIndex (NP / avg power) and efficiencyFactor (NP / avg HR) are
 * derived — trivial arithmetic over Suunto's own outputs, using the same
 * formulas and 6-significant-digit HALF_UP rounding as
 * {@code ActivityMetricsService} so the two sources stay comparable.
 *
 * <p>Suunto's units match what this system stores for Strava (meters, m/s,
 * epoch-based timestamps), so no unit conversion happens anywhere below.
 */
@ApplicationScoped
public class SuuntoWorkoutMapper {

    /**
     * Preferred TSS calculation methods, best first. POWER and PACE carry a
     * real intensityFactor; HR and MET legitimately do not — a null IF never
     * disqualifies an entry, only a null trainingStressScore does.
     */
    private static final List<String> METHOD_PRIORITY = List.of("POWER", "PACE", "HR", "MET");

    private static final MathContext METRICS_PRECISION = new MathContext(6, RoundingMode.HALF_UP);

    public Activity toActivity(UUID activityId, SuuntoWorkoutDto workout) {
        return new Activity(
                activityId,
                // the /v2/workouts list payload carries no workout name
                null,
                SuuntoSportType.nameFor(workout.activityId()),
                workout.totalDistance(),
                // totalTime is elapsed seconds with millisecond precision; Suunto
                // exposes no separate moving time, so elapsed is the honest value
                workout.totalTime() != null ? (int) Math.round(workout.totalTime()) : null,
                workout.startTime() != null ? Instant.ofEpochMilli(workout.startTime()) : null,
                workout.totalAscent(),
                averageHeartrate(workout),
                averageWatts(workout));
    }

    public ActivityMetrics toActivityMetrics(UUID activityId, SuuntoWorkoutDto workout) {
        SuuntoTrainingStress selected = selectTrainingStress(workout).orElse(null);
        Double normalizedPower = selected != null ? selected.normalizedPower() : null;
        Double intensityFactor = selected != null ? selected.intensityFactor() : null;

        BigDecimal variabilityIndex = null;
        BigDecimal efficiencyFactor = null;
        if (normalizedPower != null) {
            Double avgWatts = averageWatts(workout);
            if (avgWatts != null && avgWatts > 0) {
                variabilityIndex = round(normalizedPower / avgWatts);
            }
            Double avgHeartrate = averageHeartrate(workout);
            if (avgHeartrate != null && avgHeartrate > 0) {
                efficiencyFactor = round(normalizedPower / avgHeartrate);
            }
        }

        return new ActivityMetrics(
                activityId,
                round(normalizedPower),
                variabilityIndex,
                efficiencyFactor,
                round(intensityFactor));
    }

    /**
     * Selects the training-stress entry this system trusts for the workout:
     * POWER &gt; PACE &gt; HR &gt; MET over {@code tssList}, falling back to the
     * single {@code tss} object when the list is absent. An entry qualifies as
     * soon as its trainingStressScore is non-null. If only unknown future
     * methods carry a score, the first of those wins rather than dropping the
     * workout's load. Empty only when Suunto computed no TSS at all.
     */
    public Optional<SuuntoTrainingStress> selectTrainingStress(SuuntoWorkoutDto workout) {
        List<SuuntoTssDto> candidates = workout.tssList() != null && !workout.tssList().isEmpty()
                ? workout.tssList()
                : workout.tss() != null ? List.of(workout.tss()) : List.of();

        for (String method : METHOD_PRIORITY) {
            for (SuuntoTssDto candidate : candidates) {
                if (method.equals(candidate.calculationMethod()) && candidate.trainingStressScore() != null) {
                    return Optional.of(toTrainingStress(candidate));
                }
            }
        }
        return candidates.stream()
                .filter(candidate -> candidate.trainingStressScore() != null)
                .findFirst()
                .map(this::toTrainingStress);
    }

    private SuuntoTrainingStress toTrainingStress(SuuntoTssDto tss) {
        return new SuuntoTrainingStress(
                tss.calculationMethod(),
                tss.trainingStressScore(),
                tss.intensityFactor(),
                tss.normalizedPower());
    }

    private Double averageHeartrate(SuuntoWorkoutDto workout) {
        if (workout.hrdata() == null) {
            return null;
        }
        // workoutAvgHR and avg carry the same value in real payloads; prefer
        // the unambiguous naming, tolerate either being absent
        Integer avg = workout.hrdata().workoutAvgHR() != null
                ? workout.hrdata().workoutAvgHR()
                : workout.hrdata().avg();
        return avg != null ? avg.doubleValue() : null;
    }

    private Double averageWatts(SuuntoWorkoutDto workout) {
        if (workout.avgPower() != null) {
            return workout.avgPower();
        }
        SuuntoSummaryExtensionDto summary = findSummaryExtension(workout);
        return summary != null ? summary.avgPower() : null;
    }

    private SuuntoSummaryExtensionDto findSummaryExtension(SuuntoWorkoutDto workout) {
        if (workout.extensions() == null) {
            return null;
        }
        return workout.extensions().stream()
                .filter(SuuntoSummaryExtensionDto.class::isInstance)
                .map(SuuntoSummaryExtensionDto.class::cast)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal round(Double value) {
        return value != null
                ? BigDecimal.valueOf(value).round(METRICS_PRECISION)
                : null;
    }
}
