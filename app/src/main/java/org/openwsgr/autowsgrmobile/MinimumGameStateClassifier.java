package org.openwsgr.autowsgrmobile;

import java.util.Locale;

final class MinimumGameStateClassifier {
    static final double HOME_THRESHOLD = 0.94;

    private MinimumGameStateClassifier() {
    }

    static Result classify(Input input) {
        if (input == null || !input.hasFrame) {
            return new Result(State.NO_FRAME, "no-frame", detail(input));
        }
        if (input.blockerDetected) {
            return new Result(State.BLOCKER, "blocker-detected", detail(input));
        }
        if (!input.targetForeground) {
            return new Result(State.UNKNOWN, "target-not-foreground", detail(input));
        }
        if (input.startScreenMatched) {
            return new Result(State.START_SCREEN, "start-screen", detail(input));
        }
        if (input.combatMatched) {
            return new Result(State.COMBAT_PHASE, "combat-phase", detail(input));
        }
        if (input.mapMatched) {
            return new Result(State.MAP_PAGE, "map-page", detail(input));
        }
        if (input.homeAnchorMatched && input.homeAnchorScore >= HOME_THRESHOLD) {
            return new Result(State.HOME, "home-anchors", detail(input));
        }
        return new Result(State.UNKNOWN, "unrecognized", detail(input));
    }

    private static String detail(Input input) {
        if (input == null) {
            return "input=null";
        }
        return "hasFrame=" + input.hasFrame
                + ", targetForeground=" + input.targetForeground
                + ", homeAnchorMatched=" + input.homeAnchorMatched
                + ", blockerDetected=" + input.blockerDetected
                + ", combatMatched=" + input.combatMatched
                + ", mapMatched=" + input.mapMatched
                + ", startScreenMatched=" + input.startScreenMatched
                + ", homeAnchorScore=" + String.format(Locale.US, "%.4f", input.homeAnchorScore);
    }

    enum State {
        NO_FRAME,
        START_SCREEN,
        HOME,
        MAP_PAGE,
        COMBAT_PHASE,
        BLOCKER,
        UNKNOWN
    }

    static final class Input {
        final boolean hasFrame;
        final boolean targetForeground;
        final boolean homeAnchorMatched;
        final boolean blockerDetected;
        final boolean combatMatched;
        final boolean mapMatched;
        final boolean startScreenMatched;
        final double homeAnchorScore;

        Input(
                boolean hasFrame,
                boolean targetForeground,
                boolean homeAnchorMatched,
                boolean blockerDetected,
                boolean combatMatched,
                boolean mapMatched,
                double homeAnchorScore
        ) {
            this(
                    hasFrame,
                    targetForeground,
                    homeAnchorMatched,
                    blockerDetected,
                    combatMatched,
                    mapMatched,
                    false,
                    homeAnchorScore
            );
        }

        Input(
                boolean hasFrame,
                boolean targetForeground,
                boolean homeAnchorMatched,
                boolean blockerDetected,
                boolean combatMatched,
                boolean mapMatched,
                boolean startScreenMatched,
                double homeAnchorScore
        ) {
            this.hasFrame = hasFrame;
            this.targetForeground = targetForeground;
            this.homeAnchorMatched = homeAnchorMatched;
            this.blockerDetected = blockerDetected;
            this.combatMatched = combatMatched;
            this.mapMatched = mapMatched;
            this.startScreenMatched = startScreenMatched;
            this.homeAnchorScore = homeAnchorScore;
        }
    }

    static final class Result {
        final State state;
        final String reason;
        final String detail;

        Result(State state, String reason, String detail) {
            this.state = state;
            this.reason = reason;
            this.detail = detail;
        }
    }
}
