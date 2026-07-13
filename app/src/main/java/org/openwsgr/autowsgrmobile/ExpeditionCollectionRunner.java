package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

final class ExpeditionCollectionRunner {
    static final String LOG_PREFIX = "auto-expedition";

    private static final long FOREGROUND_TIMEOUT_MS = 12000L;
    private static final long FRESH_FRAME_TIMEOUT_MS = 10000L;
    private static final long INITIAL_STATE_TIMEOUT_MS = 45000L;
    private static final long MAP_STATE_TIMEOUT_MS = 18000L;
    private static final long SLOT_REFRESH_TIMEOUT_MS = 10000L;
    private static final int MAX_COLLECTIONS = 8;

    private final Context appContext;
    private final AutoExpeditionConfig config;
    private final AutomationSession session;
    private final MinimumGameStateRecognizer recognizer;
    private final MinimumNormalBattleNavigator homeNavigator;
    private final ExpeditionCollectionNavigator expeditionNavigator;
    private final MainPageOverlayNavigator overlayNavigator;

    private Thread worker;
    private boolean running;

    ExpeditionCollectionRunner(Context context, AutoExpeditionConfig config, AutomationCallback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? AutoExpeditionConfig.parseText("") : config;
        String runId = String.valueOf(System.currentTimeMillis());
        this.session = new AutomationSession("expedition", runId, new AutomationControl(),
                AndroidAutomationSessionSink.create(appContext, "auto-expedition", runId), callback);
        this.recognizer = new MinimumGameStateRecognizer(appContext);
        this.homeNavigator = new MinimumNormalBattleNavigator();
        this.expeditionNavigator = new ExpeditionCollectionNavigator();
        this.overlayNavigator = new MainPageOverlayNavigator();
    }

    static String startupSummary(AutoExpeditionConfig config) {
        AutoExpeditionConfig safe = config == null ? AutoExpeditionConfig.parseText("") : config;
        return "interval_seconds=" + safe.intervalSeconds
                + ", collect_only=false"
                + ", redispatch=true";
    }

    static String waitFailureOutcome(boolean cancelled, String timeoutReason) {
        return cancelled ? "cancelled" : timeoutReason;
    }

    static String returnHomeOutcome(boolean cancelled, boolean tapAttempted, boolean tapSucceeded) {
        if (cancelled) {
            return "cancelled";
        }
        if (tapAttempted && !tapSucceeded) {
            return "tap-return-home-failed";
        }
        return "success";
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        progress("自动远征：启动中", startupSummary(config));
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
        }, "AutoWSGR-expedition-collection");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    synchronized void cancel() {
        session.cancel();
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
    }

    synchronized void pause() {
        session.pause();
    }

    synchronized void resume() {
        session.resume();
    }

    synchronized boolean isRunning() {
        return running;
    }

    private void runFlow() {
        progress("自动远征：检查前置条件", "game="
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
        progress(launch.started ? "自动远征：已启动游戏" : "自动远征：启动游戏失败", launch.detail);
        if (!launch.started) {
            finishBlocked(launch.detail);
            return;
        }
        if (!waitForTargetForeground()) {
            finishWaitFailure("target-foreground-timeout, latest="
                    + AutoWsgrAccessibilityService.latestForegroundPackage());
            return;
        }
        if (!waitForFreshFrameAfter(beforeLaunch.frameCount)) {
            finishWaitFailure("fresh-frame-timeout");
            return;
        }

        MinimumGameStateRecognizer.Result initial = waitForInitialState();
        initial = advancePastStartScreen(initial);
        if (!isRunning()) {
            return;
        }
        if (session.cancelled()) {
            finishCancelled();
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
            progress("自动远征：检测到启动页", stateDetail(initial));
            if (!session.checkpoint()) {
                finishCancelled();
                return null;
            }
            if (!homeNavigator.tapStartScreenEnter(homeCancelToken(), homeNavigatorLogger())) {
                finishBlockedOrCancelled("tap-start-screen-enter-failed");
                return null;
            }
            clicks++;
            progress("自动远征：已点击进入游戏", "source=autowsgr/ops/startup.py StartScreenPage.click_enter");
            initial = waitForInitialState();
        }
        return initial;
    }

    private void runFromHome(MinimumGameStateRecognizer.Result home) {
        if (!hasMainReadyNotification()) {
            finishNoAction("collected=0, reason=no-main-notification");
            return;
        }
        if (!session.checkpoint()) {
            finishCancelled();
            return;
        }
        if (!homeNavigator.tapHomeMap(home, homeCancelToken(), homeNavigatorLogger())) {
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
        if (!hasMapNotification()) {
            if (session.cancelled()) {
                finishCancelled();
                return;
            }
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            boolean returnedHome = expeditionNavigator.tapReturnHome(
                    expeditionCancelToken(),
                    expeditionNavigatorLogger()
            );
            String outcome = returnHomeOutcome(session.cancelled(), true, returnedHome);
            if ("cancelled".equals(outcome)) {
                finishCancelled();
                return;
            }
            if (!"success".equals(outcome)) {
                finishBlocked(outcome);
                return;
            }
            finishNoAction("collected=0, reason=no-map-notification");
            return;
        }
        if (!session.checkpoint()) {
            finishCancelled();
            return;
        }
        if (!expeditionNavigator.tapExpeditionPanel(expeditionCancelToken(), expeditionNavigatorLogger())) {
            finishBlockedOrCancelled("tap-expedition-panel-failed");
            return;
        }
        collectFromExpeditionPanel();
    }

    private void collectFromExpeditionPanel() {
        int collected = 0;
        int lastUnclearedSlot = -1;
        while (!session.cancelled() && collected < MAX_COLLECTIONS) {
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            int slot = findReadySlot();
            if (slot < 0) {
                if (!hasMapNotification()) {
                    finishCollected(collected, "reason=no-map-notification");
                    return;
                }
                slot = waitForReadySlot();
                if (slot < 0) {
                    if (session.cancelled()) {
                        finishCancelled();
                        return;
                    }
                    if (!hasMapNotification()) {
                        finishCollected(collected, "reason=no-map-notification");
                        return;
                    }
                    finishBlocked("expedition-ready-slot-timeout, collected=" + collected);
                    return;
                }
            }

            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!expeditionNavigator.tapReadySlot(slot, expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-slot-" + slot + "-failed");
                return;
            }
            SystemClock.sleep(1000L);
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!expeditionNavigator.tapSkipResult(expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-skip-failed");
                return;
            }
            SystemClock.sleep(1000L);
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!expeditionNavigator.tapConfirm(expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-confirm-failed");
                return;
            }
            // "继续远征 — 是否使用该编队继续远征" dialog: tap 确认 (yellow, left) to re-dispatch the same
            // fleet. This matches the original AutoWSGR collect_expedition, whose confirm_operation
            // clicks the yellow 确认 template (confirm_5_540p) and re-sends the expedition.
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            if (!expeditionNavigator.tapRedispatchConfirm(expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-redispatch-confirm-failed");
                return;
            }
            MinimumGameStateRecognizer.Result map = waitForState(
                    MinimumGameStateClassifier.State.MAP_PAGE,
                    MAP_STATE_TIMEOUT_MS,
                    "等待远征返回地图"
            );
            if (isBlockedOrCancelled(map, "expedition-return-map-timeout")) {
                return;
            }
            collected++;
            boolean slotCleared = !slotStillReady(slot);
            progress("自动远征：已收取远征", "collected=" + collected + "/" + MAX_COLLECTIONS
                    + ", slot=" + slot
                    + ", slotCleared=" + slotCleared);
            if (slotCleared) {
                lastUnclearedSlot = -1;
            } else {
                if (lastUnclearedSlot == slot) {
                    finishBlocked("expedition-slot-" + slot + "-not-cleared");
                    return;
                }
                lastUnclearedSlot = slot;
            }
        }
        if (session.cancelled()) {
            finishCancelled();
            return;
        }
        finishSuccess("collected=" + collected + ", reason=max-collections");
    }

    private int waitForReadySlot() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < SLOT_REFRESH_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return -1;
            }
            int slot = findReadySlot();
            if (slot >= 0) {
                progress("自动远征：检测到完成槽位", "slot=" + slot);
                return slot;
            }
            if (!hasMapNotification()) {
                progress("自动远征：远征通知已消失", "readySlot=none");
                return -1;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("自动远征：等待完成槽位", "poll=" + polls);
            }
            polls++;
            SystemClock.sleep(250L);
        }
        return -1;
    }

    private boolean hasMainReadyNotification() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return false;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return ExpeditionCollectionSignatures.hasMainReadyNotification(
                    new ExpeditionCollectionSignatures.Sampler() {
                        @Override
                        public int sample(float normalizedX, float normalizedY) {
                            return sampleFrame(frame, normalizedX, normalizedY);
                        }
                    }
            );
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private boolean hasMapNotification() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return false;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return ExpeditionCollectionSignatures.hasMapExpeditionNotification(
                    new ExpeditionCollectionSignatures.Sampler() {
                        @Override
                        public int sample(float normalizedX, float normalizedY) {
                            return sampleFrame(frame, normalizedX, normalizedY);
                        }
                    }
            );
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private int findReadySlot() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return -1;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return ExpeditionCollectionSignatures.findReadySlot(
                    new ExpeditionCollectionSignatures.Sampler() {
                        @Override
                        public int sample(float normalizedX, float normalizedY) {
                            return sampleFrame(frame, normalizedX, normalizedY);
                        }
                    }
            );
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private boolean slotStillReady(final int slot) {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return false;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return ExpeditionCollectionSignatures.slotReady(
                    new ExpeditionCollectionSignatures.Sampler() {
                        @Override
                        public int sample(float normalizedX, float normalizedY) {
                            return sampleFrame(frame, normalizedX, normalizedY);
                        }
                    },
                    slot
            );
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private static int sampleFrame(Bitmap frame, float normalizedX, float normalizedY) {
        int x = Math.min(frame.getWidth() - 1, Math.max(0, Math.round(normalizedX * (frame.getWidth() - 1))));
        int y = Math.min(frame.getHeight() - 1, Math.max(0, Math.round(normalizedY * (frame.getHeight() - 1))));
        return frame.getPixel(x, y);
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
            MainOverlaySnapshot overlay = detectCurrentMainOverlay();
            if (overlay != null) {
                progress(
                        "自动远征：检测到主界面浮层",
                        "overlay=" + overlay.kind
                                + ", newsNotShowChecked=" + overlay.newsNotShowChecked
                                + ", source=autowsgr/ui/main_page/overlays.py"
                );
                if (!session.checkpoint()) {
                    finishCancelled();
                    return null;
                }
                if (!overlayNavigator.dismiss(
                        overlay.kind,
                        overlay.newsNotShowChecked,
                        overlayCancelToken(),
                        overlayNavigatorLogger()
                )) {
                    finishBlockedOrCancelled("dismiss-main-overlay-failed, overlay=" + overlay.kind);
                    return null;
                }
                progress("自动远征：已清理主界面浮层", "overlay=" + overlay.kind);
                polls++;
                continue;
            }
            if (polls == 0 || polls % 6 == 0 || isActionable(state)) {
                progress("自动远征：识别游戏画面", stateDetail(result));
            }
            if (isActionable(state)) {
                return result;
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return null;
    }

    private MainOverlaySnapshot detectCurrentMainOverlay() {
        final Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return null;
        }
        final Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            MainPageOverlaySignatures.Sampler sampler = new MainPageOverlaySignatures.Sampler() {
                @Override
                public int sample(float normalizedX, float normalizedY) {
                    return sampleFrame(frame, normalizedX, normalizedY);
                }
            };
            MainPageOverlaySignatures.Kind kind = MainPageOverlaySignatures.detect(sampler);
            if (kind == null) {
                return null;
            }
            boolean newsNotShowChecked = kind == MainPageOverlaySignatures.Kind.NEWS
                    && MainPageOverlaySignatures.isNewsNotShowChecked(sampler);
            return new MainOverlaySnapshot(kind, newsNotShowChecked);
        } finally {
            frame.recycle();
            source.recycle();
        }
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
                progress("自动远征：" + label, stateDetail(result));
                return result;
            }
            if (polls == 0 || polls % 8 == 0) {
                progress("自动远征：" + label, stateDetail(result));
            }
            polls++;
            SystemClock.sleep(700L);
        }
        return null;
    }

    private boolean waitForTargetForeground() {
        long started = SystemClock.elapsedRealtime();
        while (!session.cancelled() && SystemClock.elapsedRealtime() - started < FOREGROUND_TIMEOUT_MS) {
            if (!session.checkpoint()) {
                return false;
            }
            if (TargetGameLauncher.isTargetForeground()) {
                progress("自动远征：游戏已在前台", "package="
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
                progress("自动远征：画面帧已刷新", snapshot.toStatusLine());
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

    private void finishWaitFailure(String timeoutReason) {
        String outcome = waitFailureOutcome(session.cancelled(), timeoutReason);
        if ("cancelled".equals(outcome)) {
            finishCancelled();
        } else {
            finishBlocked(outcome);
        }
    }

    private boolean isActionable(MinimumGameStateClassifier.State state) {
        return state == MinimumGameStateClassifier.State.HOME
                || state == MinimumGameStateClassifier.State.START_SCREEN
                || state == MinimumGameStateClassifier.State.MAP_PAGE
                || state == MinimumGameStateClassifier.State.COMBAT_PHASE
                || state == MinimumGameStateClassifier.State.BLOCKER;
    }

    private MinimumNormalBattleNavigator.CancelToken homeCancelToken() {
        return new MinimumNormalBattleNavigator.CancelToken() {
            @Override
            public boolean cancelled() {
                return session.cancelled();
            }
        };
    }

    private MinimumNormalBattleNavigator.Logger homeNavigatorLogger() {
        return new MinimumNormalBattleNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("自动远征：导航点击", status + ", " + detail);
            }
        };
    }

    private ExpeditionCollectionNavigator.CancelToken expeditionCancelToken() {
        return new ExpeditionCollectionNavigator.CancelToken() {
            @Override
            public boolean cancelled() {
                return session.cancelled();
            }
        };
    }

    private ExpeditionCollectionNavigator.Logger expeditionNavigatorLogger() {
        return new ExpeditionCollectionNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("自动远征：导航点击", status + ", " + detail);
            }
        };
    }

    private MainPageOverlayNavigator.CancelToken overlayCancelToken() {
        return new MainPageOverlayNavigator.CancelToken() {
            @Override
            public boolean cancelled() {
                return session.cancelled();
            }
        };
    }

    private MainPageOverlayNavigator.Logger overlayNavigatorLogger() {
        return new MainPageOverlayNavigator.Logger() {
            @Override
            public void log(String status, String detail) {
                progress("自动远征：主界面浮层点击", status + ", " + detail);
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
        finish(AutomationOutcome.BLOCKED, "自动远征：已阻断", reason, true);
    }

    private void finishBlocker(MinimumGameStateRecognizer.Result result, String reason) {
        if (result != null && result.phase == NativeCombatPhase.DOCK_FULL) {
            finish(AutomationOutcome.DOCK_FULL, "自动远征：船坞已满", reason, true);
        } else {
            finishBlocked(reason);
        }
    }

    private void finishCancelled() {
        finish(AutomationOutcome.CANCELLED, "自动远征：已停止", "cancelled", true);
    }

    private void finishSuccess(String detail) {
        finish(AutomationOutcome.SUCCESS, "自动远征：完成", detail, false);
    }

    private void finishNoAction(String detail) {
        finish(AutomationOutcome.NO_ACTION, "自动远征：无需收取", detail, false);
    }

    private void finishCollected(int collected, String reason) {
        String detail = "collected=" + collected + ", " + reason;
        if (collected == 0) {
            finishNoAction(detail);
        } else {
            finishSuccess(detail);
        }
    }

    private void finishFailed(String detail) {
        finish(AutomationOutcome.FAILED, "自动远征：失败", detail, true);
    }

    private synchronized void finish(AutomationOutcome outcome, String message, String detail,
                                     boolean saveFailure) {
        running = false;
        worker = null;
        session.finish(outcome, message, detail, saveFailure);
    }

    private void progress(String status, String detail) {
        session.progress(status, detail);
    }

    private static final class MainOverlaySnapshot {
        final MainPageOverlaySignatures.Kind kind;
        final boolean newsNotShowChecked;

        MainOverlaySnapshot(MainPageOverlaySignatures.Kind kind, boolean newsNotShowChecked) {
            this.kind = kind;
            this.newsNotShowChecked = newsNotShowChecked;
        }
    }
}
