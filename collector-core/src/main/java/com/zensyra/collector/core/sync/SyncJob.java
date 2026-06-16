package com.zensyra.collector.core.sync;

public interface SyncJob {
    String jobId();
    String cronExpression();
    void execute(SyncContext context);

    default IntegrationSource source() {
        return null;
    }
}
