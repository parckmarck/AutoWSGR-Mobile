package org.openwsgr.autowsgrmobile;

enum AutomationOutcome {
    RUNNING(false),
    SUCCESS(true),
    NO_ACTION(true),
    EXHAUSTED(true),
    DOCK_FULL(true),
    BLOCKED(true),
    CANCELLED(true),
    FAILED(true);

    final boolean terminal;

    AutomationOutcome(boolean terminal) {
        this.terminal = terminal;
    }
}
