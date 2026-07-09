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
