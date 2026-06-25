package org.openwsgr.autowsgrmobile;

final class MinimumStartScreenSignatures {
    private static final float[][] RULES = new float[][]{
            {0.0508f, 0.8722f, 204f, 210f, 208f, 30f},
            {0.8125f, 0.8569f, 224f, 183f, 41f, 30f},
            {0.8695f, 0.9028f, 222f, 198f, 40f, 30f},
            {0.9641f, 0.8681f, 222f, 197f, 43f, 30f},
            {0.7914f, 0.7333f, 237f, 237f, 237f, 30f}
    };

    private MinimumStartScreenSignatures() {
    }

    static Result match(Sampler sampler) {
        if (sampler == null) {
            return new Result(false, "startScreenSignature=sampler-null");
        }
        StringBuilder detail = new StringBuilder("startScreenSignature=");
        int matched = 0;
        for (int i = 0; i < RULES.length; i++) {
            float[] rule = RULES[i];
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
        detail.append(", matched=").append(matched).append('/').append(RULES.length);
        return new Result(matched == RULES.length, detail.toString());
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
