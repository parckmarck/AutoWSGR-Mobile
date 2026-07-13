package org.openwsgr.autowsgrmobile;

final class AutomationCoordinator {
    private Reservation active;

    StartResult start(AutomationFlow flow) {
        if (flow == null) {
            synchronized (this) {
                return rejected("flow is required");
            }
        }

        Reservation reservation = new Reservation(flow, safe(flow.id()), safe(flow.label()));
        synchronized (this) {
            if (active != null) {
                return rejected("another flow is active");
            }
            active = reservation;
        }

        final boolean started;
        try {
            started = flow.start();
        } catch (RuntimeException | Error failure) {
            clearReservation(reservation);
            throw failure;
        }
        if (!started) {
            clearReservation(reservation);
            synchronized (this) {
                return rejected("flow did not start");
            }
        }
        return new StartResult(true, reservation.id, reservation.label, "");
    }

    synchronized boolean finish(String flowId) {
        if (active == null || !active.id.equals(safe(flowId))) {
            return false;
        }
        active = null;
        return true;
    }

    boolean cancelActive() {
        AutomationFlow flow;
        synchronized (this) {
            if (active == null || active.cancelRequested) {
                return false;
            }
            active.cancelRequested = true;
            flow = active.flow;
        }
        flow.cancel();
        return true;
    }

    boolean pauseActive() {
        AutomationFlow flow;
        synchronized (this) {
            flow = active == null ? null : active.flow;
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
            flow = active == null ? null : active.flow;
        }
        if (flow == null || !flow.supportsPause()) {
            return false;
        }
        flow.resume();
        return true;
    }

    synchronized boolean hasActiveFlow() {
        return active != null;
    }

    synchronized String activeId() {
        return active == null ? "" : active.id;
    }

    synchronized String activeLabel() {
        return active == null ? "" : active.label;
    }

    private synchronized void clearReservation(Reservation reservation) {
        if (active == reservation) {
            active = null;
        }
    }

    private StartResult rejected(String reason) {
        return new StartResult(
                false,
                active == null ? "" : active.id,
                active == null ? "" : active.label,
                reason
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Reservation {
        final AutomationFlow flow;
        final String id;
        final String label;
        boolean cancelRequested;

        Reservation(AutomationFlow flow, String id, String label) {
            this.flow = flow;
            this.id = id;
            this.label = label;
        }
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
