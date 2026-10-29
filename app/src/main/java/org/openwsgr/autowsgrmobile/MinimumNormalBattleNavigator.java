package org.openwsgr.autowsgrmobile;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MinimumNormalBattleNavigator {
    private static final long WAIT_AFTER_TAP_MS = 1200L;
    private static final float MAP_SORTIE_PANEL_X = 0.1844f;
    private static final float MAP_SORTIE_PANEL_Y = 0.0514f;
    private static final float ENTER_SORTIE_X = 0.625f;
    private static final float ENTER_SORTIE_Y = 0.556f;
    private static final float MAP_NEXT_X = 937f / 960f;
    private static final float MAP_NEXT_Y = 277f / 540f;
    private static final float MAP_PREV_X = 247f / 960f;
    private static final float MAP_PREV_Y = 277f / 540f;
    private static final float SIDEBAR_CLICK_X = 0.10f;
    private static final float CHAPTER_SELECTED_Y = 0.556f;
    private static final float CHAPTER_SPACING = 0.12f;
    private static final int CHAPTER_RESET_TAPS = 4;
    private static final int MAP_RESET_TAPS = 5;
    private static final float MAP_START_NODE_X = 0.8938f;
    private static final float MAP_START_NODE_Y = 0.7056f;
    private static final float FLEET_1_X = 0.088f;
    private static final float FLEET_1_Y = 0.170f;
    private static final float START_SCREEN_ENTER_X = 0.900f;
    private static final float START_SCREEN_ENTER_Y = 0.850f;

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

    MinimumNormalBattleNavigator() {
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

    MinimumNormalBattleNavigator(TapDispatcher tapDispatcher, Delay delay) {
        if (tapDispatcher == null || delay == null) {
            throw new IllegalArgumentException("tapDispatcher and delay are required");
        }
        this.tapDispatcher = tapDispatcher;
        this.delay = delay;
    }

    static List<TapAction> tapPlanForTarget(NormalSortieTarget target) {
        ArrayList<TapAction> actions = new ArrayList<>();
        actions.add(new TapAction("map-sortie-panel", MAP_SORTIE_PANEL_X, MAP_SORTIE_PANEL_Y));
        for (int i = 0; i < CHAPTER_RESET_TAPS; i++) {
            actions.add(chapterStepAction(-3, "chapter-reset--3"));
        }
        int remaining = target.chapter - 1;
        while (remaining > 0) {
            int step = Math.min(remaining, 3);
            actions.add(chapterStepAction(step, "chapter-step+" + step));
            remaining -= step;
        }
        for (int i = 0; i < MAP_RESET_TAPS; i++) {
            actions.add(new TapAction("map-prev-reset", MAP_PREV_X, MAP_PREV_Y));
        }
        for (int i = 1; i < target.map; i++) {
            actions.add(new TapAction("map-next", MAP_NEXT_X, MAP_NEXT_Y));
        }
        return Collections.unmodifiableList(actions);
    }

    static List<TapAction> enterSelectedMapPlan(NormalSortieTarget target) {
        ArrayList<TapAction> actions = new ArrayList<>();
        actions.add(new TapAction("enter-sortie-" + target.label(), ENTER_SORTIE_X, ENTER_SORTIE_Y));
        return Collections.unmodifiableList(actions);
    }

    static List<TapAction> startScreenEnterPlan() {
        ArrayList<TapAction> actions = new ArrayList<>();
        actions.add(new TapAction("start-screen-enter", START_SCREEN_ENTER_X, START_SCREEN_ENTER_Y));
        return Collections.unmodifiableList(actions);
    }

    boolean navigateToTarget(NormalSortieTarget target, CancelToken cancel, Logger logger) {
        return navigateToTarget(target, cancel, directBoundary(cancel), logger);
    }

    boolean navigateToTarget(NormalSortieTarget target, CancelToken cancel,
                             AutomationSession.ActionBoundary boundary, Logger logger) {
        for (TapAction action : tapPlanForTarget(target)) {
            if (!tapGame(action.name, action.gameX, action.gameY, cancel, boundary, logger)) {
                return false;
            }
        }
        return true;
    }

    boolean tapHomeMap(MinimumGameStateRecognizer.Result state, CancelToken cancel, Logger logger) {
        return tapHomeMap(state, cancel, directBoundary(cancel), logger);
    }

    boolean tapHomeMap(MinimumGameStateRecognizer.Result state, CancelToken cancel,
                       AutomationSession.ActionBoundary boundary, Logger logger) {
        return tapGame("home-map", state.homeMapTapX, state.homeMapTapY, cancel, boundary, logger);
    }

    boolean tapStartScreenEnter(CancelToken cancel, Logger logger) {
        return tapStartScreenEnter(cancel, directBoundary(cancel), logger);
    }

    boolean tapStartScreenEnter(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                                Logger logger) {
        for (TapAction action : startScreenEnterPlan()) {
            if (!tapGame(action.name, action.gameX, action.gameY, cancel, boundary, logger)) {
                return false;
            }
        }
        return true;
    }

    boolean tapMapSortiePanel(CancelToken cancel, Logger logger) {
        return tapMapSortiePanel(cancel, directBoundary(cancel), logger);
    }

    boolean tapMapSortiePanel(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                              Logger logger) {
        return tapGame("map-sortie-panel", MAP_SORTIE_PANEL_X, MAP_SORTIE_PANEL_Y,
                cancel, boundary, logger);
    }

    boolean tapEnterSortie(CancelToken cancel, Logger logger) {
        return tapEnterSortie(cancel, directBoundary(cancel), logger);
    }

    boolean tapEnterSortie(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                           Logger logger) {
        return tapGame("enter-sortie-1-1", ENTER_SORTIE_X, ENTER_SORTIE_Y,
                cancel, boundary, logger);
    }

    boolean tapEnterSortie(NormalSortieTarget target, CancelToken cancel, Logger logger) {
        return tapEnterSortie(target, cancel, directBoundary(cancel), logger);
    }

    boolean tapEnterSortie(NormalSortieTarget target, CancelToken cancel,
                           AutomationSession.ActionBoundary boundary, Logger logger) {
        for (TapAction action : enterSelectedMapPlan(target)) {
            if (!tapGame(action.name, action.gameX, action.gameY, cancel, boundary, logger)) {
                return false;
            }
        }
        return true;
    }

    boolean tapMapStartNode(CancelToken cancel, Logger logger) {
        return tapMapStartNode(cancel, directBoundary(cancel), logger);
    }

    boolean tapMapStartNode(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                            Logger logger) {
        return tapGame("map-start-node", MAP_START_NODE_X, MAP_START_NODE_Y,
                cancel, boundary, logger);
    }

    boolean tapFleetOne(CancelToken cancel, Logger logger) {
        return tapFleetOne(cancel, directBoundary(cancel), logger);
    }

    boolean tapFleetOne(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                        Logger logger) {
        return tapGame("fleet-1", FLEET_1_X, FLEET_1_Y, cancel, boundary, logger);
    }

    boolean tapStartBattle(CancelToken cancel, Logger logger) {
        return tapStartBattle(cancel, directBoundary(cancel), logger);
    }

    boolean tapStartBattle(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                           Logger logger) {
        return tapGame(
                "start-sortie",
                MinimumBattleTapTargets.START_BATTLE_X,
                MinimumBattleTapTargets.START_BATTLE_Y,
                cancel,
                boundary,
                logger
        );
    }

    boolean tapEnemyStartBattle(CancelToken cancel, Logger logger) {
        return tapEnemyStartBattle(cancel, directBoundary(cancel), logger);
    }

    boolean tapEnemyStartBattle(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                                Logger logger) {
        return tapGame(
                "enemy-start-battle",
                MinimumBattleTapTargets.ENEMY_START_BATTLE_X,
                MinimumBattleTapTargets.ENEMY_START_BATTLE_Y,
                cancel,
                boundary,
                logger
        );
    }

    boolean tapDefaultFormation(CancelToken cancel, Logger logger) {
        return tapDefaultFormation(cancel, directBoundary(cancel), logger);
    }

    boolean tapDefaultFormation(CancelToken cancel, AutomationSession.ActionBoundary boundary,
                                Logger logger) {
        return tapGame(
                "default-formation",
                MinimumBattleTapTargets.DEFAULT_FORMATION_X,
                MinimumBattleTapTargets.DEFAULT_FORMATION_Y,
                cancel,
                boundary,
                logger
        );
    }

    boolean tapNightPrompt(boolean pursue, CancelToken cancel, Logger logger) {
        return tapNightPrompt(pursue, cancel, directBoundary(cancel), logger);
    }

    boolean tapNightPrompt(boolean pursue, CancelToken cancel,
                           AutomationSession.ActionBoundary boundary, Logger logger) {
        return tapGame(
                pursue ? "night-pursue" : "night-abandon",
                pursue ? MinimumBattleTapTargets.NIGHT_YES_X : MinimumBattleTapTargets.NIGHT_NO_X,
                pursue ? MinimumBattleTapTargets.NIGHT_YES_Y : MinimumBattleTapTargets.NIGHT_NO_Y,
                cancel,
                boundary,
                logger
        );
    }

    private boolean tapGame(String name, float gameX, float gameY, CancelToken cancel,
                            AutomationSession.ActionBoundary boundary, Logger logger) {
        float screenX = GameFrameNormalizer.mapGameX(gameX);
        float screenY = GameFrameNormalizer.mapGameY(gameY);
        return tapScreen(name, screenX, screenY, cancel, boundary, logger);
    }

    private boolean tapScreen(String name, float x, float y, CancelToken cancel,
                              AutomationSession.ActionBoundary boundary, Logger logger) {
        boolean tapped = boundary.perform(new AutomationSession.Action() {
            @Override public boolean run() {
                return tapDispatcher.tap(x, y);
            }
        });
        logger.log(tapped ? "tap" : "tap-failed", "action=" + name + ", x=" + x + ", y=" + y);
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

    private static TapAction chapterStepAction(int step, String name) {
        return new TapAction(name, SIDEBAR_CLICK_X, CHAPTER_SELECTED_Y + step * CHAPTER_SPACING);
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
