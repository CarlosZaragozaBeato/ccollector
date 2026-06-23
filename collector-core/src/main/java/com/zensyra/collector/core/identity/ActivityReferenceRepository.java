package com.zensyra.collector.core.identity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ActivityReferenceRepository implements PanacheRepositoryBase<ActivityReference, UUID> {

    public Optional<ActivityReference> findByIntegrationAccountIdAndExternalActivityId(
            UUID integrationAccountId,
            String externalActivityId) {
        return find("integrationAccountId = ?1 and externalActivityId = ?2",
                integrationAccountId, externalActivityId).firstResultOptional();
    }

    /**
     * Lists every per-source observation backing the given canonical
     * {@code TrainingSession}. Today this list has at most one element,
     * because only Strava is connected and {@code ActivityIdentityService}
     * always creates a new session per Strava activity (see ADR-002
     * addendum). It returns a list, not an {@code Optional}, because once a
     * second source is connected the same session can legitimately have one
     * observation per source — adapters must not assume single-element
     * results.
     */
    public List<ActivityReference> findByTrainingSessionId(UUID trainingSessionId) {
        return list("trainingSessionId", trainingSessionId);
    }
}
