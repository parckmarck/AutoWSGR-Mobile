package org.openwsgr.autowsgrmobile;

import java.io.IOException;

final class AutomationSession {
    interface Action {
        boolean run();
    }

    interface ActionBoundary {
        boolean perform(Action action);
    }

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
    private long nestedEvidenceGeneration;
    private long activeNestedEvidenceGeneration;

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

    ActionBoundary actionBoundary() {
        return new ActionBoundary() {
            @Override public boolean perform(Action action) {
                return performAction(action);
            }
        };
    }

    boolean performAction(final Action action) {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        try {
            return control.performAction(new AutomationControl.Action() {
                @Override public boolean run() {
                    return action.run();
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    synchronized NestedEvidenceScope beginNestedEvidence(String identity) {
        long generation = ++nestedEvidenceGeneration;
        activeNestedEvidenceGeneration = generation;
        adoptedLogPath = "";
        adoptedScreenshotPath = "";
        return new NestedEvidenceScope(generation, identity);
    }

    synchronized boolean adoptEvidence(NestedEvidenceScope scope, AutomationUpdate update) {
        if (!isActiveNestedEvidence(scope) || finished || update == null) {
            return false;
        }
        adoptedLogPath = safePath(update.logPath);
        adoptedScreenshotPath = safePath(update.screenshotPath);
        return true;
    }

    synchronized boolean progressFromNested(NestedEvidenceScope scope, AutomationUpdate update,
                                            String message, String detail) {
        if (!adoptEvidence(scope, update)) {
            return false;
        }
        progress(message, detail);
        return true;
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

    long activeTimeNanos() {
        return control.activeTimeNanos();
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

    private boolean isActiveNestedEvidence(NestedEvidenceScope scope) {
        return scope != null && scope.generation == activeNestedEvidenceGeneration;
    }

    private static String safePath(String path) {
        return path == null ? "" : path;
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

    static final class NestedEvidenceScope {
        final long generation;
        final String identity;

        private NestedEvidenceScope(long generation, String identity) {
            this.generation = generation;
            this.identity = identity == null ? "" : identity;
        }
    }
}
