package com.zensyra.collector.suunto.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.zensyra.collector.suunto.api.dto.SuuntoGenericExtensionDto;
import com.zensyra.collector.suunto.api.dto.SuuntoSummaryExtensionDto;
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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void getWorkouts_sendsBothHeadersAndAllQueryParams_deserializesConfirmedFields() {
        // Realistic body: envelope + confirmed workout fields + SummaryExtension,
        // plus unmapped fields from the official sample (hrdata, avgSpeed, ...)
        // that must be tolerated and ignored.
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
                                    "workoutId": 123456789,
                                    "activityId": 3,
                                    "startTime": 1752300000000,
                                    "stopTime": 1752303600000,
                                    "totalTime": 3600.5,
                                    "totalDistance": 12345.6,
                                    "totalAscent": 250.5,
                                    "totalDescent": 248.0,
                                    "maxSpeed": 5.4,
                                    "workoutKey": "5b190f5c52ce7b316acbd520",
                                    "avgSpeed": 3.43,
                                    "energyConsumption": 780,
                                    "hrdata": { "workoutAvgHR": 145, "workoutMaxHR": 172 },
                                    "extensions": [{
                                      "type": "SummaryExtension",
                                      "pte": 3.2,
                                      "feeling": 4,
                                      "avgTemperature": 295.15,
                                      "peakEpoc": 42.5,
                                      "avgPower": 210.0,
                                      "maxPower": 480.0,
                                      "avgCadence": 1.42,
                                      "ascentTime": 1178.0,
                                      "descentTime": 1043.0
                                    }]
                                  }],
                                  "metadata": { "workoutcount": "1", "until": "1752303700000" }
                                }
                                """)));

        SuuntoWorkoutListResponse response = client.getWorkouts(
                BEARER, SUBSCRIPTION_KEY,
                1752200000000L, 1752400000000L, 50, 0, false);

        assertNull(response.error());
        assertEquals("1", response.metadata().workoutcount());
        assertEquals("1752303700000", response.metadata().until());
        assertEquals(1, response.payload().size());

        SuuntoWorkoutDto workout = response.payload().get(0);
        assertEquals(123456789L, workout.workoutId());
        assertEquals(3, workout.activityId());
        assertEquals(1752300000000L, workout.startTime());
        assertEquals(1752303600000L, workout.stopTime());
        assertEquals(3600.5, workout.totalTime());
        assertEquals(12345.6, workout.totalDistance());
        assertEquals(250.5, workout.totalAscent());
        assertEquals(248.0, workout.totalDescent());
        assertEquals(5.4, workout.maxSpeed());
        assertEquals("5b190f5c52ce7b316acbd520", workout.workoutKey());

        assertEquals(1, workout.extensions().size());
        SuuntoSummaryExtensionDto summary =
                assertInstanceOf(SuuntoSummaryExtensionDto.class, workout.extensions().get(0));
        assertEquals("SummaryExtension", summary.type());
        assertEquals(3.2, summary.pte());
        assertEquals(4, summary.feeling());
        assertEquals(295.15, summary.avgTemperature());
        assertEquals(42.5, summary.peakEpoc());
        assertEquals(210.0, summary.avgPower());
        assertEquals(480.0, summary.maxPower());
        assertEquals(1.42, summary.avgCadence());
        assertEquals(1178.0, summary.ascentTime());
        assertEquals(1043.0, summary.descentTime());
        assertNull(summary.maxTemperature());
        assertNull(summary.performanceLevel());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/v2/workouts"))
                .withQueryParam("since", equalTo("1752200000000"))
                .withQueryParam("until", equalTo("1752400000000"))
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
