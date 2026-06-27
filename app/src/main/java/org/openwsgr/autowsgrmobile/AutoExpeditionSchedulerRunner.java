package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class AutoExpeditionSchedulerRunner {
    private static final long WAIT_CHUNK_MS = 1000L;
    private static final long PASS_WAIT_CHUNK_MS = 250L;
    private static final long WAIT_STATUS_INTERVAL_MS = 60000L;
    static final long MAX_PASS_WAIT_MS = 10L * 60L * 1000L;

    private final Context appContext;
    private final AutoExpeditionConfig config;
    private final Handler mainHandler;
    private final Callback callback;

    private Thread worker;
    private volatile boolean cancelled;
    private volatile ExpeditionCollectionRunner activePass;
    private boolean running;

    AutoExpeditionSchedulerRunner(Context context, AutoExpeditionConfig config, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? AutoExpeditionConfig.parseText("") : config;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
    }

    static String startupSummary(AutoExpeditionConfig config) {
        AutoExpeditionConfig safe = config == null ? AutoExpeditionConfig.parseText("") : config;
        return "interval_seconds=" + safe.intervalSeconds
                + ", app_scoped=true"
                + ", collect_only=false";
    }

    static boolean shouldBlockNewFlowForAutoExpedition(boolean autoRunning) {
        return autoRunning;
    }

    static PassWaitDecision passWaitDecision(boolean passRunning, long startMs, long nowMs) {
        if (!passRunning) {
            return new PassWaitDecision(PassWaitAction.DONE, 0L, "pass-done");
        }
        long elapsedMs = nowMs - startMs;
        if (elapsedMs >= MAX_PASS_WAIT_MS) {
            return new PassWaitDecision(
                    PassWaitAction.TIMEOUT,
                    0L,
                    "pass-timeout, elapsed_ms=" + elapsedMs + ", max_ms=" + MAX_PASS_WAIT_MS
            );
        }
        return new PassWaitDecision(
                PassWaitAction.WAIT,
                Math.min(PASS_WAIT_CHUNK_MS, MAX_PASS_WAIT_MS - Math.max(0L, elapsedMs)),
                "pass-running, elapsed_ms=" + elapsedMs
        );
    }

    static ExpeditionCollectionRunner.Update forwardedPassUpdate(ExpeditionCollectionRunner.Update update) {
        if (update == null) {
            return ExpeditionCollectionRunner.Update.running(
                    "自动远征：等待下次检查",
                    "pass_final_status=unknown, pass_final_detail=",
                    "",
                    ""
            );
        }
        if (update.running) {
            return update;
        }
        return ExpeditionCollectionRunner.Update.running(
                "自动远征：等待下次检查",
                "pass_final_status=" + update.status + ", pass_final_detail=" + update.detail,
                update.logPath,
                update.screenshotPath
        );
    }

    static ExpeditionCollectionRunner.Update waitUpdate(
            String reason,
            long remainingMs,
            AutoExpeditionConfig config,
            String latestLogPath,
            String latestScreenshotPath
    ) {
        return ExpeditionCollectionRunner.Update.running(
                "自动远征：等待下次检查",
                "reason=" + reason + ", wait_ms=" + Math.max(0L, remainingMs) + ", " + startupSummary(config),
                latestLogPath == null ? "" : latestLogPath,
                latestScreenshotPath == null ? "" : latestScreenshotPath
        );
    }

    static ExpeditionCollectionRunner.Update stopUpdate(
            String detail,
            String latestLogPath,
            String latestScreenshotPath
    ) {
        return ExpeditionCollectionRunner.Update.finished(
                "自动远征：已停止",
                detail,
                latestLogPath == null ? "" : latestLogPath,
                latestScreenshotPath == null ? "" : latestScreenshotPath
        );
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        cancelled = false;
        if (!config.enabled()) {
            post(ExpeditionCollectionRunner.Update.finished(
                    "自动远征：已禁用",
                    startupSummary(config),
                    "",
                    ""
            ));
            return false;
        }
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "AutoWSGR-auto-expedition");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    void cancel() {
        cancelled = true;
        ExpeditionCollectionRunner pass = activePass;
        if (pass != null) {
            pass.cancel();
        }
        Thread thread;
        synchronized (this) {
            thread = worker;
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    synchronized boolean isRunning() {
        return running;
    }

    private void runLoop() {
        post(ExpeditionCollectionRunner.Update.running(
                "自动远征：启动中",
                startupSummary(config),
                "",
                ""
        ));

        boolean firstPassCompleted = false;
        long lastAttemptMs = 0L;
        String latestLogPath = "";
        String latestScreenshotPath = "";
        while (!cancelled) {
            AutoExpeditionSchedulerPolicy.Decision decision = AutoExpeditionSchedulerPolicy.decide(
                    firstPassCompleted,
                    activePass != null,
                    SystemClock.elapsedRealtime(),
                    lastAttemptMs,
                    config.intervalSeconds
            );
            if (decision.action == AutoExpeditionSchedulerPolicy.Action.DISABLED) {
                finish("自动远征：已禁用", decision.detail, "", "");
                return;
            }
            if (decision.action == AutoExpeditionSchedulerPolicy.Action.WAIT) {
                sleepForWait(decision.waitMs, decision.detail, latestLogPath, latestScreenshotPath);
                continue;
            }

            lastAttemptMs = SystemClock.elapsedRealtime();
            PassMonitor monitor = new PassMonitor();
            ExpeditionCollectionRunner pass = new ExpeditionCollectionRunner(
                    appContext,
                    config,
                    passCallback(monitor)
            );
            activePass = pass;
            if (!pass.start()) {
                activePass = null;
                sleepForWait(WAIT_CHUNK_MS, "pass-start-skipped", latestLogPath, latestScreenshotPath);
                continue;
            }
            ExpeditionCollectionRunner.Update finalUpdate = monitor.awaitFinal(lastAttemptMs);
            if (finalUpdate == null && !cancelled) {
                pass.cancel();
                post(ExpeditionCollectionRunner.Update.running(
                        "自动远征：本轮超时",
                        "pass-timeout, max_ms=" + MAX_PASS_WAIT_MS + ", " + startupSummary(config),
                        "",
                        ""
                ));
            }
            activePass = null;
            if (finalUpdate != null) {
                latestLogPath = finalUpdate.logPath == null ? "" : finalUpdate.logPath;
                latestScreenshotPath = finalUpdate.screenshotPath == null ? "" : finalUpdate.screenshotPath;
            }
            firstPassCompleted = true;
        }
        finish(stopUpdate("cancelled", latestLogPath, latestScreenshotPath));
    }

    private ExpeditionCollectionRunner.Callback passCallback(final PassMonitor monitor) {
        return new ExpeditionCollectionRunner.Callback() {
            @Override
            public void onUpdate(ExpeditionCollectionRunner.Update update) {
                post(forwardedPassUpdate(update));
                if (update != null && !update.running) {
                    monitor.finish(update);
                }
            }
        };
    }

    private void sleepForWait(
            long waitMs,
            String reason,
            String latestLogPath,
            String latestScreenshotPath
    ) {
        long remainingMs = Math.max(0L, waitMs);
        long nextStatusMs = SystemClock.elapsedRealtime();
        while (!cancelled && remainingMs > 0L) {
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs >= nextStatusMs) {
                post(waitUpdate(reason, remainingMs, config, latestLogPath, latestScreenshotPath));
                nextStatusMs = nowMs + WAIT_STATUS_INTERVAL_MS;
            }
            long chunkMs = Math.min(WAIT_CHUNK_MS, remainingMs);
            SystemClock.sleep(chunkMs);
            remainingMs -= chunkMs;
        }
    }

    private void finish(String status, String detail, String logPath, String screenshotPath) {
        finish(ExpeditionCollectionRunner.Update.finished(status, detail, logPath, screenshotPath));
    }

    private void finish(ExpeditionCollectionRunner.Update update) {
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            worker = null;
        }
        activePass = null;
        post(update);
    }

    private void post(final ExpeditionCollectionRunner.Update update) {
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
        void onUpdate(ExpeditionCollectionRunner.Update update);
    }

    private final class PassMonitor {
        private ExpeditionCollectionRunner.Update finalUpdate;

        synchronized void finish(ExpeditionCollectionRunner.Update update) {
            finalUpdate = update;
            notifyAll();
        }

        synchronized ExpeditionCollectionRunner.Update awaitFinal() {
            return awaitFinal(SystemClock.elapsedRealtime());
        }

        synchronized ExpeditionCollectionRunner.Update awaitFinal(long startMs) {
            while (!cancelled) {
                PassWaitDecision decision = passWaitDecision(finalUpdate == null, startMs, SystemClock.elapsedRealtime());
                if (decision.action == PassWaitAction.DONE || decision.action == PassWaitAction.TIMEOUT) {
                    return finalUpdate;
                }
                try {
                    wait(decision.waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return finalUpdate;
        }
    }

    enum PassWaitAction {
        WAIT,
        TIMEOUT,
        DONE
    }

    static final class PassWaitDecision {
        final PassWaitAction action;
        final long waitMs;
        final String detail;

        PassWaitDecision(PassWaitAction action, long waitMs, String detail) {
            this.action = action;
            this.waitMs = waitMs;
            this.detail = detail;
        }
    }
}
