package org.openwsgr.autowsgrmobile;

/**
 * 战役面板像素签名与坐标，对齐原版 {@code autowsgr/ui/map/data.py}。
 *
 * <ul>
 *   <li>{@code CLICK_DIFFICULTY = (0.800, 0.130)} 难度切换按钮</li>
 *   <li>{@code DIFFICULTY_EASY_COLOR = (29,139,234)} 蓝 / {@code DIFFICULTY_HARD_COLOR = (141,46,52)} 红</li>
 *   <li>{@code CLICK_PANEL[BATTLE] = (0.5750, 0.0458)} 战役面板</li>
 *   <li>{@code CAMPAIGN_POSITIONS{1..5}} 5 个战役入口</li>
 * </ul>
 *
 * <p>难度识别同原版 {@code recognize_difficulty}：切换图标为蓝色时当前为困难(hard)、红色时当前为简单(easy)。
 */
final class CampaignSignatures {
    static final float BATTLE_PANEL_X = 0.5750f;
    static final float BATTLE_PANEL_Y = 0.0458f;
    static final float CLICK_DIFFICULTY_X = 0.800f;
    static final float CLICK_DIFFICULTY_Y = 0.130f;

    private static final int EASY_R = 29;
    private static final int EASY_G = 139;
    private static final int EASY_B = 234;
    private static final int HARD_R = 141;
    private static final int HARD_G = 46;
    private static final int HARD_B = 52;
    private static final double DIFFICULTY_TOLERANCE = 50.0;
    private static final float NEIGHBOR_DELTA = 0.004f;

    static final int CAMPAIGN_COUNT = 5;
    private static final float[][] CAMPAIGN_POSITIONS = new float[][]{
            {0.17f, 0.5f},
            {0.34f, 0.5f},
            {0.51f, 0.5f},
            {0.68f, 0.5f},
            {0.85f, 0.5f}
    };

    private CampaignSignatures() {
    }

    static float campaignX(int mapIndex) {
        return CAMPAIGN_POSITIONS[mapIndex - 1][0];
    }

    static float campaignY(int mapIndex) {
        return CAMPAIGN_POSITIONS[mapIndex - 1][1];
    }

    /**
     * 识别当前难度。
     *
     * @return {@link CampaignCatalog#HARD} / {@link CampaignCatalog#EASY}，无法识别返回 {@code null}。
     */
    static String recognizeDifficulty(Sampler sampler) {
        if (sampler == null) {
            return null;
        }
        if (nearAny(sampler, CLICK_DIFFICULTY_X, CLICK_DIFFICULTY_Y, EASY_R, EASY_G, EASY_B)) {
            return CampaignCatalog.HARD;
        }
        if (nearAny(sampler, CLICK_DIFFICULTY_X, CLICK_DIFFICULTY_Y, HARD_R, HARD_G, HARD_B)) {
            return CampaignCatalog.EASY;
        }
        return null;
    }

    private static boolean nearAny(
            Sampler sampler,
            float centerX,
            float centerY,
            int expectedR,
            int expectedG,
            int expectedB
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
            if (near(sampler.sample(x, y), expectedR, expectedG, expectedB, DIFFICULTY_TOLERANCE)) {
                return true;
            }
        }
        return false;
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

    private static float clampUnit(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    interface Sampler {
        int sample(float normalizedX, float normalizedY);
    }
}
