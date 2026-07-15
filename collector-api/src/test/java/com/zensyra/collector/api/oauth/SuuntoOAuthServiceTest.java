package com.zensyra.collector.api.oauth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Quarkus context): the service's collaborators are a
 * repository (mocked) and an HTTP endpoint (WireMock), both injectable through
 * package-private fields — same approach a CDI container would use.
 */
class SuuntoOAuthServiceTest {

    static final int WIREMOCK_PORT = 19093;
    static WireMockServer wireMockServer;

    SuuntoOAuthService service;

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
    void setUp() {
        wireMockServer.resetAll();

        IntegrationCredential cred = new IntegrationCredential();
        cred.setClientId("test-client-id");
        cred.setClientSecret("test-client-secret");
        IntegrationCredentialRepository credentials = mock(IntegrationCredentialRepository.class);
        when(credentials.findBySource(IntegrationSource.SUUNTO)).thenReturn(Optional.of(cred));

        service = new SuuntoOAuthService();
        service.credentialRepository = credentials;
        service.tokenUrl = "http://localhost:" + WIREMOCK_PORT + "/oauth/token";
    }

    @Test
    void shouldExchangeCodeAndComputeExpiryFromExpiresIn() {
        // Real Suunto response shape, uk/ukv/jti included on purpose: the
        // parser must tolerate them without reading or exposing them.
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token":  "access-jwt",
                                  "token_type":    "bearer",
                                  "refresh_token": "refresh-jwt",
                                  "expires_in":    86400,
                                  "scope":         "workout",
                                  "ukv":           "1",
                                  "uk":            "opaque-ignored",
                                  "user":          "carloszaragozabeato",
                                  "jti":           "ignored-token-id"
                                }
                                """)));

        Instant before = Instant.now();
        SuuntoOAuthToken token = service.exchangeAuthorizationCode("auth-code", "https://app.example/callback");
        Instant after = Instant.now();

        assertEquals("carloszaragozabeato", token.user());
        assertEquals("access-jwt", token.accessToken());
        assertEquals("refresh-jwt", token.refreshToken());
        assertEquals("workout", token.scope());
        assertFalse(token.expiresAt().isBefore(before.plusSeconds(86400)));
        assertFalse(token.expiresAt().isAfter(after.plusSeconds(86400)));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=auth-code"))
                .withRequestBody(containing("client_id=test-client-id"))
                .withRequestBody(containing("client_secret=test-client-secret"))
                // Suunto has no scope mechanism — the exchange must never send one.
                .withRequestBody(notMatching(".*scope=.*")));
    }

    @Test
    void shouldNotLeakResponseBodyWhenExchangeFails() {
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_client\",\"detail\":\"UPSTREAM-SENSITIVE-DETAIL\"}")));

        SuuntoOAuthExchangeException e = assertThrows(SuuntoOAuthExchangeException.class,
                () -> service.exchangeAuthorizationCode("auth-code", null));

        // Same discipline as #39/#42: the message is reflected to API callers
        // as the 502 body, so it must carry the status code and nothing upstream.
        assertTrue(e.getMessage().contains("HTTP 401"));
        assertFalse(e.getMessage().contains("UPSTREAM-SENSITIVE-DETAIL"));
        assertFalse(e.getMessage().contains("invalid_client"));
    }

    @Test
    void shouldRejectResponseMissingUser() {
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token":  "access-jwt",
                                  "refresh_token": "refresh-jwt",
                                  "expires_in":    86400,
                                  "scope":         "workout"
                                }
                                """)));

        SuuntoOAuthExchangeException e = assertThrows(SuuntoOAuthExchangeException.class,
                () -> service.exchangeAuthorizationCode("auth-code", null));

        assertTrue(e.getMessage().contains("missing user"));
    }
}
