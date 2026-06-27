package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 战役（每日 Campaign）执行器。
 *
 * <p>对齐原版 {@code autowsgr/ops/campaign.py CampaignRunner}：导航到战役面板→识别/切换难度→
 * 选择战役→出征准备→出征→战斗→循环 N 次。战斗本身复用 {@link MinimumNormalBattleNavigator}
 * 的出征推进、{@link MinimumBattleAdvancePolicy} 与 {@link GuardedCombatRunner}，
 * 阵型/夜战来自合成的单节点战役计划（{@link CampaignBattleConfig#toPlanText()}）。
 */
final class CampaignBattleRunner {
    static final String LOG_PREFIX = "campaign-battle";

    private static final long FOREGROUND_TIMEOUT_MS = 12000L;
    private static final long FRESH_FRAME_TIMEOUT_MS = 10000L;
    private static final long INITIAL_STATE_TIMEOUT_MS = 45000L;
    private static final long MAP_STATE_TIMEOUT_MS = 18000L;
    private static final long BATTLE_PREP_TIMEOUT_MS = 25000L;
    private static final long BATTLE_ADVANCE_PAGE_TIMEOUT_MS = 18000L;
    private static final long COMBAT_PHASE_TIMEOUT_MS = 65000L;
    private static final long COMBAT_BATCH_TIMEOUT_MS = 90000L;
    private static final int MAX_COMBAT_BATCHES = 2;
    private static final long DIFFICULTY_SWITCH_TIMEOUT_MS = 6000L;
    // 出征后等待敌方预览的短窗口；超时且仍在出征准备页 = 战役次数用尽（仿原版 wait_leave_page）。
    private static final long EXHAUSTION_WINDOW_MS = 6000L;

    private final Context appContext;
    private final CampaignBattleConfig config;
    private final Handler mainHandler;
    private final Callback callback;
    private final File baseDir;
    private final String runId;
    private final File logFile;
    private final MinimumGameStateRecognizer recognizer;
    private final MinimumNormalBattleNavigator battleNavigator;
    private final CampaignBattleNavigator campaignNavigator;

    private Thread worker;
    private volatile boolean cancelled;
    private volatile GuardedCombatRunner activeCombatRunner;
    private CombatPlanPolicy policy;
    private boolean running;

    CampaignBattleRunner(Context context, CampaignBattleConfig config, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? CampaignBattleConfig.parseText("") : config;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        File external = appContext.getExternalFilesDir(null);
        this.baseDir = external == null ? appContext.getFilesDir() : external;
        this.runId = String.valueOf(System.currentTimeMillis());
        this.logFile = MinimumRunLogStore.logFile(baseDir, LOG_PREFIX, runId);
        this.recognizer = new MinimumGameStateRecognizer(appContext);
        this.battleNavigator = new MinimumNormalBattleNavigator();
        this.campaignNavigator = new CampaignBattleNavigator();
    }

    static String startupSummary(CampaignBattleConfig config) {
        CampaignBattleConfig safe = config == null ? CampaignBattleConfig.parseText("") : config;
        return safe.summary();
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        cancelled = false;
        running = true;
        post(Update.running("战役：启动中", "runId=" + runId + ", " + startupSummary(config),
                logFile.getAbsolutePath(), ""));
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runFlow();
            }
        }, "AutoWSGR-campaign-battle");
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
        policy = loadCampaignPolicy();
        if (policy == null) {
            finishBlocked("campaign-plan-build-failed, " + startupSummary(config));
            return;
        }
        progress("战役：检查前置条件", "game="
                + TargetGameLauncher.PACKAGE_NAME + "/" + TargetGameLauncher.ACTIVITY_NAME
                + ", " + startupSummary(config));
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
        progress(launch.started ? "战役：已启动游戏" : "战役：启动游戏失败", launch.detail);
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
            finishBlocked("initial-state-timeout");
            return;
        }

        MinimumGameStateClassifier.State state = initial.state.state;
        if (state == MinimumGameStateClassifier.State.HOME) {
            runFromHome(initial);
            return;
        }
        if (state == MinimumGameStateClassifier.State.MAP_PAGE) {
            runFromMapPage();
            return;
        }
        if (state == MinimumGameStateClassifier.State.START_SCREEN) {
            finishBlocked("start-screen-repeat-timeout, " + stateDetail(initial));
            return;
        }
        if (state == MinimumGameStateClassifier.State.BLOCKER) {
            finishBlocked("initial-blocker, " + stateDetail(initial));
            return;
        }
        if (state == MinimumGameStateClassifier.State.COMBAT_PHASE) {
            finishBlocked("combat-phase-detected, " + stateDetail(initial));
            return;
        }
        finishBlocked("initial-state-unrecognized, " + stateDetail(initial));
    }

    private MinimumGameStateRecognizer.Result advancePastStartScreen(MinimumGameStateRecognizer.Result initial) {
        int clicks = 0;
        while (!cancelled
                && initial != null
                && initial.state.state == MinimumGameStateClassifier.State.START_SCREEN) {
            if (clicks >= 2) {
                return initial;
            }
            progress("战役：检测到启动页", stateDetail(initial));
            if (!battleNavigator.tapStartScreenEnter(cancelToken(), navigatorLogger())) {
                finishBlockedOrCancelled("tap-start-screen-enter-failed");
                return null;
            }
            clicks++;
            initial = waitForInitialState();
        }
        return initial;
    }

    private void runFromHome(MinimumGameStateRecognizer.Result home) {
        if (!battleNavigator.tapHomeMap(home, cancelToken(), navigatorLogger())) {
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
        CampaignCatalog.Target target = config.target();
        int completed = 0;
        for (int battle = 1; battle <= config.times && !cancelled; battle++) {
            progress("战役：进入第 " + battle + "/" + config.times + " 场", "target=" + target.label());
            // 每场都重新进入（仿原版 _enter_battle：回地图 → 切战役面板 → 对难度 → 选战役）。
            if (battle > 1) {
                MinimumGameStateRecognizer.Result back = waitForState(
                        MinimumGameStateClassifier.State.MAP_PAGE,
                        MAP_STATE_TIMEOUT_MS,
                        "等待返回战役页"
                );
                if (back == null) {
                    if (cancelled) {
                        finishCancelled();
                        return;
                    }
                    finishSuccess("campaign-return-timeout, completed=" + completed + "/" + config.times);
                    return;
                }
                if (back.state.state == MinimumGameStateClassifier.State.BLOCKER) {
                    finishBlocked("blocker-detected, " + stateDetail(back));
                    return;
                }
            }
            if (!campaignNavigator.tapBattlePanel(campaignCancelToken(), campaignLogger())) {
                finishBlockedOrCancelled("tap-battle-panel-failed");
                return;
            }
            if (!ensureDifficulty(target.difficulty)) {
                return;
            }
            if (!campaignNavigator.tapCampaign(target.mapIndex, campaignCancelToken(), campaignLogger())) {
                finishBlockedOrCancelled("tap-campaign-" + target.mapIndex + "-failed");
                return;
            }
            if (!waitForBattlePreparation(BATTLE_PREP_TIMEOUT_MS)) {
                if (cancelled) {
                    finishCancelled();
                    return;
                }
                if (completed > 0) {
                    finishSuccess("campaign-times-exhausted, completed=" + completed + "/" + config.times);
                } else {
                    finishBlocked("battle-preparation-timeout");
                }
                return;
            }
            if (!applyRepairIfNeeded()) {
                return;
            }

            BattleResult result = runSingleBattleFromPreparation(battle);
            if ("cancelled".equals(result.kind)) {
                finishCancelled();
                return;
            }
            if ("exhausted".equals(result.kind)) {
                finishSuccess("campaign-times-exhausted, completed=" + completed + "/" + config.times);
                return;
            }
            if ("blocked".equals(result.kind)) {
                finishBlocked(result.detail + ", completed=" + completed + "/" + config.times);
                return;
            }
            completed++;
            progress("战役：第 " + battle + " 场完成", result.detail + ", completed=" + completed + "/" + config.times);
            SystemClock.sleep(1500L);
        }
        if (cancelled) {
            finishCancelled();
            return;
        }
        finishSuccess("completed=" + completed + "/" + config.times);
    }

    private boolean ensureDifficulty(String wanted) {
        String current = recognizeDifficultyWithRetry();
        progress("战役：识别难度", "current=" + current + ", wanted=" + wanted);
        if (current == null) {
            if (cancelled) {
                finishCancelled();
            } else {
                finishBlocked("difficulty-unrecognized");
            }
            return false;
        }
        if (wanted.equals(current)) {
            return true;
        }
        // 仿原版 enter_campaign：只点一次难度切换，然后轮询识别直到翻转。
        if (!campaignNavigator.tapDifficultyToggle(campaignCancelToken(), campaignLogger())) {
            finishBlockedOrCancelled("tap-difficulty-toggle-failed");
            return false;
        }
        long started = SystemClock.elapsedRealtime();
        while (!cancelled && SystemClock.elapsedRealtime() - started < DIFFICULTY_SWITCH_TIMEOUT_MS) {
            String now = recognizeDifficultyWithRetry();
            if (wanted.equals(now)) {
                progress("战役：难度已切换", "current=" + now);
                return true;
            }
            SystemClock.sleep(250L);
        }
        if (cancelled) {
            finishCancelled();
        } else {
            finishBlocked("difficulty-switch-failed, wanted=" + wanted);
        }
        return false;
    }

    private String recognizeDifficultyWithRetry() {
        for (int i = 0; i < 10 && !cancelled; i++) {
            String d = recognizeDifficulty();
            if (d != null) {
                return d;
            }
            SystemClock.sleep(250L);
        }
        return null;
    }

    /** 战前修理（仿原版 apply_repair）：按 repair_mode 阈值检测损伤并逐槽快修。返回是否可继续。 */
    private boolean applyRepairIfNeeded() {
        int threshold = config.repairThreshold();
        if (threshold < 0) {
            progress("战役：跳过修理", "repair_mode=" + config.repairMode);
            return true;
        }
        int[] states = detectPreparationDamage();
        if (states == null) {
            progress("战役：修理跳过", "reason=damage-unreliable-or-no-frame, repair_mode=" + config.repairMode);
            return true;
        }
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < states.length; i++) {
            if (states[i] >= threshold && states[i] <= ShipDamagePolicy.SEVERE) {
                if (targets.length() > 0) {
                    targets.append(',');
                }
                targets.append(i);
            }
        }
        if (targets.length() == 0) {
            progress("战役：无需修理", "repair_mode=" + config.repairMode);
            return true;
        }
        progress("战役：开始修理", "repair_mode=" + config.repairMode + ", slots=" + targets);
        if (!campaignNavigator.tapQuickRepairPanel(campaignCancelToken(), campaignLogger())) {
            if (cancelled) {
                finishCancelled();
                return false;
            }
            // 修理切换失败不阻断战斗，记录后继续。
            progress("战役：修理面板切换失败", "继续出征");
            return true;
        }
        for (int i = 0; i < states.length && !cancelled; i++) {
            if (states[i] >= threshold && states[i] <= ShipDamagePolicy.SEVERE) {
                campaignNavigator.tapRepairSlot(i, campaignCancelToken(), campaignLogger());
            }
        }
        if (cancelled) {
            finishCancelled();
            return false;
        }
        return true;
    }

    private int[] detectPreparationDamage() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return null;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            ShipDamageRecognizer.Detection detection = ShipDamageRecognizer.detectPreparation(frame);
            if (!detection.reliable) {
                return null;
            }
            int[] states = new int[6];
            for (int i = 0; i < 6; i++) {
                states[i] = detection.snapshot.stateAt(i);
            }
            return states;
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private BattleResult runSingleBattleFromPreparation(int battle) {
        // 出征准备 → 点出征
        if (!waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page.BATTLE_PREPARATION, BATTLE_ADVANCE_PAGE_TIMEOUT_MS)) {
            return cancelled ? BattleResult.cancelled() : BattleResult.blocked("battle-prep-page-timeout");
        }
        if (!battleNavigator.tapStartBattle(cancelToken(), navigatorLogger())) {
            return cancelled ? BattleResult.cancelled() : BattleResult.blocked("tap-start-sortie-failed");
        }
        // 出征后短窗口内未进入敌方预览 → 战役次数用尽（仿原版 wait_leave_page 失败）。
        if (!waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW, EXHAUSTION_WINDOW_MS)) {
            if (cancelled) {
                return BattleResult.cancelled();
            }
            if (recognizer.battlePreparationProbe().matched) {
                progress("战役：次数用尽", "出征后未进入战斗，仍在出征准备页");
                return BattleResult.exhausted();
            }
            return BattleResult.blocked("enemy-preview-timeout");
        }
        // 敌方预览 → 开始战斗
        if (!battleNavigator.tapEnemyStartBattle(cancelToken(), navigatorLogger())) {
            return cancelled ? BattleResult.cancelled() : BattleResult.blocked("tap-enemy-start-battle-failed");
        }
        // 阵型选择 → 默认阵型
        if (!waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION, BATTLE_ADVANCE_PAGE_TIMEOUT_MS)) {
            return cancelled ? BattleResult.cancelled() : BattleResult.blocked("formation-page-timeout");
        }
        if (!battleNavigator.tapDefaultFormation(cancelToken(), navigatorLogger())) {
            return cancelled ? BattleResult.cancelled() : BattleResult.blocked("tap-default-formation-failed");
        }
        return waitForCombatOrNightPromptCompletion(battle);
    }

    private BattleResult waitForCombatOrNightPromptCompletion(int battle) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        boolean handledNightPrompt = false;
        while (!cancelled && SystemClock.elapsedRealtime() - started < COMBAT_PHASE_TIMEOUT_MS) {
            MinimumGameStateRecognizer.PageProbe nightPrompt =
                    recognizer.battleAdvancePageProbe(MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT);
            if (nightPrompt.matched) {
                if (handledNightPrompt) {
                    SystemClock.sleep(700L);
                    continue;
                }
                String actionId = defaultNightPromptAction();
                if (!MinimumBattleAdvancePolicy.isNightPromptAction(actionId)) {
                    return BattleResult.blocked("night-prompt-action-unavailable, action=" + actionId);
                }
                boolean pursue = MinimumBattleAdvancePolicy.shouldPursueNight(actionId);
                progress("战役：夜战确认", "action=" + actionId + ", choice=" + (pursue ? "追击" : "放弃"));
                if (!battleNavigator.tapNightPrompt(pursue, cancelToken(), navigatorLogger())) {
                    return cancelled ? BattleResult.cancelled()
                            : BattleResult.blocked("tap-night-prompt-" + actionId + "-failed");
                }
                handledNightPrompt = true;
                started = SystemClock.elapsedRealtime();
                polls = 0;
                continue;
            }

            MinimumGameStateRecognizer.Result result = recognizer.recognize();
            MinimumGameStateClassifier.State state = result.state.state;
            if (state == MinimumGameStateClassifier.State.COMBAT_PHASE) {
                progress("战役：进入战斗", stateDetail(result));
                return runCombatLoop(battle);
            }
            if (state == MinimumGameStateClassifier.State.BLOCKER) {
                return BattleResult.blocked("blocker-detected, " + stateDetail(result));
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("战役：等待进入战斗", stateDetail(result) + "\n" + nightPrompt.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        if (cancelled) {
            return BattleResult.cancelled();
        }
        return BattleResult.blocked("combat-phase-timeout");
    }

    private BattleResult runCombatLoop(int battle) {
        for (int batch = 1; batch <= MAX_COMBAT_BATCHES && !cancelled; batch++) {
            progress("战役：执行战斗", "battle=" + battle + ", guardedBatch=" + batch + "/" + MAX_COMBAT_BATCHES);
            GuardedCombatRunner.Update finalUpdate = runGuardedBatch(battle, batch);
            if (cancelled) {
                return BattleResult.cancelled();
            }
            if (finalUpdate == null) {
                return BattleResult.blocked("combat-batch-timeout");
            }
            MinimumRunOutcomePolicy.Result outcome =
                    MinimumRunOutcomePolicy.classifyGuardedFinal(finalUpdate.status, finalUpdate.detail);
            progress("战役：战斗批次结束", "battle=" + battle + ", batch=" + batch
                    + ", action=" + outcome.action + ", reason=" + outcome.reason);
            if (outcome.action == MinimumRunOutcomePolicy.Action.SUCCESS) {
                return BattleResult.success(outcome.reason + ", " + finalUpdate.status);
            }
            if (outcome.action == MinimumRunOutcomePolicy.Action.CANCELLED) {
                return BattleResult.cancelled();
            }
            if (outcome.action == MinimumRunOutcomePolicy.Action.BLOCKED) {
                return BattleResult.blocked(outcome.reason + ", " + finalUpdate.status);
            }
            SystemClock.sleep(1000L);
        }
        if (cancelled) {
            return BattleResult.cancelled();
        }
        // 战役为单场战斗，达到批次上界仍未识别为成功，视为已打完一场（收尾相位与常规战不同）。
        return BattleResult.success("combat-max-batches-treated-as-complete");
    }

    private GuardedCombatRunner.Update runGuardedBatch(final int battle, final int batch) {
        final GuardedResult guardedResult = new GuardedResult();
        GuardedCombatRunner runner = GuardedCombatRunner.boundedLoopExecuteGuarded(
                appContext,
                policy,
                new GuardedCombatRunner.Callback() {
                    @Override
                    public void onUpdate(GuardedCombatRunner.Update update) {
                        appendLog("combat", "battle=" + battle + ", batch=" + batch
                                + ", status=" + update.status + ", detail=" + update.detail);
                        post(Update.running("战役：战斗中", update.status, logFile.getAbsolutePath(), ""));
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

    private String defaultNightPromptAction() {
        return policy == null
                ? MinimumBattleAdvancePolicy.nightPromptAction(config.night)
                : policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId;
    }

    private String recognizeDifficulty() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return null;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return CampaignSignatures.recognizeDifficulty(new CampaignSignatures.Sampler() {
                @Override
                public int sample(float normalizedX, float normalizedY) {
                    int x = Math.min(frame.getWidth() - 1, Math.max(0, Math.round(normalizedX * (frame.getWidth() - 1))));
                    int y = Math.min(frame.getHeight() - 1, Math.max(0, Math.round(normalizedY * (frame.getHeight() - 1))));
                    return frame.getPixel(x, y);
                }
            });
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private CombatPlanPolicy loadCampaignPolicy() {
        try {
            return CombatPlanPolicy.parseText(config.planPath(), config.toPlanText());
        } catch (IOException e) {
            appendLog("blocked", "campaign-plan-build-failed, reason=" + e.getMessage());
            return null;
        }
    }

    private MinimumGameStateRecognizer.Result waitForInitialState() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < INITIAL_STATE_TIMEOUT_MS) {
            MinimumGameStateRecognizer.Result result = recognizer.recognize();
            MinimumGameStateClassifier.State state = result.state.state;
            if (polls == 0 || polls % 6 == 0 || isActionable(state)) {
                progress("战役：识别游戏画面", stateDetail(result));
            }
            if (isActionable(state)) {
                return result;
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
                progress("战役：" + label, stateDetail(result));
                return result;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("战役：" + label, stateDetail(result));
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
                progress("战役：出征准备已识别", probe.detail);
                return true;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("战役：等待出征准备", probe.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return false;
    }

    private boolean waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page page, long timeoutMs) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < timeoutMs) {
            MinimumGameStateRecognizer.PageProbe probe = recognizer.battleAdvancePageProbe(page);
            if (probe.matched) {
                progress("战役：确认" + page.label(), probe.detail);
                return true;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("战役：等待" + page.label(), probe.detail);
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
                progress("战役：游戏已在前台", "package="
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
                progress("战役：画面帧已刷新", snapshot.toStatusLine());
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
                return CampaignBattleRunner.this.cancelled;
            }
        };
    }

    private MinimumNormalBattleNavigator.Logger navigatorLogger() {
        return new MinimumNormalBattleNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("战役：导航点击", status + ", " + detail);
            }
        };
    }

    private CampaignBattleNavigator.CancelToken campaignCancelToken() {
        return new CampaignBattleNavigator.CancelToken() {
            @Override
            public boolean cancelled() {
                return CampaignBattleRunner.this.cancelled;
            }
        };
    }

    private CampaignBattleNavigator.Logger campaignLogger() {
        return new CampaignBattleNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("战役：战役面板点击", status + ", " + detail);
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
        if (!beginFinish()) {
            return;
        }
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId, LOG_PREFIX);
        appendLog("blocked", reason + ", " + evidence.detail);
        finish(Update.finished("战役：已阻断", reason, logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""));
    }

    private void finishCancelled() {
        if (!beginFinish()) {
            return;
        }
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId, LOG_PREFIX);
        appendLog("cancelled", evidence.detail);
        finish(Update.finished("战役：已停止", "cancelled", logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""));
    }

    private void finishSuccess(String detail) {
        if (!beginFinish()) {
            return;
        }
        appendLog("success", detail);
        finish(Update.finished("战役：完成", detail, logFile.getAbsolutePath(), ""));
    }

    private synchronized boolean beginFinish() {
        if (!running) {
            return false;
        }
        running = false;
        worker = null;
        activeCombatRunner = null;
        return true;
    }

    private void finish(Update update) {
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
        if (callback == null) {
            return;
        }
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

    private static final class BattleResult {
        final String kind;
        final String detail;

        private BattleResult(String kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }

        static BattleResult success(String detail) {
            return new BattleResult("success", detail);
        }

        static BattleResult blocked(String detail) {
            return new BattleResult("blocked", detail);
        }

        static BattleResult exhausted() {
            return new BattleResult("exhausted", "times-exhausted");
        }

        static BattleResult cancelled() {
            return new BattleResult("cancelled", "cancelled");
        }
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
