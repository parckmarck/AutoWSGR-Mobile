package org.openwsgr.autowsgrmobile;

final class AutoExpeditionSchedulerPolicy {
    private AutoExpeditionSchedulerPolicy() {
    }

    static Decision decide(
            boolean firstPassCompleted,
            boolean passRunning,
            long nowMs,
            long lastAttemptMs,
            long intervalSeconds
    ) {
        if (intervalSeconds <= 0L) {
            return new Decision(Action.DISABLED, 0L, "disabled");
        }
        if (passRunning) {
            return new Decision(Action.WAIT, 1000L, "pass-running");
        }
        if (!firstPassCompleted) {
            return new Decision(Action.ATTEMPT, 0L, "first-pass");
        }

        if (intervalSeconds > Long.MAX_VALUE / 1000L) {
            return new Decision(Action.DISABLED, 0L, "interval-overflow");
        }
        long intervalMs = intervalSeconds * 1000L;
        long elapsedMs = nowMs - lastAttemptMs;
        if (elapsedMs >= intervalMs) {
            return new Decision(Action.ATTEMPT, 0L, "interval-due");
        }
        return new Decision(Action.WAIT, intervalMs - elapsedMs, "interval-wait");
    }

    enum Action {
        ATTEMPT,
        WAIT,
        DISABLED
    }

    static final class Decision {
        final Action action;
        final long waitMs;
        final String detail;

        Decision(Action action, long waitMs, String detail) {
            this.action = action;
            this.waitMs = waitMs;
            this.detail = detail;
        }
    }
}
