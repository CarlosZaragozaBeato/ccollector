package com.zensyra.collector.core.sync;

import java.time.Instant;

public record SyncContext(
        String jobId,
        IntegrationSource source,
        Instant triggeredAt,
        Instant lastRunAt
) {
}
