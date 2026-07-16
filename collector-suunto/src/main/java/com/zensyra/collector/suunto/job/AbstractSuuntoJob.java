package com.zensyra.collector.suunto.job;

import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.PartialJobFailureException;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.suunto.api.SuuntoApiClient;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Per-account loop and three-state outcome template for Suunto jobs —
 * mirrors {@code AbstractStravaJob} exactly (#14): all accounts attempted,
 * all-failed → RuntimeException (total failure), some-failed →
 * {@link PartialJobFailureException}, {@code executeForToken} returning
 * {@code true} aborts the remaining accounts (rate-limit signal).
 *
 * <p>Suunto differences: external user ids are strings by design (#5), so
 * there is no numeric parse helper; and every data call additionally needs
 * the per-deployment Azure APIM subscription key (#4) — resolved up front
 * and treated as a fatal setup error when absent, since no call can succeed
 * without it.
 */
public abstract class AbstractSuuntoJob implements SyncJob {

    private static final Logger LOG = Logger.getLogger(AbstractSuuntoJob.class);

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    OAuthTokenService tokenService;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    IntegrationCredentialRepository integrationCredentialRepository;

    @Inject
    @RestClient
    SuuntoApiClient suuntoApiClient;

    @Override
    public IntegrationSource source() {
        return IntegrationSource.SUUNTO;
    }

    @Override
    public final void execute(SyncContext context) {
        List<OAuthToken> tokens = tokenRepository.findAllBySource(IntegrationSource.SUUNTO);
        if (tokens.isEmpty()) {
            LOG.infof("%s: no Suunto tokens found — skipping execution", getClass().getSimpleName());
            return;
        }

        // Fail fast before touching any athlete: without the subscription key
        // every call would fail identically, which is a setup error, not N
        // per-athlete failures.
        subscriptionKey();

        int successes = 0;
        int failures = 0;

        for (OAuthToken token : tokens) {
            try {
                if (executeForToken(token, context)) {
                    break; // rate-limit or early-abort signal — not counted as success or failure
                }
                successes++;
            } catch (Exception e) {
                LOG.errorf(e, "%s: error processing athlete '%s'",
                        getClass().getSimpleName(), token.getExternalUserId());
                failures++;
            }
        }

        if (failures > 0 && successes == 0) {
            throw new RuntimeException(
                    getClass().getSimpleName() + ": all " + failures
                            + " athlete(s) failed; see preceding error logs");
        }
        if (failures > 0) {
            throw new PartialJobFailureException(getClass().getSimpleName(), successes, failures);
        }
    }

    /**
     * Execute job logic for a single authenticated user.
     *
     * @return true to abort processing of remaining tokens (e.g., on rate-limit hit), false to continue
     */
    protected abstract boolean executeForToken(OAuthToken token, SyncContext context);

    /**
     * Resolves the external user from the canonical account for new tokens.
     * Legacy unlinked rows remain executable until their backfill has run.
     */
    protected String externalUserId(OAuthToken token) {
        IntegrationAccount account = resolveIntegrationAccount(token);
        return account != null ? account.getExternalUserId() : token.getExternalUserId();
    }

    /**
     * Obtains a usable token through the canonical account. The fallback is
     * intentionally limited to legacy rows with no account link.
     */
    protected String validAccessToken(OAuthToken token) {
        UUID integrationAccountId = token.getIntegrationAccountId();
        if (integrationAccountId != null) {
            return tokenService.getValidToken(integrationAccountId);
        }
        return tokenService.getValidToken(token.getSource(), token.getExternalUserId());
    }

    /**
     * The decrypted Azure APIM subscription key sent as
     * {@code Ocp-Apim-Subscription-Key} on every Suunto data call.
     *
     * @throws IllegalStateException when the SUUNTO credential row or its key
     *         is missing — a deployment setup error, never a per-athlete one
     */
    protected String subscriptionKey() {
        return integrationCredentialRepository.findBySource(IntegrationSource.SUUNTO)
                .map(IntegrationCredential::getApiSubscriptionKey)
                .filter(key -> !key.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "No API subscription key configured for SUUNTO — seed it via "
                                + "POST /admin/credentials/suunto before enabling Suunto sync"));
    }

    private IntegrationAccount resolveIntegrationAccount(OAuthToken token) {
        UUID integrationAccountId = token.getIntegrationAccountId();
        if (integrationAccountId == null) {
            return null;
        }
        return integrationAccountRepository.findByIdOptional(integrationAccountId)
                .orElseThrow(() -> new IllegalStateException(
                        "OAuth token references a missing integration account: " + integrationAccountId
                ));
    }
}
