package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IntegrationAccountRepository implements PanacheRepositoryBase<IntegrationAccount, UUID> {

    public Optional<IntegrationAccount> findBySourceAndExternalUserId(
            IntegrationSource source,
            String externalUserId) {
        return find("source = ?1 and externalUserId = ?2", source, externalUserId).firstResultOptional();
    }

    /**
     * Lists every integration account owned by the given canonical athlete,
     * one per connected source. Used by {@code collector-query} adapters and
     * composers to resolve which sources to query for a given
     * {@code AthleteId} — never to resolve a source-specific identifier
     * back to canonical, which is the responsibility of
     * {@link #findBySourceAndExternalUserId} instead.
     */
    public List<IntegrationAccount> findByAthleteId(UUID athleteId) {
        return list("athleteId", athleteId);
    }
}