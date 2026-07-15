package com.zensyra.collector.suunto.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.model.ActivityMetrics;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure mapper tests — no Quarkus context needed. Workout inputs are built by
 * deserializing JSON snippets so field names stay verbatim with the real API,
 * matching how the DTOs were derived in the first place.
 */
class SuuntoWorkoutMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID CANONICAL_ID = UUID.fromString("7e6f7f21-9a3f-4a54-9c93-2f6a7f0f2f11");

    private final SuuntoWorkoutMapper mapper = new SuuntoWorkoutMapper();

    // ── scenario 1: all four calculation methods present ────────────────────

    @Test
    void selectsPowerMethodWhenAllFourArePresent() throws Exception {
        SuuntoWorkoutDto workout = workout("""
                {
                  "tss": { "calculationMethod": "HR", "trainingStressScore": 52.586864,
                           "intensityFactor": null, "normalizedPower": 262.49988 },
                  "tssList": [
                    { "calculationMethod": "HR",    "trainingStressScore": 52.586864, "intensityFactor": null,      "normalizedPower": 262.49988 },
                    { "calculationMethod": "POWER", "trainingStressScore": 96.928955, "intensityFactor": 1.0499995, "normalizedPower": 262.49988 },
                    { "calculationMethod": "PACE",  "trainingStressScore": 66.44755,  "intensityFactor": 0.9505806, "normalizedPower": 262.49988 },
                    { "calculationMethod": "MET",   "trainingStressScore": 49.702503, "intensityFactor": null,      "normalizedPower": null }
                  ]
                }
                """);

        SuuntoTrainingStress selected = mapper.selectTrainingStress(workout).orElseThrow();

        // POWER wins even though Suunto's own single tss defaulted to HR
        assertEquals("POWER", selected.calculationMethod());
        assertEquals(96.928955, selected.trainingStressScore());
        assertEquals(1.0499995, selected.intensityFactor());
        assertEquals(262.49988, selected.normalizedPower());
    }

    @Test
    void selectsPaceWhenPowerIsAbsent() throws Exception {
        SuuntoWorkoutDto workout = workout("""
                {
                  "tssList": [
                    { "calculationMethod": "MET",  "trainingStressScore": 49.702503, "intensityFactor": null,      "normalizedPower": null },
                    { "calculationMethod": "PACE", "trainingStressScore": 66.44755,  "intensityFactor": 0.9505806, "normalizedPower": 262.49988 }
                  ]
                }
                """);

        assertEquals("PACE", mapper.selectTrainingStress(workout).orElseThrow().calculationMethod());
    }

    // ── scenario 2: only HR/MET — TSS populates, IF stays null ──────────────

    @Test
    void hrOnlyWorkoutKeepsTssAndLeavesIntensityFactorNull() throws Exception {
        SuuntoWorkoutDto workout = workout("""
                {
                  "avgPower": 261.3,
                  "hrdata": { "workoutAvgHR": 147 },
                  "tssList": [
                    { "calculationMethod": "HR",  "trainingStressScore": 52.586864, "intensityFactor": null, "normalizedPower": 262.49988 },
                    { "calculationMethod": "MET", "trainingStressScore": 49.702503, "intensityFactor": null, "normalizedPower": null }
                  ]
                }
                """);

        SuuntoTrainingStress selected = mapper.selectTrainingStress(workout).orElseThrow();
        assertEquals("HR", selected.calculationMethod());
        assertEquals(52.586864, selected.trainingStressScore());
        assertNull(selected.intensityFactor());

        ActivityMetrics metrics = mapper.toActivityMetrics(CANONICAL_ID, workout);
        assertNull(metrics.intensityFactor());
        // NP and the derived ratios still populate from the HR entry
        assertDecimal("262.500", metrics.normalizedPower());
        assertDecimal("1.00459", metrics.variabilityIndex());
        assertDecimal("1.78571", metrics.efficiencyFactor());
    }

    // ── scenario 3: tssList absent → single tss; nothing → empty ────────────

    @Test
    void fallsBackToSingleTssWhenListIsAbsent() throws Exception {
        SuuntoWorkoutDto workout = workout("""
                { "tss": { "calculationMethod": "HR", "trainingStressScore": 52.586864,
                           "intensityFactor": null, "normalizedPower": 262.49988 } }
                """);

        SuuntoTrainingStress selected = mapper.selectTrainingStress(workout).orElseThrow();
        assertEquals("HR", selected.calculationMethod());
        assertEquals(52.586864, selected.trainingStressScore());
    }

    @Test
    void unknownFutureMethodStillContributesItsScore() throws Exception {
        SuuntoWorkoutDto workout = workout("""
                { "tssList": [
                    { "calculationMethod": "SOMETHING_NEW", "trainingStressScore": 40.5,
                      "intensityFactor": null, "normalizedPower": null } ] }
                """);

        SuuntoTrainingStress selected = mapper.selectTrainingStress(workout).orElseThrow();
        assertEquals("SOMETHING_NEW", selected.calculationMethod());
        assertEquals(40.5, selected.trainingStressScore());
    }

    @Test
    void noTssAnywhereYieldsEmptySelectionAndNullMetrics() throws Exception {
        SuuntoWorkoutDto workout = workout("{}");

        assertEquals(Optional.empty(), mapper.selectTrainingStress(workout));

        ActivityMetrics metrics = mapper.toActivityMetrics(CANONICAL_ID, workout);
        assertEquals(CANONICAL_ID, metrics.activityId());
        assertNull(metrics.normalizedPower());
        assertNull(metrics.variabilityIndex());
        assertNull(metrics.efficiencyFactor());
        assertNull(metrics.intensityFactor());
    }

    // ── scenario 4: missing extensions — mapper never needs Weather ─────────

    @Test
    void mapsWithoutAnyExtensionsAndFallsBackToSummaryAvgPowerWhenPresent() throws Exception {
        SuuntoWorkoutDto bare = workout("""
                { "activityId": 2, "totalDistance": 42195.0, "totalTime": 8100.4,
                  "startTime": 1784004759450, "totalAscent": 120.0 }
                """);

        Activity activity = mapper.toActivity(CANONICAL_ID, bare);
        assertEquals("Cycling", activity.sportType());
        assertEquals(42195.0, activity.distanceMeters());
        assertEquals(8100, activity.movingTimeSecs());
        assertNull(activity.averageHeartrate());
        assertNull(activity.averageWatts());

        // root avgPower missing → SummaryExtension supplies it; no
        // WeatherExtension present and none is ever consulted
        SuuntoWorkoutDto summaryOnly = workout("""
                { "extensions": [ { "type": "SummaryExtension", "avgPower": 250.0 } ] }
                """);
        assertEquals(250.0, mapper.toActivity(CANONICAL_ID, summaryOnly).averageWatts());
    }

    @Test
    void unknownActivityIdFallsBackToRawNumericString() throws Exception {
        SuuntoWorkoutDto workout = workout("{ \"activityId\": 999 }");

        assertEquals("999", mapper.toActivity(CANONICAL_ID, workout).sportType());
    }

    // ── scenario 5: real fixture end-to-end ─────────────────────────────────

    @Test
    void mapsRealFixtureEndToEnd() throws Exception {
        SuuntoWorkoutDto workout = realFixtureWorkout();

        Activity activity = mapper.toActivity(CANONICAL_ID, workout);
        assertEquals(CANONICAL_ID, activity.activityId());
        assertNull(activity.name());
        assertEquals("Running", activity.sportType());
        assertEquals(10015.0, activity.distanceMeters());
        assertEquals(3195, activity.movingTimeSecs());
        assertEquals(Instant.ofEpochMilli(1784004759450L), activity.startDate());
        assertEquals(65.8, activity.totalElevationGain());
        assertEquals(147.0, activity.averageHeartrate());
        assertEquals(261.3, activity.averageWatts());

        SuuntoTrainingStress selected = mapper.selectTrainingStress(workout).orElseThrow();
        assertEquals("POWER", selected.calculationMethod());
        assertEquals(96.928955, selected.trainingStressScore());

        ActivityMetrics metrics = mapper.toActivityMetrics(CANONICAL_ID, workout);
        assertEquals(CANONICAL_ID, metrics.activityId());
        assertDecimal("262.500", metrics.normalizedPower());
        assertDecimal("1.05000", metrics.intensityFactor());
        assertDecimal("1.00459", metrics.variabilityIndex());
        assertDecimal("1.78571", metrics.efficiencyFactor());
    }

    private SuuntoWorkoutDto workout(String json) throws Exception {
        return MAPPER.readValue(json, SuuntoWorkoutDto.class);
    }

    private SuuntoWorkoutDto realFixtureWorkout() throws Exception {
        try (InputStream in = SuuntoWorkoutMapperTest.class
                .getResourceAsStream("/suunto/workout-response-real.json")) {
            assertNotNull(in, "real fixture must be on the test classpath");
            return MAPPER.readValue(in, SuuntoWorkoutListResponse.class).payload().get(0);
        }
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                () -> "expected " + expected + " but was " + actual);
    }
}
