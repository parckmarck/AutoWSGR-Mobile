package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

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
    private final AutomationSession session;
    private final OneShotStartGate startGate = new OneShotStartGate();
    private final MinimumGameStateRecognizer recognizer;
    private final MinimumNormalBattleNavigator battleNavigator;
    private final CampaignBattleNavigator campaignNavigator;

    private Thread worker;
    private volatile GuardedCombatRunner activeCombatRunner;
    private CombatPlanPolicy policy;
    private boolean running;

    CampaignBattleRunner(Context context, CampaignBattleConfig config, AutomationCallback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? CampaignBattleConfig.parseText("") : config;
        String runId = String.valueOf(System.currentTimeMillis());
        this.session = new AutomationSession("campaign", runId, new AutomationControl(),
                AndroidAutomationSessionSink.create(appContext, "campaign-battle", runId), callback);
        this.recognizer = new MinimumGameStateRecognizer(appContext);
        this.battleNavigator = new MinimumNormalBattleNavigator();
        this.campaignNavigator = new CampaignBattleNavigator();
    }

    static String startupSummary(CampaignBattleConfig config) {
        CampaignBattleConfig safe = config == null ? CampaignBattleConfig.parseText("") : config;
        return safe.summary();
    }

    static AutomationOutcome terminalOutcomeForEntryWait(boolean explicitNoTimesEvidence) {
        return explicitNoTimesEvidence ? AutomationOutcome.EXHAUSTED : AutomationOutcome.BLOCKED;
    }

    synchronized boolean start() {
        OneShotStartGate.Decision decision = startGate.claim(session.cancelled());
        if (decision == OneShotStartGate.Decision.REJECTED) {
            return false;
        }
        if (decision == OneShotStartGate.Decision.PRE_CANCELLED) {
            finishCancelled();
            return true;
        }
        running = true;
        progress("战役：启动中", startupSummary(config));
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runFlow();
                } catch (Throwable failure) {
                    if (session.cancelled()) {
                        finishCancelled();
                    } else {
                        finishFailed("uncaught-exception, " + failure);
                    }
                }
            }
        }, "AutoWSGR-campaign-battle");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    synchronized void cancel() {
        session.cancel();
        GuardedCombatRunner runner = activeCombatRunner;
        if (runner != null) {
            runner.cancel();
        }
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
    }

    synchronized void pause() {
        session.pause();
        GuardedCombatRunner runner = activeCombatRunner;
        if (runner != null) {
            runner.pause();
        }
    }

    synchronized void resume() {
        session.resume();
        GuardedCombatRunner runner = activeCombatRunner;
        if (runner != null) {
            runner.resume();
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
        if (session.cancelled()) {
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
            finishBlocker(initial, "initial-blocker, " + stateDetail(initial));
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
        while (!session.cancelled()
                && initial != null
                && initial.state.state == MinimumGameStateClassifier.State.START_SCREEN) {
            if (!session.checkpoint()) {
                finishCancelled();
                return null;
            }
            if (clicks >= 2) {
                return initial;
            }
            progress("战役：检测到启动页", stateDetail(initial));
            if (!session.checkpoint()) {
                finishCancelled();
                return null;
            }
            if (!battleNavigator.tapStartScreenEnter(
                    cancelToken(), session::checkpoint, navigatorLogger())) {
                finishBlockedOrCancelled("tap-start-screen-enter-failed");
                return null;
            }
            clicks++;
            initial = waitForInitialState();
        }
        return initial;
    }

    private void runFromHome(MinimumGameStateRecognizer.Result home) {
        if (!session.checkpoint()) {
            finishCancelled();
            return;
        }
        if (!battleNavigator.tapHomeMap(
                home, cancelToken(), session::checkpoint, navigatorLogger())) {
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
        for (int battle = 1; battle <= config.times && !session.cancelled(); battle++) {
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            progress("战役：进入第 " + battle + "/" + config.times + " 场", "target=" + target.label());
            // 每场都重新进入（仿原版 _enter_battle：回地图 → 切战役面板 → 对难度 → 选战役）。
            if (battle > 1) {
                MinimumGameStateRecognizer.Result back = waitForState(
                        MinimumGameStateClassifier.State.MAP_PAGE,
                        MAP_STATE_TIMEOUT_MS,
                        "等待返回战役页"
                );
                if (back == null) {
                    if (session.cancelled()) {
                        finishCancelled();
                        return;
                    }
                    finishBlocked("campaign-return-timeout, completed=" + completed + "/" + config.times);
                    return;
                }
                if (back.state.state == MinimumGameStateClassifier.State.BLOCKER) {
                    finishBlocker(back, "blocker-detected, " + stateDetail(back));
                    return;
                }
            }
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!campaignNavigator.tapBattlePanel(campaignCancelToken(), campaignLogger())) {
                finishBlockedOrCancelled("tap-battle-panel-failed");
                return;
            }
            if (!ensureDifficulty(target.difficulty)) {
                return;
            }
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!campaignNavigator.tapCampaign(target.mapIndex, campaignCancelToken(), campaignLogger())) {
                finishBlockedOrCancelled("tap-campaign-" + target.mapIndex + "-failed");
                return;
            }
            if (!waitForBattlePreparation(BATTLE_PREP_TIMEOUT_MS)) {
                if (session.cancelled()) {
                    finishCancelled();
                    return;
                }
                finishBlocked("battle-preparation-timeout, completed="
                        + completed + "/" + config.times);
                return;
            }
            if (!applyRepairIfNeeded()) {
                return;
            }

            BattleResult result = runSingleBattleFromPreparation(battle);
            if (result.kind == BattleResult.Kind.CANCELLED) {
                finishCancelled();
                return;
            }
            if (result.kind == BattleResult.Kind.EXHAUSTED) {
                finishExhausted("campaign-times-exhausted, completed=" + completed + "/" + config.times);
                return;
            }
            if (result.kind == BattleResult.Kind.BLOCKED) {
                finishBlocked(result.detail + ", completed=" + completed + "/" + config.times);
                return;
            }
            if (result.kind == BattleResult.Kind.DOCK_FULL) {
                finishDockFull(result.detail + ", completed=" + completed + "/" + config.times);
                return;
            }
            if (result.kind == BattleResult.Kind.FAILED) {
                finishFailed(result.detail + ", completed=" + completed + "/" + config.times);
                return;
            }
            completed++;
            progress("战役：第 " + battle + " 场完成", result.detail + ", completed=" + completed + "/" + config.times);
            SystemClock.sleep(1500L);
        }
        if (session.cancelled()) {
            finishCancelled();
            return;
        }
        finishSuccess("completed=" + completed + "/" + config.times);
    }

    private boolean ensureDifficulty(String wanted) {
        String current = recognizeDifficultyWithRetry();
        progress("战役：识别难度", "current=" + current + ", wanted=" + wanted);
        if (current == null) {
            if (session.cancelled()) {
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
        if (!session.checkpoint()) {
            finishCancelled();
            return false;
        }
        if (!campaignNavigator.tapDifficultyToggle(campaignCancelToken(), campaignLogger())) {
            finishBlockedOrCancelled("tap-difficulty-toggle-failed");
            return false;
        }
        long started = SystemClock.elapsedRealtime();
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < DIFFICULTY_SWITCH_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                finishCancelled();
                return false;
            }
            String now = recognizeDifficultyWithRetry();
            if (wanted.equals(now)) {
                progress("战役：难度已切换", "current=" + now);
                return true;
            }
            SystemClock.sleep(250L);
        }
        if (session.cancelled()) {
            finishCancelled();
        } else {
            finishBlocked("difficulty-switch-failed, wanted=" + wanted);
        }
        return false;
    }

    private String recognizeDifficultyWithRetry() {
        for (int i = 0; i < 10 && !session.cancelled(); i++) {
            if (!session.checkpoint()) {
                return null;
            }
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
            if (!session.checkpoint()) {
                finishCancelled();
                return false;
            }
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
        if (!session.checkpoint()) {
            finishCancelled();
            return false;
        }
        if (!campaignNavigator.tapQuickRepairPanel(campaignCancelToken(), campaignLogger())) {
            if (session.cancelled()) {
                finishCancelled();
                return false;
            }
            // 修理切换失败不阻断战斗，记录后继续。
            progress("战役：修理面板切换失败", "继续出征");
            return true;
        }
        for (int i = 0; i < states.length && !session.cancelled(); i++) {
            if (!session.checkpoint()) {
                finishCancelled();
                return false;
            }
            if (states[i] >= threshold && states[i] <= ShipDamagePolicy.SEVERE) {
                campaignNavigator.tapRepairSlot(i, campaignCancelToken(), campaignLogger());
            }
        }
        if (session.cancelled()) {
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
                if (!session.checkpoint()) {
                    return null;
                }
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
            return session.cancelled() ? BattleResult.cancelled() : BattleResult.blocked("battle-prep-page-timeout");
        }
        if (!session.checkpoint()) {
            return BattleResult.cancelled();
        }
        if (!battleNavigator.tapStartBattle(cancelToken(), session::checkpoint, navigatorLogger())) {
            return session.cancelled() ? BattleResult.cancelled() : BattleResult.blocked("tap-start-sortie-failed");
        }
        // 出征后短窗口内未进入敌方预览 → 战役次数用尽（仿原版 wait_leave_page 失败）。
        if (!waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW, EXHAUSTION_WINDOW_MS)) {
            if (session.cancelled()) {
                return BattleResult.cancelled();
            }
            boolean explicitNoTimesEvidence = recognizer.battlePreparationProbe().matched;
            AutomationOutcome waitOutcome = terminalOutcomeForEntryWait(explicitNoTimesEvidence);
            if (waitOutcome == AutomationOutcome.EXHAUSTED) {
                progress("战役：次数用尽", "出征后未进入战斗，仍在出征准备页");
                return BattleResult.exhausted();
            }
            return BattleResult.blocked("enemy-preview-timeout");
        }
        // 敌方预览 → 开始战斗
        if (!session.checkpoint()) {
            return BattleResult.cancelled();
        }
        if (!battleNavigator.tapEnemyStartBattle(
                cancelToken(), session::checkpoint, navigatorLogger())) {
            return session.cancelled() ? BattleResult.cancelled() : BattleResult.blocked("tap-enemy-start-battle-failed");
        }
        // 阵型选择 → 默认阵型
        if (!waitForBattleAdvancePage(MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION, BATTLE_ADVANCE_PAGE_TIMEOUT_MS)) {
            return session.cancelled() ? BattleResult.cancelled() : BattleResult.blocked("formation-page-timeout");
        }
        if (!session.checkpoint()) {
            return BattleResult.cancelled();
        }
        if (!battleNavigator.tapDefaultFormation(
                cancelToken(), session::checkpoint, navigatorLogger())) {
            return session.cancelled() ? BattleResult.cancelled() : BattleResult.blocked("tap-default-formation-failed");
        }
        return waitForCombatOrNightPromptCompletion(battle);
    }

    private BattleResult waitForCombatOrNightPromptCompletion(int battle) {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        boolean handledNightPrompt = false;
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < COMBAT_PHASE_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return BattleResult.cancelled();
            }
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
                if (!session.checkpoint()) {
                    return BattleResult.cancelled();
                }
                if (!battleNavigator.tapNightPrompt(
                        pursue, cancelToken(), session::checkpoint, navigatorLogger())) {
                    return session.cancelled() ? BattleResult.cancelled()
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
                return result.phase == NativeCombatPhase.DOCK_FULL
                        ? BattleResult.dockFull("blocker-detected, " + stateDetail(result))
                        : BattleResult.blocked("blocker-detected, " + stateDetail(result));
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("战役：等待进入战斗", stateDetail(result) + "\n" + nightPrompt.detail);
            }
            polls++;
            SystemClock.sleep(700L);
        }
        if (session.cancelled()) {
            return BattleResult.cancelled();
        }
        return BattleResult.blocked("combat-phase-timeout");
    }

    private BattleResult runCombatLoop(int battle) {
        for (int batch = 1; batch <= MAX_COMBAT_BATCHES && !session.cancelled(); batch++) {
            if (!session.checkpoint()) {
                return BattleResult.cancelled();
            }
            progress("战役：执行战斗", "battle=" + battle + ", guardedBatch=" + batch + "/" + MAX_COMBAT_BATCHES);
            AutomationUpdate finalUpdate = runGuardedBatch(battle, batch);
            if (session.cancelled()) {
                return BattleResult.cancelled();
            }
            if (finalUpdate == null) {
                return BattleResult.blocked("combat-batch-timeout");
            }
            progress("战役：战斗批次结束", "battle=" + battle + ", batch=" + batch
                    + ", outcome=" + finalUpdate.outcome + ", message=" + finalUpdate.message);
            switch (finalUpdate.outcome) {
                case SUCCESS:
                    return BattleResult.success(finalUpdate.message + ", " + finalUpdate.detail);
                case CANCELLED:
                    return BattleResult.cancelled();
                case FAILED:
                    return BattleResult.failed(finalUpdate.message + ", " + finalUpdate.detail);
                case DOCK_FULL:
                    return BattleResult.dockFull(finalUpdate.message + ", " + finalUpdate.detail);
                case BLOCKED:
                    return BattleResult.blocked(finalUpdate.message + ", " + finalUpdate.detail);
                case EXHAUSTED:
                case NO_ACTION:
                case RUNNING:
                default:
                    break;
            }
            SystemClock.sleep(1000L);
        }
        if (session.cancelled()) {
            return BattleResult.cancelled();
        }
        // 战役为单场战斗，达到批次上界仍未识别为成功，视为已打完一场（收尾相位与常规战不同）。
        return BattleResult.success("combat-max-batches-treated-as-complete");
    }

    private AutomationUpdate runGuardedBatch(final int battle, final int batch) {
        final GuardedResult guardedResult = new GuardedResult();
        GuardedCombatRunner runner = GuardedCombatRunner.boundedLoopExecuteGuarded(
                appContext,
                policy,
                new AutomationCallback() {
                    @Override
                    public void onUpdate(AutomationUpdate update) {
                        session.progressFromNested(update, "战役：战斗中",
                                "battle=" + battle + ", batch=" + batch
                                + ", outcome=" + update.outcome
                                + ", message=" + update.message
                                + ", detail=" + update.detail);
                        if (update.outcome.terminal) {
                            guardedResult.finish(update);
                        }
                    }
                }
        );
        boolean publicationReady = false;
        boolean childStarted = false;
        while (!publicationReady) {
            if (!session.checkpoint()) {
                return null;
            }
            synchronized (this) {
                if (session.cancelled()) {
                    return null;
                }
                if (!session.paused()) {
                    activeCombatRunner = runner;
                    childStarted = runner.start();
                    if (!childStarted) {
                        activeCombatRunner = null;
                    }
                    publicationReady = true;
                }
            }
        }
        if (!childStarted) {
            return null;
        }
        AutomationUpdate update = guardedResult.await(COMBAT_BATCH_TIMEOUT_MS);
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
            progress("战役：方案构建失败", "campaign-plan-build-failed, reason=" + e.getMessage());
            return null;
        }
    }

    private MinimumGameStateRecognizer.Result waitForInitialState() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < INITIAL_STATE_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return null;
            }
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
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (!session.checkpoint()) {
                return null;
            }
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
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (!session.checkpoint()) {
                return false;
            }
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
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (!session.checkpoint()) {
                return false;
            }
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
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < FOREGROUND_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return false;
            }
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
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < FRESH_FRAME_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return false;
            }
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
        if (session.cancelled()) {
            finishCancelled();
            return true;
        }
        if (result == null) {
            finishBlocked(timeoutReason);
            return true;
        }
        if (result.state.state == MinimumGameStateClassifier.State.BLOCKER) {
            finishBlocker(result, "blocker-detected, " + stateDetail(result));
            return true;
        }
        return false;
    }

    private void finishBlockedOrCancelled(String reason) {
        if (session.cancelled()) {
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
                return session.cancelled();
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
                return session.cancelled();
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
        finish(AutomationOutcome.BLOCKED, "战役：已阻断", reason, true);
    }

    private void finishBlocker(MinimumGameStateRecognizer.Result result, String reason) {
        if (result != null && result.phase == NativeCombatPhase.DOCK_FULL) {
            finishDockFull(reason);
        } else {
            finishBlocked(reason);
        }
    }

    private void finishDockFull(String reason) {
        finish(AutomationOutcome.DOCK_FULL, "战役：船坞已满", reason, true);
    }

    private void finishCancelled() {
        finish(AutomationOutcome.CANCELLED, "战役：已停止", "cancelled", true);
    }

    private void finishSuccess(String detail) {
        finish(AutomationOutcome.SUCCESS, "战役：完成", detail, false);
    }

    private void finishExhausted(String detail) {
        finish(AutomationOutcome.EXHAUSTED, "战役：次数已用尽", detail, false);
    }

    private void finishFailed(String detail) {
        finish(AutomationOutcome.FAILED, "战役：失败", detail, true);
    }

    private synchronized void finish(AutomationOutcome outcome, String message, String detail,
                                     boolean saveFailure) {
        running = false;
        worker = null;
        activeCombatRunner = null;
        session.finish(outcome, message, detail, saveFailure);
    }

    private void progress(String status, String detail) {
        session.progress(status, detail);
    }

    private static final class BattleResult {
        enum Kind {
            SUCCESS,
            BLOCKED,
            EXHAUSTED,
            DOCK_FULL,
            CANCELLED,
            FAILED
        }

        final Kind kind;
        final String detail;

        private BattleResult(Kind kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }

        static BattleResult success(String detail) {
            return new BattleResult(Kind.SUCCESS, detail);
        }

        static BattleResult blocked(String detail) {
            return new BattleResult(Kind.BLOCKED, detail);
        }

        static BattleResult exhausted() {
            return new BattleResult(Kind.EXHAUSTED, "times-exhausted");
        }

        static BattleResult dockFull(String detail) {
            return new BattleResult(Kind.DOCK_FULL, detail);
        }

        static BattleResult cancelled() {
            return new BattleResult(Kind.CANCELLED, "cancelled");
        }

        static BattleResult failed(String detail) {
            return new BattleResult(Kind.FAILED, detail);
        }
    }

    private final class GuardedResult {
        private volatile AutomationUpdate update;

        AutomationUpdate await(long timeoutMs) {
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (!session.cancelled()) {
                if (!session.checkpoint()) {
                    return null;
                }
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) {
                    return null;
                }
                synchronized (this) {
                    if (update != null) {
                        return update;
                    }
                    try {
                        wait(Math.min(remaining, 1000L));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            return update;
        }

        synchronized void finish(AutomationUpdate update) {
            this.update = update;
            notifyAll();
        }
    }
}
