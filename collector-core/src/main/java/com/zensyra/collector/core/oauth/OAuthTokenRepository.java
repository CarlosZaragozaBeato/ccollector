package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OAuthTokenRepository implements PanacheRepositoryBase<OAuthToken, Long> {

    public Optional<OAuthToken> findBySourceAndUser(IntegrationSource source, String externalUserId){
        return find("source = ?1 and externalUserId = ?2", source, externalUserId).firstResultOptional();
    }

    public List<OAuthToken> findAllBySource(IntegrationSource source){
        return list("source", source);
    }

}
