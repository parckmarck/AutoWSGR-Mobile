package org.openwsgr.autowsgrmobile;

import java.io.IOException;

final class AutomationSession {
    interface Sink {
        void append(String line) throws IOException;
        String logPath();
        String saveFailure(String runId);
    }

    private final String flowId;
    private final String runId;
    private final AutomationControl control;
    private final Sink sink;
    private final AutomationCallback callback;
    private boolean finished;
    private boolean cancelRequested;
    private String adoptedLogPath = "";
    private String adoptedScreenshotPath = "";

    AutomationSession(String flowId, String runId, AutomationControl control, Sink sink,
                      AutomationCallback callback) {
        if (control == null || sink == null || callback == null) {
            throw new IllegalArgumentException("control, sink, and callback are required");
        }
        this.flowId = flowId == null ? "" : flowId;
        this.runId = runId == null ? "" : runId;
        this.control = control;
        this.sink = sink;
        this.callback = callback;
    }

    synchronized void progress(String message, String detail) {
        if (finished || cancelRequested) {
            return;
        }
        append(AutomationOutcome.RUNNING, message, detail);
        callback.onUpdate(AutomationUpdate.progress(
                flowId, runId, message, detail, effectiveLogPath(), adoptedScreenshotPath
        ));
    }

    synchronized void adoptEvidence(AutomationUpdate update) {
        if (finished || update == null) {
            return;
        }
        if (update.logPath.length() > 0) {
            adoptedLogPath = update.logPath;
        }
        if (update.screenshotPath.length() > 0) {
            adoptedScreenshotPath = update.screenshotPath;
        }
    }

    synchronized void progressFromNested(AutomationUpdate update, String message, String detail) {
        adoptEvidence(update);
        progress(message, detail);
    }

    synchronized boolean finish(AutomationOutcome outcome, String message, String detail,
                                boolean saveFailure) {
        if (outcome == null || !outcome.terminal) {
            throw new IllegalArgumentException("terminal outcome required");
        }
        if (finished) {
            return false;
        }
        finished = true;

        AutomationOutcome publishedOutcome = cancelRequested
                ? AutomationOutcome.CANCELLED : outcome;
        String publishedMessage = message;
        String publishedDetail = detail;
        boolean captureFailure = saveFailure;
        if (cancelRequested && outcome != AutomationOutcome.CANCELLED) {
            publishedMessage = "自动化：已停止";
            publishedDetail = "cancelled before terminal; discarded_outcome=" + outcome
                    + (detail == null || detail.length() == 0 ? "" : ", detail=" + detail);
            captureFailure = false;
        }
        if (publishedOutcome == AutomationOutcome.CANCELLED) {
            captureFailure = false;
        }

        append(publishedOutcome, publishedMessage, publishedDetail);
        String screenshotPath = adoptedScreenshotPath;
        if (captureFailure && screenshotPath.length() == 0) {
            try {
                String saved = sink.saveFailure(runId);
                screenshotPath = saved == null ? "" : saved;
            } catch (RuntimeException ignored) {
                screenshotPath = "";
            }
        }
        callback.onUpdate(AutomationUpdate.terminal(
                flowId, runId, publishedOutcome, publishedMessage, publishedDetail,
                effectiveLogPath(), screenshotPath
        ));
        return true;
    }

    void pause() {
        control.pause();
    }

    void resume() {
        control.resume();
    }

    synchronized void cancel() {
        if (!finished) {
            cancelRequested = true;
        }
        control.cancel();
    }

    boolean checkpoint() {
        try {
            return control.checkpoint();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    boolean cancelled() {
        return control.cancelled();
    }

    boolean paused() {
        return control.paused();
    }

    private void append(AutomationOutcome outcome, String message, String detail) {
        try {
            sink.append(MinimumRunLogStore.line(
                    runId, outcome.name(), combinedDetail(message, detail)
            ));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private String effectiveLogPath() {
        if (adoptedLogPath.length() > 0) {
            return adoptedLogPath;
        }
        try {
            String path = sink.logPath();
            return path == null ? "" : path;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String combinedDetail(String message, String detail) {
        String safeMessage = message == null ? "" : message;
        String safeDetail = detail == null ? "" : detail;
        if (safeMessage.length() == 0) {
            return safeDetail;
        }
        if (safeDetail.length() == 0) {
            return safeMessage;
        }
        return safeMessage + ", " + safeDetail;
    }
}
