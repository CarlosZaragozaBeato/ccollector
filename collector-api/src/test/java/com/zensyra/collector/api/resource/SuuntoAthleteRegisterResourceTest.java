package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.oauth.SuuntoOAuthExchangeException;
import com.zensyra.collector.api.oauth.SuuntoOAuthService;
import com.zensyra.collector.api.oauth.SuuntoOAuthToken;
import com.zensyra.collector.core.identity.AthleteProfileRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class SuuntoAthleteRegisterResourceTest {

    private static final String API_KEY = "test-api-key";
    // Suunto's external identifier is a username string, not Strava's numeric id.
    private static final String SUUNTO_USER = "carloszaragozabeato";

    @InjectMock
    SuuntoOAuthService suuntoOAuthService;

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    AthleteProfileRepository athleteProfileRepository;

    @BeforeEach
    void cleanDb() {
        QuarkusTransaction.requiringNew().run(() -> {
            tokenRepository.deleteAll();
            integrationAccountRepository.deleteAll();
            athleteProfileRepository.deleteAll();
        });
    }

    @Test
    void shouldCreateTokenWithScopeWhenAthleteRegistersFirstTime() {
        Instant expiresAt = Instant.parse("2026-07-14T14:00:00Z");
        when(suuntoOAuthService.exchangeAuthorizationCode(eq("suunto-code-1"), eq("https://app.example/callback")))
                .thenReturn(new SuuntoOAuthToken(
                        SUUNTO_USER,
                        "suunto-access-jwt",
                        "suunto-refresh-jwt",
                        expiresAt,
                        "workout"
                ));

        String responseAthleteId = given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "suunto-code-1",
                          "redirectUri": "https://app.example/callback"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register/suunto")
                .then()
                .statusCode(201)
                .body("created", is(true))
                .body("expiresAt", is(expiresAt.toString()))
                .extract().path("athleteId");

        OAuthToken saved = tokenRepository.findBySourceAndUser(IntegrationSource.SUUNTO, SUUNTO_USER).orElseThrow();
        IntegrationAccount account = integrationAccountRepository
                .findBySourceAndExternalUserId(IntegrationSource.SUUNTO, SUUNTO_USER)
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(account.getAthleteId().toString(), responseAthleteId);
        org.junit.jupiter.api.Assertions.assertEquals("suunto-access-jwt", saved.getAccessToken());
        org.junit.jupiter.api.Assertions.assertEquals("suunto-refresh-jwt", saved.getRefreshToken());
        org.junit.jupiter.api.Assertions.assertEquals(expiresAt, saved.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertEquals("workout", saved.getScope());
        org.junit.jupiter.api.Assertions.assertEquals(account.getId(), saved.getIntegrationAccountId());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                tokenRepository.findAllBySource(IntegrationSource.SUUNTO).size()
        );
    }

    @Test
    void shouldUpdateExistingSuuntoTokenWhenReRegistering() {
        OAuthToken existing = new OAuthToken();
        existing.setSource(IntegrationSource.SUUNTO);
        existing.setExternalUserId(SUUNTO_USER);
        existing.setAccessToken("old-access-jwt");
        existing.setRefreshToken("old-refresh-jwt");
        existing.setExpiresAt(Instant.parse("2026-07-13T10:00:00Z"));
        QuarkusTransaction.requiringNew().run(() -> tokenRepository.persist(existing));

        Instant newExpiry = Instant.parse("2026-07-14T18:00:00Z");
        when(suuntoOAuthService.exchangeAuthorizationCode(eq("suunto-code-2"), eq(null)))
                .thenReturn(new SuuntoOAuthToken(
                        SUUNTO_USER,
                        "new-access-jwt",
                        "new-refresh-jwt",
                        newExpiry,
                        "workout"
                ));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "suunto-code-2"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register/suunto")
                .then()
                .statusCode(200)
                .body("created", is(false))
                .body("expiresAt", is(newExpiry.toString()));

        OAuthToken updated = tokenRepository.findBySourceAndUser(IntegrationSource.SUUNTO, SUUNTO_USER).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("new-access-jwt", updated.getAccessToken());
        org.junit.jupiter.api.Assertions.assertEquals("new-refresh-jwt", updated.getRefreshToken());
        org.junit.jupiter.api.Assertions.assertEquals(newExpiry, updated.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertEquals("workout", updated.getScope());
    }

    @Test
    void shouldReturn502WithBodyFreeMessageWhenSuuntoOAuthFails() {
        // The exception message mirrors what SuuntoOAuthService builds on a
        // non-200: status code only. The 502 body must reflect exactly that
        // and nothing more.
        when(suuntoOAuthService.exchangeAuthorizationCode(eq("bad-code"), eq(null)))
                .thenThrow(new SuuntoOAuthExchangeException("Suunto OAuth exchange failed — HTTP 401"));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "bad-code"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register/suunto")
                .then()
                .statusCode(502)
                .body("error", is("Suunto OAuth exchange failed — HTTP 401"));
    }
}
