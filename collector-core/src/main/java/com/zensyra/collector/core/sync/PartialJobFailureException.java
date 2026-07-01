package com.zensyra.collector.core.sync;

/**
 * Thrown by a {@link SyncJob} when at least one per-athlete operation failed
 * but at least one succeeded. The job did useful work, so
 * {@code SyncJobRecord.lastSuccessAt} and {@code consecutiveFailures} behave
 * as they would for full success — but {@code lastFailureAt} is also updated
 * so the partial outcome is visible in persistent state.
 *
 * <p>This is distinct from a plain {@link RuntimeException}, which signals
 * total failure (all athletes failed or a fatal setup error occurred) and
 * increments {@code consecutiveFailures}.
 */
public class PartialJobFailureException extends RuntimeException {

    private final int succeeded;
    private final int failed;

    public PartialJobFailureException(String jobName, int succeeded, int failed) {
        super(jobName + ": partial failure — " + succeeded + " athlete(s) succeeded, "
                + failed + " failed; see preceding error logs");
        this.succeeded = succeeded;
        this.failed = failed;
    }

    public int getSucceeded() { return succeeded; }
    public int getFailed() { return failed; }
}
