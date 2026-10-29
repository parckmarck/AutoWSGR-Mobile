package org.openwsgr.autowsgrmobile;

import java.util.Locale;

/**
 * 战役配置，对齐原版 {@code DailyAutomationConfig}（battle_type）与
 * {@code CampaignRunner}（times / formation / night / fleet）。
 *
 * <p>解析 {@code config/campaign-battle.yaml}，并能合成单节点战役战斗计划文本，
 * 交给 {@link CombatPlanPolicy#parse} 复用现有战斗机制。
 */
final class CampaignBattleConfig {
    static final String DEFAULT_BATTLE_TYPE = "困难潜艇";
    static final int DEFAULT_TIMES = 3;
    static final int DEFAULT_FORMATION = 2; // 复纵阵 double_column，同原版默认
    static final boolean DEFAULT_NIGHT = true;
    // 修理模式：none 不修 / moderate 中破及以上修 / severe 仅大破修。
    // 原版 CampaignRunner 默认 repair_mode=moderate_damage。
    static final String REPAIR_NONE = "none";
    static final String REPAIR_MODERATE = "moderate";
    static final String REPAIR_SEVERE = "severe";
    static final String DEFAULT_REPAIR_MODE = REPAIR_MODERATE;

    final String battleType;
    final int times;
    final int formation;
    final boolean night;
    final String repairMode;

    private CampaignBattleConfig(String battleType, int times, int formation, boolean night, String repairMode) {
        this.battleType = battleType;
        this.times = times;
        this.formation = formation;
        this.night = night;
        this.repairMode = repairMode;
    }

    static CampaignBattleConfig parseText(String text) {
        String battleType = DEFAULT_BATTLE_TYPE;
        int times = DEFAULT_TIMES;
        int formation = DEFAULT_FORMATION;
        boolean night = DEFAULT_NIGHT;
        String repairMode = DEFAULT_REPAIR_MODE;

        String raw = text == null ? "" : text;
        String[] lines = raw.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.length() == 0) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = cleanScalar(line.substring(colon + 1).trim());
            if ("battle_type".equals(key)) {
                battleType = value;
            } else if ("times".equals(key)) {
                times = parsePositiveInt("times", value);
            } else if ("formation".equals(key)) {
                formation = parseFormation(value);
            } else if ("night".equals(key)) {
                night = parseBoolean(value);
            } else if ("repair_mode".equals(key)) {
                repairMode = parseRepairMode(value);
            }
        }

        if (!CampaignCatalog.isValid(battleType)) {
            throw new IllegalArgumentException(
                    "invalid battle_type: " + battleType + ", expected one of " + CampaignCatalog.availableNames()
            );
        }
        return new CampaignBattleConfig(battleType, times, formation, night, repairMode);
    }

    static String defaultConfigText() {
        return "# AutoWSGR Android 战役配置\n"
                + "# battle_type: 简单/困难 + 驱逐/巡洋/战列/航母/潜艇\n"
                + "battle_type: " + DEFAULT_BATTLE_TYPE + "\n"
                + "times: " + DEFAULT_TIMES + "\n"
                + "# formation: 1单纵 2复纵 3轮型 4梯形 5单横\n"
                + "formation: " + DEFAULT_FORMATION + "\n"
                + "night: " + DEFAULT_NIGHT + "\n"
                + "# repair_mode: none 不修 / moderate 中破及以上修 / severe 仅大破修（使用当前舰队）\n"
                + "repair_mode: " + DEFAULT_REPAIR_MODE + "\n";
    }

    CampaignCatalog.Target target() {
        return CampaignCatalog.parse(battleType);
    }

    /** 修理阈值：需要修理的最低损伤状态（{@link ShipDamagePolicy} 值）；不修理返回 -1。 */
    int repairThreshold() {
        if (REPAIR_MODERATE.equals(repairMode)) {
            return ShipDamagePolicy.MODERATE;
        }
        if (REPAIR_SEVERE.equals(repairMode)) {
            return ShipDamagePolicy.SEVERE;
        }
        return -1;
    }

    /** 合成单节点战役战斗计划文本，供 {@link CombatPlanPolicy#parse} 解析（使用当前舰队，不指定 fleet_id）。 */
    String toPlanText() {
        return "mode: battle\n"
                + "node_defaults:\n"
                + "  formation: " + formation + "\n"
                + "  night: " + (night ? "true" : "false") + "\n";
    }

    String planPath() {
        return "campaign://" + battleType;
    }

    String summary() {
        return "battle_type=" + battleType
                + ", times=" + times
                + ", formation=" + formation
                + ", night=" + night
                + ", repair_mode=" + repairMode;
    }

    private static String parseRepairMode(String value) {
        String v = value.trim().toLowerCase(Locale.US);
        if (REPAIR_NONE.equals(v) || "off".equals(v) || "never".equals(v) || "不修".equals(value.trim())) {
            return REPAIR_NONE;
        }
        if (REPAIR_MODERATE.equals(v) || "moderate_damage".equals(v) || "中破".equals(value.trim())) {
            return REPAIR_MODERATE;
        }
        if (REPAIR_SEVERE.equals(v) || "severe_damage".equals(v) || "大破".equals(value.trim())) {
            return REPAIR_SEVERE;
        }
        throw new IllegalArgumentException("invalid repair_mode: " + value);
    }

    private static int parseFormation(String value) {
        String v = value.trim();
        if ("单纵".equals(v) || "单纵阵".equals(v) || "single_column".equalsIgnoreCase(v)) {
            return 1;
        }
        if ("复纵".equals(v) || "复纵阵".equals(v) || "双纵".equals(v) || "双纵阵".equals(v)
                || "double_column".equalsIgnoreCase(v)) {
            return 2;
        }
        if ("轮型".equals(v) || "轮型阵".equals(v) || "circular".equalsIgnoreCase(v)) {
            return 3;
        }
        if ("梯形".equals(v) || "梯形阵".equals(v) || "wedge".equalsIgnoreCase(v)) {
            return 4;
        }
        if ("单横".equals(v) || "单横阵".equals(v) || "single_horizontal".equalsIgnoreCase(v)) {
            return 5;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid formation: " + value, e);
        }
        if (parsed < 1 || parsed > 5) {
            throw new IllegalArgumentException("invalid formation (must be 1..5): " + value);
        }
        return parsed;
    }

    private static int parsePositiveInt(String key, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + key + ": " + value, e);
        }
        if (parsed < 1) {
            throw new IllegalArgumentException("invalid " + key + " (must be >= 1): " + value);
        }
        return parsed;
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("invalid night: " + value);
    }

    private static String stripComment(String line) {
        String value = line == null ? "" : line;
        int comment = value.indexOf('#');
        if (comment >= 0) {
            return value.substring(0, comment);
        }
        return value;
    }

    private static String cleanScalar(String raw) {
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
