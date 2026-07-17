package com.zensyra.collector.suunto.workout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.AthleteIdentityService;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same shape as collector-strava's ActivityUpsertServiceTest: real H2
 * persistence, identity seeded through the canonical AthleteIdentityService.
 * Existence is checked by workoutKey (#36) — the idempotency test is the
 * re-sync/double-count guarantee the training-load design relies on.
 */
@QuarkusTest
class SuuntoWorkoutUpsertServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUUNTO_USER = "test-suunto-user";
    private static final String FIXTURE_WORKOUT_KEY = "6a55d9e7994229464c7710bb";

    @Inject
    SuuntoWorkoutUpsertService upsertService;

    @Inject
    SuuntoWorkoutRepository workoutRepository;

    @Inject
    AthleteIdentityService athleteIdentityService;

    @Inject
    ActivityReferenceRepository activityReferenceRepository;

    @Test
    @TestTransaction
    void shouldBeIdempotent_reSyncNeverDuplicatesRowOrTssContribution() throws Exception {
        IntegrationAccount account = prepareSuuntoAccount();
        SuuntoWorkoutDto dto = realFixtureWorkout();

        upsertService.upsert(SUUNTO_USER, dto);
        upsertService.upsert(SUUNTO_USER, dto); // re-sync — must update, never insert twice

        assertEquals(1L, workoutRepository.count("workoutKey", FIXTURE_WORKOUT_KEY));
        assertEquals(1L, activityReferenceRepository.count());
        activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(account.getId(), FIXTURE_WORKOUT_KEY)
                .orElseThrow();
    }

    @Test
    @TestTransaction
    void realFixturePersistsEveryMappedColumn() throws Exception {
        prepareSuuntoAccount();

        upsertService.upsert(SUUNTO_USER, realFixtureWorkout());

        SuuntoWorkout workout = workoutRepository.findByWorkoutKey(FIXTURE_WORKOUT_KEY).orElseThrow();
        assertEquals(SUUNTO_USER, workout.getSuuntoUser());
        assertEquals(1, workout.getActivityId());
        assertEquals("Running", workout.getSportType());
        assertEquals(10015.0, workout.getTotalDistance());
        assertEquals(3195, workout.getMovingTimeSecs());
        assertEquals(Instant.ofEpochMilli(1784004759450L), workout.getStartDate());
        assertEquals(65.8, workout.getTotalElevationGain());
        assertEquals(147.0, workout.getAverageHeartrate());
        assertEquals(261.3, workout.getAverageWatts());
        // POWER selected from tssList (not Suunto's HR-defaulted single tss)
        assertEquals("POWER", workout.getTssCalculationMethod());
        assertEquals(96.928955, workout.getTss());
        assertDecimal("262.500", workout.getNormalizedPower());
        assertDecimal("1.05000", workout.getIntensityFactor());
        assertDecimal("1.00459", workout.getVariabilityIndex());
        assertDecimal("1.78571", workout.getEfficiencyFactor());
        assertEquals(1784011239246L, workout.getLastModified());
        assertNotNull(workout.getCreatedAt());
    }

    @Test
    @TestTransaction
    void secondUpsertUpdatesChangedValuesInPlace() throws Exception {
        prepareSuuntoAccount();
        upsertService.upsert(SUUNTO_USER, workout("""
                { "workoutKey": "wk-update", "totalDistance": 5000.0,
                  "tssList": [ { "calculationMethod": "PACE", "trainingStressScore": 40.0,
                                 "intensityFactor": 0.8, "normalizedPower": null } ] }
                """));

        upsertService.upsert(SUUNTO_USER, workout("""
                { "workoutKey": "wk-update", "totalDistance": 5200.0,
                  "tssList": [ { "calculationMethod": "PACE", "trainingStressScore": 43.5,
                                 "intensityFactor": 0.82, "normalizedPower": null } ] }
                """));

        SuuntoWorkout updated = workoutRepository.findByWorkoutKey("wk-update").orElseThrow();
        assertEquals(5200.0, updated.getTotalDistance());
        assertEquals(43.5, updated.getTss());
        assertNull(updated.getNormalizedPower());
    }

    // --- helpers ---

    private IntegrationAccount prepareSuuntoAccount() {
        return athleteIdentityService.resolveOrCreateAccount(IntegrationSource.SUUNTO, SUUNTO_USER);
    }

    private SuuntoWorkoutDto workout(String json) throws Exception {
        return MAPPER.readValue(json, SuuntoWorkoutDto.class);
    }

    private SuuntoWorkoutDto realFixtureWorkout() throws Exception {
        try (InputStream in = SuuntoWorkoutUpsertServiceTest.class
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
