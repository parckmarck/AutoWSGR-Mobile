package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.os.SystemClock;

final class AutoExpeditionSchedulerRunner {
    private static final long WAIT_CHUNK_MS = 1000L;
    private static final long PASS_WAIT_CHUNK_MS = 250L;
    private static final long WAIT_STATUS_INTERVAL_MS = 60000L;
    static final long MAX_PASS_WAIT_MS = 10L * 60L * 1000L;

    private final Context appContext;
    private final AutoExpeditionConfig config;
    private final AutomationSession session;

    private Thread worker;
    private volatile ExpeditionCollectionRunner activePass;
    private boolean running;

    AutoExpeditionSchedulerRunner(Context context, AutoExpeditionConfig config,
                                  AutomationCallback callback) {
        this.appContext = context.getApplicationContext();
        this.config = config == null ? AutoExpeditionConfig.parseText("") : config;
        String runId = String.valueOf(System.currentTimeMillis());
        this.session = new AutomationSession("expedition", runId, new AutomationControl(),
                AndroidAutomationSessionSink.create(appContext, "auto-expedition", runId), callback);
    }

    static String startupSummary(AutoExpeditionConfig config) {
        AutoExpeditionConfig safe = config == null ? AutoExpeditionConfig.parseText("") : config;
        return "interval_seconds=" + safe.intervalSeconds
                + ", app_scoped=true"
                + ", collect_only=false";
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

    static AutomationUpdate forwardedPassUpdate(AutomationUpdate update, String schedulerRunId,
                                                 String schedulerLogPath) {
        String message = update == null || update.outcome.terminal
                ? "自动远征：等待下次检查"
                : update.message;
        String detail = update == null
                ? "pass_final_outcome=unknown, pass_final_message=, pass_final_detail="
                : "pass_outcome=" + update.outcome
                + ", pass_message=" + update.message
                + ", pass_detail=" + update.detail;
        return AutomationUpdate.progress(
                "expedition", schedulerRunId, message, detail, schedulerLogPath, ""
        );
    }

    synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    runLoop();
                } catch (Throwable failure) {
                    if (session.cancelled()) {
                        finishCancelled();
                    } else {
                        finish(AutomationOutcome.FAILED, "自动远征：失败",
                                "uncaught-exception, " + failure, true);
                    }
                }
            }
        }, "AutoWSGR-auto-expedition");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    void cancel() {
        session.cancel();
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

    void pause() {
        session.pause();
        ExpeditionCollectionRunner pass = activePass;
        if (pass != null) {
            pass.pause();
        }
    }

    void resume() {
        session.resume();
        ExpeditionCollectionRunner pass = activePass;
        if (pass != null) {
            pass.resume();
        }
    }

    synchronized boolean isRunning() {
        return running;
    }

    private void runLoop() {
        progress("自动远征：启动中", startupSummary(config));
        if (!config.enabled()) {
            finish(AutomationOutcome.NO_ACTION, "自动远征：已禁用", startupSummary(config), false);
            return;
        }

        boolean firstPassCompleted = false;
        long lastAttemptMs = 0L;
        while (!session.cancelled()) {
            if (!session.checkpoint()) {
                finishCancelled();
                return;
            }
            AutoExpeditionSchedulerPolicy.Decision decision = AutoExpeditionSchedulerPolicy.decide(
                    firstPassCompleted,
                    activePass != null,
                    SystemClock.elapsedRealtime(),
                    lastAttemptMs,
                    config.intervalSeconds
            );
            if (decision.action == AutoExpeditionSchedulerPolicy.Action.DISABLED) {
                finish(AutomationOutcome.NO_ACTION, "自动远征：已禁用", decision.detail, false);
                return;
            }
            if (decision.action == AutoExpeditionSchedulerPolicy.Action.WAIT) {
                sleepForWait(decision.waitMs, decision.detail);
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
            if (!session.checkpoint()) {
                activePass = null;
                finishCancelled();
                return;
            }
            if (!pass.start()) {
                activePass = null;
                sleepForWait(WAIT_CHUNK_MS, "pass-start-skipped");
                continue;
            }
            AutomationUpdate finalUpdate = monitor.awaitFinal(lastAttemptMs);
            if (finalUpdate == null && !session.cancelled()) {
                pass.cancel();
                progress("自动远征：本轮超时",
                        "pass-timeout, max_ms=" + MAX_PASS_WAIT_MS + ", " + startupSummary(config));
            }
            activePass = null;
            if (finalUpdate != null && finalUpdate.outcome == AutomationOutcome.FAILED) {
                finish(AutomationOutcome.FAILED, "自动远征：本轮失败",
                        "pass_message=" + finalUpdate.message + ", pass_detail=" + finalUpdate.detail,
                        true);
                return;
            }
            firstPassCompleted = true;
        }
        finishCancelled();
    }

    private AutomationCallback passCallback(final PassMonitor monitor) {
        return new AutomationCallback() {
            @Override public void onUpdate(AutomationUpdate update) {
                AutomationUpdate forwarded = forwardedPassUpdate(update, "", "");
                progress(forwarded.message, forwarded.detail);
                if (update.outcome.terminal) {
                    monitor.finish(update);
                }
            }
        };
    }

    private void sleepForWait(long waitMs, String reason) {
        long remainingMs = Math.max(0L, waitMs);
        long nextStatusMs = SystemClock.elapsedRealtime();
        while (!session.cancelled() && remainingMs > 0L) {
            if (!session.checkpoint()) {
                return;
            }
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs >= nextStatusMs) {
                progress("自动远征：等待下次检查",
                        "reason=" + reason + ", wait_ms=" + remainingMs + ", " + startupSummary(config));
                nextStatusMs = nowMs + WAIT_STATUS_INTERVAL_MS;
            }
            long chunkMs = Math.min(WAIT_CHUNK_MS, remainingMs);
            SystemClock.sleep(chunkMs);
            remainingMs -= chunkMs;
        }
    }

    private void finishCancelled() {
        finish(AutomationOutcome.CANCELLED, "自动远征：已停止", "cancelled", false);
    }

    private synchronized void finish(AutomationOutcome outcome, String message, String detail,
                                     boolean saveFailure) {
        running = false;
        worker = null;
        activePass = null;
        session.finish(outcome, message, detail, saveFailure);
    }

    private void progress(String message, String detail) {
        session.progress(message, detail);
    }

    private final class PassMonitor {
        private volatile AutomationUpdate finalUpdate;

        synchronized void finish(AutomationUpdate update) {
            finalUpdate = update;
            notifyAll();
        }

        AutomationUpdate awaitFinal() {
            return awaitFinal(SystemClock.elapsedRealtime());
        }

        AutomationUpdate awaitFinal(long startMs) {
            while (!session.cancelled()) {
                if (!session.checkpoint()) {
                    return finalUpdate;
                }
                PassWaitDecision decision = passWaitDecision(
                        finalUpdate == null, startMs, SystemClock.elapsedRealtime()
                );
                if (decision.action == PassWaitAction.DONE
                        || decision.action == PassWaitAction.TIMEOUT) {
                    return finalUpdate;
                }
                synchronized (this) {
                    if (finalUpdate != null) {
                        return finalUpdate;
                    }
                    try {
                        wait(decision.waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
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
