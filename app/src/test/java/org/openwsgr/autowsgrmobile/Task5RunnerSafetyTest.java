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
        AutomationSession session = session("normal-multi-tap", update -> { });
        CountDownLatch firstTap = new CountDownLatch(1);
        AtomicInteger taps = new AtomicInteger();
        MinimumNormalBattleNavigator navigator = new MinimumNormalBattleNavigator(
                (x, y) -> {
                    if (taps.incrementAndGet() == 1) {
                        session.pause();
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
                session::cancelled,
                session.actionBoundary(),
                (status, detail) -> { }
        )), "normal-navigator-pause");

        worker.start();
        assertTrue(firstTap.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals("pause after tap one must block before tap two", 1, taps.get());
        assertTrue(worker.isAlive());

        session.resume();
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertEquals(Boolean.TRUE, result.get());
        assertEquals(MinimumNormalBattleNavigator.tapPlanForTarget(target).size(), taps.get());
    }

    @Test public void overlayCancelUnblocksPausedBoundaryWithoutSecondTap() throws Exception {
        AutomationSession session = session("overlay-multi-tap", update -> { });
        CountDownLatch firstTap = new CountDownLatch(1);
        AtomicInteger taps = new AtomicInteger();
        MainPageOverlayNavigator navigator = new MainPageOverlayNavigator(
                (x, y) -> {
                    if (taps.incrementAndGet() == 1) {
                        session.pause();
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
                session::cancelled,
                session.actionBoundary(),
                (status, detail) -> { }
        )), "overlay-navigator-cancel");

        worker.start();
        assertTrue(firstTap.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(1, taps.get());
        session.cancel();
        worker.join(1000);

        assertFalse(worker.isAlive());
        assertEquals(Boolean.FALSE, result.get());
        assertEquals("cancelled paused boundary must dispatch no second gesture", 1, taps.get());
    }

    @Test public void cancellationFirstPreventsActualTapInAllFourNavigators() {
        AutomationSession session = session("cancel-first-navigators", update -> { });
        session.cancel();
        AtomicInteger taps = new AtomicInteger();
        MinimumNormalBattleNavigator normal = new MinimumNormalBattleNavigator(
                (x, y) -> { taps.incrementAndGet(); return true; }, delayMs -> { }
        );
        MainPageOverlayNavigator overlay = new MainPageOverlayNavigator(
                (x, y) -> { taps.incrementAndGet(); return true; }, delayMs -> { }
        );
        ExpeditionCollectionNavigator expedition = new ExpeditionCollectionNavigator(
                (x, y) -> { taps.incrementAndGet(); return true; }, delayMs -> { }
        );
        CampaignBattleNavigator campaign = new CampaignBattleNavigator(
                (x, y) -> { taps.incrementAndGet(); return true; }, delayMs -> { }
        );

        assertFalse(normal.tapStartBattle(
                session::cancelled, session.actionBoundary(), (status, detail) -> { }
        ));
        assertFalse(overlay.dismiss(
                MainPageOverlaySignatures.Kind.SIGN, false,
                session::cancelled, session.actionBoundary(), (status, detail) -> { }
        ));
        assertFalse(expedition.tapExpeditionPanel(
                session::cancelled, session.actionBoundary(), (status, detail) -> { }
        ));
        assertFalse(campaign.tapBattlePanel(
                session::cancelled, session.actionBoundary(), (status, detail) -> { }
        ));
        assertEquals("a cancellation admitted before the action must prevent every real tap", 0,
                taps.get());
    }

    @Test public void actionFirstAllowsExactlyOneTapAndMakesCancellationWait() throws Exception {
        AutomationSession session = session("action-first-navigator", update -> { });
        CountDownLatch tapEntered = new CountDownLatch(1);
        CountDownLatch releaseTap = new CountDownLatch(1);
        AtomicInteger taps = new AtomicInteger();
        MinimumNormalBattleNavigator navigator = new MinimumNormalBattleNavigator(
                (x, y) -> {
                    taps.incrementAndGet();
                    tapEntered.countDown();
                    awaitUninterruptibly(releaseTap);
                    return true;
                },
                delayMs -> { }
        );
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread action = new Thread(() -> result.set(navigator.tapStartBattle(
                session::cancelled, session.actionBoundary(), (status, detail) -> { }
        )), "navigator-action-first");
        action.start();
        assertTrue(tapEntered.await(1, TimeUnit.SECONDS));

        Thread cancel = new Thread(session::cancel, "navigator-action-first-cancel");
        cancel.start();
        boolean blocked = awaitThreadState(cancel, Thread.State.BLOCKED, 1000L);
        if (!blocked) {
            releaseTap.countDown();
        }
        assertTrue("cancel must wait for the admitted single gesture closure", blocked);
        assertEquals(1, taps.get());

        releaseTap.countDown();
        action.join(1000L);
        cancel.join(1000L);
        assertFalse(action.isAlive());
        assertFalse(cancel.isAlive());
        assertEquals(1, taps.get());
        assertEquals(Boolean.FALSE, result.get());
    }

    @Test public void pauseBlocksRealNavigatorActionAndCancelUnblocksWithoutTap() throws Exception {
        AutomationSession session = session("paused-expedition-action", update -> { });
        session.pause();
        AtomicInteger taps = new AtomicInteger();
        ExpeditionCollectionNavigator navigator = new ExpeditionCollectionNavigator(
                (x, y) -> { taps.incrementAndGet(); return true; }, delayMs -> { }
        );
        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread action = new Thread(() -> result.set(navigator.tapReadySlot(
                2, session::cancelled, session.actionBoundary(), (status, detail) -> { }
        )), "paused-expedition-action");
        action.start();
        assertTrue("the session action boundary must be waiting on pause",
                awaitThreadState(action, Thread.State.WAITING, 1000L));
        assertEquals(0, taps.get());

        session.cancel();
        action.join(1000L);
        assertFalse(action.isAlive());
        assertEquals(Boolean.FALSE, result.get());
        assertEquals(0, taps.get());
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

    @Test public void waiterCancellationBeforePublicationCancelsOnceAndNeverStarts() {
        GuardedCombatStepper.WaiterLifecycle lifecycle =
                new GuardedCombatStepper.WaiterLifecycle();
        FakeWaiter waiter = new FakeWaiter(null, null);

        lifecycle.cancel();
        assertEquals(
                GuardedCombatStepper.WaiterStartOutcome.CANCELLED,
                lifecycle.publishAndStart(waiter)
        );
        lifecycle.cancel();

        assertEquals(0, waiter.starts.get());
        assertEquals(1, waiter.cancels.get());
        assertFalse(lifecycle.hasActiveWaiter());
    }

    @Test public void waiterPublicationFirstMakesCancellationReachSameWaiterExactlyOnce()
            throws Exception {
        GuardedCombatStepper.WaiterLifecycle lifecycle =
                new GuardedCombatStepper.WaiterLifecycle();
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        FakeWaiter waiter = new FakeWaiter(startEntered, releaseStart);
        AtomicReference<GuardedCombatStepper.WaiterStartOutcome> outcome =
                new AtomicReference<>();
        Thread publication = new Thread(
                () -> outcome.set(lifecycle.publishAndStart(waiter)),
                "waiter-publication-first"
        );
        publication.start();
        assertTrue(startEntered.await(1, TimeUnit.SECONDS));

        Thread cancel = new Thread(lifecycle::cancel, "waiter-publication-cancel");
        cancel.start();
        cancel.join(1000L);
        assertFalse(cancel.isAlive());
        assertEquals(1, waiter.cancels.get());

        releaseStart.countDown();
        publication.join(1000L);
        lifecycle.cancel();
        assertFalse(publication.isAlive());
        assertEquals(GuardedCombatStepper.WaiterStartOutcome.CANCELLED, outcome.get());
        assertEquals(1, waiter.starts.get());
        assertEquals(1, waiter.cancels.get());
        assertFalse("a cancelled publication must not leak an active waiter",
                lifecycle.hasActiveWaiter());
    }

    @Test public void cancellationBeforeGestureRunningEnqueueRejectsPublication() {
        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        AtomicInteger gestures = new AtomicInteger(1);
        AtomicInteger enqueues = new AtomicInteger();

        gate.cancel();
        GuardedCombatStepper.PublicationResult result = gate.publishRunning(() -> {
            enqueues.incrementAndGet();
            return true;
        });

        assertEquals("the already-admitted gesture is not erased", 1, gestures.get());
        assertEquals(GuardedCombatStepper.PublicationResult.REJECTED, result);
        assertEquals("cancel-first must suppress gesture-dispatched RUNNING", 0,
                enqueues.get());
    }

    @Test public void runningEnqueueFirstMakesCancelWaitThenRejectsLaterRunning()
            throws Exception {
        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        CountDownLatch enqueueEntered = new CountDownLatch(1);
        CountDownLatch releaseEnqueue = new CountDownLatch(1);
        AtomicInteger enqueues = new AtomicInteger();
        AtomicReference<GuardedCombatStepper.PublicationResult> first =
                new AtomicReference<>();
        Thread publish = new Thread(() -> first.set(gate.publishRunning(() -> {
            enqueues.incrementAndGet();
            enqueueEntered.countDown();
            awaitUninterruptibly(releaseEnqueue);
            return true;
        })), "stepper-running-enqueue-first");
        publish.start();
        assertTrue(enqueueEntered.await(1, TimeUnit.SECONDS));

        Thread cancel = new Thread(gate::cancel, "stepper-publication-cancel");
        cancel.start();
        boolean blocked = awaitThreadState(cancel, Thread.State.BLOCKED, 1000L);
        if (!blocked) {
            releaseEnqueue.countDown();
        }
        assertTrue("cancel must linearize after the in-gate enqueue action", blocked);

        releaseEnqueue.countDown();
        publish.join(1000L);
        cancel.join(1000L);
        assertFalse(publish.isAlive());
        assertFalse(cancel.isAlive());
        assertEquals(GuardedCombatStepper.PublicationResult.ENQUEUED, first.get());
        assertEquals(1, enqueues.get());
        assertEquals(
                GuardedCombatStepper.PublicationResult.REJECTED,
                gate.publishRunning(() -> { enqueues.incrementAndGet(); return true; })
        );
        assertEquals(1, enqueues.get());
    }

    @Test public void cancellationBetweenWaiterObservationAndPublicationRejectsRunning()
            throws Exception {
        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        CountDownLatch waiterStateObserved = new CountDownLatch(1);
        CountDownLatch allowPublication = new CountDownLatch(1);
        AtomicInteger enqueues = new AtomicInteger();
        AtomicReference<GuardedCombatStepper.PublicationResult> result =
                new AtomicReference<>();
        Thread waiterCallback = new Thread(() -> {
            assertFalse(gate.cancelled());
            waiterStateObserved.countDown();
            awaitUninterruptibly(allowPublication);
            result.set(gate.publishRunning(() -> {
                enqueues.incrementAndGet();
                return true;
            }));
        }, "waiter-running-publication-race");
        waiterCallback.start();
        assertTrue(waiterStateObserved.await(1, TimeUnit.SECONDS));

        gate.cancel();
        allowPublication.countDown();
        waiterCallback.join(1000L);

        assertFalse(waiterCallback.isAlive());
        assertEquals(GuardedCombatStepper.PublicationResult.REJECTED, result.get());
        assertEquals(0, enqueues.get());
    }

    @Test public void terminalPublicationRemainsAvailableExactlyOnceAfterCancellation() {
        GuardedCombatStepper.PublicationGate gate =
                new GuardedCombatStepper.PublicationGate();
        AtomicInteger terminals = new AtomicInteger();
        gate.cancel();

        assertEquals(
                GuardedCombatStepper.PublicationResult.ENQUEUED,
                gate.publishTerminal(() -> { terminals.incrementAndGet(); return true; })
        );
        assertEquals(
                GuardedCombatStepper.PublicationResult.REJECTED,
                gate.publishTerminal(() -> { terminals.incrementAndGet(); return true; })
        );
        assertEquals(1, terminals.get());
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
        assertEquals(
                AutomationOutcome.DOCK_FULL,
                MinimumNormalBattleRunner.nestedGuardedOutcome(AutomationOutcome.DOCK_FULL)
        );
        assertEquals(
                AutomationOutcome.DOCK_FULL,
                CampaignBattleRunner.nestedGuardedOutcome(AutomationOutcome.DOCK_FULL)
        );
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

    private static AutomationSession session(String runId, AutomationCallback callback) {
        return new AutomationSession(
                "test", runId, new AutomationControl(), new EmptySink(), callback
        );
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

    private static final class FakeWaiter implements GuardedCombatStepper.WaiterHandle {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final CountDownLatch startEntered;
        final CountDownLatch releaseStart;

        FakeWaiter(CountDownLatch startEntered, CountDownLatch releaseStart) {
            this.startEntered = startEntered;
            this.releaseStart = releaseStart;
        }

        @Override public boolean start() {
            starts.incrementAndGet();
            if (startEntered != null) {
                startEntered.countDown();
            }
            if (releaseStart != null) {
                awaitUninterruptibly(releaseStart);
            }
            return true;
        }

        @Override public void cancel() {
            cancels.incrementAndGet();
        }
    }
}
