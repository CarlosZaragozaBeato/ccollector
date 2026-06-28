package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Centralizes the {@code (integrationAccountId, externalActivityId) →
 * canonical ActivityId} translation shared by every Strava query-port
 * adapter ({@link StravaActivityQueryPort}, {@link StravaBestEffortQueryPort},
 * and any future one). Extracted once a second adapter needed the identical
 * lookup, rather than duplicating it a third time when the pattern repeats
 * again for athlete-stats or training-load adapters.
 */
@ApplicationScoped
public class ActivityReferenceResolver {

    private final ActivityReferenceRepository activityReferenceRepository;

    @Inject
    public ActivityReferenceResolver(ActivityReferenceRepository activityReferenceRepository) {
        this.activityReferenceRepository = activityReferenceRepository;
    }

    /**
     * Returns the canonical {@code ActivityId} ({@code TrainingSession.id})
     * backing the given source observation, or {@code null} if no
     * {@link ActivityReference} exists for it. Callers are expected to skip
     * the row rather than propagate a null id — see
     * {@link StravaActivityQueryPort} and {@link StravaBestEffortQueryPort}
     * for the established pattern.
     */
    public UUID resolveCanonicalActivityId(UUID integrationAccountId, String externalActivityId) {
        return activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(integrationAccountId, externalActivityId)
                .map(ActivityReference::getTrainingSessionId)
                .orElse(null);
    }
}
