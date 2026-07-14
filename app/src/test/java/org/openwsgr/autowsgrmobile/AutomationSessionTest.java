package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class AutomationSessionTest {
    @Test public void activeTimeExcludesPausedDurationIncludingCancellationWhilePaused() {
        AtomicLong clockNanos = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(100L));
        AutomationControl control = new AutomationControl(clockNanos::get);
        long startedAtNanos = control.activeTimeNanos();

        clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(5L));
        assertEquals(5L, TimeUnit.NANOSECONDS.toMillis(
                control.activeTimeNanos() - startedAtNanos
        ));

        control.pause();
        clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(200L));
        assertEquals(5L, TimeUnit.NANOSECONDS.toMillis(
                control.activeTimeNanos() - startedAtNanos
        ));

        control.resume();
        clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(7L));
        assertEquals(12L, TimeUnit.NANOSECONDS.toMillis(
                control.activeTimeNanos() - startedAtNanos
        ));

        control.pause();
        clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(300L));
        control.cancel();
        assertEquals(12L, TimeUnit.NANOSECONDS.toMillis(
                control.activeTimeNanos() - startedAtNanos
        ));
    }

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

    @Test public void cancellationThatLinearizesFirstForcesCancelledTerminal() throws Exception {
        AutomationControl control = new AutomationControl();
        RecordingSink sink = new RecordingSink();
        List<AutomationUpdate> updates = Collections.synchronizedList(new ArrayList<>());
        AutomationSession session = new AutomationSession(
                "normal", "cancel-wins", control, sink, updates::add
        );
        CountDownLatch actionEntered = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        Thread action = new Thread(() -> {
            try {
                control.performAction(() -> {
                    actionEntered.countDown();
                    try {
                        releaseAction.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cancel-wins-held-action");
        action.start();
        assertTrue(actionEntered.await(1, TimeUnit.SECONDS));

        Thread cancel = new Thread(session::cancel, "cancel-wins-cancel");
        cancel.start();
        boolean cancelBlocked = awaitThreadState(cancel, Thread.State.BLOCKED, 1000L);
        if (!cancelBlocked) {
            releaseAction.countDown();
        }
        assertTrue("cancel must own the session lock while waiting for the action lock",
                cancelBlocked);

        AtomicReference<Boolean> finishResult = new AtomicReference<>();
        Thread finish = new Thread(() -> finishResult.set(session.finish(
                AutomationOutcome.SUCCESS, "完成", "success raced cancel", false
        )), "cancel-wins-finish");
        finish.start();
        Thread.sleep(100);
        assertNull(finishResult.get());

        releaseAction.countDown();
        action.join(1000);
        cancel.join(1000);
        finish.join(1000);
        assertFalse(action.isAlive());
        assertFalse(cancel.isAlive());
        assertFalse(finish.isAlive());
        assertEquals(Boolean.TRUE, finishResult.get());
        assertEquals(1, countTerminal(updates));
        assertEquals(AutomationOutcome.CANCELLED, updates.get(0).outcome);
        assertEquals(0, sink.saveCalls.get());
    }

    @Test public void terminalThatLinearizesFirstIsNotReplacedByLaterCancel() throws Exception {
        BlockingAppendSink sink = new BlockingAppendSink();
        List<AutomationUpdate> updates = Collections.synchronizedList(new ArrayList<>());
        AutomationSession session = new AutomationSession(
                "campaign", "finish-wins", new AutomationControl(), sink, updates::add
        );

        Thread finish = new Thread(() -> session.finish(
                AutomationOutcome.SUCCESS, "完成", "terminal claimed first", false
        ), "finish-wins-finish");
        finish.start();
        assertTrue(sink.appendEntered.await(1, TimeUnit.SECONDS));

        Thread cancel = new Thread(session::cancel, "finish-wins-cancel");
        cancel.start();
        Thread.sleep(100);
        assertTrue("cancel must wait while terminal owns the session lock", cancel.isAlive());

        sink.releaseAppend.countDown();
        finish.join(1000);
        cancel.join(1000);
        assertFalse(finish.isAlive());
        assertFalse(cancel.isAlive());
        assertEquals(1, countTerminal(updates));
        assertEquals(AutomationOutcome.SUCCESS, updates.get(0).outcome);
    }

    @Test public void adoptedNestedEvidenceSurvivesRunningAndCancellationTerminal() {
        RecordingSink sink = new RecordingSink();
        List<AutomationUpdate> updates = new ArrayList<>();
        AutomationSession session = new AutomationSession(
                "expedition", "scheduler", new AutomationControl(), sink, updates::add
        );
        AutomationUpdate nested = AutomationUpdate.progress(
                "expedition", "pass", "本轮", "collecting",
                "nested-pass.log", "nested-pass.png"
        );

        AutomationSession.NestedEvidenceScope scope =
                session.beginNestedEvidence("pass-cancel-evidence");
        assertTrue(session.progressFromNested(scope, nested, "调度中", "forwarded pass"));
        session.cancel();
        assertTrue(session.finish(
                AutomationOutcome.FAILED, "本轮失败", "cancel won", true
        ));

        assertEquals(2, updates.size());
        assertEquals(AutomationOutcome.RUNNING, updates.get(0).outcome);
        assertEquals("nested-pass.log", updates.get(0).logPath);
        assertEquals("nested-pass.png", updates.get(0).screenshotPath);
        assertEquals(AutomationOutcome.CANCELLED, updates.get(1).outcome);
        assertEquals("nested-pass.log", updates.get(1).logPath);
        assertEquals("nested-pass.png", updates.get(1).screenshotPath);
        assertEquals("cancel must not replace adopted evidence with a failure capture",
                0, sink.saveCalls.get());
    }

    @Test public void newNestedPassClearsOldScreenshotAndFailureCapturesFreshEvidence() {
        FreshEvidenceSink sink = new FreshEvidenceSink("fresh.png");
        List<AutomationUpdate> updates = new ArrayList<>();
        AutomationSession session = new AutomationSession(
                "expedition", "scheduler-two-pass", new AutomationControl(), sink, updates::add
        );
        AutomationSession.NestedEvidenceScope passOne =
                session.beginNestedEvidence("pass-1");
        assertTrue(session.progressFromNested(
                passOne,
                AutomationUpdate.progress(
                        "expedition", "pass-1", "第一轮", "done",
                        "pass1.log", "old.png"
                ),
                "调度第一轮", "pass=1"
        ));

        AutomationSession.NestedEvidenceScope passTwo =
                session.beginNestedEvidence("pass-2");
        assertFalse("late pass-one evidence must not reclaim the active scope",
                session.progressFromNested(
                        passOne,
                        AutomationUpdate.terminal(
                                "expedition", "pass-1", AutomationOutcome.FAILED,
                                "迟到", "late", "late-pass1.log", "late-old.png"
                        ),
                        "迟到第一轮", "pass=1 late"
                ));
        assertTrue(session.progressFromNested(
                passTwo,
                AutomationUpdate.terminal(
                        "expedition", "pass-2", AutomationOutcome.FAILED,
                        "第二轮失败", "no screenshot", "pass2.log", ""
                ),
                "调度第二轮", "pass=2 failed"
        ));

        assertTrue(session.finish(
                AutomationOutcome.FAILED, "调度失败", "pass=2", true
        ));

        assertEquals(1, sink.saveCalls.get());
        AutomationUpdate terminal = updates.get(updates.size() - 1);
        assertEquals(AutomationOutcome.FAILED, terminal.outcome);
        assertEquals("pass2.log", terminal.logPath);
        assertEquals("fresh.png", terminal.screenshotPath);
        assertFalse("the late prior-pass callback must not publish progress",
                containsMessage(updates, "迟到第一轮"));
    }

    @Test public void currentNestedPassNonEmptyEvidenceReplacesPriorPassEvidence() {
        RecordingSink sink = new RecordingSink();
        List<AutomationUpdate> updates = new ArrayList<>();
        AutomationSession session = new AutomationSession(
                "expedition", "scheduler-replace", new AutomationControl(), sink, updates::add
        );
        AutomationSession.NestedEvidenceScope passOne =
                session.beginNestedEvidence("pass-1");
        assertTrue(session.progressFromNested(
                passOne,
                AutomationUpdate.progress(
                        "expedition", "pass-1", "第一轮", "old",
                        "pass1.log", "old.png"
                ),
                "第一轮", "old"
        ));
        AutomationSession.NestedEvidenceScope passTwo =
                session.beginNestedEvidence("pass-2");
        assertTrue(session.progressFromNested(
                passTwo,
                AutomationUpdate.terminal(
                        "expedition", "pass-2", AutomationOutcome.FAILED,
                        "第二轮", "new", "pass2.log", "new.png"
                ),
                "第二轮", "new"
        ));

        assertTrue(session.finish(AutomationOutcome.FAILED, "失败", "pass=2", true));
        AutomationUpdate terminal = updates.get(updates.size() - 1);
        assertEquals("pass2.log", terminal.logPath);
        assertEquals("new.png", terminal.screenshotPath);
        assertEquals("current non-empty screenshot needs no parent recapture",
                0, sink.saveCalls.get());
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

    private static boolean containsMessage(List<AutomationUpdate> updates, String message) {
        for (AutomationUpdate update : updates) {
            if (message.equals(update.message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean awaitThreadState(Thread thread, Thread.State state, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == state) {
                return true;
            }
            Thread.sleep(5L);
        }
        return thread.getState() == state;
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

    private static final class BlockingAppendSink implements AutomationSession.Sink {
        final CountDownLatch appendEntered = new CountDownLatch(1);
        final CountDownLatch releaseAppend = new CountDownLatch(1);

        @Override public void append(String line) throws IOException {
            appendEntered.countDown();
            try {
                releaseAppend.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override public String logPath() { return "finish-first.log"; }
        @Override public String saveFailure(String runId) { return "finish-first.png"; }
    }

    private static final class FreshEvidenceSink implements AutomationSession.Sink {
        final AtomicInteger saveCalls = new AtomicInteger();
        final String savedPath;

        FreshEvidenceSink(String savedPath) {
            this.savedPath = savedPath;
        }

        @Override public void append(String line) throws IOException { }
        @Override public String logPath() { return "parent.log"; }

        @Override public String saveFailure(String runId) {
            saveCalls.incrementAndGet();
            return savedPath;
        }
    }
}
