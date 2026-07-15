package com.zensyra.collector.suunto.mapping;

/**
 * Per-workout training-stress contribution selected from Suunto's own
 * computed values (see {@link SuuntoWorkoutMapper#selectTrainingStress}).
 *
 * <p>This is deliberately not the neutral {@code TrainingLoad} read-model:
 * that model is a per-athlete daily row (tssDay plus CTL/ATL/TSB exponential
 * moving averages over 42/7 days) and cannot be produced from one workout.
 * The sync job (#7) aggregates these contributions into daily rows using the
 * real Suunto TSS — never the 0.75 fallback intensity factor Strava's
 * estimator needs.
 *
 * <p>Values are kept unrounded exactly as Suunto reported them;
 * {@code intensityFactor} is null for the HR and MET calculation methods.
 */
public record SuuntoTrainingStress(
        String calculationMethod,
        Double trainingStressScore,
        Double intensityFactor,
        Double normalizedPower
) {
}
