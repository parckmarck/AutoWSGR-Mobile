package org.openwsgr.autowsgrmobile;

import java.util.Locale;

final class MinimumRunOutcomePolicy {
    private MinimumRunOutcomePolicy() {
    }

    static Result classifyGuardedFinal(String status, String detail) {
        String text = ((status == null ? "" : status) + " " + (detail == null ? "" : detail))
                .toLowerCase(Locale.US);
        if (text.contains("cancel")) {
            return new Result(Action.CANCELLED, "combat-cancelled");
        }
        if (text.contains("route complete")) {
            return new Result(Action.SUCCESS, "route-complete");
        }
        if (text.contains("max steps reached")) {
            return new Result(Action.CONTINUE, "combat-needs-more-steps");
        }
        if (text.contains("no guarded phase")) {
            return new Result(Action.BLOCKED, "combat-no-guarded-phase");
        }
        if (text.contains("wait timed out") || text.contains("timed out")) {
            return new Result(Action.BLOCKED, "combat-wait-timeout");
        }
        if (text.contains("blocked")) {
            return new Result(Action.BLOCKED, "combat-step-blocked");
        }
        if (text.contains("route node out of plan")) {
            return new Result(Action.BLOCKED, "combat-route-deviation");
        }
        if (text.contains("no action spec")) {
            return new Result(Action.BLOCKED, "combat-no-action-spec");
        }
        if (text.trim().length() == 0) {
            return new Result(Action.BLOCKED, "combat-empty-status");
        }
        return new Result(Action.BLOCKED, "combat-unknown-terminal-status");
    }

    enum Action {
        SUCCESS,
        CONTINUE,
        BLOCKED,
        CANCELLED
    }

    static final class Result {
        final Action action;
        final String reason;

        Result(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }
    }
}
