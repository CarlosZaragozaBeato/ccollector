package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OAuthTokenRepository implements PanacheRepositoryBase<OAuthToken, Long> {

    public Optional<OAuthToken> findBySourceAndUser(IntegrationSource source, String externalUserId){
        return find("source = ?1 and externalUserId = ?2", source, externalUserId).firstResultOptional();
    }

    public Optional<OAuthToken> findByIntegrationAccountId(UUID integrationAccountId) {
        return find("integrationAccountId", integrationAccountId).firstResultOptional();
    }

    public List<OAuthToken> findAllByIntegrationAccountId(UUID integrationAccountId) {
        return list("integrationAccountId", integrationAccountId);
    }

    public List<OAuthToken> findAllBySource(IntegrationSource source){
        return find("""
                select token
                from OAuthToken token
                left join IntegrationAccount account on token.integrationAccountId = account.id
                where (token.integrationAccountId is not null and account.source = ?1)
                   or (token.integrationAccountId is null and token.source = ?1)
                """, source).list();
    }

}
