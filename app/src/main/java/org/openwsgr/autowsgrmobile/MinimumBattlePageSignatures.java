package org.openwsgr.autowsgrmobile;

final class MinimumBattlePageSignatures {
    private static final float[][] BATTLE_PREPARATION = new float[][]{
            {0.0758f, 0.7806f, 46f, 61f, 80f, 30f},
            {0.8758f, 0.0500f, 216f, 223f, 229f, 30f},
            {0.9422f, 0.9389f, 255f, 219f, 47f, 30f},
            {0.8070f, 0.9417f, 255f, 219f, 47f, 30f}
    };
    private static final float[][] ENEMY_PREVIEW = new float[][]{
            {0.735f, 0.920f, 104f, 11f, 14f, 70f},
            {0.840f, 0.920f, 193f, 173f, 42f, 70f},
            {0.930f, 0.920f, 190f, 168f, 42f, 70f}
    };
    private static final float[][] FORMATION_SELECTION = new float[][]{
            {0.620f, 0.220f, 28f, 103f, 183f, 65f},
            {0.700f, 0.220f, 227f, 227f, 227f, 45f},
            {0.920f, 0.220f, 227f, 227f, 227f, 45f},
            {0.560f, 0.360f, 29f, 105f, 188f, 65f},
            {0.620f, 0.500f, 227f, 227f, 227f, 50f}
    };
    private static final float[][] FLEET_SUPPLY_PROMPT = new float[][]{
            {0.330f, 0.360f, 237f, 237f, 237f, 35f},
            {0.500f, 0.470f, 237f, 237f, 237f, 35f},
            {0.350f, 0.630f, 161f, 2f, 2f, 70f},
            {0.650f, 0.650f, 50f, 151f, 246f, 70f},
            {0.500f, 0.550f, 64f, 161f, 109f, 70f}
    };
    private static final float[][] COMBAT_RESULT = new float[][]{
            {0.545f, 0.092f, 255f, 255f, 255f, 55f},
            {0.916f, 0.915f, 255f, 255f, 255f, 55f},
            {0.620f, 0.389f, 226f, 182f, 66f, 75f},
            {0.700f, 0.389f, 230f, 188f, 68f, 75f},
            {0.200f, 0.265f, 46f, 174f, 66f, 80f},
            {0.500f, 0.500f, 15f, 18f, 22f, 45f}
    };

    private MinimumBattlePageSignatures() {
    }

    static Result match(MinimumBattleAdvancePolicy.Page page, Sampler sampler) {
        if (page == null || sampler == null) {
            return new Result(false, "battlePage=missing-input");
        }
        float[][] rules = rulesFor(page);
        StringBuilder detail = new StringBuilder("battlePage=")
                .append(page.name())
                .append(", samples=");
        int matched = 0;
        for (int i = 0; i < rules.length; i++) {
            float[] rule = rules[i];
            int pixel = sampler.sample(rule[0], rule[1]);
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            boolean ok = near(r, g, b, (int) rule[2], (int) rule[3], (int) rule[4], rule[5]);
            if (ok) {
                matched++;
            }
            if (i > 0) {
                detail.append(';');
            }
            detail.append(i).append(ok ? ":ok" : ":miss")
                    .append('(').append(r).append(',').append(g).append(',').append(b).append(')');
        }
        detail.append(", matched=").append(matched).append('/').append(rules.length);
        return new Result(matched == rules.length, detail.toString());
    }

    private static float[][] rulesFor(MinimumBattleAdvancePolicy.Page page) {
        if (page == MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW) {
            return ENEMY_PREVIEW;
        }
        if (page == MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION) {
            return FORMATION_SELECTION;
        }
        if (page == MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT) {
            return FLEET_SUPPLY_PROMPT;
        }
        if (page == MinimumBattleAdvancePolicy.Page.COMBAT_RESULT) {
            return COMBAT_RESULT;
        }
        return BATTLE_PREPARATION;
    }

    private static boolean near(
            int r,
            int g,
            int b,
            int expectedR,
            int expectedG,
            int expectedB,
            double tolerance
    ) {
        int dr = r - expectedR;
        int dg = g - expectedG;
        int db = b - expectedB;
        return Math.sqrt(dr * dr + dg * dg + db * db) <= tolerance;
    }

    interface Sampler {
        int sample(float normalizedX, float normalizedY);
    }

    static final class Result {
        final boolean matched;
        final String detail;

        Result(boolean matched, String detail) {
            this.matched = matched;
            this.detail = detail;
        }
    }
}
