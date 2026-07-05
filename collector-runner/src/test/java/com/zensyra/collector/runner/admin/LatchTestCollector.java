package com.zensyra.collector.runner.admin;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.core.sync.SyncJob;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only {@link DataCollector} used by {@code AdminTriggerResourceConcurrencyTest}.
 *
 * <p>Exposes two jobs:
 * <ul>
 *   <li>{@code test.latch-job} — blocks inside {@code execute()} until
 *       {@link #release()} is called, so a test can hold the concurrency guard
 *       open while it fires a second request;</li>
 *   <li>{@code test.fast-job} — returns immediately, used to prove a DIFFERENT
 *       jobId is not blocked while the latch job is held.</li>
 * </ul>
 *
 * <p>Both jobs use a far-future cron so the scheduler never fires them on its own
 * during the test; execution only happens through the admin trigger endpoint.
 */
@ApplicationScoped
public class LatchTestCollector implements DataCollector {

    static final String LATCH_JOB_ID = "test.latch-job";
    static final String FAST_JOB_ID = "test.fast-job";
    private static final String NEVER_CRON = "0 0 0 1 1 ? 2099";

    // Reset per test so each run starts from a clean state.
    private volatile CountDownLatch releaseLatch = new CountDownLatch(1);
    private volatile CountDownLatch startedLatch = new CountDownLatch(1);

    void reset() {
        releaseLatch = new CountDownLatch(1);
        startedLatch = new CountDownLatch(1);
    }

    /** Blocks until the running latch job has entered {@code execute()}. */
    boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return startedLatch.await(timeout, unit);
    }

    /** Unblocks the running latch job so it can complete. */
    void release() {
        releaseLatch.countDown();
    }

    @Override
    public IntegrationSource source() {
        return null;
    }

    @Override
    public List<SyncJob> jobs() {
        return List.of(
                new SyncJob() {
                    @Override public String jobId() { return LATCH_JOB_ID; }
                    @Override public String cronExpression() { return NEVER_CRON; }
                    @Override public void execute(SyncContext context) {
                        startedLatch.countDown();
                        try {
                            // Bounded so a broken test fails fast instead of hanging forever.
                            if (!releaseLatch.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("latch job was never released within 10s");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("latch job interrupted", e);
                        }
                    }
                },
                new SyncJob() {
                    @Override public String jobId() { return FAST_JOB_ID; }
                    @Override public String cronExpression() { return NEVER_CRON; }
                    @Override public void execute(SyncContext context) {
                        // no-op: completes immediately
                    }
                }
        );
    }
}
