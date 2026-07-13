package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;

final class GuardedCombatRunner {
    private static final long PHASE_SCAN_TIMEOUT_MS = 3500L;
    private static final int PHASE_SCAN_STRIDE = 24;
    private static final double PHASE_SCAN_THRESHOLD = 0.999;
    private static final int DEFAULT_LOOP_MAX_STEPS = 5;

    private final Context appContext;
    private final AutomationSession session;
    private final int maxSteps;
    private final CombatPlanPolicy planPolicy;
    private final MapNodeData mapNodeData;
    private final RouteNodeTracker routeNodeTracker;
    private final String runnerPlan;
    private ShipDamagePolicy.DamageSnapshot damageSnapshot;

    private Thread worker;
    private GuardedCombatStepper activeStepper;
    private boolean running;

    private GuardedCombatRunner(
            Context context,
            int maxSteps,
            CombatPlanPolicy selectedPolicy,
            AutomationCallback callback
    ) {
        this.appContext = context.getApplicationContext();
        String runId = String.valueOf(System.currentTimeMillis());
        this.session = new AutomationSession("guarded", runId, new AutomationControl(),
                AndroidAutomationSessionSink.create(appContext, "guarded-combat", runId), callback);
        this.maxSteps = Math.max(1, maxSteps);
        this.planPolicy = selectedPolicy == null ? loadDefaultPolicy() : selectedPolicy;
        this.mapNodeData = loadMapNodeData();
        this.routeNodeTracker = mapNodeData == null ? null : new RouteNodeTracker(mapNodeData);
        this.damageSnapshot = ShipDamagePolicy.unknown("ship damage recognizer pending");
        this.runnerPlan = buildRunnerPlan();
    }

    static GuardedCombatRunner oneStepExecuteGuarded(Context context, AutomationCallback callback) {
        return new GuardedCombatRunner(context, 1, null, callback);
    }

    static GuardedCombatRunner boundedLoopExecuteGuarded(Context context, AutomationCallback callback) {
        return new GuardedCombatRunner(context, DEFAULT_LOOP_MAX_STEPS, null, callback);
    }

    static GuardedCombatRunner boundedLoopExecuteGuarded(
            Context context,
            CombatPlanPolicy selectedPolicy,
            AutomationCallback callback
    ) {
        return new GuardedCombatRunner(context, DEFAULT_LOOP_MAX_STEPS, selectedPolicy, callback);
    }

    static String policySummaryForRunner(Context context, CombatPlanPolicy planPolicy) {
        if (planPolicy == null) {
            return context == null
                    ? "planPolicy=unavailable, reason=missing selected policy"
                    : CombatPlanPolicy.defaultRunnerPolicySummary(context);
        }
        return planPolicy.toRunnerSummary();
    }

    static ProbeResult probeSpotContextDecision(Context context) {
        StringBuilder detail = new StringBuilder();
        Bitmap frame = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        frame.eraseColor(0xff182028);
        try {
            EnemyCompositionRecognizer.paintSyntheticFightSlots(
                    frame,
                    "DD",
                    "CVL",
                    "BB",
                    "BB",
                    "AP",
                    "AP"
            );
            EnemyFormationRecognizer.paintSyntheticFormationRoi(frame);

            EnemyCompositionRecognizer.Detection composition = EnemyCompositionRecognizer.detectFight(frame);
            EnemyFormationRecognizer.Detection formation = EnemyFormationRecognizer.detectFight(frame);
            append(detail, "runner spot context=scan"
                    + ", compositionReliable=" + composition.reliable
                    + ", formationReliable=" + formation.reliable);
            append(detail, "runner spot context enemyScan=" + composition.detailLine());
            append(detail, "runner spot context formationScan=" + formation.detailLine());

            CombatPlanPolicy policy = CombatPlanPolicy.load(context, CombatPlanPolicy.RULE_PLAN);
            int routeIndex = policy.routeIndexForNode("C", 0);
            CombatPlanPolicy.PlanDecision spotDecision = policy.decisionForSpotEnemyContext(
                    routeIndex,
                    composition.reliable ? composition.snapshot : null,
                    formation.reliable ? formation.formation : ""
            );
            CombatPlanPolicy.PlanDecision nextFormation = policy.decisionForPhase(
                    NativeCombatPhase.FORMATION,
                    routeIndex,
                    ShipDamagePolicy.allNormal("runner spot context probe"),
                    spotDecision.ruleFormation
            );
            append(detail, "runner spot context=accepted"
                    + ", spotAction=" + spotDecision.actionId
                    + ", carryFormation=" + spotDecision.ruleFormation
                    + ", carriesRuleFormation=" + spotDecision.carriesRuleFormation
                    + ", nextFormation=" + nextFormation.actionId
                    + ", consumesRuleFormation=" + nextFormation.consumesRuleFormation);
            append(detail, "runner spot context spotDecision=" + spotDecision.detail());
            append(detail, "runner spot context nextDecision=" + nextFormation.detail());

            boolean ok = composition.reliable
                    && formation.reliable
                    && "轮型阵".equals(formation.formation)
                    && "spot_fight".equals(spotDecision.actionId)
                    && spotDecision.carriesRuleFormation
                    && spotDecision.ruleFormation == 4
                    && "formation_4".equals(nextFormation.actionId)
                    && nextFormation.consumesRuleFormation;
            return new ProbeResult(ok, detail.toString());
        } catch (java.io.IOException e) {
            append(detail, "runner spot context=failed, reason=" + e.getMessage());
            return new ProbeResult(false, detail.toString());
        } finally {
            frame.recycle();
        }
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        progress("Guarded runner: starting", runnerPlan + "\nphaseScan=queued");
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runLoop();
                } catch (Throwable failure) {
                    if (session.cancelled()) {
                        finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled",
                                runnerPlan + "\nreason=" + failure, false);
                    } else {
                        finish(AutomationOutcome.FAILED, "Guarded runner: failed",
                                runnerPlan + "\nreason=" + failure, true);
                    }
                }
            }
        }, "AutoWSGR-guarded-runner");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    synchronized void cancel() {
        session.cancel();
        if (activeStepper != null) {
            activeStepper.cancel();
        }
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
    }

    synchronized void pause() {
        session.pause();
        if (activeStepper != null) {
            activeStepper.pause();
        }
    }

    synchronized void resume() {
        session.resume();
        if (activeStepper != null) {
            activeStepper.resume();
        }
    }

    synchronized boolean isRunning() {
        return running;
    }

    private void runLoop() {
        int completedSteps = 0;
        int routeIndex = 0;
        int pendingRuleFormation = 0;
        while (!session.cancelled() && completedSteps < maxSteps) {
            if (!session.checkpoint()) {
                finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled",
                        runnerPlan + "\nstepsCompleted=" + completedSteps, false);
                return;
            }
            int stepIndex = completedSteps + 1;
            RouteSync routeSync = updateRouteCursorFromLiveNode(routeIndex);
            routeIndex = routeSync.routeIndex;
            String routeDetail = routeSummary(routeIndex);
            if (routeSync.blocked) {
                finish(AutomationOutcome.BLOCKED,
                        "Guarded runner: route node out of plan",
                        runnerPlan
                                + "\n" + routeSync.detail
                                + "\n" + routeDetail
                                + "\nstepsCompleted=" + completedSteps
                                + "\nphaseScan=skipped by route guard"
                , true);
                return;
            }
            progress(
                    "Guarded runner: scanning step " + stepIndex + "/" + maxSteps,
                    runnerPlan
                            + "\n" + routeSync.detail
                            + "\n" + routeDetail
                            + "\nstepsCompleted=" + completedSteps
                            + "\nphaseScan=candidates "
                            + NativeCombatTransitions.format(GuardedCombatStepper.guardedPhases())
            );

            NativeCombatRecognizer.Recognition recognition = recognizeGuardablePhase();
            if (session.cancelled()) {
                finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled", runnerPlan + "\nstepsCompleted=" + completedSteps + "\nphaseScan=cancelled", false);
                return;
            }
            if (!recognition.matched) {
                finish(AutomationOutcome.BLOCKED,
                        "Guarded runner: no guarded phase",
                        runnerPlan
                                + "\n" + routeSync.detail
                                + "\n" + routeDetail
                                + "\nstepsCompleted=" + completedSteps
                                + "\nphaseScan=" + GuardedCombatStepper.formatRecognition(recognition)
                , true);
                return;
            }

            String damageScanDetail = updateDamageSnapshotForPhase(recognition.phase);
            EnemyScan enemyScan = updateEnemyScanForPhase(recognition.phase);
            CombatPlanPolicy.PlanDecision planDecision = planDecisionFor(
                    recognition.phase,
                    routeIndex,
                    pendingRuleFormation,
                    enemyScan
            );
            final String actionId = planDecision.hasAction()
                    ? planDecision.actionId
                    : GuardedCombatStepper.actionIdForPhase(recognition.phase);
            final String planDecisionDetail = damageScanDetail
                    + "\n" + enemyScan.detail
                    + "\n" + planDecision.detail();
            StepResult stepResult = new StepResult();
            GuardedCombatStepper stepper = GuardedCombatStepper.executeGuardedForActionId(
                    appContext,
                    actionId,
                    stepperCallback(stepIndex, recognition, actionId, routeDetail, planDecisionDetail, stepResult)
            );
            if (stepper == null) {
                finish(AutomationOutcome.BLOCKED,
                        "Guarded runner: no action spec",
                        runnerPlan
                                + "\n" + routeDetail
                                + "\nstepsCompleted=" + completedSteps
                                + "\nphaseScan=" + GuardedCombatStepper.formatRecognition(recognition)
                                + "\n" + planDecisionDetail
                                + "\nselectedAction=none"
                , true);
                return;
            }

            synchronized (this) {
                if (session.cancelled()) {
                    finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled",
                            runnerPlan + "\n" + routeDetail + "\nstepsCompleted=" + completedSteps
                                    + "\nphaseScan=cancelled", false);
                    return;
                }
                activeStepper = stepper;
            }

            String selectedDetail = runnerPlan
                    + "\n" + routeDetail
                    + "\nstepsCompleted=" + completedSteps
                    + "\nstepIndex=" + stepIndex + "/" + maxSteps
                    + "\nphaseScan=" + GuardedCombatStepper.formatRecognition(recognition)
                    + "\n" + planDecisionDetail
                    + "\nselectedAction=" + actionId;
            progress("Guarded runner: selected " + actionId, selectedDetail);
            if (!session.checkpoint()) {
                finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled",
                        selectedDetail + "\ncheckpoint=cancelled before stepper", false);
                return;
            }
            if (!stepper.start()) {
                finish(AutomationOutcome.BLOCKED, "Guarded runner: stepper already running", selectedDetail, true);
                return;
            }

            GuardedCombatStepper.Update finalUpdate = stepResult.await();
            synchronized (this) {
                activeStepper = null;
            }
            if (finalUpdate == null || session.cancelled()) {
                finish(AutomationOutcome.CANCELLED, "Guarded runner: cancelled", selectedDetail + "\nstepperStatus=cancelled or interrupted", false);
                return;
            }

            String finalDetail = selectedDetail
                    + "\nstepperStatus=" + finalUpdate.status
                    + "\n" + finalUpdate.detail;
            if (!stepCompleted(finalUpdate)) {
                finish(stepAutomationOutcome(finalUpdate.outcome),
                        finalStatus(finalUpdate.outcome, actionId), finalDetail,
                        finalUpdate.outcome == GuardedCombatStepper.StepOutcome.FAILED);
                return;
            }

            completedSteps++;
            if (planDecision.carriesRuleFormation) {
                pendingRuleFormation = planDecision.ruleFormation;
                finalDetail += "\nruleFormationCarry=set " + pendingRuleFormation;
            }
            if (planDecision.consumesRuleFormation) {
                pendingRuleFormation = 0;
                finalDetail += "\nruleFormationCarry=consumed";
            }
            if (planDecision.routeAdvances) {
                routeIndex++;
                finalDetail += "\nrouteAdvance=next node, " + routeSummary(routeIndex);
            }
            if (planDecision.routeEnds) {
                finish(AutomationOutcome.SUCCESS, "Guarded runner: route complete", finalDetail + "\nstepsCompleted=" + completedSteps, false);
                return;
            }
            if (completedSteps >= maxSteps) {
                finish(AutomationOutcome.EXHAUSTED, "Guarded runner: max steps reached", finalDetail + "\nstepsCompleted=" + completedSteps, false);
                return;
            }

            progress(
                    "Guarded runner: step complete " + completedSteps + "/" + maxSteps,
                    finalDetail + "\nnext=scan next guarded phase"
            );
        }

        AutomationOutcome outcome = session.cancelled()
                ? AutomationOutcome.CANCELLED : AutomationOutcome.EXHAUSTED;
        finish(outcome,
                session.cancelled() ? "Guarded runner: cancelled" : "Guarded runner: max steps reached",
                runnerPlan + "\n" + routeSummary(routeIndex) + "\nstepsCompleted=" + completedSteps,
                false);
    }

    private NativeCombatRecognizer.Recognition recognizeGuardablePhase() {
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return NativeCombatRecognizer.Recognition.missing("missing MediaProjection frame");
        }
        Bitmap scanFrame = null;
        try {
            scanFrame = GameFrameNormalizer.toPhaseScanFrame(liveFrame);
            return NativeCombatRecognizer.identifyCurrent(
                    scanFrame,
                    new AutoWsgrAssetStore(appContext),
                    GuardedCombatStepper.guardedPhases(),
                    PHASE_SCAN_STRIDE,
                    PHASE_SCAN_THRESHOLD,
                    new PhaseScanDeadline()
            );
        } finally {
            if (scanFrame != null) {
                scanFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private GuardedCombatStepper.Callback stepperCallback(
            final int stepIndex,
            final NativeCombatRecognizer.Recognition phaseScan,
            final String actionId,
            final String routeDetail,
            final String planDecisionDetail,
            final StepResult stepResult
    ) {
        return new GuardedCombatStepper.Callback() {
            @Override
            public void onUpdate(GuardedCombatStepper.Update update) {
                String detail = runnerPlan
                        + "\n" + routeDetail
                        + "\nstepIndex=" + stepIndex + "/" + maxSteps
                        + "\nphaseScan=" + GuardedCombatStepper.formatRecognition(phaseScan)
                        + "\n" + planDecisionDetail
                        + "\nselectedAction=" + actionId
                        + "\nstepperStatus=" + update.status
                        + "\n" + update.detail;
                if (update.running()) {
                    if (!session.cancelled()) {
                        progress("Guarded runner: step running " + stepIndex + "/" + maxSteps + " " + actionId, detail);
                    }
                    return;
                }
                stepResult.finish(update);
            }
        };
    }

    private synchronized void finish(AutomationOutcome outcome, String status, String detail,
                                     boolean saveFailure) {
        running = false;
        activeStepper = null;
        session.finish(outcome, status, detail, saveFailure);
    }

    private void progress(String status, String detail) {
        session.progress(status, detail);
    }

    private String buildRunnerPlan() {
        return "runner=guarded_combat"
                    + "\nmode=execute_guarded_loop"
                    + "\nmaxSteps=" + maxSteps
                    + "\npolicy=scan current guarded phase, guard route deviations, sync live route node when available, scan enemy composition and formation when available, choose action from YAML plan route cursor, carry rule-selected formation, continue only after next wait matched"
                    + "\nphaseScan=stride " + PHASE_SCAN_STRIDE
                    + ", threshold " + String.format(java.util.Locale.US, "%.4f", PHASE_SCAN_THRESHOLD)
                    + ", input=" + GameFrameNormalizer.phaseScanInputLabel()
                    + ", timeout " + PHASE_SCAN_TIMEOUT_MS + "ms"
                    + "\nactionCatalog=" + GuardedCombatStepper.guardedActionCatalog()
                    + "\n" + runnerPolicySummary()
                    + "\nrouteNodePolicy=" + routeNodePolicySummary()
                    + "\ndamagePolicy=live result-page probe, current=" + damageSnapshot.shortLabel()
                    + ", proceed_stop unknown => conservative return"
                    + "\npreflight=" + AndroidControllerBridge.captureStatus()
                + "\npreflight=" + AndroidControllerBridge.touchStatus();
    }

    private CombatPlanPolicy loadDefaultPolicy() {
        try {
            return CombatPlanPolicy.loadDefault(appContext);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private MapNodeData loadMapNodeData() {
        if (planPolicy == null) {
            return null;
        }
        try {
            return MapNodeData.loadForPlan(appContext, planPolicy);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private String runnerPolicySummary() {
        return policySummaryForRunner(appContext, planPolicy);
    }

    private String routeSummary(int routeIndex) {
        if (planPolicy == null) {
            return "routeCursor=unavailable";
        }
        return planPolicy.routeSummary(routeIndex);
    }

    private String routeNodePolicySummary() {
        if (planPolicy == null) {
            return "unavailable(plan policy missing)";
        }
        if (mapNodeData == null) {
            return "unavailable(map node data missing)";
        }
        return "live yellow-ship tracker with selected-route guard, map=" + mapNodeData.path
                + ", nodes=" + mapNodeData.size()
                + ", routable=" + mapNodeData.routable
                + ", current=" + (routeNodeTracker == null ? "unavailable" : routeNodeTracker.currentNode());
    }

    private RouteSync updateRouteCursorFromLiveNode(int currentRouteIndex) {
        if (planPolicy == null) {
            return new RouteSync(currentRouteIndex, "routeNodeScan=skip plan policy unavailable");
        }
        if (routeNodeTracker == null) {
            return new RouteSync(currentRouteIndex, "routeNodeScan=skip map node tracker unavailable");
        }
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return new RouteSync(currentRouteIndex, "routeNodeScan=unavailable missing MediaProjection frame");
        }
        Bitmap gameFrame = null;
        try {
            gameFrame = GameFrameNormalizer.toLandscapeGameFrame(liveFrame);
            RouteNodeTracker.RouteUpdate update = routeNodeTracker.updateFromFrame(gameFrame);
            if (update.acceptedPosition && !planPolicy.isRouteNodeAllowed(update.currentNode)) {
                return new RouteSync(
                        currentRouteIndex,
                        true,
                        update.detail
                                + ", routeCursorSync=blocked"
                                + ", trackerNode=" + update.currentNode
                                + ", reason=tracker node outside selected route " + planPolicy.selectedNodes
                );
            }
            int syncedIndex = planPolicy.routeIndexForNode(update.currentNode, currentRouteIndex);
            String syncLabel = syncedIndex == currentRouteIndex
                    ? "unchanged"
                    : "synced " + currentRouteIndex + "->" + syncedIndex;
            return new RouteSync(
                    syncedIndex,
                    false,
                    update.detail
                            + ", routeCursorSync=" + syncLabel
                            + ", trackerNode=" + update.currentNode
            );
        } finally {
            if (gameFrame != null) {
                gameFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private CombatPlanPolicy.PlanDecision planDecisionFor(
            NativeCombatPhase phase,
            int routeIndex,
            int pendingRuleFormation,
            EnemyScan enemyScan
    ) {
        if (planPolicy != null) {
            if (phase == NativeCombatPhase.SPOT_ENEMY_SUCCESS
                    && enemyScan != null
                    && enemyScan.hasReliableSpotContext()) {
                return planPolicy.decisionForSpotEnemyContext(
                        routeIndex,
                        enemyScan.composition != null && enemyScan.composition.reliable
                                ? enemyScan.composition.snapshot
                                : null,
                        enemyScan.formation != null && enemyScan.formation.reliable
                                ? enemyScan.formation.formation
                                : ""
                );
            }
            return planPolicy.decisionForPhase(phase, routeIndex, damageSnapshot, pendingRuleFormation);
        }
        return new CombatPlanPolicy.PlanDecision(
                routeIndex,
                "default",
                CombatPlanPolicy.NodeDecision.defaults(),
                phase,
                GuardedCombatStepper.actionIdForPhase(phase),
                "plan unavailable, using legacy phase default",
                false,
                false
        );
    }

    private EnemyScan updateEnemyScanForPhase(NativeCombatPhase phase) {
        if (phase != NativeCombatPhase.SPOT_ENEMY_SUCCESS) {
            return new EnemyScan(null, null, "enemyScan=skip phase=" + (phase == null ? "none" : phase.id));
        }
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return new EnemyScan(null, null, "enemyScan=spot unavailable: missing MediaProjection frame");
        }
        Bitmap gameFrame = null;
        try {
            gameFrame = GameFrameNormalizer.toLandscapeGameFrame(liveFrame);
            EnemyCompositionRecognizer.Detection detection = EnemyCompositionRecognizer.detectFight(gameFrame);
            EnemyFormationRecognizer.Detection formation = EnemyFormationRecognizer.detectFight(gameFrame);
            String action = detection.reliable || formation.reliable
                    ? "accepted for spot rules"
                    : "ignored, default spot_fight";
            return new EnemyScan(
                    detection,
                    formation,
                    "enemyScan=" + detection.detailLine()
                            + "\nformationScan=" + formation.detailLine()
                            + ", action=" + action
            );
        } finally {
            if (gameFrame != null) {
                gameFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private String updateDamageSnapshotForPhase(NativeCombatPhase phase) {
        if (phase != NativeCombatPhase.RESULT) {
            return "damageScan=skip phase=" + (phase == null ? "none" : phase.id)
                    + ", current=" + damageSnapshot.shortLabel();
        }
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return "damageScan=result unavailable: missing MediaProjection frame, current="
                    + damageSnapshot.shortLabel();
        }
        Bitmap gameFrame = null;
        try {
            gameFrame = GameFrameNormalizer.toLandscapeGameFrame(liveFrame);
            ShipDamageRecognizer.Detection detection = ShipDamageRecognizer.detectResult(
                    gameFrame,
                    damageSnapshot
            );
            if (detection.reliable) {
                damageSnapshot = detection.snapshot;
            }
            return "damageScan=" + detection.detailLine()
                    + ", accepted=" + detection.reliable
                    + ", current=" + damageSnapshot.shortLabel();
        } finally {
            if (gameFrame != null) {
                gameFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    static boolean stepCompleted(GuardedCombatStepper.Update update) {
        if (update == null) {
            return false;
        }
        switch (update.outcome) {
            case MATCHED:
                return true;
            case RUNNING:
            case BLOCKED:
            case TIMED_OUT:
            case CANCELLED:
            case FAILED:
            default:
                return false;
        }
    }

    private static String finalStatus(GuardedCombatStepper.StepOutcome outcome, String actionId) {
        switch (outcome) {
            case CANCELLED:
                return "Guarded runner: cancelled";
            case BLOCKED:
                return "Guarded runner: step blocked " + actionId;
            case TIMED_OUT:
                return "Guarded runner: wait timed out " + actionId;
            case MATCHED:
                return "Guarded runner: step complete " + actionId;
            case FAILED:
                return "Guarded runner: step failed " + actionId;
            case RUNNING:
            default:
                return "Guarded runner: step finished " + actionId;
        }
    }

    private static AutomationOutcome stepAutomationOutcome(GuardedCombatStepper.StepOutcome outcome) {
        switch (outcome) {
            case CANCELLED:
                return AutomationOutcome.CANCELLED;
            case FAILED:
                return AutomationOutcome.FAILED;
            case MATCHED:
                return AutomationOutcome.SUCCESS;
            case BLOCKED:
            case TIMED_OUT:
            case RUNNING:
            default:
                return AutomationOutcome.BLOCKED;
        }
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private final class PhaseScanDeadline implements TemplateMatcher.ScanControl {
        private final long startedAtMs = SystemClock.elapsedRealtime();

        @Override
        public boolean shouldStop() {
            return session.cancelled() || SystemClock.elapsedRealtime() - startedAtMs >= PHASE_SCAN_TIMEOUT_MS;
        }

        @Override
        public String stopReason() {
            return session.cancelled() ? "phase scan cancelled" : "phase scan timed out";
        }
    }

    static final class ProbeResult {
        final boolean ok;
        final String detail;

        ProbeResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }
    }

    private final class StepResult {
        private volatile GuardedCombatStepper.Update update;

        GuardedCombatStepper.Update await() {
            while (!session.cancelled()) {
                if (!session.checkpoint()) {
                    return null;
                }
                synchronized (this) {
                    if (update != null) {
                        return update;
                    }
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            return update;
        }

        synchronized void finish(GuardedCombatStepper.Update update) {
            this.update = update;
            notifyAll();
        }
    }

    private static final class RouteSync {
        final int routeIndex;
        final boolean blocked;
        final String detail;

        RouteSync(int routeIndex, String detail) {
            this(routeIndex, false, detail);
        }

        RouteSync(int routeIndex, boolean blocked, String detail) {
            this.routeIndex = routeIndex;
            this.blocked = blocked;
            this.detail = detail;
        }
    }

    private static final class EnemyScan {
        final EnemyCompositionRecognizer.Detection composition;
        final EnemyFormationRecognizer.Detection formation;
        final String detail;

        EnemyScan(
                EnemyCompositionRecognizer.Detection composition,
                EnemyFormationRecognizer.Detection formation,
                String detail
        ) {
            this.composition = composition;
            this.formation = formation;
            this.detail = detail;
        }

        boolean hasReliableSpotContext() {
            return (composition != null && composition.reliable)
                    || (formation != null && formation.reliable);
        }
    }
}
