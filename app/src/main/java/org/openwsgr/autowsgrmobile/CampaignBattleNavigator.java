package org.openwsgr.autowsgrmobile;

import android.os.SystemClock;

/**
 * 战役面板导航：切战役面板、切换难度、点选战役。
 *
 * <p>坐标对齐原版 {@code autowsgr/ui/map/data.py} 与 {@code ui/map/panels/campaign.py}，
 * 点击经 {@link GameFrameNormalizer} 归一化，沿用既有 {@code tapGame} 模式。
 */
final class CampaignBattleNavigator {
    private static final long WAIT_AFTER_TAP_MS = 800L;
    private static final long WAIT_AFTER_REPAIR_TAP_MS = 1500L;
    // 出征准备页「快速修理」面板标签，对齐原版 ui/battle/base.py CLICK_PANEL[QUICK_REPAIR]。
    private static final float QUICK_REPAIR_PANEL_X = 0.417f;
    private static final float QUICK_REPAIR_PANEL_Y = 0.793f;
    // 6 个舰船血条修理点，对齐原版 ui/battle/constants.py BLOOD_BAR_PROBE（= 安卓 PREPARATION_PROBES）。
    private static final float[][] REPAIR_SLOTS = new float[][]{
            {0.0583f, 0.5963f},
            {0.1750f, 0.5963f},
            {0.2917f, 0.5963f},
            {0.4083f, 0.5963f},
            {0.5250f, 0.5963f},
            {0.6417f, 0.5963f}
    };

    interface CancelToken {
        boolean cancelled();
    }

    interface Logger {
        void log(String status, String detail);
    }

    static TapAction battlePanelTap() {
        return new TapAction("campaign-battle-panel",
                CampaignSignatures.BATTLE_PANEL_X, CampaignSignatures.BATTLE_PANEL_Y);
    }

    static TapAction difficultyToggleTap() {
        return new TapAction("campaign-difficulty-toggle",
                CampaignSignatures.CLICK_DIFFICULTY_X, CampaignSignatures.CLICK_DIFFICULTY_Y);
    }

    static TapAction campaignTap(int mapIndex) {
        return new TapAction("campaign-select-" + mapIndex,
                CampaignSignatures.campaignX(mapIndex), CampaignSignatures.campaignY(mapIndex));
    }

    boolean tapBattlePanel(CancelToken cancel, Logger logger) {
        return tapGame(battlePanelTap(), cancel, logger);
    }

    boolean tapDifficultyToggle(CancelToken cancel, Logger logger) {
        return tapGame(difficultyToggleTap(), cancel, logger);
    }

    boolean tapCampaign(int mapIndex, CancelToken cancel, Logger logger) {
        return tapGame(campaignTap(mapIndex), cancel, logger);
    }

    boolean tapQuickRepairPanel(CancelToken cancel, Logger logger) {
        return tapGame(new TapAction("campaign-quick-repair-panel",
                QUICK_REPAIR_PANEL_X, QUICK_REPAIR_PANEL_Y), cancel, logger);
    }

    boolean tapRepairSlot(int slot, CancelToken cancel, Logger logger) {
        if (slot < 0 || slot >= REPAIR_SLOTS.length) {
            return false;
        }
        return tapGame(new TapAction("campaign-repair-slot-" + slot,
                REPAIR_SLOTS[slot][0], REPAIR_SLOTS[slot][1]), cancel, logger, WAIT_AFTER_REPAIR_TAP_MS);
    }

    private boolean tapGame(TapAction action, CancelToken cancel, Logger logger) {
        return tapGame(action, cancel, logger, WAIT_AFTER_TAP_MS);
    }

    private boolean tapGame(TapAction action, CancelToken cancel, Logger logger, long waitMs) {
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
        SystemClock.sleep(waitMs);
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
