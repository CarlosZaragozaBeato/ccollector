package com.zensyra.collector.runner.health;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.core.sync.SyncJobRecord;
import com.zensyra.collector.core.sync.SyncJobRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.Optional;

/**
 * Readiness check that verifies jobs are registered and none has exceeded
 * the consecutive-failure threshold.
 */
@Readiness
@ApplicationScoped
public final class JobRegistryHealthCheck implements HealthCheck {

    private static final String CHECK_NAME = "job-registry";
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final Instance<DataCollector> collectors;
    private final SyncJobRecordRepository syncJobRecordRepository;

    @Inject
    public JobRegistryHealthCheck(
            final Instance<DataCollector> collectors,
            final SyncJobRecordRepository syncJobRecordRepository) {
        this.collectors = collectors;
        this.syncJobRecordRepository = syncJobRecordRepository;
    }

    @Override
    @Transactional
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named(CHECK_NAME);

        int totalJobs = 0;
        boolean anyDown = false;

        for (DataCollector collector : collectors) {
            for (SyncJob job : collector.jobs()) {
                totalJobs++;
                String jobId = job.jobId();
                Optional<SyncJobRecord> recordOpt = syncJobRecordRepository.findByJobId(jobId);

                if (recordOpt.isPresent()) {
                    SyncJobRecord record = recordOpt.get();
                    int failures = record.getConsecutiveFailures();
                    if (failures > MAX_CONSECUTIVE_FAILURES) {
                        anyDown = true;
                    }
                    builder.withData(jobId + ".lastSuccessAt",
                            record.getLastSuccessAt() != null ? record.getLastSuccessAt().toString() : "never");
                    builder.withData(jobId + ".lastFailureAt",
                            record.getLastFailureAt() != null ? record.getLastFailureAt().toString() : "never");
                    builder.withData(jobId + ".consecutiveFailures", (long) failures);
                } else {
                    builder.withData(jobId + ".lastSuccessAt", "never");
                    builder.withData(jobId + ".lastFailureAt", "never");
                    builder.withData(jobId + ".consecutiveFailures", 0L);
                }
            }
        }

        return builder
                .withData("registeredJobs", (long) totalJobs)
                .status(totalJobs > 0 && !anyDown)
                .build();
    }
}
