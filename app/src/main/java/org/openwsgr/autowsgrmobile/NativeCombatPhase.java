package org.openwsgr.autowsgrmobile;

enum NativeCombatPhase {
    START_FIGHT("start_fight", null, null),
    DOCK_FULL("dock_full", NativeTemplateKey.DOCK_FULL, null),
    PROCEED("proceed", NativeTemplateKey.PROCEED, NativePhasePixelSignatures.Kind.PROCEED_PAGE),
    FIGHT_CONDITION("fight_condition", NativeTemplateKey.FIGHT_CONDITION, null),
    SPOT_ENEMY_SUCCESS("spot_enemy_success", NativeTemplateKey.SPOT_ENEMY, null),
    FORMATION("formation", null, NativePhasePixelSignatures.Kind.CHOOSE_FORMATION),
    MISSILE_ANIMATION("missile_animation", NativeTemplateKey.MISSILE_ANIMATION, null),
    FIGHT_PERIOD("fight_period", NativeTemplateKey.FIGHT_PERIOD, null),
    NIGHT_PROMPT("night_prompt", NativeTemplateKey.NIGHT_BATTLE, null),
    RESULT("result", NativeTemplateKey.RESULT, NativePhasePixelSignatures.Kind.RESULT_PAGE),
    GET_SHIP("get_ship", NativeTemplateKey.GET_SHIP_OR_ITEM, NativePhasePixelSignatures.Kind.GET_SHIP_PAGE),
    FLAGSHIP_SEVERE_DAMAGE("flagship_severe_damage", NativeTemplateKey.FLAGSHIP_DAMAGE, null),
    MAP_PAGE("map_page", NativeTemplateKey.END_MAP_PAGE, NativePhasePixelSignatures.Kind.MAP_PAGE),
    EXERCISE_PAGE("exercise_page", NativeTemplateKey.END_EXERCISE_PAGE, null),
    EVENT_MAP_PAGE("event_map_page", null, NativePhasePixelSignatures.Kind.EVENT_MAP_PAGE);

    final String id;
    final NativeTemplateKey templateKey;
    final NativePhasePixelSignatures.Kind pixelSignatureKind;

    NativeCombatPhase(
            String id,
            NativeTemplateKey templateKey,
            NativePhasePixelSignatures.Kind pixelSignatureKind
    ) {
        this.id = id;
        this.templateKey = templateKey;
        this.pixelSignatureKind = pixelSignatureKind;
    }

    boolean hasTemplateSignature() {
        return templateKey != null;
    }

    boolean hasPixelSignature() {
        return pixelSignatureKind != null;
    }

    boolean hasAnySignature() {
        return hasTemplateSignature() || hasPixelSignature();
    }

    NativePhasePixelSignatures.PixelMatch matchPixelSignature(android.graphics.Bitmap screen) {
        if (pixelSignatureKind == null) {
            return NativePhasePixelSignatures.PixelMatch.missing("phase has no pixel signature");
        }
        return NativePhasePixelSignatures.match(pixelSignatureKind, screen);
    }

    static int templateBackedCount() {
        int count = 0;
        for (NativeCombatPhase phase : values()) {
            if (phase.hasTemplateSignature()) {
                count++;
            }
        }
        return count;
    }

    static int pixelBackedCount() {
        int count = 0;
        for (NativeCombatPhase phase : values()) {
            if (phase.hasPixelSignature()) {
                count++;
            }
        }
        return count;
    }

    static int recognizableCount() {
        int count = 0;
        for (NativeCombatPhase phase : values()) {
            if (phase.hasAnySignature()) {
                count++;
            }
        }
        return count;
    }
}
