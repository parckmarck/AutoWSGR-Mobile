package org.openwsgr.autowsgrmobile;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MainPageOverlayNavigator {
    private static final long NEWS_NOT_SHOW_WAIT_MS = 300L;
    private static final long WAIT_AFTER_TAP_MS = 1000L;

    interface CancelToken {
        boolean cancelled();
    }

    interface Logger {
        void log(String status, String detail);
    }

    static List<String> dismissPlan(MainPageOverlaySignatures.Kind kind, boolean newsNotShowChecked) {
        ArrayList<String> names = new ArrayList<>();
        for (TapAction action : dismissTapPlan(kind, newsNotShowChecked)) {
            names.add(action.name);
        }
        return Collections.unmodifiableList(names);
    }

    boolean dismiss(
            MainPageOverlaySignatures.Kind kind,
            boolean newsNotShowChecked,
            CancelToken cancel,
            Logger logger
    ) {
        for (TapAction action : dismissTapPlan(kind, newsNotShowChecked)) {
            if (!tapGame(action, cancel, logger)) {
                return false;
            }
        }
        return true;
    }

    private static List<TapAction> dismissTapPlan(
            MainPageOverlaySignatures.Kind kind,
            boolean newsNotShowChecked
    ) {
        ArrayList<TapAction> actions = new ArrayList<>();
        if (kind == MainPageOverlaySignatures.Kind.NEWS) {
            if (!newsNotShowChecked) {
                actions.add(new TapAction(
                        "news-not-show",
                        MainPageOverlaySignatures.NEWS_NOT_SHOW_X,
                        MainPageOverlaySignatures.NEWS_NOT_SHOW_Y,
                        NEWS_NOT_SHOW_WAIT_MS
                ));
            }
            actions.add(new TapAction(
                    "news-close",
                    MainPageOverlaySignatures.NEWS_CLOSE_X,
                    MainPageOverlaySignatures.NEWS_CLOSE_Y,
                    WAIT_AFTER_TAP_MS
            ));
        } else if (kind == MainPageOverlaySignatures.Kind.SIGN) {
            actions.add(new TapAction(
                    "sign-confirm",
                    MainPageOverlaySignatures.SIGN_CONFIRM_X,
                    MainPageOverlaySignatures.SIGN_CONFIRM_Y,
                    WAIT_AFTER_TAP_MS
            ));
            actions.add(new TapAction(
                    "sign-reward-confirm",
                    MainPageOverlaySignatures.SIGN_REWARD_CONFIRM_X,
                    MainPageOverlaySignatures.SIGN_REWARD_CONFIRM_Y,
                    WAIT_AFTER_TAP_MS
            ));
        } else if (kind == MainPageOverlaySignatures.Kind.BOOKING) {
            actions.add(new TapAction(
                    "booking-close",
                    MainPageOverlaySignatures.BOOKING_X,
                    MainPageOverlaySignatures.BOOKING_Y,
                    WAIT_AFTER_TAP_MS
            ));
        }
        return Collections.unmodifiableList(actions);
    }

    private boolean tapGame(TapAction action, CancelToken cancel, Logger logger) {
        if (cancel.cancelled()) {
            logger.log("cancelled", "before=" + action.name);
            return false;
        }
        float screenX = GameFrameNormalizer.mapGameX(action.gameX);
        float screenY = GameFrameNormalizer.mapGameY(action.gameY);
        boolean tapped = AndroidControllerBridge.tap(screenX, screenY);
        logger.log(tapped ? "tap" : "tap-failed", "action=" + action.name
                + ", x=" + screenX + ", y=" + screenY);
        if (!tapped) {
            return false;
        }
        SystemClock.sleep(action.waitAfterTapMs);
        return !cancel.cancelled();
    }

    private static final class TapAction {
        final String name;
        final float gameX;
        final float gameY;
        final long waitAfterTapMs;

        TapAction(String name, float gameX, float gameY, long waitAfterTapMs) {
            this.name = name;
            this.gameX = gameX;
            this.gameY = gameY;
            this.waitAfterTapMs = waitAfterTapMs;
        }
    }
}
