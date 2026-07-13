package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class Task5RunnerSafetyTest {
    @Test public void normalNavigatorPausesAtEveryIndividualTapBoundary() throws Exception {
        AutomationControl control = new AutomationControl();
        CountDownLatch firstTap = new CountDownLatch(1);
        AtomicInteger taps = new AtomicInteger();
        MinimumNormalBattleNavigator navigator = new MinimumNormalBattleNavigator(
                (x, y) -> {
                    if (taps.incrementAndGet() == 1) {
                        control.pause();
                        firstTap.countDown();
                    }
                    return true;
                },
                delayMs -> { }
        );
        NormalSortieTarget target = NormalSortieTarget.of(1, 1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(navigator.navigateToTarget(
                target,
                control::cancelled,
                checkpoint(control),
                (status, detail) -> { }
        )), "normal-navigator-pause");

        worker.start();
        assertTrue(firstTap.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals("pause after tap one must block before tap two", 1, taps.get());
        assertTrue(worker.isAlive());

        control.resume();
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertEquals(Boolean.TRUE, result.get());
        assertEquals(MinimumNormalBattleNavigator.tapPlanForTarget(target).size(), taps.get());
    }

    @Test public void overlayCancelUnblocksPausedBoundaryWithoutSecondTap() throws Exception {
        AutomationControl control = new AutomationControl();
        CountDownLatch firstTap = new CountDownLatch(1);
        AtomicInteger taps = new AtomicInteger();
        MainPageOverlayNavigator navigator = new MainPageOverlayNavigator(
                (x, y) -> {
                    if (taps.incrementAndGet() == 1) {
                        control.pause();
                        firstTap.countDown();
                    }
                    return true;
                },
                delayMs -> { }
        );
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(navigator.dismiss(
                MainPageOverlaySignatures.Kind.SIGN,
                false,
                control::cancelled,
                overlayCheckpoint(control),
                (status, detail) -> { }
        )), "overlay-navigator-cancel");

        worker.start();
        assertTrue(firstTap.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(1, taps.get());
        control.cancel();
        worker.join(1000);

        assertFalse(worker.isAlive());
        assertEquals(Boolean.FALSE, result.get());
        assertEquals("cancelled paused boundary must dispatch no second gesture", 1, taps.get());
    }

    @Test public void oneShotGateRejectsReuseAndReportsPreStartCancellation() {
        OneShotStartGate gate = new OneShotStartGate();

        assertEquals(OneShotStartGate.Decision.PRE_CANCELLED, gate.claim(true));
        assertEquals(OneShotStartGate.Decision.REJECTED, gate.claim(false));

        OneShotStartGate accepted = new OneShotStartGate();
        assertEquals(OneShotStartGate.Decision.START, accepted.claim(false));
        assertEquals(OneShotStartGate.Decision.REJECTED, accepted.claim(false));
    }

    @Test public void cancelledGuardedActionBoundaryDispatchesNoGesture() throws Exception {
        AutomationControl control = new AutomationControl();
        AtomicInteger gestures = new AtomicInteger();
        control.cancel();

        boolean dispatched = control.performAction(() -> {
            gestures.incrementAndGet();
            return true;
        });

        assertFalse(dispatched);
        assertEquals(0, gestures.get());
    }

    @Test public void waiterExceptionBecomesExplicitFailedTerminal() {
        List<LiveCombatFlowWaiter.Update> updates = new ArrayList<>();

        LiveCombatFlowWaiter.runWorkerBoundary(
                () -> { throw new IllegalStateException("scan exploded"); },
                updates::add
        );

        assertEquals(1, updates.size());
        LiveCombatFlowWaiter.Update failed = updates.get(0);
        assertFalse(failed.running);
        assertTrue(failed.failed);
        assertFalse(failed.matched);
        assertFalse(failed.cancelled);
        assertFalse(failed.timedOut);
    }

    @Test public void stoppedGuardedChildWithoutTerminalFailsClosedWithoutHang() {
        AutomationSession session = new AutomationSession(
                "guarded", "parent", new AutomationControl(), new EmptySink(), update -> { }
        );
        GuardedCombatRunner.StepResult result = new GuardedCombatRunner.StepResult();
        AtomicBoolean childCancelled = new AtomicBoolean();
        GuardedCombatRunner.ChildState stoppedChild = new GuardedCombatRunner.ChildState() {
            @Override public boolean start() { return true; }
            @Override public boolean isRunning() { return false; }
            @Override public void cancel() { childCancelled.set(true); }
        };

        long started = System.nanoTime();
        GuardedCombatStepper.Update update = result.await(
                session, stoppedChild, 500L, 10L
        );
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertNotNull(update);
        assertEquals(GuardedCombatStepper.StepOutcome.FAILED, update.outcome);
        assertTrue("stopped child must fail closed promptly, elapsed=" + elapsedMs,
                elapsedMs < 400L);
        assertFalse("already-stopped child needs no timeout cancellation", childCancelled.get());
    }

    @Test public void parentCancellationPreventsGuardedChildPublicationStart() {
        AutomationSession session = new AutomationSession(
                "guarded", "parent-cancelled", new AutomationControl(), new EmptySink(),
                update -> { }
        );
        AtomicInteger childStarts = new AtomicInteger();
        GuardedCombatRunner.ChildState child = new GuardedCombatRunner.ChildState() {
            @Override public boolean start() {
                childStarts.incrementAndGet();
                return true;
            }

            @Override public boolean isRunning() { return false; }
            @Override public void cancel() { }
        };
        session.cancel();

        assertFalse(GuardedCombatRunner.startChildIfAllowed(session, child));
        assertEquals(0, childStarts.get());
    }

    @Test public void guardedChildTerminalTimeoutCancelsChildAndFailsClosed() {
        AutomationSession session = new AutomationSession(
                "guarded", "parent-timeout", new AutomationControl(), new EmptySink(),
                update -> { }
        );
        AtomicBoolean childCancelled = new AtomicBoolean();
        GuardedCombatRunner.ChildState stuckChild = new GuardedCombatRunner.ChildState() {
            @Override public boolean start() { return true; }
            @Override public boolean isRunning() { return true; }
            @Override public void cancel() { childCancelled.set(true); }
        };

        GuardedCombatStepper.Update update = new GuardedCombatRunner.StepResult().await(
                session, stuckChild, 80L, 10L
        );

        assertNotNull(update);
        assertEquals(GuardedCombatStepper.StepOutcome.FAILED, update.outcome);
        assertTrue(childCancelled.get());
    }

    @Test public void dockFullEvidenceIsTerminalAndNeverPermitsGesture() {
        AtomicInteger gestures = new AtomicInteger();

        AutomationOutcome outcome = GuardedCombatRunner.terminalOutcomeBeforeGesture(
                NativeCombatPhase.DOCK_FULL
        );
        if (outcome == null) {
            gestures.incrementAndGet();
        }

        assertEquals(AutomationOutcome.DOCK_FULL, outcome);
        assertEquals(0, gestures.get());
        assertTrue(contains(GuardedCombatStepper.guardedPhases(), NativeCombatPhase.DOCK_FULL));
    }

    @Test public void expeditionNotificationProbeIsTypedTriState() {
        assertEquals(
                AutomationOutcome.BLOCKED,
                ExpeditionCollectionRunner.notificationOutcome(
                        ExpeditionCollectionRunner.NotificationProbe.unavailable("missing frame"), 0
                )
        );
        assertEquals(
                AutomationOutcome.NO_ACTION,
                ExpeditionCollectionRunner.notificationOutcome(
                        ExpeditionCollectionRunner.NotificationProbe.available(false), 0
                )
        );
        assertEquals(
                AutomationOutcome.SUCCESS,
                ExpeditionCollectionRunner.notificationOutcome(
                        ExpeditionCollectionRunner.NotificationProbe.available(false), 1
                )
        );
        assertEquals(
                AutomationOutcome.RUNNING,
                ExpeditionCollectionRunner.notificationOutcome(
                        ExpeditionCollectionRunner.NotificationProbe.available(true), 0
                )
        );
    }

    @Test public void campaignTimeoutIsBlockedUnlessNoTimesEvidenceIsExplicit() {
        assertEquals(
                AutomationOutcome.BLOCKED,
                CampaignBattleRunner.terminalOutcomeForEntryWait(false)
        );
        assertEquals(
                AutomationOutcome.EXHAUSTED,
                CampaignBattleRunner.terminalOutcomeForEntryWait(true)
        );
    }

    @Test public void schedulerRunningForwardPreservesNestedEvidencePaths() {
        AutomationUpdate nested = AutomationUpdate.terminal(
                "expedition", "pass", AutomationOutcome.FAILED,
                "本轮失败", "recognition failed", "nested.log", "nested.png"
        );

        AutomationUpdate forwarded = AutoExpeditionSchedulerRunner.forwardedPassUpdate(
                nested, "scheduler", "scheduler.log"
        );

        assertEquals(AutomationOutcome.RUNNING, forwarded.outcome);
        assertEquals("nested.log", forwarded.logPath);
        assertEquals("nested.png", forwarded.screenshotPath);
    }

    private static MinimumNormalBattleNavigator.ActionBoundary checkpoint(
            AutomationControl control
    ) {
        return () -> {
            try {
                return control.checkpoint();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }

    private static MainPageOverlayNavigator.ActionBoundary overlayCheckpoint(
            final AutomationControl control
    ) {
        return () -> {
            try {
                return control.checkpoint();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }

    private static boolean contains(NativeCombatPhase[] phases, NativeCombatPhase expected) {
        for (NativeCombatPhase phase : phases) {
            if (phase == expected) {
                return true;
            }
        }
        return false;
    }

    private static final class EmptySink implements AutomationSession.Sink {
        @Override public void append(String line) throws IOException { }
        @Override public String logPath() { return "parent.log"; }
        @Override public String saveFailure(String runId) { return "parent.png"; }
    }
}
