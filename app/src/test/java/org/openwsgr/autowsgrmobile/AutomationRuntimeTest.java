package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class AutomationRuntimeTest {
    private static final CallbackDispatcher IMMEDIATE = Runnable::run;

    @Test public void runtimeReplaysLatestUpdateToNewActivityListener() {
        AutomationRuntime runtime = new AutomationRuntime(IMMEDIATE);
        AutomationUpdate latest = AutomationUpdate.progress(
                "normal", "run-1", "航行中", "node=2", "run.log", ""
        );
        runtime.onUpdate(latest);
        List<AutomationUpdate> received = new ArrayList<>();

        runtime.addListener(received::add, true);

        assertEquals(1, received.size());
        assertSame(latest, received.get(0));
        assertSame(latest, runtime.latestUpdate());
    }

    @Test public void removingActivityListenerDoesNotCancelActiveFlow() {
        AutomationRuntime runtime = new AutomationRuntime(IMMEDIATE);
        TestFlow active = new TestFlow("expedition", "远征");
        AtomicInteger listenerCalls = new AtomicInteger();
        AutomationRuntime.Listener listener = update -> listenerCalls.incrementAndGet();
        runtime.addListener(listener, false);
        assertTrue(runtime.start(active).started);

        runtime.removeListener(listener);
        runtime.onUpdate(AutomationUpdate.progress(
                "expedition", "run-2", "等待", "", "", ""
        ));

        assertEquals(0, active.cancelCalls.get());
        assertEquals(0, listenerCalls.get());
        AutomationCoordinator.StartResult rejected = runtime.start(new TestFlow("campaign", "战役"));
        assertFalse(rejected.started);
        assertEquals("expedition", rejected.activeId);
    }

    @Test public void runtimeDeliversListenerUpdatesThroughInjectedDispatcher() {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        AutomationRuntime runtime = new AutomationRuntime(dispatcher);
        TestFlow first = new TestFlow("normal", "普通战役");
        TestFlow second = new TestFlow("campaign", "战役");
        assertTrue(runtime.start(first).started);
        List<AutomationUpdate> received = new ArrayList<>();
        runtime.addListener(received::add, false);

        AutomationUpdate terminal = AutomationUpdate.terminal(
                "normal", "run-3", AutomationOutcome.SUCCESS, "完成", "", "", ""
        );
        runtime.onUpdate(terminal);

        assertEquals(1, dispatcher.actions.size());
        assertTrue("terminal reservation must release before dispatch", runtime.start(second).started);
        assertEquals(0, received.size());
        dispatcher.runAll();
        assertEquals(1, received.size());
        assertSame(terminal, received.get(0));

        runtime.addListener(received::add, true);
        assertEquals(1, received.size());
        assertEquals(1, dispatcher.actions.size());
        dispatcher.runAll();
        assertEquals(2, received.size());
        assertSame(terminal, received.get(1));

        AtomicInteger removedCalls = new AtomicInteger();
        AutomationRuntime.Listener removed = update -> removedCalls.incrementAndGet();
        runtime.addListener(removed, false);
        runtime.onUpdate(AutomationUpdate.progress(
                "campaign", "run-4", "进行中", "", "", ""
        ));
        runtime.removeListener(removed);
        dispatcher.runAll();
        assertEquals("queued callbacks must honor listener removal", 0, removedCalls.get());
    }

    @Test public void terminalHandoffCannotOverwriteNextFlowsSynchronousProgress() throws Exception {
        QueuedDispatcher dispatcher = new QueuedDispatcher();
        CountDownLatch terminalReleasedInsideStateLock = new CountDownLatch(1);
        CountDownLatch continueTerminalRecording = new CountDownLatch(1);
        AutomationRuntime runtime = new AutomationRuntime(dispatcher, update -> {
            terminalReleasedInsideStateLock.countDown();
            try {
                continueTerminalRecording.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        TestFlow first = new TestFlow("normal", "普通战役");
        assertTrue(runtime.start(first).started);
        List<AutomationUpdate> received = Collections.synchronizedList(new ArrayList<>());
        runtime.addListener(received::add, false);
        AutomationUpdate terminal = AutomationUpdate.terminal(
                "normal", "run-a", AutomationOutcome.SUCCESS, "A完成", "", "", ""
        );
        AutomationUpdate nextProgress = AutomationUpdate.progress(
                "campaign", "run-b", "B进行中", "", "", ""
        );

        Thread terminalThread = new Thread(() -> runtime.onUpdate(terminal));
        terminalThread.start();
        assertTrue(terminalReleasedInsideStateLock.await(1, TimeUnit.SECONDS));

        CountDownLatch nextStartEntered = new CountDownLatch(1);
        AtomicReference<AutomationCoordinator.StartResult> nextStart = new AtomicReference<>();
        AutomationFlow second = new SynchronousUpdateFlow(
                "campaign", "战役", runtime, nextProgress, nextStartEntered
        );
        Thread nextThread = new Thread(() -> nextStart.set(runtime.start(second)));
        nextThread.start();
        assertTrue("B may reserve after A releases", nextStartEntered.await(1, TimeUnit.SECONDS));

        continueTerminalRecording.countDown();
        terminalThread.join(1000);
        nextThread.join(1000);
        assertFalse(terminalThread.isAlive());
        assertFalse(nextThread.isAlive());
        assertTrue(nextStart.get().started);
        assertSame(nextProgress, runtime.latestUpdate());

        dispatcher.runAll();
        assertFalse(received.isEmpty());
        assertSame("listener delivery must not regress to stale A", nextProgress,
                received.get(received.size() - 1));
    }

    private static final class QueuedDispatcher implements CallbackDispatcher {
        final List<Runnable> actions = new ArrayList<>();

        @Override public synchronized void dispatch(Runnable action) { actions.add(action); }

        void runAll() {
            List<Runnable> pending;
            synchronized (this) {
                pending = new ArrayList<>(actions);
                actions.clear();
            }
            for (Runnable action : pending) {
                action.run();
            }
        }
    }

    private static final class SynchronousUpdateFlow implements AutomationFlow {
        private final String id;
        private final String label;
        private final AutomationRuntime runtime;
        private final AutomationUpdate update;
        private final CountDownLatch startEntered;

        SynchronousUpdateFlow(String id, String label, AutomationRuntime runtime,
                              AutomationUpdate update, CountDownLatch startEntered) {
            this.id = id;
            this.label = label;
            this.runtime = runtime;
            this.update = update;
            this.startEntered = startEntered;
        }

        @Override public String id() { return id; }
        @Override public String label() { return label; }

        @Override public boolean start() {
            startEntered.countDown();
            runtime.onUpdate(update);
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
        final AtomicInteger cancelCalls = new AtomicInteger();

        TestFlow(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override public String id() { return id; }
        @Override public String label() { return label; }
        @Override public boolean start() { return true; }
        @Override public boolean supportsPause() { return true; }
        @Override public void pause() { }
        @Override public void resume() { }
        @Override public void cancel() { cancelCalls.incrementAndGet(); }
        @Override public boolean isRunning() { return true; }
    }
}
