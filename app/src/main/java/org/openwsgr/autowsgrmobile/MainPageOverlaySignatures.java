package org.openwsgr.autowsgrmobile;

final class MainPageOverlaySignatures {
    static final float NEWS_NOT_SHOW_X = 0.0729f;
    static final float NEWS_NOT_SHOW_Y = 0.8981f;
    static final float NEWS_CLOSE_X = 0.0313f;
    static final float NEWS_CLOSE_Y = 0.0556f;
    static final float SIGN_CONFIRM_X = 0.4938f;
    static final float SIGN_CONFIRM_Y = 0.6611f;
    static final float SIGN_REWARD_CONFIRM_X = 0.5000f;
    static final float SIGN_REWARD_CONFIRM_Y = 0.5680f;
    static final float BOOKING_X = 0.6180f;
    static final float BOOKING_Y = 0.5640f;

    private static final Rule[][] NEWS = new Rule[][]{
            new Rule[]{
                    new Rule(0.6523f, 0.8292f, 148, 88, 86, 30.0),
                    new Rule(0.7008f, 0.8306f, 119, 67, 62, 30.0),
                    new Rule(0.7844f, 0.8278f, 135, 79, 73, 30.0),
                    new Rule(0.8516f, 0.8278f, 126, 72, 65, 30.0)
            },
            new Rule[]{
                    new Rule(0.1800f, 0.7790f, 176, 114, 109, 35.0),
                    new Rule(0.4200f, 0.7790f, 167, 108, 103, 35.0),
                    new Rule(0.7200f, 0.7790f, 121, 68, 67, 35.0),
                    new Rule(0.9000f, 0.7790f, 149, 92, 90, 35.0)
            }
    };
    private static final Rule[][] NEWS_NOT_SHOW = new Rule[][]{
            new Rule[]{
                    new Rule(0.0714f, 0.9065f, 49, 130, 211, 40.0),
                    new Rule(0.0620f, 0.9130f, 52, 130, 205, 40.0)
            },
            new Rule[]{
                    new Rule(0.0586f, 0.9179f, 54, 129, 201, 40.0),
                    new Rule(0.0665f, 0.9040f, 54, 129, 201, 40.0)
            }
    };
    private static final Rule[][] SIGN = new Rule[][]{
            new Rule[]{
                    new Rule(0.4484f, 0.6514f, 250, 215, 40, 30.0),
                    new Rule(0.4516f, 0.6903f, 253, 219, 48, 30.0),
                    new Rule(0.5500f, 0.6528f, 254, 215, 44, 30.0),
                    new Rule(0.5508f, 0.6917f, 253, 219, 48, 30.0)
            }
    };
    private static final Rule[][] BOOKING = new Rule[][]{
            new Rule[]{
                    new Rule(0.3375f, 0.3861f, 225, 225, 225, 30.0),
                    new Rule(0.6078f, 0.3625f, 225, 225, 225, 30.0),
                    new Rule(0.3344f, 0.5694f, 33, 122, 216, 30.0),
                    new Rule(0.5789f, 0.5681f, 153, 37, 37, 30.0),
                    new Rule(0.4875f, 0.5736f, 225, 225, 225, 30.0)
            }
    };

    private MainPageOverlaySignatures() {
    }

    static Kind detect(Sampler sampler) {
        if (matches(sampler, Kind.NEWS)) {
            return Kind.NEWS;
        }
        if (matches(sampler, Kind.SIGN)) {
            return Kind.SIGN;
        }
        if (matches(sampler, Kind.BOOKING)) {
            return Kind.BOOKING;
        }
        return null;
    }

    static boolean isNewsNotShowChecked(Sampler sampler) {
        return matches(sampler, Kind.NEWS_NOT_SHOW);
    }

    static int ruleCount(Kind kind) {
        Rule[][] variants = variantsFor(kind);
        return variants.length == 0 ? 0 : variants[0].length;
    }

    private static boolean matches(Sampler sampler, Kind kind) {
        if (sampler == null) {
            return false;
        }
        Rule[][] variants = variantsFor(kind);
        for (Rule[] rules : variants) {
            boolean matched = rules.length > 0;
            for (Rule rule : rules) {
                if (!rule.matches(sampler.sample(rule.x, rule.y))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static Rule[][] variantsFor(Kind kind) {
        if (kind == Kind.NEWS) {
            return NEWS;
        }
        if (kind == Kind.NEWS_NOT_SHOW) {
            return NEWS_NOT_SHOW;
        }
        if (kind == Kind.SIGN) {
            return SIGN;
        }
        if (kind == Kind.BOOKING) {
            return BOOKING;
        }
        return new Rule[0][0];
    }

    enum Kind {
        NEWS,
        NEWS_NOT_SHOW,
        SIGN,
        BOOKING
    }

    interface Sampler {
        int sample(float normalizedX, float normalizedY);
    }

    private static final class Rule {
        final float x;
        final float y;
        final int r;
        final int g;
        final int b;
        final double tolerance;

        Rule(float x, float y, int r, int g, int b, double tolerance) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.tolerance = tolerance;
        }

        boolean matches(int argb) {
            int ar = (argb >> 16) & 0xff;
            int ag = (argb >> 8) & 0xff;
            int ab = argb & 0xff;
            int dr = ar - r;
            int dg = ag - g;
            int db = ab - b;
            return Math.sqrt(dr * dr + dg * dg + db * db) <= tolerance;
        }
    }
}
