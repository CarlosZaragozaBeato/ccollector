package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.oauth.StravaOAuthExchangeException;
import com.zensyra.collector.api.oauth.StravaOAuthService;
import com.zensyra.collector.api.oauth.StravaOAuthToken;
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
class AthleteRegisterResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final String ATHLETE_ID = "987654";

    @InjectMock
    StravaOAuthService stravaOAuthService;

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
    void shouldCreateTokenWhenAthleteRegistersFirstTime() {
        Instant expiresAt = Instant.parse("2026-04-03T14:00:00Z");
        when(stravaOAuthService.exchangeAuthorizationCode(eq("oauth-code-1"), eq("https://app.example/callback")))
                .thenReturn(new StravaOAuthToken(
                        ATHLETE_ID,
                        "access-1",
                        "refresh-1",
                        expiresAt
                ));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "oauth-code-1",
                          "redirectUri": "https://app.example/callback"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register")
                .then()
                .statusCode(201)
                .body("athleteId", is(ATHLETE_ID))
                .body("created", is(true))
                .body("expiresAt", is(expiresAt.toString()));

        OAuthToken saved = tokenRepository.findBySourceAndUser(IntegrationSource.STRAVA, ATHLETE_ID).orElseThrow();
        IntegrationAccount account = integrationAccountRepository
                .findBySourceAndExternalUserId(IntegrationSource.STRAVA, ATHLETE_ID)
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("access-1", saved.getAccessToken());
        org.junit.jupiter.api.Assertions.assertEquals("refresh-1", saved.getRefreshToken());
        org.junit.jupiter.api.Assertions.assertEquals(expiresAt, saved.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertEquals(account.getId(), saved.getIntegrationAccountId());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                tokenRepository.findAllBySource(IntegrationSource.STRAVA).size()
        );
    }

    @Test
    void shouldUpdateExistingAthleteTokenWhenReRegistering() {
        OAuthToken existing = new OAuthToken();
        existing.setSource(IntegrationSource.STRAVA);
        existing.setExternalUserId(ATHLETE_ID);
        existing.setAccessToken("old-access");
        existing.setRefreshToken("old-refresh");
        existing.setExpiresAt(Instant.parse("2026-04-03T10:00:00Z"));
        QuarkusTransaction.requiringNew().run(() -> tokenRepository.persist(existing));

        Instant newExpiry = Instant.parse("2026-04-03T18:00:00Z");
        when(stravaOAuthService.exchangeAuthorizationCode(eq("oauth-code-2"), eq(null)))
                .thenReturn(new StravaOAuthToken(
                        ATHLETE_ID,
                        "new-access",
                        "new-refresh",
                        newExpiry
                ));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "oauth-code-2"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register")
                .then()
                .statusCode(200)
                .body("athleteId", is(ATHLETE_ID))
                .body("created", is(false))
                .body("expiresAt", is(newExpiry.toString()));

        OAuthToken updated = tokenRepository.findBySourceAndUser(IntegrationSource.STRAVA, ATHLETE_ID).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("new-access", updated.getAccessToken());
        org.junit.jupiter.api.Assertions.assertEquals("new-refresh", updated.getRefreshToken());
        org.junit.jupiter.api.Assertions.assertEquals(newExpiry, updated.getExpiresAt());
    }

    @Test
    void shouldReturn502WhenStravaOAuthFails() {
        when(stravaOAuthService.exchangeAuthorizationCode(eq("bad-code"), eq(null)))
                .thenThrow(new StravaOAuthExchangeException("Strava OAuth exchange failed"));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("""
                        {
                          "code": "bad-code"
                        }
                        """)
                .when()
                .post("/api/v1/athletes/register")
                .then()
                .statusCode(502)
                .body("error", is("Strava OAuth exchange failed"));
    }
}
