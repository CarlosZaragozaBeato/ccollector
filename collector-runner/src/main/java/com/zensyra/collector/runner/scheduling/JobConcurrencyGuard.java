package com.zensyra.collector.runner.scheduling;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mutual-exclusion guard for job execution, keyed by {@code jobId}.
 *
 * <p>Prevents two concurrent triggers of the <em>same</em> job from running at
 * once (e.g. two overlapping admin triggers of a long backfill racing on
 * {@code SyncJobRecord} updates). Exclusion is per-jobId: acquiring job "A"
 * never blocks a concurrent acquisition of job "B".
 *
 * <p><strong>Deployment assumption:</strong> this guard is process-local and is
 * sufficient only for the current single-instance, self-hosted deployment model.
 * If CCollector is ever deployed as multiple instances behind a load balancer,
 * an in-memory set no longer provides mutual exclusion across instances and this
 * would need to become a Postgres advisory lock ({@code pg_try_advisory_lock})
 * or an equivalent distributed lock.
 */
@ApplicationScoped
public class JobConcurrencyGuard {

    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * Attempts to acquire the guard for {@code jobId}.
     *
     * @param jobId the job identifier to lock
     * @return {@code true} if the guard was acquired (the caller now owns it and
     *         must {@link #release(String)} it), {@code false} if the job is
     *         already running
     */
    public boolean tryAcquire(String jobId) {
        return running.add(jobId);
    }

    /**
     * Releases the guard for {@code jobId}. Safe to call even if not held.
     *
     * @param jobId the job identifier to unlock
     */
    public void release(String jobId) {
        running.remove(jobId);
    }

    /**
     * @param jobId the job identifier
     * @return {@code true} if the job is currently held by the guard
     */
    public boolean isRunning(String jobId) {
        return running.contains(jobId);
    }
}
