package org.openwsgr.autowsgrmobile;

final class MinimumBattleAdvancePolicy {
    private static final Step[] STEPS = new Step[]{
            Step.START_SORTIE,
            Step.ENEMY_START_BATTLE,
            Step.DEFAULT_FORMATION
    };

    private MinimumBattleAdvancePolicy() {
    }

    static Step[] steps() {
        return STEPS.clone();
    }

    enum Step {
        START_SORTIE(Page.BATTLE_PREPARATION),
        ENEMY_START_BATTLE(Page.ENEMY_PREVIEW),
        DEFAULT_FORMATION(Page.FORMATION_SELECTION);

        private final Page requiredPage;

        Step(Page requiredPage) {
            this.requiredPage = requiredPage;
        }

        Page requiredPage() {
            return requiredPage;
        }
    }

    enum Page {
        BATTLE_PREPARATION("出征准备"),
        ENEMY_PREVIEW("敌方预览"),
        FORMATION_SELECTION("阵型选择"),
        FLEET_SUPPLY_PROMPT("夜战确认（舰队补给提示）"),
        COMBAT_RESULT("战斗结果");

        private final String label;

        Page(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    static boolean isUnsupportedPrompt(Page page) {
        return false;
    }

    static String unsupportedReason(Page page) {
        return "unsupported-battle-prompt";
    }

    static String promptPhaseId(Page page) {
        if (page == Page.FLEET_SUPPLY_PROMPT) {
            return "night_prompt";
        }
        return "";
    }

    static String nightPromptAction(boolean pursue) {
        return pursue ? "night_yes" : "night_no";
    }

    static boolean isNightPromptAction(String actionId) {
        return "night_yes".equals(actionId) || "night_no".equals(actionId);
    }

    static boolean shouldPursueNight(String actionId) {
        return "night_yes".equals(actionId);
    }
}
