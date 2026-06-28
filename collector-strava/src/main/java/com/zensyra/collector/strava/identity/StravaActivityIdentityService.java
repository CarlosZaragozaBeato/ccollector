package com.zensyra.collector.strava.identity;

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
 * Translates Strava identifiers to the canonical activity identity owned by collector-core.
 */
@ApplicationScoped
public class StravaActivityIdentityService {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityIdentityService activityIdentityService;

    @Inject
    public StravaActivityIdentityService(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityIdentityService activityIdentityService) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityIdentityService = activityIdentityService;
    }

    @Transactional
    public ActivityReference resolveOrCreateReference(Long athleteStravaId, Long activityStravaId) {
        if (athleteStravaId == null) {
            throw new CollectorException("Strava athlete id is required");
        }
        if (activityStravaId == null) {
            throw new CollectorException("Strava activity id is required");
        }

        IntegrationAccount account = integrationAccountRepository
                .findBySourceAndExternalUserId(IntegrationSource.STRAVA, athleteStravaId.toString())
                .orElseThrow(() -> new CollectorException(
                        "No canonical Strava account found for athlete " + athleteStravaId));

        return activityIdentityService.resolveOrCreateReference(
                account.getAthleteId(),
                account.getId(),
                activityStravaId.toString());
    }
}
