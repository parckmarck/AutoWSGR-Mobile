package org.openwsgr.autowsgrmobile;

final class AutomationUpdate {
    final String flowId;
    final String runId;
    final AutomationOutcome outcome;
    final String message;
    final String detail;
    final String logPath;
    final String screenshotPath;

    private AutomationUpdate(String flowId, String runId, AutomationOutcome outcome,
                             String message, String detail, String logPath, String screenshotPath) {
        this.flowId = flowId == null ? "" : flowId;
        this.runId = runId == null ? "" : runId;
        this.outcome = outcome;
        this.message = message == null ? "" : message;
        this.detail = detail == null ? "" : detail;
        this.logPath = logPath == null ? "" : logPath;
        this.screenshotPath = screenshotPath == null ? "" : screenshotPath;
    }

    static AutomationUpdate progress(String flowId, String runId, String message, String detail,
                                     String logPath, String screenshotPath) {
        return new AutomationUpdate(flowId, runId, AutomationOutcome.RUNNING,
                message, detail, logPath, screenshotPath);
    }

    static AutomationUpdate terminal(String flowId, String runId, AutomationOutcome outcome,
                                     String message, String detail, String logPath,
                                     String screenshotPath) {
        if (outcome == null || !outcome.terminal) {
            throw new IllegalArgumentException("terminal outcome required");
        }
        return new AutomationUpdate(flowId, runId, outcome,
                message, detail, logPath, screenshotPath);
    }

    boolean running() {
        return !outcome.terminal;
    }
}
