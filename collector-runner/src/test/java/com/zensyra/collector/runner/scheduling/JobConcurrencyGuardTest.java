package com.zensyra.collector.runner.scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic unit tests for the guard's acquire/release semantics — no
 * thread timing involved. End-to-end HTTP concurrency (409 rejection) is
 * covered separately by {@code AdminTriggerResourceConcurrencyTest}.
 */
class JobConcurrencyGuardTest {

    @Test
    void secondAcquireOfSameJobFailsUntilReleased() {
        JobConcurrencyGuard guard = new JobConcurrencyGuard();

        assertTrue(guard.tryAcquire("job-a"), "first acquire must succeed");
        assertFalse(guard.tryAcquire("job-a"), "second acquire of the same job must fail while held");

        guard.release("job-a");

        assertTrue(guard.tryAcquire("job-a"), "acquire must succeed again after release");
    }

    @Test
    void differentJobsAreIndependent() {
        JobConcurrencyGuard guard = new JobConcurrencyGuard();

        assertTrue(guard.tryAcquire("job-a"), "acquiring job-a must succeed");
        assertTrue(guard.tryAcquire("job-b"), "acquiring job-b must succeed even while job-a is held");

        // Each remains independently held.
        assertFalse(guard.tryAcquire("job-a"));
        assertFalse(guard.tryAcquire("job-b"));

        guard.release("job-a");
        assertTrue(guard.tryAcquire("job-a"), "job-a re-acquirable after its own release");
        assertFalse(guard.tryAcquire("job-b"), "releasing job-a must not release job-b");
    }

    @Test
    void releaseIsIdempotentAndSafeWhenNotHeld() {
        JobConcurrencyGuard guard = new JobConcurrencyGuard();

        // Releasing something never acquired must not throw and must not corrupt state.
        guard.release("never-acquired");
        assertTrue(guard.tryAcquire("never-acquired"));

        guard.release("never-acquired");
        guard.release("never-acquired"); // double release is a no-op
        assertTrue(guard.tryAcquire("never-acquired"),
                "state stays consistent after redundant releases");
    }

    @Test
    void isRunningReflectsHeldState() {
        JobConcurrencyGuard guard = new JobConcurrencyGuard();

        assertFalse(guard.isRunning("job-a"));
        guard.tryAcquire("job-a");
        assertTrue(guard.isRunning("job-a"));
        guard.release("job-a");
        assertFalse(guard.isRunning("job-a"));
    }
}
