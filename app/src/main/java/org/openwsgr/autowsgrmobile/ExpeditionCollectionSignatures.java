package org.openwsgr.autowsgrmobile;

final class ExpeditionCollectionSignatures {
    static final float MAIN_READY_X = 0.9719f;
    static final float MAIN_READY_Y = 0.8407f;
    static final float MAP_NOTIFICATION_X = 0.4953f;
    static final float MAP_NOTIFICATION_Y = 0.0213f;
    static final int READY_SLOT_COUNT = 4;

    private static final int MAIN_READY_R = 255;
    private static final int MAIN_READY_G = 89;
    private static final int MAIN_READY_B = 45;
    private static final int MAP_NOTIFICATION_R = 245;
    private static final int MAP_NOTIFICATION_G = 88;
    private static final int MAP_NOTIFICATION_B = 47;
    private static final int READY_SLOT_R = 253;
    private static final int READY_SLOT_G = 228;
    private static final int READY_SLOT_B = 66;
    private static final double NOTIFICATION_TOLERANCE = 40.0;
    private static final double READY_SLOT_TOLERANCE = 30.0;
    // Small spatial radius (in normalized frame space, ~4px on a 960-wide frame) sampled around
    // each calibrated probe so a slight per-device calibration drift no longer misses a solid badge.
    private static final float NEIGHBOR_DELTA = 0.004f;
    private static final float[][] READY_SLOTS = new float[][]{
            {0.8516f, 0.2736f},
            {0.8531f, 0.4736f},
            {0.8539f, 0.6667f},
            {0.8547f, 0.8694f}
    };

    private ExpeditionCollectionSignatures() {
    }

    static float readySlotX(int index) {
        return READY_SLOTS[index][0];
    }

    static float readySlotY(int index) {
        return READY_SLOTS[index][1];
    }

    static boolean hasMainReadyNotification(Sampler sampler) {
        if (sampler == null) {
            return false;
        }
        return nearAny(
                sampler,
                MAIN_READY_X,
                MAIN_READY_Y,
                MAIN_READY_R,
                MAIN_READY_G,
                MAIN_READY_B,
                NOTIFICATION_TOLERANCE
        );
    }

    static boolean hasMapExpeditionNotification(Sampler sampler) {
        if (sampler == null) {
            return false;
        }
        return nearAny(
                sampler,
                MAP_NOTIFICATION_X,
                MAP_NOTIFICATION_Y,
                MAP_NOTIFICATION_R,
                MAP_NOTIFICATION_G,
                MAP_NOTIFICATION_B,
                NOTIFICATION_TOLERANCE
        );
    }

    static int findReadySlot(Sampler sampler) {
        if (sampler == null) {
            return -1;
        }
        for (int i = 0; i < READY_SLOTS.length; i++) {
            if (slotReady(sampler, i)) {
                return i;
            }
        }
        return -1;
    }

    static boolean slotReady(Sampler sampler, int index) {
        if (sampler == null || index < 0 || index >= READY_SLOTS.length) {
            return false;
        }
        return nearAny(
                sampler,
                READY_SLOTS[index][0],
                READY_SLOTS[index][1],
                READY_SLOT_R,
                READY_SLOT_G,
                READY_SLOT_B,
                READY_SLOT_TOLERANCE
        );
    }

    private static boolean nearAny(
            Sampler sampler,
            float centerX,
            float centerY,
            int expectedR,
            int expectedG,
            int expectedB,
            double tolerance
    ) {
        float[][] offsets = new float[][]{
                {0f, 0f},
                {-NEIGHBOR_DELTA, -NEIGHBOR_DELTA},
                {NEIGHBOR_DELTA, -NEIGHBOR_DELTA},
                {-NEIGHBOR_DELTA, NEIGHBOR_DELTA},
                {NEIGHBOR_DELTA, NEIGHBOR_DELTA}
        };
        for (float[] offset : offsets) {
            float x = clampUnit(centerX + offset[0]);
            float y = clampUnit(centerY + offset[1]);
            if (near(sampler.sample(x, y), expectedR, expectedG, expectedB, tolerance)) {
                return true;
            }
        }
        return false;
    }

    private static float clampUnit(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static boolean near(int argb, int expectedR, int expectedG, int expectedB, double tolerance) {
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        int dr = r - expectedR;
        int dg = g - expectedG;
        int db = b - expectedB;
        return Math.sqrt(dr * dr + dg * dg + db * db) <= tolerance;
    }

    interface Sampler {
        int sample(float normalizedX, float normalizedY);
    }
}
