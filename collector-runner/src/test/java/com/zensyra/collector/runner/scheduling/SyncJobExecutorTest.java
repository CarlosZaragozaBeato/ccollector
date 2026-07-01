package com.zensyra.collector.runner.scheduling;

import com.zensyra.collector.core.sync.PartialJobFailureException;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.core.sync.SyncJobRecord;
import com.zensyra.collector.core.sync.SyncJobRecordRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
class SyncJobExecutorTest {

    @InjectMock
    SyncJobRecordRepository recordRepository;

    @Inject
    SyncJobExecutor executor;

    // --- case 1: job throws → SyncJobRecord.consecutiveFailures == 1 ---

    @Test
    void jobThrows_consecutiveFailuresIncrementedToOne() {
        SyncJobRecord record = makeRecord(1L, 0);
        when(recordRepository.findByJobId("test-job")).thenReturn(Optional.of(record));
        when(recordRepository.findByIdOptional(1L)).thenReturn(Optional.of(record));

        RuntimeException jobEx = new RuntimeException("simulated job failure");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(failingJob(jobEx)));

        assertSame(jobEx, thrown, "original exception must propagate unchanged");
        assertEquals(1, record.getConsecutiveFailures(),
                "consecutiveFailures must be incremented to 1");
    }

    // --- case 2: PartialJobFailureException → lastSuccessAt AND lastFailureAt both set, consecutiveFailures stays 0 ---

    @Test
    void partialFailure_setsLastFailureAtWithoutIncrementingConsecutiveFailures() throws Exception {
        SyncJobRecord record = makeRecord(1L, 0);
        when(recordRepository.findByJobId("test-job")).thenReturn(Optional.of(record));
        when(recordRepository.findByIdOptional(1L)).thenReturn(Optional.of(record));

        executor.execute(partiallyFailingJob());

        assertNotNull(record.getLastSuccessAt(), "lastSuccessAt must be set — some athletes succeeded");
        assertNotNull(record.getLastFailureAt(), "lastFailureAt must be set — some athletes failed");
        assertEquals(0, record.getConsecutiveFailures(),
                "consecutiveFailures must stay 0 — partial is not a total failure");
    }

    // --- case 3: job throws AND markFailure() also throws → CRITICAL log + original exception propagates ---

    @Test
    void markFailureThrows_originalExceptionStillPropagates() {
        SyncJobRecord record = makeRecord(1L, 0);
        when(recordRepository.findByJobId("test-job")).thenReturn(Optional.of(record));
        // Simulate DB down: findByIdOptional throws during markFailure
        when(recordRepository.findByIdOptional(anyLong()))
                .thenThrow(new RuntimeException("DB connection lost"));

        RuntimeException jobEx = new RuntimeException("simulated job failure");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(failingJob(jobEx)));

        // The original job exception must reach the caller, not the DB exception.
        // The CRITICAL log line is emitted as a side-effect (observable in test output).
        assertSame(jobEx, thrown, "original job exception must propagate even when markFailure throws");
    }

    // --- helpers ---

    private SyncJobRecord makeRecord(Long id, int consecutiveFailures) {
        SyncJobRecord r = new SyncJobRecord();
        r.setId(id);
        r.setJobId("test-job");
        r.setConsecutiveFailures(consecutiveFailures);
        return r;
    }

    private SyncJob failingJob(RuntimeException toThrow) {
        return new SyncJob() {
            @Override public String jobId() { return "test-job"; }
            @Override public String cronExpression() { return "0 * * * * ?"; }
            @Override public void execute(SyncContext ctx) { throw toThrow; }
        };
    }

    private SyncJob partiallyFailingJob() {
        return new SyncJob() {
            @Override public String jobId() { return "test-job"; }
            @Override public String cronExpression() { return "0 * * * * ?"; }
            @Override public void execute(SyncContext ctx) {
                throw new PartialJobFailureException("test-job", 3, 1);
            }
        };
    }
}
