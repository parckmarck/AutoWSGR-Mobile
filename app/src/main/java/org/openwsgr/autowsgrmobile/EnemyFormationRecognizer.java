package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import java.io.IOException;
import java.util.ArrayList;

final class EnemyFormationRecognizer {
    private static final int BASE_WIDTH = 960;
    private static final int BASE_HEIGHT = 540;
    private static final float ROI_LEFT = 0.11f;
    private static final float ROI_TOP = 0.05f;
    private static final float ROI_RIGHT = 0.20f;
    private static final float ROI_BOTTOM = 0.15f;
    private static final FormationBackend FORMATION_BACKEND = new SignatureFormationBackend();

    private EnemyFormationRecognizer() {
    }

    static Detection detectFight(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Detection.unavailable("fight formation", "missing bitmap", "");
        }
        RoiSample sample = sampleRoi(bitmap);
        String formation = FORMATION_BACKEND.recognize(sample);
        if (formation.length() > 0) {
            return new Detection(
                    "fight formation",
                    true,
                    formation,
                    "roi-signature",
                    "signature OCR hook backend accepted, allowlist=单纵复轮型梯形横阵",
                    sample.detailLine()
            );
        }
        return Detection.unavailable(
                "fight formation",
                "OCR backend unavailable, signature OCR hook backend found no calibrated match",
                sample.detailLine()
        );
    }

    static String formationSignatureSummary() {
        return SignatureFormationBackend.summary();
    }

    static Detection fromRecognizedText(String source, String text) {
        String formation = normalizeFormation(text);
        if (formation.length() == 0) {
            return Detection.unavailable(source, "formation text not recognized", "");
        }
        return new Detection(source, true, formation, text == null ? "" : text.trim(), "recognized formation text", "");
    }

    static ProbeResult probe(Context context) {
        StringBuilder detail = new StringBuilder();
        Detection wheel = fromRecognizedText("synthetic formation OCR", "敌方轮型阵");
        Detection lineAbreast = fromRecognizedText("synthetic formation OCR", "单横");
        append(detail, "synthetic formation=" + wheel.detailLine());
        append(detail, "synthetic formation alias=" + lineAbreast.detailLine());

        boolean ok = wheel.reliable
                && "轮型阵".equals(wheel.formation)
                && lineAbreast.reliable
                && "单横阵".equals(lineAbreast.formation);

        Bitmap synthetic = syntheticFormationFrame();
        try {
            RoiSample sample = sampleRoi(synthetic);
            append(detail, "formation roi sampler=" + sample.detailLine());
            ok &= sample.available
                    && sample.rect.left == Math.round(ROI_LEFT * BASE_WIDTH)
                    && sample.rect.top == Math.round(ROI_TOP * BASE_HEIGHT)
                    && sample.rect.width() == Math.round((ROI_RIGHT - ROI_LEFT) * BASE_WIDTH)
                    && sample.rect.height() == Math.round((ROI_BOTTOM - ROI_TOP) * BASE_HEIGHT)
                    && sample.meanLuma > 80;

            Detection backend = detectFight(synthetic);
            append(detail, "formation backend=" + backend.detailLine());
            ok &= backend.reliable
                    && "轮型阵".equals(backend.formation)
                    && backend.reason.contains("signature OCR hook backend");
        } finally {
            synthetic.recycle();
        }

        try {
            ArrayList<String> formationItems = new ArrayList<>();
            formationItems.add("[轮型阵, 4]");
            formationItems.add("[单横阵, retreat]");
            EnemyRuleEngine formationRules = EnemyRuleEngine.fromFormationRuleItems(formationItems);
            EnemyRuleEngine.RuleAction wheelAction = formationRules.evaluateFormation(wheel.formation);
            EnemyRuleEngine.RuleAction lineAction = formationRules.evaluateFormation(lineAbreast.formation);

            ArrayList<String> enemyItems = new ArrayList<>();
            enemyItems.add("[AP != 1, retreat]");
            EnemyRuleEngine enemyRules = EnemyRuleEngine.fromEnemyRuleItems(enemyItems);
            EnemyRuleEngine.RuleAction enemyFallback = enemyRules.evaluate(
                    EnemyRuleEngine.EnemySnapshot.of("AP missing", "AP", 0)
            );

            CombatPlanPolicy plan = CombatPlanPolicy.load(context, CombatPlanPolicy.RULE_PLAN);
            CombatPlanPolicy.PlanDecision wheelDecision = plan.decisionForSpotRuleAction(
                    plan.routeIndexForNode("C", 0),
                    wheelAction
            );
            CombatPlanPolicy.PlanDecision retreatDecision = plan.decisionForSpotRuleAction(
                    plan.routeIndexForNode("A", 0),
                    lineAction
            );
            append(detail, "formation rule linkage=wheelAction=" + wheelAction.shortLabel()
                    + ", lineAction=" + lineAction.shortLabel()
                    + ", enemyFallback=" + enemyFallback.shortLabel()
                    + ", wheelDecision=" + wheelDecision.detail()
                    + ", retreatDecision=" + retreatDecision.detail());
            ok &= wheelAction.isFormation(4)
                    && lineAction.isRetreat()
                    && enemyFallback.isRetreat()
                    && "spot_fight".equals(wheelDecision.actionId)
                    && wheelDecision.carriesRuleFormation
                    && wheelDecision.ruleFormation == 4
                    && "spot_retreat".equals(retreatDecision.actionId);
        } catch (IOException e) {
            ok = false;
            append(detail, "formation rule linkage failed: " + e.getMessage());
        }

        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            append(detail, "live enemy formation unavailable: missing MediaProjection frame");
            return new ProbeResult(ok, detail.toString());
        }

        Bitmap gameFrame = null;
        try {
            gameFrame = GameFrameNormalizer.toLandscapeGameFrame(liveFrame);
            Detection live = detectFight(gameFrame);
            append(detail, "live formation=" + live.detailLine());
            return new ProbeResult(ok, detail.toString());
        } finally {
            if (gameFrame != null) {
                gameFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private static String normalizeFormation(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim().replace(" ", "").replace("_", "").replace("-", "");
        if (value.length() == 0) {
            return "";
        }
        if (value.contains("单纵")) {
            return "单纵阵";
        }
        if (value.contains("复纵")) {
            return "复纵阵";
        }
        if (value.contains("轮型")) {
            return "轮型阵";
        }
        if (value.contains("梯形")) {
            return "梯形阵";
        }
        if (value.contains("单横")) {
            return "单横阵";
        }
        return "";
    }

    private static RoiSample sampleRoi(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return RoiSample.unavailable("missing bitmap");
        }
        Rect rect = new Rect(
                Math.round(ROI_LEFT * bitmap.getWidth()),
                Math.round(ROI_TOP * bitmap.getHeight()),
                Math.round(ROI_RIGHT * bitmap.getWidth()),
                Math.round(ROI_BOTTOM * bitmap.getHeight())
        );
        rect.left = clamp(rect.left, 0, Math.max(0, bitmap.getWidth() - 1));
        rect.top = clamp(rect.top, 0, Math.max(0, bitmap.getHeight() - 1));
        rect.right = clamp(rect.right, rect.left + 1, bitmap.getWidth());
        rect.bottom = clamp(rect.bottom, rect.top + 1, bitmap.getHeight());

        int width = rect.width();
        int height = rect.height();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, rect.left, rect.top, width, height);

        int[] lumas = new int[pixels.length];
        long sum = 0L;
        long sumSquares = 0L;
        int dark = 0;
        int bright = 0;
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int r = (argb >> 16) & 0xff;
            int g = (argb >> 8) & 0xff;
            int b = argb & 0xff;
            int luma = (299 * r + 587 * g + 114 * b) / 1000;
            lumas[i] = luma;
            sum += luma;
            sumSquares += (long) luma * luma;
            if (luma < 80) {
                dark++;
            }
            if (luma > 180) {
                bright++;
            }
        }

        int mean = pixels.length == 0 ? 0 : Math.round(sum / (float) pixels.length);
        int stdev = 0;
        if (pixels.length > 0) {
            long averageSquares = sumSquares / pixels.length;
            int variance = (int) Math.max(0L, averageSquares - (long) mean * mean);
            stdev = (int) Math.round(Math.sqrt(variance));
        }
        int darkPermille = pixels.length == 0 ? 0 : Math.round(dark * 1000f / pixels.length);
        int brightPermille = pixels.length == 0 ? 0 : Math.round(bright * 1000f / pixels.length);
        return RoiSample.available(
                rect,
                bitmap.getWidth(),
                bitmap.getHeight(),
                mean,
                stdev,
                darkPermille,
                brightPermille,
                averageHash64(lumas, width, height)
        );
    }

    private static Bitmap syntheticFormationFrame() {
        Bitmap bitmap = Bitmap.createBitmap(BASE_WIDTH, BASE_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(gray(28));
        paintSyntheticFormationRoi(bitmap);
        return bitmap;
    }

    static void paintSyntheticFormationRoi(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        Rect rect = new Rect(
                Math.round(ROI_LEFT * bitmap.getWidth()),
                Math.round(ROI_TOP * bitmap.getHeight()),
                Math.round(ROI_RIGHT * bitmap.getWidth()),
                Math.round(ROI_BOTTOM * bitmap.getHeight())
        );
        for (int y = rect.top; y < rect.bottom; y++) {
            for (int x = rect.left; x < rect.right; x++) {
                int stripe = ((x - rect.left) / 6 + (y - rect.top) / 4) % 2 == 0 ? 74 : 150;
                bitmap.setPixel(x, y, gray(stripe));
            }
        }
    }

    private static long averageHash64(int[] lumas, int width, int height) {
        if (lumas.length == 0 || width <= 0 || height <= 0) {
            return 0L;
        }
        int[] grid = new int[64];
        int total = 0;
        for (int gy = 0; gy < 8; gy++) {
            int y = Math.min(height - 1, (gy * height + height / 2) / 8);
            for (int gx = 0; gx < 8; gx++) {
                int x = Math.min(width - 1, (gx * width + width / 2) / 8);
                int value = lumas[y * width + x];
                grid[gy * 8 + gx] = value;
                total += value;
            }
        }
        int average = total / grid.length;
        long hash = 0L;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] >= average) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    private static int gray(int value) {
        int channel = clamp(value, 0, 255);
        return 0xff000000 | (channel << 16) | (channel << 8) | channel;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String percent(int permille) {
        int tenths = permille / 10;
        return (tenths / 10) + "." + (tenths % 10) + "%";
    }

    private static String hashString(long hash) {
        String value = Long.toHexString(hash);
        StringBuilder builder = new StringBuilder();
        for (int i = value.length(); i < 16; i++) {
            builder.append('0');
        }
        builder.append(value);
        return builder.toString();
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    static final class Detection {
        final String source;
        final boolean reliable;
        final String formation;
        final String rawText;
        final String reason;
        final String sampleSummary;

        Detection(String source, boolean reliable, String formation, String rawText, String reason, String sampleSummary) {
            this.source = source;
            this.reliable = reliable;
            this.formation = formation;
            this.rawText = rawText;
            this.reason = reason;
            this.sampleSummary = sampleSummary;
        }

        static Detection unavailable(String source, String reason, String sampleSummary) {
            return new Detection(source, false, "", "", reason, sampleSummary);
        }

        String detailLine() {
            return "source=" + source
                    + ", reliable=" + reliable
                    + ", formation=" + (formation.length() == 0 ? "none" : formation)
                    + ", raw=" + (rawText.length() == 0 ? "none" : rawText)
                    + ", reason=" + reason
                    + (sampleSummary.length() == 0 ? "" : ", roiSample=" + sampleSummary);
        }
    }

    static final class ProbeResult {
        final boolean ok;
        final String detail;

        ProbeResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Enemy formation probe: " + (ok ? "passed" : "failed");
        }
    }

    private interface FormationBackend {
        String recognize(RoiSample sample);
    }

    private static final class SignatureFormationBackend implements FormationBackend {
        private static final long WHEEL_HASH = 0xe0e01f1f1fe0e0e0L;
        private static final int MEAN_MIN = 100;
        private static final int MEAN_MAX = 125;
        private static final int STDEV_MIN = 30;
        private static final int STDEV_MAX = 46;
        private static final int HASH_DISTANCE_MAX = 6;

        @Override
        public String recognize(RoiSample sample) {
            if (sample == null || !sample.available) {
                return "";
            }
            int hashDistance = Long.bitCount(sample.hash64 ^ WHEEL_HASH);
            boolean wheelLike = sample.meanLuma >= MEAN_MIN
                    && sample.meanLuma <= MEAN_MAX
                    && sample.stdevLuma >= STDEV_MIN
                    && sample.stdevLuma <= STDEV_MAX
                    && hashDistance <= HASH_DISTANCE_MAX;
            return wheelLike ? "轮型阵" : "";
        }

        static String summary() {
            return "wheelHash=" + hashString(WHEEL_HASH)
                    + ", mean=" + MEAN_MIN + "-" + MEAN_MAX
                    + ", sd=" + STDEV_MIN + "-" + STDEV_MAX
                    + ", hashDistance<=" + HASH_DISTANCE_MAX;
        }
    }

    static final class RoiSample {
        final boolean available;
        final String reason;
        final Rect rect;
        final int frameWidth;
        final int frameHeight;
        final int meanLuma;
        final int stdevLuma;
        final int darkPermille;
        final int brightPermille;
        final long hash64;

        private RoiSample(
                boolean available,
                String reason,
                Rect rect,
                int frameWidth,
                int frameHeight,
                int meanLuma,
                int stdevLuma,
                int darkPermille,
                int brightPermille,
                long hash64
        ) {
            this.available = available;
            this.reason = reason;
            this.rect = rect;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.meanLuma = meanLuma;
            this.stdevLuma = stdevLuma;
            this.darkPermille = darkPermille;
            this.brightPermille = brightPermille;
            this.hash64 = hash64;
        }

        static RoiSample available(
                Rect rect,
                int frameWidth,
                int frameHeight,
                int meanLuma,
                int stdevLuma,
                int darkPermille,
                int brightPermille,
                long hash64
        ) {
            return new RoiSample(true, "", rect, frameWidth, frameHeight, meanLuma, stdevLuma, darkPermille, brightPermille, hash64);
        }

        static RoiSample unavailable(String reason) {
            return new RoiSample(false, reason, new Rect(0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0L);
        }

        String detailLine() {
            if (!available) {
                return "unavailable=" + reason;
            }
            return "frame=" + frameWidth + "x" + frameHeight
                    + ", normalizedFrom=" + BASE_WIDTH + "x" + BASE_HEIGHT
                    + ", roi=(" + rect.left + "," + rect.top + ")-(" + rect.right + "," + rect.bottom + ")"
                    + ", size=" + rect.width() + "x" + rect.height()
                    + ", mean=" + meanLuma
                    + ", sd=" + stdevLuma
                    + ", dark=" + percent(darkPermille)
                    + ", bright=" + percent(brightPermille)
                    + ", hash=" + hashString(hash64);
        }
    }
}
