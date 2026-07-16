package com.zensyra.collector.suunto.job;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.identity.AthleteIdentityService;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof over the real wiring: the job drives the REAL REST client
 * (against WireMock serving the live-captured fixture), the real mapper, the
 * real upsert and identity resolution into H2 — only the token/credential
 * plumbing is mocked. Asserts the row lands with #6's exact expected values,
 * both required headers were sent, and the run was a single page.
 */
@QuarkusTest
@TestProfile(SyncSuuntoWorkoutsJobEndToEndTest.WireMockProfile.class)
class SyncSuuntoWorkoutsJobEndToEndTest {

    static final int WIREMOCK_PORT = 19095;
    static final String SUUNTO_USER = "e2e-suunto-user";
    static WireMockServer wireMockServer;

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    IntegrationCredentialRepository credentialRepository;

    @Inject
    SyncSuuntoWorkoutsJob job;

    @Inject
    SuuntoWorkoutRepository workoutRepository;

    @Inject
    AthleteIdentityService athleteIdentityService;

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
    void resetStubsAndPlumbing() {
        wireMockServer.resetAll();

        OAuthToken token = new OAuthToken();
        token.setSource(IntegrationSource.SUUNTO);
        token.setExternalUserId(SUUNTO_USER);
        token.setAccessToken("e2e-access-token");
        token.setRefreshToken("e2e-refresh-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(tokenRepository.findAllBySource(IntegrationSource.SUUNTO)).thenReturn(List.of(token));
        when(tokenService.getValidToken(any(IntegrationSource.class), anyString()))
                .thenReturn("e2e-access-token");

        IntegrationCredential credential = mock(IntegrationCredential.class);
        when(credential.getApiSubscriptionKey()).thenReturn("e2e-subscription-key");
        when(credentialRepository.findBySource(IntegrationSource.SUUNTO))
                .thenReturn(Optional.of(credential));
    }

    @Test
    @TestTransaction
    void realFixtureFlowsThroughClientMapperAndUpsertIntoARow() {
        athleteIdentityService.resolveOrCreateAccount(IntegrationSource.SUUNTO, SUUNTO_USER);

        wireMockServer.stubFor(get(urlPathEqualTo("/v2/workouts"))
                .withHeader("Authorization", equalTo("Bearer e2e-access-token"))
                .withHeader("Ocp-Apim-Subscription-Key", equalTo("e2e-subscription-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(realFixture())));

        assertDoesNotThrow(() -> job.execute(new SyncContext(
                job.jobId(), IntegrationSource.SUUNTO, Instant.now(), null)));

        SuuntoWorkout workout = workoutRepository
                .findByWorkoutKey("6a55d9e7994229464c7710bb").orElseThrow();
        assertEquals(SUUNTO_USER, workout.getSuuntoUser());
        assertEquals("Running", workout.getSportType());
        assertEquals(10015.0, workout.getTotalDistance());
        assertEquals("POWER", workout.getTssCalculationMethod());
        assertEquals(96.928955, workout.getTss());
        assertEquals(1784011239246L, workout.getLastModified());

        // One workout < page size 50 → exactly one request, no second page.
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v2/workouts")));
    }

    static String realFixture() {
        try (InputStream in = SyncSuuntoWorkoutsJobEndToEndTest.class
                .getResourceAsStream("/suunto/workout-response-real.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class WireMockProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.rest-client.suunto-api.url", "http://localhost:" + WIREMOCK_PORT
            );
        }
    }
}
