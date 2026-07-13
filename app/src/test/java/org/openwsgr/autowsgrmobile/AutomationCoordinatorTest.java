package org.openwsgr.autowsgrmobile;

import org.junit.Test;

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
