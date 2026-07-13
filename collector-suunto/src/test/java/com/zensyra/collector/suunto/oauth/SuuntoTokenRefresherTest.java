package com.zensyra.collector.suunto.oauth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.oauth.TokenRefreshException;
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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(SuuntoTokenRefresherTest.RetryEnabledProfile.class)
class SuuntoTokenRefresherTest {

    static final int WIREMOCK_PORT = 19092;
    static WireMockServer wireMockServer;

    @InjectMock
    IntegrationCredentialRepository credentialRepository;

    @Inject
    SuuntoTokenRefresher tokenRefresher;

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
        when(credentialRepository.findBySource(IntegrationSource.SUUNTO))
                .thenReturn(Optional.of(cred));
    }

    @Test
    void shouldRefreshAndComputeExpiryFromExpiresIn() throws IOException, InterruptedException {
        // Real Suunto shape: relative expires_in (24h), plus uk/ukv/jti noise
        // fields that must be tolerated and ignored, never persisted.
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token":  "refreshed-access-jwt",
                                  "token_type":    "bearer",
                                  "refresh_token": "refreshed-refresh-jwt",
                                  "expires_in":    86400,
                                  "scope":         "workout",
                                  "ukv":           "1",
                                  "uk":            "opaque-ignored",
                                  "user":          "suuntouser",
                                  "jti":           "ignored-token-id"
                                }
                                """)));

        Instant before = Instant.now();
        TokenRefreshResult result = tokenRefresher.refresh("old-refresh-jwt");
        Instant after = Instant.now();

        assertNotNull(result);
        assertEquals("refreshed-access-jwt", result.accessToken());
        assertEquals("refreshed-refresh-jwt", result.refreshToken());
        // expiresAt is computed as now + expires_in — bound it between the
        // instants sampled around the call instead of asserting an exact value.
        assertFalse(result.expiresAt().isBefore(before.plusSeconds(86400)));
        assertFalse(result.expiresAt().isAfter(after.plusSeconds(86400)));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth/token"))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=old-refresh-jwt"))
                .withRequestBody(containing("client_id=test-client-id"))
                .withRequestBody(containing("client_secret=test-client-secret")));
    }

    @Test
    void shouldNotLeakResponseBodyWhenRefreshFails() {
        String upstreamBody = "{\"error\":\"invalid_grant\",\"detail\":\"UPSTREAM-SENSITIVE-DETAIL\"}";
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(upstreamBody)));

        TokenRefreshException e = assertThrows(TokenRefreshException.class,
                () -> tokenRefresher.refresh("old-refresh-jwt"));

        // Same discipline as #39/#42: status code only, never the raw body.
        assertTrue(e.getMessage().contains("HTTP 400"));
        assertFalse(e.getMessage().contains("UPSTREAM-SENSITIVE-DETAIL"));
        assertFalse(e.getMessage().contains("invalid_grant"));
    }

    @Test
    void shouldRetryOnNetworkErrorAndSucceedOnThirdAttempt() throws IOException, InterruptedException {
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
                                  "access_token":  "refreshed-access-jwt",
                                  "refresh_token": "refreshed-refresh-jwt",
                                  "expires_in":    86400
                                }
                                """)));

        TokenRefreshResult result = tokenRefresher.refresh("old-refresh-jwt");

        assertNotNull(result);
        assertEquals("refreshed-access-jwt", result.accessToken());

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
                    "suunto.oauth.token-url", "http://localhost:" + WIREMOCK_PORT + "/oauth/token"
            );
        }
    }
}
