package com.zensyra.collector.strava.oauth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.oauth.TokenRefreshResult;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Verifies that @Retry retries up to 2 times on transient network failures
 * (IOException) before returning the successful result on the third attempt.
 *
 * Uses a separate TestProfile to re-enable @Retry (globally disabled in the
 * default test application.properties) and set delay=0 to keep the test fast.
 */
@QuarkusTest
@TestProfile(StravaTokenRefresherTest.RetryEnabledProfile.class)
class StravaTokenRefresherTest {

    static final int WIREMOCK_PORT = 19091;
    static WireMockServer wireMockServer;

    @InjectMock
    IntegrationCredentialRepository credentialRepository;

    @Inject
    StravaTokenRefresher tokenRefresher;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        IntegrationCredential cred = new IntegrationCredential();
        cred.setClientId("test-client-id");
        cred.setClientSecret("test-client-secret");
        when(credentialRepository.findBySource(IntegrationSource.STRAVA))
                .thenReturn(Optional.of(cred));
    }

    @Test
    void shouldRetryOnNetworkErrorAndSucceedOnThirdAttempt() throws IOException, InterruptedException {
        // Scenario: two connection failures followed by a successful response
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("attempt-2")
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt-2")
                .willSetStateTo("attempt-3")
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token":  "refreshed-access-token",
                                  "refresh_token": "refreshed-refresh-token",
                                  "expires_at":    9999999999
                                }
                                """)));

        TokenRefreshResult result = tokenRefresher.refresh("old-refresh-token");

        assertNotNull(result);
        assertEquals("refreshed-access-token", result.accessToken());
        assertEquals("refreshed-refresh-token", result.refreshToken());

        // Three HTTP calls: two failures + one success
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    public static class RetryEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Re-enable @Retry for this test class only (globally off in test application.properties)
                    "Retry/enabled", "true",
                    // Zero delay so the test runs fast
                    "Retry/delay", "0",
                    // Point token URL at the local WireMock server
                    "strava.oauth.token-url", "http://localhost:" + WIREMOCK_PORT + "/oauth/token"
            );
        }
    }
}
