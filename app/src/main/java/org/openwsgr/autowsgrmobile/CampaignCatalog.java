package org.openwsgr.autowsgrmobile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * 战役名称 ↔ (战役编号 1-5, 难度 easy/hard) 映射。
 *
 * <p>对齐原版 {@code autowsgr/ops/campaign.py}：
 * {@code CAMPAIGN_NAMES = {1:'驱逐', 2:'巡洋', 3:'战列', 4:'航母', 5:'潜艇'}}，
 * 用户友好名称为 {@code 简单/困难 + 短名}。
 */
final class CampaignCatalog {
    static final String EASY = "easy";
    static final String HARD = "hard";

    private static final String[] SHORT_NAMES = {"驱逐", "巡洋", "战列", "航母", "潜艇"};

    private static final Map<String, Target> NAME_MAP = buildNameMap();

    private CampaignCatalog() {
    }

    private static Map<String, Target> buildNameMap() {
        LinkedHashMap<String, Target> map = new LinkedHashMap<>();
        for (int i = 0; i < SHORT_NAMES.length; i++) {
            int mapIndex = i + 1;
            map.put("简单" + SHORT_NAMES[i], new Target(mapIndex, EASY));
            map.put("困难" + SHORT_NAMES[i], new Target(mapIndex, HARD));
        }
        return Collections.unmodifiableMap(map);
    }

    static Target parse(String name) {
        String key = name == null ? "" : name.trim();
        Target target = NAME_MAP.get(key);
        if (target == null) {
            throw new IllegalArgumentException(
                    "invalid battle_type: " + name + ", expected one of " + availableNames()
            );
        }
        return target;
    }

    static boolean isValid(String name) {
        return name != null && NAME_MAP.containsKey(name.trim());
    }

    static String availableNames() {
        return new TreeSet<>(NAME_MAP.keySet()).toString();
    }

    static String shortName(int mapIndex) {
        if (mapIndex < 1 || mapIndex > SHORT_NAMES.length) {
            return "未知";
        }
        return SHORT_NAMES[mapIndex - 1];
    }

    static final class Target {
        final int mapIndex;
        final String difficulty;

        Target(int mapIndex, String difficulty) {
            this.mapIndex = mapIndex;
            this.difficulty = difficulty;
        }

        String label() {
            return (HARD.equals(difficulty) ? "困难" : "简单") + shortName(mapIndex);
        }
    }
}
