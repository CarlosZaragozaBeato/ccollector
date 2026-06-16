package com.zensyra.collector.core.sync;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class SyncJobRecordRepository implements PanacheRepositoryBase<SyncJobRecord, Long> {

    public Optional<SyncJobRecord> findByJobId(String jobId) {
        return find("jobId", jobId).firstResultOptional();
    }
}
