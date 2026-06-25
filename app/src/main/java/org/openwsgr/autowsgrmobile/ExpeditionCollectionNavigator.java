package org.openwsgr.autowsgrmobile;

import android.os.SystemClock;

final class ExpeditionCollectionNavigator {
    private static final long WAIT_AFTER_TAP_MS = 1000L;
    private static final float EXPEDITION_PANEL_X = 0.4422f;
    private static final float EXPEDITION_PANEL_Y = 0.0514f;
    private static final float SKIP_RESULT_X = 0.76f;
    private static final float SKIP_RESULT_Y = 0.5f;
    private static final float CONFIRM_X = 0.62f;
    private static final float CONFIRM_Y = 0.78f;
    private static final float REDISPATCH_CANCEL_X = 0.6190f;
    private static final float REDISPATCH_CANCEL_Y = 0.6380f;
    private static final float RETURN_HOME_X = 0.050f;
    private static final float RETURN_HOME_Y = 0.055f;

    interface CancelToken {
        boolean cancelled();
    }

    interface Logger {
        void log(String status, String detail);
    }

    static TapAction expeditionPanelTap() {
        return new TapAction("expedition-panel", EXPEDITION_PANEL_X, EXPEDITION_PANEL_Y);
    }

    static TapAction readySlotTap(int slotIndex) {
        return new TapAction(
                "expedition-slot-" + slotIndex,
                ExpeditionCollectionSignatures.readySlotX(slotIndex),
                ExpeditionCollectionSignatures.readySlotY(slotIndex)
        );
    }

    static TapAction skipResultTap() {
        return new TapAction("expedition-skip", SKIP_RESULT_X, SKIP_RESULT_Y);
    }

    static TapAction redispatchCancelTap() {
        return new TapAction("expedition-redispatch-cancel", REDISPATCH_CANCEL_X, REDISPATCH_CANCEL_Y);
    }

    boolean tapExpeditionPanel(CancelToken cancel, Logger logger) {
        return tapGame(expeditionPanelTap(), cancel, logger);
    }

    boolean tapReadySlot(int slotIndex, CancelToken cancel, Logger logger) {
        return tapGame(readySlotTap(slotIndex), cancel, logger);
    }

    boolean tapSkipResult(CancelToken cancel, Logger logger) {
        return tapGame(skipResultTap(), cancel, logger);
    }

    boolean tapConfirm(CancelToken cancel, Logger logger) {
        return tapGame(new TapAction("expedition-confirm", CONFIRM_X, CONFIRM_Y), cancel, logger);
    }

    boolean tapRedispatchCancel(CancelToken cancel, Logger logger) {
        return tapGame(redispatchCancelTap(), cancel, logger);
    }

    boolean tapReturnHome(CancelToken cancel, Logger logger) {
        return tapGame(new TapAction("map-return-home", RETURN_HOME_X, RETURN_HOME_Y), cancel, logger);
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
        SystemClock.sleep(WAIT_AFTER_TAP_MS);
        return !cancel.cancelled();
    }

    static final class TapAction {
        final String name;
        final float gameX;
        final float gameY;

        TapAction(String name, float gameX, float gameY) {
            this.name = name;
            this.gameX = gameX;
            this.gameY = gameY;
        }
    }
}
