package org.openwsgr.autowsgrmobile;

final class DefaultUnifiedConfig {
    private DefaultUnifiedConfig() {
    }

    static String text() {
        return "version: 1\n"
                + "profile_name: 内置安全配置\n"
                + "automation:\n"
                + "  tasks: []\n"
                + "normal_sortie:\n"
                + "  plan_path: autowsgr/data/plan/normal_fight/1-1.yaml\n"
                + "auto_expedition:\n"
                + "  interval_seconds: 900\n"
                + "campaign:\n"
                + "  battle_type: 困难潜艇\n"
                + "  times: 3\n"
                + "  formation: 2\n"
                + "  night: true\n"
                + "  repair_mode: moderate\n";
    }
}
