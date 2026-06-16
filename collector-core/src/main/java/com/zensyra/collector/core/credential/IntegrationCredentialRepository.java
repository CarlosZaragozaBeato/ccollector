package com.zensyra.collector.core.credential;

import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class IntegrationCredentialRepository implements PanacheRepositoryBase<IntegrationCredential, Long> {
    public Optional<IntegrationCredential> findBySource(IntegrationSource source){
        return find("source", source).firstResultOptional();
    }
}
