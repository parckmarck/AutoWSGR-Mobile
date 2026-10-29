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
    // "继续远征 — 是否使用该编队继续远征" dialog: 确认 (yellow) is the LEFT button, 取消 (red) the RIGHT.
    // The Android port had the cancel button calibrated at (0.619, 0.638); the confirm button is its
    // mirror across the centred dialog. Tapping 确认 re-dispatches the same fleet — matching the
    // original AutoWSGR confirm_operation, which clicks the yellow 确认 template (confirm_5_540p).
    private static final float REDISPATCH_CONFIRM_X = 0.381f;
    private static final float REDISPATCH_CONFIRM_Y = 0.638f;
    private static final float RETURN_HOME_X = 0.050f;
    private static final float RETURN_HOME_Y = 0.055f;

    interface CancelToken {
        boolean cancelled();
    }

    interface TapDispatcher {
        boolean tap(float x, float y);
    }

    interface Delay {
        void sleep(long delayMs);
    }

    interface Logger {
        void log(String status, String detail);
    }

    private final TapDispatcher tapDispatcher;
    private final Delay delay;

    ExpeditionCollectionNavigator() {
        this(new TapDispatcher() {
            @Override public boolean tap(float x, float y) {
                return AndroidControllerBridge.tap(x, y);
            }
        }, new Delay() {
            @Override public void sleep(long delayMs) {
                SystemClock.sleep(delayMs);
            }
        });
    }

    ExpeditionCollectionNavigator(TapDispatcher tapDispatcher, Delay delay) {
        if (tapDispatcher == null || delay == null) {
            throw new IllegalArgumentException("tapDispatcher and delay are required");
        }
        this.tapDispatcher = tapDispatcher;
        this.delay = delay;
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

    static TapAction redispatchConfirmTap() {
        return new TapAction("expedition-redispatch-confirm", REDISPATCH_CONFIRM_X, REDISPATCH_CONFIRM_Y);
    }

    boolean tapExpeditionPanel(CancelToken cancel, Logger logger) {
        return tapExpeditionPanel(cancel, directBoundary(cancel), logger);
    }

    boolean tapExpeditionPanel(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                               Logger logger) {
        return tapGame(expeditionPanelTap(), cancel, boundary, logger);
    }

    boolean tapReadySlot(int slotIndex, CancelToken cancel, Logger logger) {
        return tapReadySlot(slotIndex, cancel, directBoundary(cancel), logger);
    }

    boolean tapReadySlot(int slotIndex, CancelToken cancel,
                         AutomationSession.ActionBoundary boundary, Logger logger) {
        return tapGame(readySlotTap(slotIndex), cancel, boundary, logger);
    }

    boolean tapSkipResult(CancelToken cancel, Logger logger) {
        return tapSkipResult(cancel, directBoundary(cancel), logger);
    }

    boolean tapSkipResult(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                          Logger logger) {
        return tapGame(skipResultTap(), cancel, boundary, logger);
    }

    boolean tapConfirm(CancelToken cancel, Logger logger) {
        return tapConfirm(cancel, directBoundary(cancel), logger);
    }

    boolean tapConfirm(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                       Logger logger) {
        return tapGame(new TapAction("expedition-confirm", CONFIRM_X, CONFIRM_Y),
                cancel, boundary, logger);
    }

    boolean tapRedispatchConfirm(CancelToken cancel, Logger logger) {
        return tapRedispatchConfirm(cancel, directBoundary(cancel), logger);
    }

    boolean tapRedispatchConfirm(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                                 Logger logger) {
        return tapGame(redispatchConfirmTap(), cancel, boundary, logger);
    }

    boolean tapReturnHome(CancelToken cancel, Logger logger) {
        return tapReturnHome(cancel, directBoundary(cancel), logger);
    }

    boolean tapReturnHome(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                          Logger logger) {
        return tapGame(new TapAction("map-return-home", RETURN_HOME_X, RETURN_HOME_Y),
                cancel, boundary, logger);
    }

    private boolean tapGame(TapAction action, CancelToken cancel,
                            AutomationSession.ActionBoundary boundary, Logger logger) {
        float screenX = GameFrameNormalizer.mapGameX(action.gameX);
        float screenY = GameFrameNormalizer.mapGameY(action.gameY);
        boolean tapped = boundary.perform(new AutomationSession.Action() {
            @Override public boolean run() {
                return tapDispatcher.tap(screenX, screenY);
            }
        });
        logger.log(tapped ? "tap" : "tap-failed", "action=" + action.name
                + ", x=" + screenX + ", y=" + screenY);
        if (!tapped) {
            return false;
        }
        delay.sleep(WAIT_AFTER_TAP_MS);
        return !cancel.cancelled();
    }

    private static AutomationSession.ActionBoundary directBoundary(final CancelToken cancel) {
        return new AutomationSession.ActionBoundary() {
            @Override public boolean perform(AutomationSession.Action action) {
                return !cancel.cancelled() && action.run();
            }
        };
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
