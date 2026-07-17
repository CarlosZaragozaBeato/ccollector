package com.zensyra.collector.runner.admin;

import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real round-trip test for the credential seed endpoint.
 *
 * <p>Runs against a real PostgreSQL container (via {@link CredentialSeedTestProfile})
 * so that JPA {@code @Convert} encryption fires on write and the same key decrypts
 * on read — confirming the fix for the bug described in audit-004/F-2.
 *
 * <p>The repository is NOT mocked: the test calls the HTTP endpoint and reads back
 * through the actual {@link IntegrationCredentialRepository#findBySource} path,
 * which is what the OAuth token-refresh code calls at runtime.
 */
@QuarkusTest
@TestProfile(CredentialSeedTestProfile.class)
class CredentialSeedIT {

    private static final String ADMIN_TOKEN = "test-token-for-ci";
    private static final String CLIENT_ID = "test-strava-client-id-42";
    private static final String CLIENT_SECRET = "test-strava-client-secret-abc";

    @Inject
    IntegrationCredentialRepository repo;

    @Inject
    UserTransaction userTransaction;

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanCredentials() throws Exception {
        userTransaction.begin();
        em.createNativeQuery("DELETE FROM integration_credentials").executeUpdate();
        userTransaction.commit();
    }

    @Test
    void seedStrava_freshInstall_roundTripsViaJpaEncryption() {
        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\"" + CLIENT_SECRET + "\"}")
                .when().post("/admin/credentials/strava")
                .then()
                .statusCode(200)
                .body("source", is("STRAVA"))
                .body("clientId", is(CLIENT_ID))
                .body("seeded", is(true))
                .body("clientSecret", is(nullValue()));

        IntegrationCredential found = repo.findBySource(IntegrationSource.STRAVA)
                .orElseThrow(() -> new AssertionError("Credential not found after seed"));
        assertEquals(CLIENT_ID, found.getClientId());
        assertEquals(CLIENT_SECRET, found.getClientSecret());
    }

    @Test
    void seedSuunto_freshInstall_roundTripsViaJpaEncryption() throws Exception {
        String subscriptionKey = "test-suunto-subscription-key-0123456789abcdef";
        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"suunto-client-id\",\"clientSecret\":\"suunto-client-secret\","
                        + "\"subscriptionKey\":\"" + subscriptionKey + "\"}")
                .when().post("/admin/credentials/suunto")
                .then()
                .statusCode(200)
                .body("source", is("SUUNTO"))
                .body("clientId", is("suunto-client-id"))
                .body("seeded", is(true))
                .body("clientSecret", is(nullValue()))
                .body("subscriptionKey", is(nullValue()));

        // Round-trip through the real JPA path (what the API client will call).
        IntegrationCredential found = repo.findBySource(IntegrationSource.SUUNTO)
                .orElseThrow(() -> new AssertionError("Credential not found after seed"));
        assertEquals("suunto-client-id", found.getClientId());
        assertEquals("suunto-client-secret", found.getClientSecret());
        assertEquals(subscriptionKey, found.getApiSubscriptionKey());

        // Encryption actually engaged at rest: the raw column value in PostgreSQL
        // must NOT be the plaintext (a mocked or converter-less path would store
        // it verbatim — exactly the failure #47 originally uncovered).
        userTransaction.begin();
        String rawStored = (String) em.createNativeQuery(
                "SELECT api_subscription_key FROM integration_credentials WHERE source = 'SUUNTO'"
        ).getSingleResult();
        userTransaction.commit();
        org.junit.jupiter.api.Assertions.assertNotEquals(subscriptionKey, rawStored);
        org.junit.jupiter.api.Assertions.assertFalse(rawStored.contains(subscriptionKey),
                "Raw stored value must not embed the plaintext subscription key");
    }

    @Test
    void seedSuunto_existingBrokenRow_overwritesCleanlyWithoutThrowing() throws Exception {
        // Plaintext row inserted behind the converter's back — the delete-then-
        // insert upsert must replace it without ever attempting to decrypt it.
        userTransaction.begin();
        em.createNativeQuery(
                "INSERT INTO integration_credentials (source, client_id, client_secret, api_subscription_key) " +
                "VALUES ('SUUNTO', 'old-client-id', 'plaintext-secret', 'plaintext-subscription-key')"
        ).executeUpdate();
        userTransaction.commit();

        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"new-suunto-id\",\"clientSecret\":\"new-suunto-secret\","
                        + "\"subscriptionKey\":\"new-subscription-key\"}")
                .when().post("/admin/credentials/suunto")
                .then()
                .statusCode(200);

        IntegrationCredential found = assertDoesNotThrow(
                () -> repo.findBySource(IntegrationSource.SUUNTO)
                        .orElseThrow(() -> new AssertionError("Credential not found after upsert")),
                "Decryption of the newly-written credential must not throw"
        );
        assertEquals("new-suunto-id", found.getClientId());
        assertEquals("new-suunto-secret", found.getClientSecret());
        assertEquals("new-subscription-key", found.getApiSubscriptionKey());
    }

    @Test
    void seedStrava_leavesSubscriptionKeyNull() {
        // Guards the "Strava never has a subscription key" assumption: the
        // existing Strava seed path must leave the new column NULL and the
        // JPA read-back must return null without touching the converter.
        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\"" + CLIENT_SECRET + "\"}")
                .when().post("/admin/credentials/strava")
                .then()
                .statusCode(200);

        IntegrationCredential found = repo.findBySource(IntegrationSource.STRAVA)
                .orElseThrow(() -> new AssertionError("Credential not found after seed"));
        org.junit.jupiter.api.Assertions.assertNull(found.getApiSubscriptionKey());
    }

    @Test
    void seedSuunto_missingSubscriptionKey_returns400() {
        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"suunto-client-id\",\"clientSecret\":\"suunto-client-secret\"}")
                .when().post("/admin/credentials/suunto")
                .then()
                .statusCode(400)
                .body("error", is("clientId, clientSecret and subscriptionKey are required"));
    }

    @Test
    void seedStrava_existingPlaintextRow_overwritesCleanlyWithoutThrowing() throws Exception {
        // Insert a plaintext value directly — replicates exactly the bug:
        // the old README instructed users to run a psql INSERT that bypasses @Convert.
        userTransaction.begin();
        em.createNativeQuery(
                "INSERT INTO integration_credentials (source, client_id, client_secret) " +
                "VALUES ('STRAVA', 'old-client-id', 'plaintext-not-encrypted')"
        ).executeUpdate();
        userTransaction.commit();

        // The endpoint must delete the broken row and insert an encrypted one —
        // never attempting to read/decrypt whatever was there.
        String newClientId = "new-client-id";
        String newClientSecret = "new-client-secret";
        given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType("application/json")
                .body("{\"clientId\":\"" + newClientId + "\",\"clientSecret\":\"" + newClientSecret + "\"}")
                .when().post("/admin/credentials/strava")
                .then()
                .statusCode(200);

        // JPA read-back must decrypt correctly without AEADBadTagException.
        IntegrationCredential found = assertDoesNotThrow(
                () -> repo.findBySource(IntegrationSource.STRAVA)
                        .orElseThrow(() -> new AssertionError("Credential not found after upsert")),
                "Decryption of the newly-written credential must not throw"
        );
        assertEquals(newClientId, found.getClientId());
        assertEquals(newClientSecret, found.getClientSecret());
    }
}
