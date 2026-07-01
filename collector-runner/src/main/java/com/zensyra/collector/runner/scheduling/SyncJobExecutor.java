package com.zensyra.collector.runner.scheduling;

import com.zensyra.collector.core.sync.PartialJobFailureException;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.core.sync.SyncJobRecord;
import com.zensyra.collector.core.sync.SyncJobRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Instant;

@ApplicationScoped
public class SyncJobExecutor {

    private static final Logger LOG = Logger.getLogger(SyncJobExecutor.class);

    @Inject
    SyncJobRecordRepository recordRepository;

    @Inject
    MeterRegistry meterRegistry;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void execute(SyncJob syncJob) throws Exception {
        MDC.put("jobId", syncJob.jobId());
        Timer.Sample sample = Timer.start(meterRegistry);

        SyncJobRecord record = recordRepository.findByJobId(syncJob.jobId())
                .orElseGet(() -> createRecord(syncJob.jobId()));

        SyncContext context = new SyncContext(
                syncJob.jobId(),
                syncJob.source(),
                Instant.now(),
                record.getLastSuccessAt()
        );

        try {
            syncJob.execute(context);
            markSuccess(record.getId());
            LOG.infof("Job '%s' executed successfully", syncJob.jobId());
        } catch (PartialJobFailureException partial) {
            // Some athletes succeeded, some failed. Mark success (work was done,
            // consecutiveFailures reset) and also record lastFailureAt so that the
            // partial outcome is visible in SyncJobRecord without triggering alerts
            // that are reserved for total failure.
            markSuccess(record.getId());
            markLastFailureAt(record.getId());
            LOG.warnf("Job '%s' completed with partial failures: %s", syncJob.jobId(), partial.getMessage());
        } catch (Exception jobException) {
            try {
                markFailure(record.getId(), record.getConsecutiveFailures() + 1);
                LOG.errorf(jobException, "Job '%s' failed (consecutive failures: %d)",
                        syncJob.jobId(), record.getConsecutiveFailures() + 1);
            } catch (Exception markException) {
                LOG.errorf(markException,
                        "CRITICAL: could not record failure for job '%s' — consecutiveFailures not updated",
                        syncJob.jobId());
            }
            throw jobException;
        } finally {
            sample.stop(Timer.builder("collector.sync_job.duration")
                    .tag("jobId", syncJob.jobId())
                    .register(meterRegistry));
            MDC.clear();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected SyncJobRecord createRecord(String jobId) {
        SyncJobRecord record = new SyncJobRecord();
        record.setJobId(jobId);
        record.setConsecutiveFailures(0);
        recordRepository.persist(record);
        LOG.infof("Created a new SyncJobRecord for job '%s'", jobId);
        return record;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void markSuccess(Long recordId) {
        recordRepository.findByIdOptional(recordId).ifPresent(r -> {
            r.setLastSuccessAt(Instant.now());
            r.setConsecutiveFailures(0);
        });
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void markFailure(Long recordId, int consecutiveFailures) {
        recordRepository.findByIdOptional(recordId).ifPresent(r -> {
            r.setLastFailureAt(Instant.now());
            r.setConsecutiveFailures(consecutiveFailures);
        });
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void markLastFailureAt(Long recordId) {
        recordRepository.findByIdOptional(recordId).ifPresent(r -> r.setLastFailureAt(Instant.now()));
    }
}
