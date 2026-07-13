package org.openwsgr.autowsgrmobile;

final class AutomationCoordinator {
    private AutomationFlow activeFlow;
    private String activeId = "";
    private String activeLabel = "";

    StartResult start(AutomationFlow flow) {
        if (flow == null) {
            synchronized (this) {
                return rejected("flow is required");
            }
        }

        String flowId = safe(flow.id());
        String flowLabel = safe(flow.label());
        synchronized (this) {
            if (activeFlow != null) {
                return rejected("another flow is active");
            }
            activeFlow = flow;
            activeId = flowId;
            activeLabel = flowLabel;
        }

        final boolean started;
        try {
            started = flow.start();
        } catch (RuntimeException | Error failure) {
            clearReservation(flow);
            throw failure;
        }
        if (!started) {
            clearReservation(flow);
            synchronized (this) {
                return rejected("flow did not start");
            }
        }
        return new StartResult(true, flowId, flowLabel, "");
    }

    synchronized boolean finish(String flowId) {
        if (activeFlow == null || !activeId.equals(safe(flowId))) {
            return false;
        }
        activeFlow = null;
        activeId = "";
        activeLabel = "";
        return true;
    }

    boolean cancelActive() {
        AutomationFlow flow;
        synchronized (this) {
            flow = activeFlow;
        }
        if (flow == null) {
            return false;
        }
        flow.cancel();
        return true;
    }

    boolean pauseActive() {
        AutomationFlow flow;
        synchronized (this) {
            flow = activeFlow;
        }
        if (flow == null || !flow.supportsPause()) {
            return false;
        }
        flow.pause();
        return true;
    }

    boolean resumeActive() {
        AutomationFlow flow;
        synchronized (this) {
            flow = activeFlow;
        }
        if (flow == null || !flow.supportsPause()) {
            return false;
        }
        flow.resume();
        return true;
    }

    synchronized boolean hasActiveFlow() {
        return activeFlow != null;
    }

    synchronized String activeId() {
        return activeId;
    }

    synchronized String activeLabel() {
        return activeLabel;
    }

    private synchronized void clearReservation(AutomationFlow flow) {
        if (activeFlow == flow) {
            activeFlow = null;
            activeId = "";
            activeLabel = "";
        }
    }

    private StartResult rejected(String reason) {
        return new StartResult(false, activeId, activeLabel, reason);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class StartResult {
        final boolean started;
        final String activeId;
        final String activeLabel;
        final String reason;

        private StartResult(boolean started, String activeId, String activeLabel, String reason) {
            this.started = started;
            this.activeId = activeId;
            this.activeLabel = activeLabel;
            this.reason = reason;
        }
    }
}
