package com.zensyra.collector.suunto.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.zensyra.collector.suunto.api.dto.SuuntoFitnessExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoGenericExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoIntensityExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoSummaryExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoTssDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWeatherExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises the REAL MicroProfile REST client against WireMock (same pattern
 * as SuuntoTokenRefresherTest) — not a Mockito mock of the interface, which
 * would prove nothing about headers or deserialization.
 *
 * <p>Every stub REQUIRES both the Authorization and Ocp-Apim-Subscription-Key
 * headers to match: if the client ever stops sending either one, the stub
 * returns 404 and the test fails. Both headers empirically required by the
 * real API — a call missing either one fails upstream.
 *
 * <p>The happy path serves a real, complete /v2/workouts response captured
 * from a live Partner Program account (workout-response-real.json) and
 * asserts field-level deserialization of every mapped field, including all
 * four extension subtypes and the full tssList.
 */
@QuarkusTest
@TestProfile(SuuntoApiClientTest.WireMockProfile.class)
class SuuntoApiClientTest {

    static final int WIREMOCK_PORT = 19093;
    static final String BEARER = "Bearer test-access-token";
    static final String SUBSCRIPTION_KEY = "test-subscription-key";
    static WireMockServer wireMockServer;

    @Inject
    @RestClient
    SuuntoApiClient client;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    static String realFixture() {
        try (InputStream in = SuuntoApiClientTest.class.getResourceAsStream("/suunto/workout-response-real.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void getWorkouts_realFixture_deserializesEveryFieldOfTheCompletePayload() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/workouts"))
                .withHeader("Authorization", equalTo(BEARER))
                .withHeader("Ocp-Apim-Subscription-Key", equalTo(SUBSCRIPTION_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(realFixture())));

        SuuntoWorkoutListResponse response = client.getWorkouts(
                BEARER, SUBSCRIPTION_KEY,
                1783900000000L, 1784100000000L, 50, 0, false);

        assertNull(response.error());
        assertEquals("1", response.metadata().workoutcount());
        assertEquals("1784036711816", response.metadata().until());
        assertEquals(1, response.payload().size());

        SuuntoWorkoutDto workout = response.payload().get(0);

        // --- root scalars ---
        assertEquals(0L, workout.workoutId());
        assertEquals(1, workout.activityId());
        assertEquals(1784004759450L, workout.startTime());
        assertEquals(1784007954080L, workout.stopTime());
        assertEquals(3195.1609999999996, workout.totalTime());
        assertEquals(0, workout.estimatedFloorsClimbed());
        assertEquals(10015.0, workout.totalDistance());
        assertEquals(65.8, workout.totalAscent());
        assertEquals(59.2, workout.totalDescent());
        assertEquals(3.67, workout.maxSpeed());
        assertEquals(8645, workout.stepCount());
        assertEquals(53460L, workout.recoveryTime());
        assertEquals(432000L, workout.cumulativeRecoveryTime());
        assertEquals(6763.0, workout.minAltitude());
        assertEquals(7227.0, workout.maxAltitude());
        assertFalse(workout.isEdited());
        assertFalse(workout.isManuallyAdded());
        assertEquals(261.3, workout.avgPower());
        assertEquals(0, workout.viewCount());
        assertEquals(0, workout.pictureCount());
        assertEquals(0, workout.commentCount());
        assertEquals(5.32, workout.avgPace());
        assertEquals(120, workout.timeOffsetInMinutes());
        assertEquals(663, workout.energyConsumption());
        assertEquals(3.13, workout.avgSpeed());
        assertEquals("6a55d9e7994229464c7710bb", workout.workoutKey());
        assertEquals(1784011239246L, workout.lastModified());
        assertEquals(List.of("IMPACT_AEROBIC_TO_ANAEROBIC"), workout.suuntoTags());

        // --- positions (x = longitude, y = latitude) ---
        assertEquals(-3.3428966666666664, workout.startPosition().x());
        assertEquals(39.61966, workout.startPosition().y());
        assertEquals(-3.343005, workout.stopPosition().x());
        assertEquals(39.619796666666666, workout.stopPosition().y());
        assertEquals(-3.3694800000000003, workout.centerPosition().x());
        assertEquals(39.62107833333334, workout.centerPosition().y());

        // --- rankings ---
        assertEquals(2, workout.rankings().totalTimeOnRouteRanking().originalRanking());
        assertEquals(2, workout.rankings().totalTimeOnRouteRanking().originalNumberOfWorkouts());

        // --- hrdata / cadence (root shapes, distinct from SummaryExtension's) ---
        assertEquals(161, workout.hrdata().workoutMaxHR());
        assertEquals(147, workout.hrdata().workoutAvgHR());
        assertEquals(194, workout.hrdata().userMaxHR());
        assertEquals(161, workout.hrdata().hrmax());
        assertEquals(147, workout.hrdata().avg());
        assertEquals(194, workout.hrdata().max());
        assertEquals(84, workout.cadence().max());
        assertEquals(82, workout.cadence().avg());

        // --- extensionTypes: all 21 stream/extension markers ---
        assertEquals(21, workout.extensionTypes().size());
        assertEquals("ALTITUDESTREAM", workout.extensionTypes().get(0));
        assertEquals("WEATHERSTREAM", workout.extensionTypes().get(20));

        // --- tss (single, HR-preferred) + tssList (all 4 methods) ---
        assertEquals("HR", workout.tss().calculationMethod());
        assertEquals(52.586864, workout.tss().trainingStressScore());
        assertNull(workout.tss().intensityFactor());
        assertEquals(262.49988, workout.tss().normalizedPower());
        assertEquals(3.2046084, workout.tss().averageGradeAdjustedPace());

        assertEquals(4, workout.tssList().size());
        assertEquals(List.of("HR", "POWER", "PACE", "MET"),
                workout.tssList().stream().map(SuuntoTssDto::calculationMethod).toList());
        SuuntoTssDto powerTss = workout.tssList().get(1);
        assertEquals(96.928955, powerTss.trainingStressScore());
        assertEquals(1.0499995, powerTss.intensityFactor());
        SuuntoTssDto metTss = workout.tssList().get(3);
        assertEquals(49.702503, metTss.trainingStressScore());
        assertNull(metTss.intensityFactor());
        assertNull(metTss.normalizedPower());
        assertNull(metTss.averageGradeAdjustedPace());

        // --- extensions: all four subtypes, none falling into the generic fallback ---
        assertEquals(4, workout.extensions().size());

        SuuntoFitnessExtensionDto fitness =
                assertInstanceOf(SuuntoFitnessExtensionDto.class, workout.extensions().get(0));
        assertEquals("FitnessExtension", fitness.type());
        assertEquals(194, fitness.maxHeartRate());
        assertEquals(55, fitness.vo2Max());
        assertEquals(54.7, fitness.estimatedVo2Max());
        assertEquals(26, fitness.fitnessAge());

        SuuntoIntensityExtensionDto intensity =
                assertInstanceOf(SuuntoIntensityExtensionDto.class, workout.extensions().get(1));
        assertEquals("IntensityExtension", intensity.type());
        assertEquals(245.89, intensity.zones().heartRate().zone1().totalTime());
        assertEquals(0.0, intensity.zones().heartRate().zone1().lowerLimit());
        assertEquals(1846.469, intensity.zones().heartRate().zone2().totalTime());
        assertEquals(169.0, intensity.zones().heartRate().zone5().lowerLimit());
        assertEquals(2278.008, intensity.zones().speed().zone3().totalTime());
        assertEquals(3.703, intensity.zones().speed().zone5().lowerLimit());
        assertEquals(2596.03, intensity.zones().power().zone5().totalTime());
        assertEquals(250.0, intensity.zones().power().zone5().lowerLimit());
        assertNull(intensity.physiologicalThresholds());
        assertNull(intensity.overallIntensity());

        SuuntoSummaryExtensionDto summary =
                assertInstanceOf(SuuntoSummaryExtensionDto.class, workout.extensions().get(2));
        assertEquals("SummaryExtension", summary.type());
        assertEquals(3.134, summary.avgSpeed());
        assertEquals(261.3, summary.avgPower());
        assertEquals(317.0, summary.maxPower());
        assertEquals(0.109985, summary.avgVerticalOscillation());
        assertNull(summary.avgStrideLength());
        assertEquals(0.25431, summary.avgGroundContactTime());
        assertEquals(1.363, summary.avgCadence());
        assertEquals(1.4, summary.maxCadence());
        assertEquals(65.8, summary.ascent());
        assertEquals(59.2, summary.descent());
        assertEquals(1732.0, summary.ascentTime());
        assertEquals(1439.0, summary.descentTime());
        assertEquals(2.9, summary.pte());
        assertEquals(51.9, summary.peakEpoc());
        assertNull(summary.performanceLevel());
        assertEquals(53040.0, summary.recoveryTime());
        assertNull(summary.weather());
        assertEquals(293.7, summary.minTemperature());
        assertEquals(295.6, summary.avgTemperature());
        assertEquals(303.1, summary.maxTemperature());
        assertNull(summary.workoutType());
        assertEquals(2, summary.feeling());
        assertNull(summary.tags());
        assertEquals("Suunto", summary.gear().manufacturer());
        assertEquals("Suunto Race 2", summary.gear().name());
        assertNull(summary.gear().displayName());
        assertEquals("2539C7000493", summary.gear().serialNumber());
        assertEquals("2.53.42", summary.gear().softwareVersion());
        assertEquals("Sailfish_RevA1", summary.gear().hardwareVersion());
        assertEquals("SPORT_WATCH", summary.gear().productType());
        assertNull(summary.additionalGears());
        assertNull(summary.exerciseId());
        assertEquals(1, summary.apps().size());
        assertEquals("Manual Intervals - Running Pace", summary.apps().get(0).name());
        assertEquals("zzmirpen", summary.apps().get(0).id());
        assertEquals(3, summary.apps().get(0).summaryOutputs().size());
        assertEquals("IntervalCount", summary.apps().get(0).summaryOutputs().get(0).id());
        assertEquals("Count_Fourdigits", summary.apps().get(0).summaryOutputs().get(0).format());
        assertEquals("Intervals", summary.apps().get(0).summaryOutputs().get(0).name());
        assertEquals("", summary.apps().get(0).summaryOutputs().get(0).postfix());
        assertEquals(1.0, summary.apps().get(0).summaryOutputs().get(0).summaryValue());
        assertNull(summary.repetitionCount());
        assertNull(summary.lacticThHr());
        assertNull(summary.avgAscentSpeed());
        assertNull(summary.maxAscentSpeed());
        assertNull(summary.avgDescentSpeed());
        assertNull(summary.maxDescentSpeed());
        assertNull(summary.avgDistancePerStroke());
        assertNull(summary.fatConsumption());
        assertNull(summary.carbohydrateConsumption());
        assertNull(summary.avgLeftGroundContactBalance());
        assertNull(summary.avgRightGroundContactBalance());
        assertNull(summary.lacticThPace());
        assertNull(summary.avgFlightTime());
        assertNull(summary.avgContactTimeRatio());
        assertNull(summary.teamSportId());
        assertEquals("Normal", summary.heartRateRecovery().comparisonLevel());
        assertEquals(-27, summary.heartRateRecovery().drop());
        assertEquals("Low", summary.heartRateRecovery().level());
        assertNull(summary.finalEndurance());
        assertNull(summary.minimumEndurance());
        assertNull(summary.curEnduranceDistance());
        assertNull(summary.minEnduranceDistance());
        assertNull(summary.enduranceValid());

        SuuntoWeatherExtensionDto weather =
                assertInstanceOf(SuuntoWeatherExtensionDto.class, workout.extensions().get(3));
        assertEquals("WeatherExtension", weather.type());
        assertEquals("01d", weather.weatherIcon());
        assertEquals(295.37, weather.temperature());
        assertEquals(1.75, weather.windSpeed());
        assertEquals(169.0, weather.windDirection());
        assertEquals(31, weather.humidity());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/v2/workouts"))
                .withQueryParam("since", equalTo("1783900000000"))
                .withQueryParam("until", equalTo("1784100000000"))
                .withQueryParam("limit", equalTo("50"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("filter-by-modification-time", equalTo("false")));
    }

    @Test
    void getWorkouts_nullParams_omitsAllQueryParamsSoServerDefaultsApply() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/workouts"))
                .withHeader("Authorization", equalTo(BEARER))
                .withHeader("Ocp-Apim-Subscription-Key", equalTo(SUBSCRIPTION_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "error": null, "payload": [], "metadata": { "workoutcount": "0", "until": "0" } }
                                """)));

        SuuntoWorkoutListResponse response =
                client.getWorkouts(BEARER, SUBSCRIPTION_KEY, null, null, null, null, null);

        assertEquals(0, response.payload().size());
        // urlEqualTo matches the FULL url including query string: equality with
        // the bare path proves no null param leaked into the request.
        wireMockServer.verify(getRequestedFor(urlEqualTo("/v2/workouts")));
    }

    @Test
    void getWorkouts_unknownExtensionType_fallsBackToGenericWithoutFailing() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/workouts"))
                .withHeader("Authorization", equalTo(BEARER))
                .withHeader("Ocp-Apim-Subscription-Key", equalTo(SUBSCRIPTION_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": null,
                                  "payload": [{
                                    "workoutKey": "abc123",
                                    "extensions": [{
                                      "type": "BrandNewExtensionSuuntoShippedYesterday",
                                      "someField": 1.0,
                                      "nested": { "x": true }
                                    }]
                                  }],
                                  "metadata": { "workoutcount": "1", "until": "0" }
                                }
                                """)));

        SuuntoWorkoutListResponse response =
                client.getWorkouts(BEARER, SUBSCRIPTION_KEY, null, null, null, null, null);

        SuuntoGenericExtensionDto generic = assertInstanceOf(
                SuuntoGenericExtensionDto.class,
                response.payload().get(0).extensions().get(0));
        assertEquals("BrandNewExtensionSuuntoShippedYesterday", generic.type());
    }

    public static class WireMockProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Point the REST client at the local WireMock server
                    "quarkus.rest-client.suunto-api.url", "http://localhost:" + WIREMOCK_PORT
            );
        }
    }
}
