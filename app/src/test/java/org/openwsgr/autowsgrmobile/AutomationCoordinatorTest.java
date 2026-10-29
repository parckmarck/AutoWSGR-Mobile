package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class AutomationCoordinatorTest {
    @Test public void coordinatorRejectsSecondFlowAndNamesActiveFlow() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        TestFlow second = new TestFlow("campaign", "战役", true, true);
        AtomicReference<AutomationCoordinator.StartResult> reentrant = new AtomicReference<>();
        TestFlow first = new TestFlow("normal", "普通战役", true, true);
        first.onStart = () -> reentrant.set(coordinator.start(second));

        AutomationCoordinator.StartResult started = coordinator.start(first);

        assertTrue(started.started);
        assertFalse(reentrant.get().started);
        assertEquals("normal", reentrant.get().activeId);
        assertEquals("普通战役", reentrant.get().activeLabel);
        AutomationCoordinator.StartResult rejected = coordinator.start(second);
        assertFalse(rejected.started);
        assertEquals("normal", rejected.activeId);
        assertEquals("普通战役", rejected.activeLabel);
        assertFalse(coordinator.start(null).started);
    }

    @Test public void failedStartReleasesReservation() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        TestFlow declined = new TestFlow("normal", "普通战役", true, false);

        assertFalse(coordinator.start(declined).started);
        assertFalse(coordinator.hasActiveFlow());

        TestFlow broken = new TestFlow("campaign", "战役", true, true);
        IllegalStateException failure = new IllegalStateException("boom");
        broken.startFailure = failure;
        assertEquals(failure, assertThrows(
                IllegalStateException.class,
                () -> coordinator.start(broken)
        ));
        assertFalse(coordinator.hasActiveFlow());

        assertTrue(coordinator.start(new TestFlow("expedition", "远征", true, true)).started);
    }

    @Test public void falseOuterStartCannotClearNewReservationForSameFlowObject() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        SameObjectRestartFlow flow = new SameObjectRestartFlow(coordinator, false);

        AutomationCoordinator.StartResult outer = coordinator.start(flow);

        assertFalse(outer.started);
        assertTrue(flow.nestedResult.get().started);
        assertEquals(2, flow.startCalls.get());
        assertTrue("outer cleanup must not clear the nested reservation", coordinator.hasActiveFlow());
        assertEquals("same", coordinator.activeId());
    }

    @Test public void throwingOuterStartCannotClearNewReservationForSameFlowObject() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        SameObjectRestartFlow flow = new SameObjectRestartFlow(coordinator, true);

        assertEquals(flow.failure, assertThrows(
                IllegalStateException.class,
                () -> coordinator.start(flow)
        ));

        assertTrue(flow.nestedResult.get().started);
        assertEquals(2, flow.startCalls.get());
        assertTrue("throw cleanup must not clear the nested reservation", coordinator.hasActiveFlow());
        assertEquals("same", coordinator.activeId());
    }

    @Test public void terminalCallbackReleasesOnlyMatchingFlow() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        assertTrue(coordinator.start(new TestFlow("normal", "普通战役", true, true)).started);

        assertFalse(coordinator.finish("campaign"));
        assertEquals("normal", coordinator.activeId());
        assertTrue(coordinator.finish("normal"));
        assertFalse(coordinator.finish("normal"));
        assertFalse(coordinator.hasActiveFlow());
        assertEquals("", coordinator.activeId());
        assertEquals("", coordinator.activeLabel());
    }

    @Test public void cancelTargetsTheActiveFlowExactlyOnce() throws Exception {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        TestFlow active = new TestFlow("normal", "普通战役", true, true);
        assertTrue(coordinator.start(active).started);

        assertTrue(coordinator.pauseActive());
        assertTrue(coordinator.resumeActive());
        assertEquals(1, active.pauseCalls.get());
        assertEquals(1, active.resumeCalls.get());

        AtomicBoolean lockWasAvailable = new AtomicBoolean(false);
        active.onCancel = () -> {
            Thread observer = new Thread(() -> {
                coordinator.activeId();
                lockWasAvailable.set(true);
            });
            observer.start();
            try {
                observer.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        assertTrue(coordinator.cancelActive());
        assertEquals(1, active.cancelCalls.get());
        assertTrue("cancel delegate must run outside the coordinator lock", lockWasAvailable.get());
        assertTrue("cancel waits for a matching terminal callback", coordinator.hasActiveFlow());

        assertTrue(coordinator.finish("normal"));
        assertFalse(coordinator.cancelActive());
        assertEquals(1, active.cancelCalls.get());

        TestFlow unsupported = new TestFlow("waiter", "诊断等待", false, true);
        assertTrue(coordinator.start(unsupported).started);
        assertFalse(coordinator.pauseActive());
        assertFalse(coordinator.resumeActive());
        assertEquals(0, unsupported.pauseCalls.get());
        assertEquals(0, unsupported.resumeCalls.get());
    }

    @Test public void cancelRequestIsIdempotentForRepeatedAndReentrantCallers() {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        TestFlow active = new TestFlow("normal", "普通战役", true, true);
        AtomicBoolean reenter = new AtomicBoolean(true);
        AtomicReference<Boolean> reentrantResult = new AtomicReference<>();
        active.onCancel = () -> {
            if (reenter.compareAndSet(true, false)) {
                reentrantResult.set(coordinator.cancelActive());
            }
        };
        assertTrue(coordinator.start(active).started);

        assertTrue(coordinator.cancelActive());
        assertEquals(Boolean.FALSE, reentrantResult.get());
        assertFalse(coordinator.cancelActive());
        assertEquals(1, active.cancelCalls.get());
        assertTrue("cancel request must not release the reservation", coordinator.hasActiveFlow());
    }

    @Test public void concurrentCancelRequestsInvokeDelegateOnce() throws Exception {
        AutomationCoordinator coordinator = new AutomationCoordinator();
        TestFlow active = new TestFlow("normal", "普通战役", true, true);
        CountDownLatch firstDelegateEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstDelegate = new CountDownLatch(1);
        AtomicBoolean blockFirst = new AtomicBoolean(true);
        active.onCancel = () -> {
            if (blockFirst.compareAndSet(true, false)) {
                firstDelegateEntered.countDown();
                try {
                    releaseFirstDelegate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        assertTrue(coordinator.start(active).started);

        AtomicInteger successfulRequests = new AtomicInteger();
        Thread first = new Thread(() -> {
            if (coordinator.cancelActive()) {
                successfulRequests.incrementAndGet();
            }
        });
        first.start();
        assertTrue(firstDelegateEntered.await(1, TimeUnit.SECONDS));

        int callerCount = 8;
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch callTogether = new CountDownLatch(1);
        CountDownLatch callersDone = new CountDownLatch(callerCount);
        List<Thread> callers = new ArrayList<>();
        for (int index = 0; index < callerCount; index++) {
            Thread caller = new Thread(() -> {
                callersReady.countDown();
                try {
                    callTogether.await();
                    if (coordinator.cancelActive()) {
                        successfulRequests.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    callersDone.countDown();
                }
            });
            callers.add(caller);
            caller.start();
        }
        assertTrue(callersReady.await(1, TimeUnit.SECONDS));
        callTogether.countDown();
        try {
            assertTrue(callersDone.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirstDelegate.countDown();
        }
        first.join(1000);
        for (Thread caller : callers) {
            caller.join(1000);
        }

        assertEquals(1, successfulRequests.get());
        assertEquals(1, active.cancelCalls.get());
        assertTrue(coordinator.hasActiveFlow());
    }

    private static final class SameObjectRestartFlow implements AutomationFlow {
        private final AutomationCoordinator coordinator;
        private final boolean throwOuter;
        final AtomicInteger startCalls = new AtomicInteger();
        final AtomicReference<AutomationCoordinator.StartResult> nestedResult =
                new AtomicReference<>();
        final IllegalStateException failure = new IllegalStateException("outer failed");

        SameObjectRestartFlow(AutomationCoordinator coordinator, boolean throwOuter) {
            this.coordinator = coordinator;
            this.throwOuter = throwOuter;
        }

        @Override public String id() { return "same"; }
        @Override public String label() { return "同一对象"; }

        @Override public boolean start() {
            if (startCalls.incrementAndGet() == 1) {
                assertTrue(coordinator.finish(id()));
                nestedResult.set(coordinator.start(this));
                if (throwOuter) {
                    throw failure;
                }
                return false;
            }
            return true;
        }

        @Override public boolean supportsPause() { return true; }
        @Override public void pause() { }
        @Override public void resume() { }
        @Override public void cancel() { }
        @Override public boolean isRunning() { return true; }
    }

    private static final class TestFlow implements AutomationFlow {
        private final String id;
        private final String label;
        private final boolean supportsPause;
        private final boolean startResult;
        final AtomicInteger pauseCalls = new AtomicInteger();
        final AtomicInteger resumeCalls = new AtomicInteger();
        final AtomicInteger cancelCalls = new AtomicInteger();
        Runnable onStart;
        Runnable onCancel;
        RuntimeException startFailure;

        TestFlow(String id, String label, boolean supportsPause, boolean startResult) {
            this.id = id;
            this.label = label;
            this.supportsPause = supportsPause;
            this.startResult = startResult;
        }

        @Override public String id() { return id; }
        @Override public String label() { return label; }

        @Override public boolean start() {
            if (onStart != null) {
                onStart.run();
            }
            if (startFailure != null) {
                throw startFailure;
            }
            return startResult;
        }

        @Override public boolean supportsPause() { return supportsPause; }
        @Override public void pause() { pauseCalls.incrementAndGet(); }
        @Override public void resume() { resumeCalls.incrementAndGet(); }

        @Override public void cancel() {
            cancelCalls.incrementAndGet();
            if (onCancel != null) {
                onCancel.run();
            }
        }

        @Override public boolean isRunning() { return true; }
    }
}
