package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

final class MinimumNormalBattleRunner {
    private static final long FOREGROUND_TIMEOUT_MS = 12000L;
    private static final long FRESH_FRAME_TIMEOUT_MS = 10000L;
    private static final long INITIAL_STATE_TIMEOUT_MS = 45000L;
    private static final long MAP_STATE_TIMEOUT_MS = 18000L;
    private static final long BATTLE_PREP_TIMEOUT_MS = 25000L;
    private static final long BATTLE_ADVANCE_PAGE_TIMEOUT_MS = 18000L;
    private static final long COMBAT_PHASE_TIMEOUT_MS = 65000L;
    private static final long COMBAT_BATCH_TIMEOUT_MS = 90000L;
    private static final int MAX_COMBAT_BATCHES = 2;

    private final Context appContext;
    private final Handler mainHandler;
    private final Callback callback;
    private final File baseDir;
    private final String runId;
    private final File logFile;
    private final MinimumGameStateRecognizer recognizer;
    private final MinimumNormalBattleNavigator navigator;
    private final NormalSortieRunConfig runConfig;
    private NormalSortieTarget sortieTarget;

    private Thread worker;
    private volatile boolean cancelled;
    private volatile GuardedCombatRunner activeCombatRunner;
    private CombatPlanPolicy selectedPolicy;
    private boolean running;

    MinimumNormalBattleRunner(Context context, Callback callback) {
        this(context, NormalSortieTarget.of(1, 1), callback);
    }

    MinimumNormalBattleRunner(Context context, NormalSortieTarget sortieTarget, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        this.sortieTarget = sortieTarget == null ? NormalSortieTarget.of(1, 1) : sortieTarget;
        this.runConfig = null;
        File external = appContext.getExternalFilesDir(null);
        this.baseDir = external == null ? appContext.getFilesDir() : external;
        this.runId = String.valueOf(System.currentTimeMillis());
        this.logFile = MinimumRunLogStore.logFile(baseDir, runId);
        this.recognizer = new MinimumGameStateRecognizer(appContext);
        this.navigator = new MinimumNormalBattleNavigator();
    }

    MinimumNormalBattleRunner(Context context, NormalSortieRunConfig runConfig, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        this.sortieTarget = NormalSortieTarget.of(1, 1);
        this.runConfig = runConfig;
        File external = appContext.getExternalFilesDir(null);
        this.baseDir = external == null ? appContext.getFilesDir() : external;
        this.runId = String.valueOf(System.currentTimeMillis());
        this.logFile = MinimumRunLogStore.logFile(baseDir, runId);
        this.recognizer = new MinimumGameStateRecognizer(appContext);
        this.navigator = new MinimumNormalBattleNavigator();
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        cancelled = false;
        running = true;
        post(Update.running("最小普通战：启动中", "runId=" + runId, logFile.getAbsolutePath(), ""));
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runFlow();
            }
        }, "AutoWSGR-minimum-normal-battle");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    synchronized void cancel() {
        cancelled = true;
        GuardedCombatRunner runner = activeCombatRunner;
        if (runner != null) {
            runner.cancel();
        }
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
    }

    synchronized boolean isRunning() {
        return running;
    }

    private void runFlow() {
        selectedPolicy = loadSelectedPolicy();
        if (selectedPolicy == null) {
            finishBlocked("selected-plan-load-failed, " + startupConfigOrTargetSummary());
            return;
        }
        progress("最小普通战：检查前置条件", "game="
                + TargetGameLauncher.PACKAGE_NAME + "/" + TargetGameLauncher.ACTIVITY_NAME
                + ", " + startupConfigOrTargetSummary());
        ScreenFrameStore.Snapshot beforeLaunch = ScreenFrameStore.snapshot();
        MinimumRunPreflight.Result preflight = MinimumRunPreflight.validate(
                beforeLaunch.hasFrame(),
                AutoWsgrAccessibilityService.isActive(),
                TargetGameLauncher.isInstalled(appContext)
        );
        if (!preflight.ready) {
            finishBlocked(preflight.reason);
            return;
        }

        TargetGameLauncher.LaunchResult launch = TargetGameLauncher.launch(appContext);
        progress(launch.started ? "最小普通战：已启动游戏" : "最小普通战：启动游戏失败", launch.detail);
        if (!launch.started) {
            finishBlocked(launch.detail);
            return;
        }
        if (!waitForTargetForeground()) {
            finishBlocked("target-foreground-timeout, latest="
                    + AutoWsgrAccessibilityService.latestForegroundPackage());
            return;
        }
        if (!waitForFreshFrameAfter(beforeLaunch.frameCount)) {
            finishBlocked("fresh-frame-timeout");
            return;
        }

        MinimumGameStateRecognizer.Result initial = waitForInitialState();
        initial = advancePastStartScreen(initial);
        if (cancelled) {
            finishCancelled();
            return;
        }
        if (!running) {
            return;
        }
        if (initial == null) {
            MinimumGameStateRecognizer.PageProbe prep = recognizer.battlePreparationProbe();
            if (prep.matched) {
                progress("最小普通战：已在出征准备", prep.detail);
                runFromBattlePreparation();
                return;
            }
            finishBlocked("initial-state-timeout, " + prep.detail);
            return;
        }

        MinimumGameStateClassifier.State state = initial.state.state;
        if (state == MinimumGameStateClassifier.State.START_SCREEN) {
            finishBlocked("start-screen-repeat-timeout, " + stateDetail(initial));
            return;
        }
        if (state == MinimumGameStateClassifier.State.BLOCKER) {
            finishBlocked("initial-blocker, " + stateDetail(initial));
            return;
        }
        if (state == MinimumGameStateClassifier.State.HOME) {
            runFromHome(initial);
            return;
        }
        if (state == MinimumGameStateClassifier.State.MAP_PAGE) {
            runFromMapPage();
            return;
        }
        if (state == MinimumGameStateClassifier.State.COMBAT_PHASE) {
            runCombatLoop();
            return;
        }

        MinimumGameStateRecognizer.PageProbe prep = recognizer.battlePreparationProbe();
        if (prep.matched) {
            progress("最小普通战：已在出征准备", prep.detail);
            runFromBattlePreparation();
            return;
        }
        finishBlocked("initial-state-unrecognized, " + stateDetail(initial) + ", " + prep.detail);
    }

    private MinimumGameStateRecognizer.Result advancePastStartScreen(MinimumGameStateRecognizer.Result initial) {
        int clicks = 0;
        while (!cancelled
                && initial != null
                && initial.state.state == MinimumGameStateClassifier.State.START_SCREEN) {
            if (clicks >= 2) {
                return initial;
            }
            progress("最小普通战：检测到启动页", stateDetail(initial));
            if (!navigator.tapStartScreenEnter(cancelToken(), navigatorLogger())) {
                finishBlockedOrCancelled("tap-start-screen-enter-failed");
                return null;
            }
            clicks++;
            progress("最小普通战：已点击进入游戏", "source=autowsgr/ops/startup.py StartScreenPage.click_enter");
            initial = waitForInitialState();
        }
        return initial;
    }

    private void runFromHome(MinimumGameStateRecognizer.Result home) {
        if (!navigator.tapHomeMap(home, cancelToken(), navigatorLogger())) {
            finishBlockedOrCancelled("tap-home-map-failed");
            return;
        }
        MinimumGameStateRecognizer.Result map = waitForState(
                MinimumGameStateClassifier.State.MAP_PAGE,
                MAP_STATE_TIMEOUT_MS,
                "等待地图页"
        );
        if (isBlockedOrCancelled(map, "home-to-map-timeout")) {
            return;
        }
        runFromMapPage();
    }

    private void runFromMapPage() {
        if (!navigator.navigateToTarget(sortieTarget, cancelToken(), navigatorLogger())) {
            finishBlockedOrCancelled("navigate-map-target-" + sortieTarget.label() + "-failed");
            return;
        }
        MinimumGameStateRecognizer.Result stillMap = waitForState(
                MinimumGameStateClassifier.State.MAP_PAGE,
                MAP_STATE_TIMEOUT_MS,
                "等待出征地图页 " + sortieTarget.label()
        );
        if (isBlockedOrCancelled(stillMap, "map-target-page-timeout")) {
            return;
        }
        if (stillMap != null && stillMap.state.state == MinimumGameStateClassifier.State.BLOCKER) {
            finishBlocked("map-blocker, " + stateDetail(stillMap));
            return;
        }
        if (!verifyMapTargetBeforeEnter(stillMap)) {
            return;
        }
        if (!navigator.tapEnterSortie(sortieTarget, cancelToken(), navigatorLogger())) {
            finishBlockedOrCancelled("tap-enter-sortie-failed");
            return;
        }
        if (!waitForBattlePreparation(8000L)) {
            progress("最小普通战：尝试当前地图起点", "enter-sortie did not reach battle preparation");
            if (!navigator.tapMapStartNode(cancelToken(), navigatorLogger())) {
                finishBlockedOrCancelled("tap-map-start-node-failed");
                return;
            }
            if (!waitForBattlePreparation(BATTLE_PREP_TIMEOUT_MS)) {
                finishBlockedOrCancelled("battle-preparation-timeout");
                return;
            }
        }
        runFromBattlePreparation();
    }

    private void runFromBattlePreparation() {
        for (MinimumBattleAdvancePolicy.Step step : MinimumBattleAdvancePolicy.steps()) {
            if (!waitForBattleAdvancePage(step.requiredPage(), BATTLE_ADVANCE_PAGE_TIMEOUT_MS)) {
                finishBlockedOrCancelled("battle-advance-page-timeout, page=" + step.requiredPage().name());
                return;
            }
            boolean tapped;
            if (step == MinimumBattleAdvancePolicy.Step.START_SORTIE) {
                tapped = navigator.tapStartBattle(cancelToken(), navigatorLogger());
            } else if (step == MinimumBattleAdvancePolicy.Step.ENEMY_START_BATTLE) {
                tapped = navigator.tapEnemyStartBattle(cancelToken(), navigatorLogger());
            } else {
                tapped = navigator.tapDefaultFormation(cancelToken(), navigatorLogger());
            }
            if (!tapped) {
                finishBlockedOrCancelled("tap-" + step.name().toLowerCase().replace('_', '-') + "-failed");
                return;
            }
        }
        waitForCombatOrNightPromptCompletion();
    }

    private void runCombatLoop() {
        for (int batch = 1; batch <= MAX_COMBAT_BATCHES && !cancelled; batch++) {
            progress("最小普通战：执行战斗", "guardedBatch=" + batch + "/" + MAX_COMBAT_BATCHES);
            GuardedCombatRunner.Update finalUpdate = runGuardedBatch(batch);
            if (cancelled) {
                finishCancelled();
                return;
            }
            if (finalUpdate == null) {
                finishBlocked("combat-batch-timeout");
                return;
            }
            MinimumRunOutcomePolicy.Result outcome =
                    MinimumRunOutcomePolicy.classifyGuardedFinal(finalUpdate.status, finalUpdate.detail);
            progress("最小普通战：战斗批次结束", "batch=" + batch
                    + ", action=" + outcome.action
                    + ", reason=" + outcome.reason
                    + ", status=" + finalUpdate.status);
            if (outcome.action == MinimumRunOutcomePolicy.Action.SUCCESS) {
                finishSuccess(outcome.reason + ", " + finalUpdate.status);
                return;
            }
            if (outcome.action == MinimumRunOutcomePolicy.Action.CANCELLED) {
                finishCancelled();
                return;
            }
            if (outcome.action == MinimumRunOutcomePolicy.Action.BLOCKED) {
                finishBlocked(outcome.reason + ", " + finalUpdate.status);
                return;
            }
            SystemClock.sleep(1000L);
        }
        if (cancelled) {
            finishCancelled();
            return;
        }
        finishBlocked("combat-max-batches");
    }

    private boolean verifyMapTargetBeforeEnter(MinimumGameStateRecognizer.Result stillMap) {
        MapNodeData mapData;
        try {
            mapData = MapNodeData.loadForPlan(appContext, selectedPolicy);
        } catch (IOException e) {
            finishBlocked("map-target-mapdata-load-failed, target=" + sortieTarget.label()
                    + ", reason=" + e.getMessage());
            return false;
        }
        MinimumMapTargetVerifier.Result result = MinimumMapTargetVerifier.verify(
                selectedPolicy,
                mapData,
                MinimumMapTargetVerifier.TitleSignal.unavailable("android map-title OCR backend unavailable"),
                stillMap != null && stillMap.state.state == MinimumGameStateClassifier.State.MAP_PAGE
        );
        progress("最小普通战：校验出征地图", result.detail);
        if (!result.canProceed) {
            finishBlocked("map-target-verification-" + result.reason + ", " + result.detail);
            return false;
        }
        return true;
    }

    private GuardedCombatRunner.Update runGuardedBatch(final int batch) {
        final GuardedResult guardedResult = new GuardedResult();
        GuardedCombatRunner runner = GuardedCombatRunner.boundedLoopExecuteGuarded(
                appContext,
                selectedPolicy,
                new GuardedCombatRunner.Callback() {
                    @Override
                    public void onUpdate(GuardedCombatRunner.Update update) {
                        appendLog("combat", "batch=" + batch
                                + ", status=" + update.status
                                + ", detail=" + update.detail);
                        post(Update.running("最小普通战：战斗中", update.status, logFile.getAbsolutePath(), ""));
                        if (!update.running) {
                            guardedResult.finish(update);
                        }
                    }
                }
        );
        activeCombatRunner = runner;
        if (!runner.start()) {
            activeCombatRunner = null;
            return null;
        }
        GuardedCombatRunner.Update update = guardedResult.await(COMBAT_BATCH_TIMEOUT_MS);
        if (activeCombatRunner == runner) {
            activeCombatRunner = null;
        }
        if (update == null && runner.isRunning()) {
            runner.cancel();
        }
        return update;
    }

    private MinimumGameStateRecognizer.Result waitForInitialState() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < INITIAL_STATE_TIMEOUT_MS) {
            MinimumGameStateRecognizer.Result result = recognizer.recognize();
            MinimumGameStateClassifier.State state = result.state.state;
            if (polls == 0 || polls % 6 == 0 || isActionable(state)) {
                progress("最小普通战：识别游戏画面", stateDetail(result));
            }
            if (isActionable(state)) {
                return result;
            }
            MinimumGameStateRecognizer.PageProbe prep = recognizer.battlePreparationProbe();
            if (prep.matched) {
                progress("最小普通战：识别出征准备", prep.detail);
                return null;
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return null;
    }

    private MinimumGameStateRecognizer.Result waitForState(
            MinimumGameStateClassifier.State wanted,
            long timeoutMs,
            String label
    ) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < timeoutMs) {
            MinimumGameStateRecognizer.Result result = recognizer.recognize();
            MinimumGameStateClassifier.State state = result.state.state;
            if (state == wanted || state == MinimumGameStateClassifier.State.BLOCKER) {
                progress("最小普通战：" + label, stateDetail(result));
                return result;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("最小普通战：" + label, stateDetail(result));
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return null;
    }

    private boolean waitForBattlePreparation(long timeoutMs) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < timeoutMs) {
            MinimumGameStateRecognizer.PageProbe probe = recognizer.battlePreparationProbe();
            if (probe.matched) {
                progress("最小普通战：出征准备已识别", probe.detail);
                return true;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("最小普通战：等待出征准备", probe.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return false;
    }

    private void waitForCombatOrNightPromptCompletion() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        boolean handledNightPrompt = false;
        while (!cancelled && SystemClock.elapsedRealtime() - started < COMBAT_PHASE_TIMEOUT_MS) {
            MinimumGameStateRecognizer.PageProbe nightPrompt =
                    recognizer.battleAdvancePageProbe(MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT);
            if (nightPrompt.matched) {
                if (handledNightPrompt) {
                    progress("最小普通战：等待夜战确认消失", nightPrompt.detail);
                    SystemClock.sleep(700L);
                    continue;
                }
                String actionId = defaultNightPromptAction();
                if (!MinimumBattleAdvancePolicy.isNightPromptAction(actionId)) {
                    finishBlocked("night-prompt-action-unavailable, action=" + actionId
                            + ", " + nightPrompt.detail);
                    return;
                }
                boolean pursue = MinimumBattleAdvancePolicy.shouldPursueNight(actionId);
                progress("最小普通战：夜战确认（舰队补给提示）",
                        "phase=" + MinimumBattleAdvancePolicy.promptPhaseId(
                                MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT
                        )
                                + ", action=" + actionId
                                + ", choice=" + (pursue ? "追击" : "放弃")
                                + ", " + nightPrompt.detail);
                if (!navigator.tapNightPrompt(pursue, cancelToken(), navigatorLogger())) {
                    finishBlockedOrCancelled("tap-night-prompt-" + actionId + "-failed");
                    return;
                }
                handledNightPrompt = true;
                started = SystemClock.elapsedRealtime();
                polls = 0;
                continue;
            }

            MinimumGameStateRecognizer.Result result = recognizer.recognize();
            MinimumGameStateClassifier.State state = result.state.state;
            if (state == MinimumGameStateClassifier.State.COMBAT_PHASE) {
                progress("最小普通战：等待进入战斗", stateDetail(result));
                runCombatLoop();
                return;
            }
            if (state == MinimumGameStateClassifier.State.BLOCKER) {
                finishBlocked("blocker-detected, " + stateDetail(result));
                return;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("最小普通战：等待进入战斗", stateDetail(result)
                        + "\n" + nightPrompt.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        if (cancelled) {
            finishCancelled();
            return;
        }
        finishBlocked("combat-phase-timeout");
    }

    private String defaultNightPromptAction() {
        try {
            CombatPlanPolicy policy = selectedPolicy == null ? CombatPlanPolicy.loadDefault(appContext) : selectedPolicy;
            return policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId;
        } catch (IOException e) {
            return MinimumBattleAdvancePolicy.nightPromptAction(false);
        }
    }

    static String startupTargetSummary(NormalSortieTarget target, CombatPlanPolicy policy) {
        NormalSortieTarget safeTarget = target == null ? NormalSortieTarget.of(1, 1) : target;
        if (policy == null) {
            return safeTarget.runnerSummary() + ", plan=unavailable, fleet=1";
        }
        return safeTarget.runnerSummary()
                + ", plan=" + policy.path
                + ", fleet=" + policy.fleetId;
    }

    static String startupConfigSummary(NormalSortieRunConfig config, CombatPlanPolicy policy) {
        String source = config == null ? "unavailable" : config.sourcePath;
        if (policy == null) {
            return "config=" + source + ", target=unavailable, plan=unavailable, fleet=1";
        }
        String targetLabel = "unavailable";
        try {
            targetLabel = policy.normalTarget().label();
        } catch (IOException ignored) {
        }
        return "config=" + source
                + ", target=" + targetLabel
                + ", map=" + MapNodeData.mapPathForPlan(policy)
                + ", plan=" + policy.path
                + ", fleet=" + policy.fleetId;
    }

    private CombatPlanPolicy loadSelectedPolicy() {
        try {
            CombatPlanPolicy policy;
            if (runConfig != null) {
                policy = CombatPlanPolicy.loadForRunConfig(appContext, runConfig);
            } else {
                policy = CombatPlanPolicy.loadForNormalTarget(appContext, sortieTarget);
            }
            sortieTarget = policy.normalTarget();
            return policy;
        } catch (IOException e) {
            appendLog("blocked", "selected-plan-load-failed, "
                    + startupConfigOrTargetSummary() + ", reason=" + e.getMessage());
            return null;
        }
    }

    private String startupConfigOrTargetSummary() {
        if (runConfig != null) {
            return startupConfigSummary(runConfig, selectedPolicy);
        }
        return startupTargetSummary(sortieTarget, selectedPolicy);
    }

    private boolean waitForBattleAdvancePage(
            MinimumBattleAdvancePolicy.Page page,
            long timeoutMs
    ) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < timeoutMs) {
            MinimumGameStateRecognizer.PageProbe probe = recognizer.battleAdvancePageProbe(page);
            if (probe.matched) {
                progress("最小普通战：确认" + page.label(), probe.detail);
                return true;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("最小普通战：等待" + page.label(), probe.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return false;
    }

    private boolean waitForTargetForeground() {
        long started = SystemClock.elapsedRealtime();
        while (!cancelled && SystemClock.elapsedRealtime() - started < FOREGROUND_TIMEOUT_MS) {
            if (TargetGameLauncher.isTargetForeground()) {
                progress("最小普通战：游戏已在前台", "package="
                        + AutoWsgrAccessibilityService.latestForegroundPackage());
                return true;
            }
            SystemClock.sleep(300L);
        }
        return false;
    }

    private boolean waitForFreshFrameAfter(long frameCount) {
        long started = SystemClock.elapsedRealtime();
        while (!cancelled && SystemClock.elapsedRealtime() - started < FRESH_FRAME_TIMEOUT_MS) {
            ScreenFrameStore.Snapshot snapshot = ScreenFrameStore.snapshot();
            long ageMs = Math.max(0L, System.currentTimeMillis() - snapshot.lastFrameTimeMs);
            if (snapshot.hasFrame() && (snapshot.frameCount > frameCount || ageMs < 1200L)) {
                progress("最小普通战：画面帧已刷新", snapshot.toStatusLine());
                return true;
            }
            SystemClock.sleep(250L);
        }
        return false;
    }

    private boolean isBlockedOrCancelled(MinimumGameStateRecognizer.Result result, String timeoutReason) {
        if (cancelled) {
            finishCancelled();
            return true;
        }
        if (result == null) {
            finishBlocked(timeoutReason);
            return true;
        }
        if (result.state.state == MinimumGameStateClassifier.State.BLOCKER) {
            finishBlocked("blocker-detected, " + stateDetail(result));
            return true;
        }
        return false;
    }

    private void finishBlockedOrCancelled(String reason) {
        if (cancelled) {
            finishCancelled();
        } else {
            finishBlocked(reason);
        }
    }

    private boolean isActionable(MinimumGameStateClassifier.State state) {
        return state == MinimumGameStateClassifier.State.HOME
                || state == MinimumGameStateClassifier.State.START_SCREEN
                || state == MinimumGameStateClassifier.State.MAP_PAGE
                || state == MinimumGameStateClassifier.State.COMBAT_PHASE
                || state == MinimumGameStateClassifier.State.BLOCKER;
    }

    private MinimumNormalBattleNavigator.CancelToken cancelToken() {
        return new MinimumNormalBattleNavigator.CancelToken() {
            @Override
            public boolean cancelled() {
                return MinimumNormalBattleRunner.this.cancelled;
            }
        };
    }

    private MinimumNormalBattleNavigator.Logger navigatorLogger() {
        return new MinimumNormalBattleNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("最小普通战：导航点击", status + ", " + detail);
            }
        };
    }

    private String stateDetail(MinimumGameStateRecognizer.Result result) {
        if (result == null) {
            return "state=null";
        }
        return "state=" + result.state.state
                + ", reason=" + result.state.reason
                + ", " + result.state.detail
                + "\n" + result.navigationDetail
                + "\n" + result.combatDetail;
    }

    private void finishBlocked(String reason) {
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId);
        appendLog("blocked", reason + ", " + evidence.detail);
        finish(Update.finished(
                "最小普通战：已阻断",
                reason,
                logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""
        ));
    }

    private void finishCancelled() {
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId);
        appendLog("cancelled", evidence.detail);
        finish(Update.finished(
                "最小普通战：已停止",
                "cancelled",
                logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""
        ));
    }

    private void finishSuccess(String detail) {
        appendLog("success", detail);
        finish(Update.finished("最小普通战：完成", detail, logFile.getAbsolutePath(), ""));
    }

    private synchronized void finish(Update update) {
        running = false;
        activeCombatRunner = null;
        post(update);
    }

    private void progress(String status, String detail) {
        appendLog("running", status + ", " + detail);
        post(Update.running(status, detail, logFile.getAbsolutePath(), ""));
    }

    private void appendLog(String status, String detail) {
        File parent = logFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        FileWriter writer = null;
        try {
            writer = new FileWriter(logFile, true);
            writer.write(MinimumRunLogStore.line(runId, status, detail));
            writer.write('\n');
        } catch (IOException ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void post(final Update update) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onUpdate(update);
            }
        });
    }

    interface Callback {
        void onUpdate(Update update);
    }

    static final class Update {
        final boolean running;
        final String status;
        final String detail;
        final String logPath;
        final String screenshotPath;

        private Update(boolean running, String status, String detail, String logPath, String screenshotPath) {
            this.running = running;
            this.status = status;
            this.detail = detail;
            this.logPath = logPath;
            this.screenshotPath = screenshotPath;
        }

        static Update running(String status, String detail, String logPath, String screenshotPath) {
            return new Update(true, status, detail, logPath, screenshotPath);
        }

        static Update finished(String status, String detail, String logPath, String screenshotPath) {
            return new Update(false, status, detail, logPath, screenshotPath);
        }
    }

    private final class GuardedResult {
        private GuardedCombatRunner.Update update;

        synchronized GuardedCombatRunner.Update await(long timeoutMs) {
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (update == null && !cancelled) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) {
                    return null;
                }
                try {
                    wait(Math.min(remaining, 1000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return update;
        }

        synchronized void finish(GuardedCombatRunner.Update update) {
            this.update = update;
            notifyAll();
        }
    }
}
