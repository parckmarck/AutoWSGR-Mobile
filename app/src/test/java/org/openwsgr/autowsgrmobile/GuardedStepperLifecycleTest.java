package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class GuardedStepperLifecycleTest {
    @Test public void cancellationBeforePublicationCancelsPublishedWaiterExactlyOnce() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        FakeWaiter waiter = new FakeWaiter();

        lifecycle.cancel();
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.CANCELLED,
                lifecycle.publishAndStart(waiter));
        lifecycle.cancel();

        assertEquals(0, waiter.starts.get());
        assertEquals(1, waiter.cancels.get());
        assertTrue("cancellation alone must not masquerade as a terminal callback",
                lifecycle.isRunning());
        assertTrue(lifecycle.completeWaiter(waiter));
        assertFalse(lifecycle.isRunning());
    }

    @Test public void publicationBeforeCancellationCancelsTheExactWaiterOnce()
            throws Exception {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        FakeWaiter waiter = new FakeWaiter(startEntered, releaseStart);
        waiter.onStart = () -> assertTrue(lifecycle.isCurrentWaiter(waiter));
        AtomicReference<GuardedCombatStepper.WaiterStartOutcome> outcome =
                new AtomicReference<>();

        Thread publisher = new Thread(
                () -> outcome.set(lifecycle.publishAndStart(waiter)),
                "task6-waiter-publisher"
        );
        publisher.start();
        assertTrue(startEntered.await(1, TimeUnit.SECONDS));

        lifecycle.cancel();
        assertEquals(1, waiter.cancels.get());
        releaseStart.countDown();
        publisher.join(1000L);
        lifecycle.cancel();

        assertFalse(publisher.isAlive());
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.CANCELLED, outcome.get());
        assertEquals(1, waiter.starts.get());
        assertEquals(1, waiter.cancels.get());
    }

    @Test public void terminalCallbackAtomicallyClearsRunningAndCurrentWaiter() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        FakeWaiter waiter = new FakeWaiter();

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.STARTED,
                lifecycle.publishAndStart(waiter));
        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.hasActiveWaiter());
        assertTrue(lifecycle.isCurrentWaiter(waiter));

        assertTrue(lifecycle.completeWaiter(waiter));
        assertFalse(lifecycle.isRunning());
        assertFalse(lifecycle.hasActiveWaiter());
        assertFalse(lifecycle.isCurrentWaiter(waiter));
        assertFalse("the same terminal callback must not finish twice",
                lifecycle.completeWaiter(waiter));
    }

    @Test public void staleWaiterCallbackCannotClearOrFinishCurrentLifecycle() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        FakeWaiter stale = new FakeWaiter();
        stale.startResult = false;
        FakeWaiter current = new FakeWaiter();

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.REJECTED,
                lifecycle.publishAndStart(stale));
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.STARTED,
                lifecycle.publishAndStart(current));

        assertFalse(lifecycle.completeWaiter(stale));
        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.isCurrentWaiter(current));
        assertTrue(lifecycle.hasActiveWaiter());

        assertTrue(lifecycle.completeWaiter(current));
        assertFalse(lifecycle.isRunning());
    }

    @Test public void concurrentCancellationStillDeliversOneExternalCancel()
            throws Exception {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        FakeWaiter waiter = new FakeWaiter();
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.STARTED,
                lifecycle.publishAndStart(waiter));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Thread first = cancellationThread(lifecycle, ready, go, "task6-cancel-1");
        Thread second = cancellationThread(lifecycle, ready, go, "task6-cancel-2");

        first.start();
        second.start();
        assertTrue(ready.await(1, TimeUnit.SECONDS));
        go.countDown();
        first.join(1000L);
        second.join(1000L);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, waiter.cancels.get());
    }

    @Test public void synchronizedIsRunningIsVisibleAcrossThreads() throws Exception {
        GuardedCombatStepper.WaiterLifecycle lifecycle =
                new GuardedCombatStepper.WaiterLifecycle();
        assertTrue(lifecycle.begin());
        AtomicBoolean observedRunning = new AtomicBoolean();
        Thread runningReader = new Thread(
                () -> observedRunning.set(lifecycle.isRunning()),
                "task6-running-reader"
        );
        runningReader.start();
        runningReader.join(1000L);
        assertTrue(observedRunning.get());

        assertTrue(lifecycle.completeWithoutWaiter());
        AtomicBoolean observedStopped = new AtomicBoolean(true);
        Thread stoppedReader = new Thread(
                () -> observedStopped.set(lifecycle.isRunning()),
                "task6-stopped-reader"
        );
        stoppedReader.start();
        stoppedReader.join(1000L);
        assertFalse(observedStopped.get());
    }

    @Test public void runtimeExceptionDuringStartFailsClosedAndPublishesOneFailedTerminal() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        RuntimeException failure = new SecurityException("start denied");
        FakeWaiter waiter = new FakeWaiter();
        waiter.startFailure = failure;

        GuardedCombatStepper.WaiterStartResult result =
                lifecycle.publishAndStartResult(waiter);

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.FAILED, result.outcome);
        assertSame(failure, result.failure);
        assertEquals(GuardedCombatStepper.StepOutcome.FAILED, result.terminalOutcome());
        assertFalse(lifecycle.isRunning());
        assertFalse(lifecycle.hasActiveWaiter());
        assertFalse(lifecycle.isCurrentWaiter(waiter));
        assertFalse("the failed waiter must already own the terminal lifecycle claim",
                lifecycle.completeWaiter(waiter));

        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        AtomicInteger terminals = new AtomicInteger();
        AtomicReference<GuardedCombatStepper.StepOutcome> published =
                new AtomicReference<>();
        assertEquals(GuardedCombatStepper.PublicationResult.ENQUEUED,
                gate.publishTerminal(() -> {
                    terminals.incrementAndGet();
                    published.set(GuardedCombatStepper.terminalOutcomeForPublication(
                            gate.cancelled(), result.terminalOutcome()
                    ));
                    return true;
                }));
        gate.cancel();
        assertEquals(GuardedCombatStepper.PublicationResult.REJECTED,
                gate.publishTerminal(() -> {
                    terminals.incrementAndGet();
                    return true;
                }));
        assertEquals(GuardedCombatStepper.StepOutcome.FAILED, published.get());
        assertEquals(1, terminals.get());
    }

    @Test public void errorDuringStartAlsoFailsClosedWithoutEscapingCaller() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        AssertionError failure = new AssertionError("native start linkage failed");
        FakeWaiter waiter = new FakeWaiter();
        waiter.startFailure = failure;

        GuardedCombatStepper.WaiterStartResult result =
                lifecycle.publishAndStartResult(waiter);

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.FAILED, result.outcome);
        assertSame(failure, result.failure);
        assertEquals(GuardedCombatStepper.StepOutcome.FAILED, result.terminalOutcome());
        assertFalse(lifecycle.isRunning());
        assertFalse(lifecycle.hasActiveWaiter());
        assertFalse(lifecycle.isCurrentWaiter(waiter));
        assertFalse(lifecycle.completeWaiter(waiter));
    }

    @Test public void staleStartFailureCannotClearANewerCurrentWaiter() {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        FakeWaiter stale = new FakeWaiter();
        stale.startResult = false;
        FakeWaiter current = new FakeWaiter();
        RuntimeException staleFailure = new IllegalStateException("late old failure");

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.REJECTED,
                lifecycle.publishAndStart(stale));
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.STARTED,
                lifecycle.publishAndStart(current));

        GuardedCombatStepper.WaiterStartResult result =
                lifecycle.failStart(stale, staleFailure);

        assertEquals(GuardedCombatStepper.WaiterStartOutcome.REJECTED, result.outcome);
        assertSame(staleFailure, result.failure);
        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.isCurrentWaiter(current));
        assertTrue(lifecycle.hasActiveWaiter());
        assertTrue(lifecycle.completeWaiter(current));
    }

    @Test public void cancellationLinearizedBeforeThrowingStartPublishesCancelledOnce()
            throws Exception {
        GuardedCombatStepper.WaiterLifecycle lifecycle = lifecycleRunning();
        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        FakeWaiter waiter = new FakeWaiter(startEntered, releaseStart);
        waiter.startFailure = new SecurityException("cancelled start failed");
        AtomicReference<GuardedCombatStepper.WaiterStartResult> result =
                new AtomicReference<>();
        Thread publisher = new Thread(
                () -> result.set(lifecycle.publishAndStartResult(waiter)),
                "task6-throwing-start"
        );
        publisher.start();
        assertTrue(startEntered.await(1, TimeUnit.SECONDS));

        final GuardedCombatStepper.WaiterHandle[] claimed =
                new GuardedCombatStepper.WaiterHandle[1];
        gate.cancelWith(() -> claimed[0] = lifecycle.claimCancellationWith(null));
        if (claimed[0] != null) {
            claimed[0].cancel();
        }
        releaseStart.countDown();
        publisher.join(1000L);

        assertFalse(publisher.isAlive());
        assertEquals(1, waiter.cancels.get());
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.FAILED,
                result.get().outcome);
        assertFalse(lifecycle.isRunning());
        assertFalse(lifecycle.hasActiveWaiter());
        assertFalse(lifecycle.isCurrentWaiter(waiter));

        AtomicInteger terminals = new AtomicInteger();
        AtomicReference<GuardedCombatStepper.StepOutcome> published =
                new AtomicReference<>();
        assertEquals(GuardedCombatStepper.PublicationResult.ENQUEUED,
                gate.publishTerminal(() -> {
                    terminals.incrementAndGet();
                    published.set(GuardedCombatStepper.terminalOutcomeForPublication(
                            gate.cancelled(), result.get().terminalOutcome()
                    ));
                    return true;
                }));
        assertEquals(GuardedCombatStepper.PublicationResult.REJECTED,
                gate.publishTerminal(() -> {
                    terminals.incrementAndGet();
                    return true;
                }));
        assertEquals(GuardedCombatStepper.StepOutcome.CANCELLED, published.get());
        assertEquals(1, terminals.get());
    }

    private static GuardedCombatStepper.WaiterLifecycle lifecycleRunning() {
        GuardedCombatStepper.WaiterLifecycle lifecycle =
                new GuardedCombatStepper.WaiterLifecycle();
        assertTrue(lifecycle.begin());
        return lifecycle;
    }

    private static Thread cancellationThread(
            GuardedCombatStepper.WaiterLifecycle lifecycle,
            CountDownLatch ready,
            CountDownLatch go,
            String name
    ) {
        return new Thread(() -> {
            ready.countDown();
            awaitUninterruptibly(go);
            lifecycle.cancel();
        }, name);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakeWaiter implements GuardedCombatStepper.WaiterHandle {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final CountDownLatch startEntered;
        final CountDownLatch releaseStart;
        Runnable onStart;
        boolean startResult = true;
        Throwable startFailure;

        FakeWaiter() {
            this(null, null);
        }

        FakeWaiter(CountDownLatch startEntered, CountDownLatch releaseStart) {
            this.startEntered = startEntered;
            this.releaseStart = releaseStart;
        }

        @Override public boolean start() {
            starts.incrementAndGet();
            if (onStart != null) {
                onStart.run();
            }
            if (startEntered != null) {
                startEntered.countDown();
            }
            if (releaseStart != null) {
                awaitUninterruptibly(releaseStart);
            }
            if (startFailure instanceof RuntimeException) {
                throw (RuntimeException) startFailure;
            }
            if (startFailure instanceof Error) {
                throw (Error) startFailure;
            }
            return startResult;
        }

        @Override public void cancel() {
            cancels.incrementAndGet();
        }
    }
}
