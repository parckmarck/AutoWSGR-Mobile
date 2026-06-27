package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
    private final Handler mainHandler;
    private final Callback callback;
    private final File baseDir;
    private final String runId;
    private final File logFile;
    private final MinimumGameStateRecognizer recognizer;
    private final MinimumNormalBattleNavigator homeNavigator;
    private final ExpeditionCollectionNavigator expeditionNavigator;
    private final MainPageOverlayNavigator overlayNavigator;

    private Thread worker;
    private volatile boolean cancelled;
    private boolean running;

    ExpeditionCollectionRunner(Context context, AutoExpeditionConfig config, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? AutoExpeditionConfig.parseText("") : config;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        File external = appContext.getExternalFilesDir(null);
        this.baseDir = external == null ? appContext.getFilesDir() : external;
        this.runId = String.valueOf(System.currentTimeMillis());
        this.logFile = MinimumRunLogStore.logFile(baseDir, LOG_PREFIX, runId);
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

    static boolean shouldRunFinishSideEffects(boolean running) {
        return running;
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        cancelled = false;
        running = true;
        post(Update.running("自动远征：启动中", "runId=" + runId + ", "
                + startupSummary(config), logFile.getAbsolutePath(), ""));
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runFlow();
            }
        }, "AutoWSGR-expedition-collection");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    synchronized void cancel() {
        cancelled = true;
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
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
        if (cancelled) {
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
            progress("自动远征：检测到启动页", stateDetail(initial));
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
            finishSuccess("collected=0, reason=no-main-notification");
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
            if (cancelled) {
                finishCancelled();
                return;
            }
            boolean returnedHome = expeditionNavigator.tapReturnHome(
                    expeditionCancelToken(),
                    expeditionNavigatorLogger()
            );
            String outcome = returnHomeOutcome(cancelled, true, returnedHome);
            if ("cancelled".equals(outcome)) {
                finishCancelled();
                return;
            }
            if (!"success".equals(outcome)) {
                finishBlocked(outcome);
                return;
            }
            finishSuccess("collected=0, reason=no-map-notification");
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
        while (!cancelled && collected < MAX_COLLECTIONS) {
            int slot = findReadySlot();
            if (slot < 0) {
                if (!hasMapNotification()) {
                    finishSuccess("collected=" + collected);
                    return;
                }
                slot = waitForReadySlot();
                if (slot < 0) {
                    if (cancelled) {
                        finishCancelled();
                        return;
                    }
                    if (!hasMapNotification()) {
                        finishSuccess("collected=" + collected);
                        return;
                    }
                    finishBlocked("expedition-ready-slot-timeout, collected=" + collected);
                    return;
                }
            }

            if (!expeditionNavigator.tapReadySlot(slot, expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-slot-" + slot + "-failed");
                return;
            }
            SystemClock.sleep(1000L);
            if (!expeditionNavigator.tapSkipResult(expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-skip-failed");
                return;
            }
            SystemClock.sleep(1000L);
            if (!expeditionNavigator.tapConfirm(expeditionCancelToken(), expeditionNavigatorLogger())) {
                finishBlockedOrCancelled("tap-expedition-confirm-failed");
                return;
            }
            // "继续远征 — 是否使用该编队继续远征" dialog: tap 确认 (yellow, left) to re-dispatch the same
            // fleet. This matches the original AutoWSGR collect_expedition, whose confirm_operation
            // clicks the yellow 确认 template (confirm_5_540p) and re-sends the expedition.
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
        if (cancelled) {
            finishCancelled();
            return;
        }
        finishSuccess("collected=" + collected + ", reason=max-collections");
    }

    private int waitForReadySlot() {
        long started = SystemClock.elapsedRealtime();
        int polls = 0;
        while (!cancelled && SystemClock.elapsedRealtime() - started < SLOT_REFRESH_TIMEOUT_MS) {
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
        while (!cancelled && SystemClock.elapsedRealtime() - started < INITIAL_STATE_TIMEOUT_MS) {
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
        while (!cancelled && SystemClock.elapsedRealtime() - started < timeoutMs) {
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
        while (!cancelled && SystemClock.elapsedRealtime() - started < FOREGROUND_TIMEOUT_MS) {
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
        while (!cancelled && SystemClock.elapsedRealtime() - started < FRESH_FRAME_TIMEOUT_MS) {
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

    private void finishWaitFailure(String timeoutReason) {
        String outcome = waitFailureOutcome(cancelled, timeoutReason);
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
                return ExpeditionCollectionRunner.this.cancelled;
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
                return ExpeditionCollectionRunner.this.cancelled;
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
                return ExpeditionCollectionRunner.this.cancelled;
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
        if (!beginFinish()) {
            return;
        }
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId, LOG_PREFIX);
        appendLog("blocked", reason + ", " + evidence.detail);
        finish(Update.finished(
                "自动远征：已阻断",
                reason,
                logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""
        ));
    }

    private void finishCancelled() {
        if (!beginFinish()) {
            return;
        }
        FailureEvidenceStore.SaveResult evidence = FailureEvidenceStore.saveLatestFailure(baseDir, runId, LOG_PREFIX);
        appendLog("cancelled", evidence.detail);
        finish(Update.finished(
                "自动远征：已停止",
                "cancelled",
                logFile.getAbsolutePath(),
                evidence.saved ? evidence.file.getAbsolutePath() : ""
        ));
    }

    private void finishSuccess(String detail) {
        if (!beginFinish()) {
            return;
        }
        appendLog("success", detail);
        finish(Update.finished("自动远征：完成", detail, logFile.getAbsolutePath(), ""));
    }

    private synchronized boolean beginFinish() {
        if (!shouldRunFinishSideEffects(running)) {
            return false;
        }
        running = false;
        worker = null;
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

    private static final class MainOverlaySnapshot {
        final MainPageOverlaySignatures.Kind kind;
        final boolean newsNotShowChecked;

        MainOverlaySnapshot(MainPageOverlaySignatures.Kind kind, boolean newsNotShowChecked) {
            this.kind = kind;
            this.newsNotShowChecked = newsNotShowChecked;
        }
    }
}
