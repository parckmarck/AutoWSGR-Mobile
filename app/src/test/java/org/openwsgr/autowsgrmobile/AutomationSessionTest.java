package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class AutomationSessionTest {
    @Test public void pausedControlBlocksCheckpointUntilResume() throws Exception {
        AutomationControl control = new AutomationControl();
        control.pause();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            entered.countDown();
            try {
                result.set(control.checkpoint());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertNull(result.get());
        assertTrue(control.paused());

        control.resume();
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertEquals(Boolean.TRUE, result.get());
        assertFalse(control.paused());
    }

    @Test public void cancelUnblocksPausedCheckpointAndReturnsFalse() throws Exception {
        AutomationControl control = new AutomationControl();
        control.pause();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            entered.countDown();
            try {
                result.set(control.checkpoint());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        control.cancel();
        worker.join(1000);

        assertFalse(worker.isAlive());
        assertEquals(Boolean.FALSE, result.get());
        assertTrue(control.cancelled());
        assertFalse(control.paused());
        assertFalse(control.checkpoint());
    }

    @Test public void sessionFinalizationLogsAndSavesEvidenceOnlyOnce() throws Exception {
        RecordingSink sink = new RecordingSink();
        List<AutomationUpdate> updates = Collections.synchronizedList(new ArrayList<>());
        AutomationSession session = new AutomationSession(
                "normal", "run-7", new AutomationControl(), sink, updates::add
        );
        session.progress("准备", "preflight");

        int workerCount = 12;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerCount);
        AtomicInteger successfulFinishes = new AtomicInteger();
        for (int index = 0; index < workerCount; index++) {
            final int worker = index;
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    if (worker % 2 == 0) {
                        session.progress("进行中", "worker-" + worker);
                    } else if (session.finish(
                            AutomationOutcome.FAILED, "失败", "worker-" + worker, true
                    )) {
                        successfulFinishes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue(ready.await(1, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));

        assertEquals(1, successfulFinishes.get());
        assertEquals(1, sink.saveCalls.get());
        assertEquals(1, countContaining(sink.lines, "status=FAILED"));
        assertEquals(1, countTerminal(updates));
        assertEquals(AutomationOutcome.FAILED, updates.get(updates.size() - 1).outcome);
        AutomationUpdate terminal = updates.get(updates.size() - 1);
        assertEquals("run.log", terminal.logPath);
        assertEquals("failure.png", terminal.screenshotPath);

        assertFalse(session.finish(AutomationOutcome.CANCELLED, "取消", "later", true));
        assertEquals(1, sink.saveCalls.get());
        assertEquals(1, countTerminal(updates));
        assertThrows(IllegalArgumentException.class, () -> session.finish(
                AutomationOutcome.RUNNING, "bad", "", false
        ));
    }

    private static int countContaining(List<String> lines, String needle) {
        int count = 0;
        synchronized (lines) {
            for (String line : lines) {
                if (line.contains(needle)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countTerminal(List<AutomationUpdate> updates) {
        int count = 0;
        synchronized (updates) {
            for (AutomationUpdate update : updates) {
                if (update.outcome.terminal) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class RecordingSink implements AutomationSession.Sink {
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger saveCalls = new AtomicInteger();

        @Override public void append(String line) throws IOException { lines.add(line); }
        @Override public String logPath() { return "run.log"; }

        @Override public String saveFailure(String runId) {
            saveCalls.incrementAndGet();
            return "failure.png";
        }
    }
}
