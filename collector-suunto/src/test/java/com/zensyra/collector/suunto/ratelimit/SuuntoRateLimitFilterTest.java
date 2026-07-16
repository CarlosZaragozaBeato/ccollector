package com.zensyra.collector.suunto.ratelimit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.zensyra.collector.suunto.api.SuuntoApiClient;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the rate limiter is genuinely wired into the REST client's call
 * path (via {@code @RegisterProvider} on SuuntoApiClient), not just that the
 * bucket mechanism works in isolation: with capacity 1 and a 1-second
 * refill, the second real HTTP call through the client must block until a
 * token is refilled. Same WireMock pattern as SuuntoApiClientTest.
 */
@QuarkusTest
@TestProfile(SuuntoRateLimitFilterTest.TightLimitProfile.class)
class SuuntoRateLimitFilterTest {

    static final int WIREMOCK_PORT = 19094;
    static final String EMPTY_ENVELOPE =
            "{ \"error\": null, \"payload\": [], \"metadata\": { \"workoutcount\": \"0\", \"until\": \"0\" } }";
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
    void secondCallThroughTheClientBlocksUntilATokenIsRefilled() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/workouts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(EMPTY_ENVELOPE)));

        // Call 1 consumes the single available permit; call 2 then blocks
        // until a refill tick, which synchronizes the test with the refill
        // timer's phase (it started at bean construction, not at call 1).
        client.getWorkouts("Bearer t", "key", null, null, null, null, null);
        client.getWorkouts("Bearer t", "key", null, null, null, null, null);

        // Call 3 starts right after a tick with an empty bucket, so it must
        // wait ~the full 1-second refill interval before its HTTP request
        // goes out. Well under that would mean the filter is not actually in
        // the client's call path.
        long start = System.nanoTime();
        client.getWorkouts("Bearer t", "key", null, null, null, null, null);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMillis >= 500,
                "third call completed in " + elapsedMillis + " ms — the rate limiter is not throttling the client");

        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/v2/workouts")));
    }

    public static class TightLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.rest-client.suunto-api.url", "http://localhost:" + WIREMOCK_PORT,
                    // Tiny bucket so throttling is observable within the test
                    "suunto.ratelimit.max-requests", "1",
                    "suunto.ratelimit.refill-seconds", "1"
            );
        }
    }
}
