package com.zensyra.collector.runner.admin;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the per-jobId concurrency guard on
 * {@code POST /admin/trigger/{jobId}} (Issue #38).
 *
 * <p>Execution is synchronous, so a trigger holds the guard for the whole job
 * duration. {@link LatchTestCollector} lets us hold one job open inside
 * {@code execute()} while we fire further requests, making all three acceptance
 * criteria observable deterministically (no reliance on thread-race timing to
 * produce the collision — the first request is provably still running).
 */
@QuarkusTest
class AdminTriggerResourceConcurrencyTest {

    private static final String ADMIN_TOKEN = "test-token-for-ci";

    @Inject
    LatchTestCollector latchCollector;

    @BeforeEach
    void resetLatches() {
        latchCollector.reset();
    }

    private int triggerStatus(String jobId) {
        return given()
                .header("X-Admin-Token", ADMIN_TOKEN)
                .when().post("/admin/trigger/{jobId}", jobId)
                .then().extract().statusCode();
    }

    @Test
    void concurrentTriggerOfSameJobReturns409WhileFirstIsRunning() throws Exception {
        AtomicInteger firstStatus = new AtomicInteger(-1);
        CountDownLatch firstDone = new CountDownLatch(1);

        // Thread 1: fires the latch job and blocks inside execute() until released.
        Thread first = new Thread(() -> {
            firstStatus.set(triggerStatus(LatchTestCollector.LATCH_JOB_ID));
            firstDone.countDown();
        }, "trigger-1");
        first.start();

        // Wait until the job is provably running (guard held).
        assertTrue(latchCollector.awaitStarted(5, TimeUnit.SECONDS),
                "latch job should have started executing");

        // AC-1: a second trigger of the SAME jobId is rejected with 409 while the first runs.
        given().header("X-Admin-Token", ADMIN_TOKEN)
                .when().post("/admin/trigger/{jobId}", LatchTestCollector.LATCH_JOB_ID)
                .then().statusCode(409)
                .body("error", is("job '" + LatchTestCollector.LATCH_JOB_ID + "' is already running"));

        // Release the first job and let it finish.
        latchCollector.release();
        assertTrue(firstDone.await(5, TimeUnit.SECONDS), "first trigger should complete");
        first.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(200, firstStatus.get(), "the first (winning) trigger returns 200");

        // AC-3: guard released after completion → same job is triggerable again.
        assertEquals(200, triggerStatus(LatchTestCollector.LATCH_JOB_ID),
                "job must be triggerable again after the previous run completed");
    }

    @Test
    void differentJobIdRunsConcurrentlyWhileAnotherIsHeld() throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            triggerStatus(LatchTestCollector.LATCH_JOB_ID);
            firstDone.countDown();
        }, "trigger-latch");
        first.start();

        assertTrue(latchCollector.awaitStarted(5, TimeUnit.SECONDS),
                "latch job should have started executing");

        // AC-2: a DIFFERENT jobId is NOT blocked by the held latch job (guard is per-jobId).
        given().header("X-Admin-Token", ADMIN_TOKEN)
                .when().post("/admin/trigger/{jobId}", LatchTestCollector.FAST_JOB_ID)
                .then().statusCode(200)
                .body("triggered", is(LatchTestCollector.FAST_JOB_ID));

        latchCollector.release();
        assertTrue(firstDone.await(5, TimeUnit.SECONDS), "latch trigger should complete");
        first.join(TimeUnit.SECONDS.toMillis(5));
    }

    @Test
    void guardReleasesAfterSuccessSoJobCanRunSequentially() {
        // Two sequential triggers of the same fast job both succeed — proves the
        // guard is released on the normal (non-blocked) success path too.
        assertEquals(200, triggerStatus(LatchTestCollector.FAST_JOB_ID));
        assertEquals(200, triggerStatus(LatchTestCollector.FAST_JOB_ID));
    }
}
