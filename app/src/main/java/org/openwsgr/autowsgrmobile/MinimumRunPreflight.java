package org.openwsgr.autowsgrmobile;

final class MinimumRunPreflight {
    private MinimumRunPreflight() {
    }

    static Result validate(boolean hasFrame, boolean accessibilityActive, boolean targetPackageAvailable) {
        if (!hasFrame) {
            return Result.blocked("missing-screen-capture");
        }
        if (!accessibilityActive) {
            return Result.blocked("accessibility-disabled");
        }
        if (!targetPackageAvailable) {
            return Result.blocked("target-package-missing");
        }
        return Result.ready();
    }

    static final class Result {
        final boolean ready;
        final String reason;

        private Result(boolean ready, String reason) {
            this.ready = ready;
            this.reason = reason;
        }

        static Result ready() {
            return new Result(true, "ready");
        }

        static Result blocked(String reason) {
            return new Result(false, reason);
        }
    }
}
