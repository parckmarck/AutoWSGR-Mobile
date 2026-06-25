package org.openwsgr.autowsgrmobile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

enum NativeTemplateKey {
    FORMATION("formation", "combat/formation_540p.png"),
    SPOT_ENEMY("spot_enemy", "combat/spot_enemy_540p.png"),
    RESULT("result", "combat/result_540p.png"),
    FLAGSHIP_DAMAGE("flagship_damage", "combat/flagship_damage_540p.png"),
    PROCEED("proceed", "combat/proceed_540p.png"),
    NIGHT_BATTLE("night_battle", "combat/night_battle_540p.png"),
    FIGHT_CONDITION("fight_condition", "combat/fight_condition_540p.png"),
    BYPASS("bypass", "combat/bypass_540p.png"),
    RESULT_PAGE("result_page", "combat/result_page_540p.png"),
    MISSILE_SUPPORT("missile_support", "combat/missile_support_540p.png"),
    MISSILE_ANIMATION("missile_animation", "combat/missile_animation_540p.png"),
    FIGHT_PERIOD("fight_period", "combat/fight_period_540p.png"),
    GET_SHIP("get_ship", "combat/get_ship_540p.png"),
    GET_ITEM("get_item", "combat/get_item_540p.png"),
    GET_SHIP_OR_ITEM("get_ship_or_item", "combat/get_ship_540p.png", "combat/get_item_540p.png"),
    END_MAP_PAGE("end_map_page", "combat/end_map_page_540p.png"),
    END_BATTLE_PAGE("end_battle_page", "combat/end_battle_page_540p.png"),
    END_EXERCISE_PAGE("end_exercise_page", "combat/end_exercise_page_540p.png"),
    DOCK_FULL("dock_full", "build/ship_full_depot_540p.png"),
    BATTLE_TIMES_EXCEED("battle_times_exceed", "combat/battle_times_exceed_540p.png"),
    GRADE_SS("grade_ss", "combat/result/ss_540p.png"),
    GRADE_S("grade_s", "combat/result/s_540p.png"),
    GRADE_A("grade_a", "combat/result/a_540p.png"),
    GRADE_B("grade_b", "combat/result/b_540p.png"),
    GRADE_C("grade_c", "combat/result/c_540p.png"),
    GRADE_D("grade_d", "combat/result/d_540p.png"),
    GRADE_LOOT("grade_loot", "combat/result/loot_540p.png");

    private static final String IMAGE_ROOT = "autowsgr/data/images/";

    final String id;
    final String[] relativePaths;

    NativeTemplateKey(String id, String... relativePaths) {
        this.id = id;
        this.relativePaths = relativePaths;
    }

    List<NativeTemplateScanner.Template> loadTemplates(AutoWsgrAssetStore store) throws IOException {
        List<NativeTemplateScanner.Template> templates = new ArrayList<>();
        for (String relativePath : relativePaths) {
            String path = IMAGE_ROOT + relativePath;
            templates.add(new NativeTemplateScanner.Template(id, path, store.readBitmap(path)));
        }
        return templates;
    }

    static int mappedTemplateFileCount() {
        int count = 0;
        for (NativeTemplateKey key : values()) {
            count += key.relativePaths.length;
        }
        return count;
    }
}
