package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;

import java.util.Locale;

final class LiveCombatFlowWaiter {
    interface Callback {
        void onUpdate(Update update);
    }

    private static final long DEFAULT_TIMEOUT_MS = 6000L;
    private static final long DEFAULT_POLL_INTERVAL_MS = 250L;
    private static final long FINAL_SCAN_GRACE_MS = 1200L;
    private static final int DEFAULT_STRIDE = 24;
    private static final double DEFAULT_THRESHOLD = 0.999;

    private final Context appContext;
    private final Handler mainHandler;
    private final Callback callback;
    private final String scenarioId;
    private final NativeCombatTransitions.Graph graph;
    private final NativeCombatPhase current;
    private final String lastAction;
    private final long timeoutMs;
    private final long pollIntervalMs;
    private final int stride;
    private final double threshold;

    private volatile boolean cancelled;
    private volatile boolean running;
    private Thread worker;

    private LiveCombatFlowWaiter(
            Context context,
            Callback callback,
            String scenarioId,
            NativeCombatTransitions.Graph graph,
            NativeCombatPhase current,
            String lastAction,
            long timeoutMs,
            long pollIntervalMs,
            int stride,
            double threshold
    ) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        this.scenarioId = scenarioId;
        this.graph = graph;
        this.current = current;
        this.lastAction = lastAction;
        this.timeoutMs = timeoutMs;
        this.pollIntervalMs = pollIntervalMs;
        this.stride = stride;
        this.threshold = threshold;
    }

    static LiveCombatFlowWaiter mapSpotFight(Context context, Callback callback) {
        return mapScenario(context, callback, "spot_fight", NativeCombatPhase.SPOT_ENEMY_SUCCESS, "fight");
    }

    static LiveCombatFlowWaiter mapStart(Context context, Callback callback) {
        return mapScenario(context, callback, "map_start", NativeCombatPhase.START_FIGHT, "");
    }

    static LiveCombatFlowWaiter mapResult(Context context, Callback callback) {
        return mapScenario(context, callback, "map_result", NativeCombatPhase.RESULT, "");
    }

    static LiveCombatFlowWaiter mapProceedNo(Context context, Callback callback) {
        return mapScenario(context, callback, "map_proceed_no", NativeCombatPhase.PROCEED, "no");
    }

    static LiveCombatFlowWaiter mapCustom(
            Context context,
            Callback callback,
            String scenarioId,
            NativeCombatPhase current,
            String lastAction
    ) {
        return mapScenario(context, callback, scenarioId, current, lastAction);
    }

    private static LiveCombatFlowWaiter mapScenario(
            Context context,
            Callback callback,
            String scenarioId,
            NativeCombatPhase current,
            String lastAction
    ) {
        return new LiveCombatFlowWaiter(
                context,
                callback,
                scenarioId,
                NativeCombatTransitions.build(
                        NativeCombatTransitions.ModeCategory.MAP,
                        NativeCombatPhase.MAP_PAGE
                ),
                current,
                lastAction,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_POLL_INTERVAL_MS,
                DEFAULT_STRIDE,
                DEFAULT_THRESHOLD
        );
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        cancelled = false;
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "AutoWSGR-live-combat-wait");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    void cancel() {
        cancelled = true;
        Thread active = worker;
        if (active != null) {
            active.interrupt();
        }
    }

    boolean isRunning() {
        return running;
    }

    private void runLoop() {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(appContext);
        NativeCombatPhase[] candidates = graph.successors(current, lastAction);
        long startedAtMs = SystemClock.elapsedRealtime();
        int attempts = 0;
        post(Update.progress(attempts, 0L, startDetail(candidates)));

        try {
            while (true) {
                long elapsedMs = SystemClock.elapsedRealtime() - startedAtMs;
                if (cancelled) {
                    post(Update.cancelled(attempts, elapsedMs, "cancel requested"));
                    return;
                }
                boolean finalAttempt = shouldAttemptFinalScanAtTimeout(elapsedMs, timeoutMs);

                Bitmap liveFrame = ScreenFrameStore.latestBitmap();
                attempts++;
                if (liveFrame == null) {
                    if (finalAttempt) {
                        post(Update.timedOut(attempts, elapsedMs,
                                timeoutDetail(candidates, null, null) + "\nfinalScan=missing frame"));
                        return;
                    }
                    post(Update.progress(attempts, elapsedMs, "waiting for MediaProjection frame"));
                    sleepBetweenAttempts();
                    continue;
                }

                ScanDeadline deadline = new ScanDeadline(
                        startedAtMs,
                        finalAttempt ? finalScanDeadlineElapsedMs(timeoutMs) : timeoutMs
                );
                NativeCombatRecognizer.Recognition recognition;
                Bitmap scanFrame = null;
                try {
                    scanFrame = GameFrameNormalizer.toPhaseScanFrame(liveFrame);
                    recognition = NativeCombatRecognizer.identifyCurrent(
                            scanFrame,
                            store,
                            candidates,
                            stride,
                            threshold,
                            deadline
                    );
                    elapsedMs = SystemClock.elapsedRealtime() - startedAtMs;
                    String detail = attemptDetail(candidates, scanFrame, recognition, attempts, elapsedMs, finalAttempt);

                    if (cancelled) {
                        post(Update.cancelled(attempts, elapsedMs, detail));
                        return;
                    }
                    if (recognition.matched) {
                        post(Update.matched(attempts, elapsedMs, detail));
                        return;
                    }
                    if (deadline.timedOut() || finalAttempt) {
                        post(Update.timedOut(attempts, elapsedMs, detail));
                        return;
                    }
                    post(Update.progress(attempts, elapsedMs, detail));
                } finally {
                    if (scanFrame != null) {
                        scanFrame.recycle();
                    }
                    liveFrame.recycle();
                }

                sleepBetweenAttempts();
            }
        } finally {
            running = false;
        }
    }

    static boolean shouldAttemptFinalScanAtTimeout(long elapsedMs, long timeoutMs) {
        return timeoutMs >= 0L && elapsedMs >= timeoutMs;
    }

    static long finalScanDeadlineElapsedMs(long timeoutMs) {
        if (timeoutMs > Long.MAX_VALUE - FINAL_SCAN_GRACE_MS) {
            return Long.MAX_VALUE;
        }
        return timeoutMs + FINAL_SCAN_GRACE_MS;
    }

    private String startDetail(NativeCombatPhase[] candidates) {
        StringBuilder detail = new StringBuilder();
        append(detail, "wait scenario=" + scenarioId
                + ", current=" + current.id
                + ", action=" + lastAction
                + ", candidates=" + NativeCombatTransitions.format(candidates));
        append(detail, "worker=background, timeout=" + timeoutMs + "ms"
                + ", stride=" + stride
                + ", threshold=" + formatScore(threshold)
                + ", input=" + GameFrameNormalizer.phaseScanInputLabel());
        return detail.toString();
    }

    private String timeoutDetail(
            NativeCombatPhase[] candidates,
            Bitmap liveFrame,
            NativeCombatRecognizer.Recognition recognition
    ) {
        StringBuilder detail = new StringBuilder(startDetail(candidates));
        if (liveFrame != null) {
            append(detail, "live frame=" + liveFrame.getWidth() + "x" + liveFrame.getHeight());
        }
        if (recognition != null) {
            append(detail, "latest recognition=" + formatRecognition(recognition));
        }
        return detail.toString();
    }

    private String attemptDetail(
            NativeCombatPhase[] candidates,
            Bitmap liveFrame,
            NativeCombatRecognizer.Recognition recognition,
            int attempts,
            long elapsedMs,
            boolean finalAttempt
    ) {
        StringBuilder detail = new StringBuilder();
        append(detail, "wait scenario=" + scenarioId
                + ", current=" + current.id
                + ", action=" + lastAction
                + ", candidates=" + NativeCombatTransitions.format(candidates));
        append(detail, "attempts=" + attempts
                + ", elapsed=" + elapsedMs + "ms"
                + ", stride=" + stride
                + ", threshold=" + formatScore(threshold));
        append(detail, "live frame=" + liveFrame.getWidth() + "x" + liveFrame.getHeight());
        append(detail, "latest recognition=" + formatRecognition(recognition));
        if (finalAttempt) {
            append(detail, "finalScan=timeout-boundary");
        }
        return detail.toString();
    }

    private void sleepBetweenAttempts() {
        if (pollIntervalMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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

    private static String formatRecognition(NativeCombatRecognizer.Recognition recognition) {
        if (!recognition.matched) {
            return "miss reason=" + recognition.reason + ", bestPhase="
                    + (recognition.phase == null ? "none" : recognition.phase.id)
                    + ", score=" + formatScore(recognition.score);
        }
        if ("pixel".equals(recognition.signatureType)) {
            return "hit phase=" + recognition.phase.id
                    + ", type=pixel"
                    + ", signature=" + recognition.pixelSignatureName
                    + ", matched=" + recognition.matchedPixelSignatureName
                    + ", rules=" + recognition.matchedRules + "/" + recognition.totalRules;
        }
        return "hit phase=" + recognition.phase.id
                + ", type=template"
                + ", key=" + recognition.templateKeyId
                + ", at=(" + recognition.x + "," + recognition.y + ")"
                + ", score=" + formatScore(recognition.score);
    }

    private static String formatScore(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private final class ScanDeadline implements TemplateMatcher.ScanControl {
        private final long startedAtMs;
        private final long deadlineElapsedMs;
        private boolean timedOut;

        ScanDeadline(long startedAtMs, long deadlineElapsedMs) {
            this.startedAtMs = startedAtMs;
            this.deadlineElapsedMs = deadlineElapsedMs;
        }

        @Override
        public boolean shouldStop() {
            if (cancelled) {
                return true;
            }
            if (SystemClock.elapsedRealtime() - startedAtMs >= deadlineElapsedMs) {
                timedOut = true;
                return true;
            }
            return false;
        }

        @Override
        public String stopReason() {
            return cancelled ? "scan cancelled" : "scan timed out";
        }

        boolean timedOut() {
            return timedOut;
        }
    }

    static final class Update {
        final boolean running;
        final boolean matched;
        final boolean cancelled;
        final boolean timedOut;
        final int attempts;
        final long elapsedMs;
        final String detail;

        private Update(
                boolean running,
                boolean matched,
                boolean cancelled,
                boolean timedOut,
                int attempts,
                long elapsedMs,
                String detail
        ) {
            this.running = running;
            this.matched = matched;
            this.cancelled = cancelled;
            this.timedOut = timedOut;
            this.attempts = attempts;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }

        static Update progress(int attempts, long elapsedMs, String detail) {
            return new Update(true, false, false, false, attempts, elapsedMs, detail);
        }

        static Update matched(int attempts, long elapsedMs, String detail) {
            return new Update(false, true, false, false, attempts, elapsedMs, detail);
        }

        static Update cancelled(int attempts, long elapsedMs, String detail) {
            return new Update(false, false, true, false, attempts, elapsedMs, detail);
        }

        static Update timedOut(int attempts, long elapsedMs, String detail) {
            return new Update(false, false, false, true, attempts, elapsedMs, detail);
        }

        String toStatusLine() {
            if (running) {
                return "Live combat wait: running attempts=" + attempts + ", elapsed=" + elapsedMs + "ms";
            }
            if (matched) {
                return "Live combat wait: matched in " + elapsedMs + "ms";
            }
            if (cancelled) {
                return "Live combat wait: cancelled after " + elapsedMs + "ms";
            }
            if (timedOut) {
                return "Live combat wait: timed out after " + elapsedMs + "ms";
            }
            return "Live combat wait: failed";
        }
    }
}
