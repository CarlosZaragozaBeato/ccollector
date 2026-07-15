package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.identity.ActivityIdentityService;
import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Translates Suunto identifiers to the canonical activity identity owned by
 * collector-core. Mirrors {@code StravaActivityIdentityService}; the only
 * difference is that Suunto's identifiers are naturally strings — the OAuth
 * {@code user} value (already the account's external_user_id since #3) and
 * the {@code workoutKey} every detail endpoint and webhook payload uses.
 */
@ApplicationScoped
public class SuuntoActivityIdentityService {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityIdentityService activityIdentityService;

    @Inject
    public SuuntoActivityIdentityService(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityIdentityService activityIdentityService) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityIdentityService = activityIdentityService;
    }

    @Transactional
    public ActivityReference resolveOrCreateReference(String suuntoUser, String workoutKey) {
        if (suuntoUser == null || suuntoUser.isBlank()) {
            throw new CollectorException("Suunto user is required");
        }
        if (workoutKey == null || workoutKey.isBlank()) {
            throw new CollectorException("Suunto workout key is required");
        }

        IntegrationAccount account = integrationAccountRepository
                .findBySourceAndExternalUserId(IntegrationSource.SUUNTO, suuntoUser)
                .orElseThrow(() -> new CollectorException(
                        "No canonical Suunto account found for user " + suuntoUser));

        return activityIdentityService.resolveOrCreateReference(
                account.getAthleteId(),
                account.getId(),
                workoutKey);
    }
}
