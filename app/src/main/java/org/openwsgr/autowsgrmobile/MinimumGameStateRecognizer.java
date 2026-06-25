package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MinimumGameStateRecognizer {
    private static final int COMBAT_STRIDE = 24;
    private static final int HOME_STRIDE = 16;
    private static final double COMBAT_THRESHOLD = 0.999;
    private static final double HOME_THRESHOLD = MinimumGameStateClassifier.HOME_THRESHOLD;
    private static final int HOME_SIGNATURE_MIN_MATCHES = 3;
    private static final float DEFAULT_HOME_SORTIE_X = 0.9444f;
    private static final float DEFAULT_HOME_SORTIE_Y = 0.9556f;
    private static final String[] HOME_ANCHORS = new String[]{
            "autowsgr/data/images/ui/map.png",
            "autowsgr/data/images/ui/mission.png",
            "autowsgr/data/images/ui/build.png",
            "autowsgr/data/images/ui/friend.png",
            "autowsgr/data/images/ui/intensify.png"
    };
    private static final NativeCombatPhase[] BLOCKER_PHASES = new NativeCombatPhase[]{
            NativeCombatPhase.DOCK_FULL,
            NativeCombatPhase.FLAGSHIP_SEVERE_DAMAGE
    };
    private static final float[][] TAB_PROBES = new float[][]{
            {0.1539f, 0.0472f},
            {0.2719f, 0.0625f},
            {0.4039f, 0.0528f},
            {0.5359f, 0.0500f},
            {0.6641f, 0.0500f}
    };
    private static final int TAB_BLUE_R = 15;
    private static final int TAB_BLUE_G = 132;
    private static final int TAB_BLUE_B = 228;
    private static final double TAB_BLUE_TOLERANCE = 35.0;
    private static final int TAB_DARK_MAX = 80;
    private static final float[][] HOME_SIGNATURE_RULES = new float[][]{
            {0.5417f, 0.0346f, 245f, 245f, 246f, 45f},
            {0.9860f, 0.8470f, 255f, 89f, 45f, 80f},
            {0.8610f, 0.9210f, 58f, 62f, 74f, 70f},
            {0.9650f, 0.9580f, 51f, 51f, 51f, 65f},
            {0.0500f, 0.1630f, 98f, 79f, 82f, 80f},
            {0.0514f, 0.9210f, 142f, 141f, 142f, 80f}
    };

    private final Context appContext;
    private final AutoWsgrAssetStore store;

    MinimumGameStateRecognizer(Context context) {
        this.appContext = context.getApplicationContext();
        this.store = new AutoWsgrAssetStore(appContext);
    }

    Result recognize() {
        Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            MinimumGameStateClassifier.Result state = MinimumGameStateClassifier.classify(
                    new MinimumGameStateClassifier.Input(false, false, false, false, false, false, 0.0)
            );
            return new Result(state, "", "", null, DEFAULT_HOME_SORTIE_X, DEFAULT_HOME_SORTIE_Y);
        }
        GameFrameViewport viewport = GameFrameViewport.fromFrame(source.getWidth(), source.getHeight());
        Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            boolean targetForeground = TargetGameLauncher.isTargetForeground();
            NativeCombatRecognizer.Recognition blocker = NativeCombatRecognizer.identifyCurrent(
                    frame,
                    store,
                    BLOCKER_PHASES,
                    COMBAT_STRIDE,
                    COMBAT_THRESHOLD
            );
            NativeCombatRecognizer.Recognition combat = NativeCombatRecognizer.identifyCurrent(
                    frame,
                    store,
                    GuardedCombatStepper.guardedPhases(),
                    COMBAT_STRIDE,
                    COMBAT_THRESHOLD
            );
            NativeCombatRecognizer.Recognition map = NativeCombatRecognizer.identifyCurrent(
                    frame,
                    store,
                    new NativeCombatPhase[]{NativeCombatPhase.MAP_PAGE},
                    COMBAT_STRIDE,
                    COMBAT_THRESHOLD
            );
            HomeScan home = scanHome(frame);
            PageProbe homeSignature = isHomeSignature(frame);
            PageProbe startScreen = isStartScreen(frame);
            boolean mapMatched = map.matched || isTabbedMapPage(frame);
            boolean homeMatched = home.matched || homeSignature.matched;
            double homeScore = homeSignature.matched
                    ? Math.max(home.score, HOME_THRESHOLD)
                    : home.score;
            MinimumGameStateClassifier.Result classified = MinimumGameStateClassifier.classify(
                    new MinimumGameStateClassifier.Input(
                            true,
                            targetForeground,
                            homeMatched,
                            blocker.matched,
                            combat.matched,
                            mapMatched,
                            startScreen.matched,
                            homeScore
                    )
            );
            NativeCombatPhase phase = blocker.matched ? blocker.phase : combat.phase;
            return new Result(
                    classified,
                    formatRecognition("blocker", blocker) + "\n" + formatRecognition("combat", combat),
                    viewport.detail()
                            + "\n" + formatRecognition("map", map)
                            + "\nmapTabbedSignature=" + mapMatched
                            + "\nbattlePrepSignature=" + isBattlePreparation(frame).matched
                            + "\n" + startScreen.detail
                            + "\n" + homeSignature.detail
                    + "\n" + home.detail,
                    phase,
                    home.tapX,
                    home.tapY
            );
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    PageProbe battlePreparationProbe() {
        return battleAdvancePageProbe(MinimumBattleAdvancePolicy.Page.BATTLE_PREPARATION);
    }

    PageProbe battleAdvancePageProbe(final MinimumBattleAdvancePolicy.Page page) {
        Bitmap source = ScreenFrameStore.latestBitmap();
        if (source == null) {
            return new PageProbe(false, "battlePage=" + page + ", missing-frame");
        }
        Bitmap frame = GameFrameNormalizer.toLandscapeGameFrame(source);
        try {
            return battlePageProbe(frame, page);
        } finally {
            frame.recycle();
            source.recycle();
        }
    }

    private HomeScan scanHome(Bitmap frame) {
        List<NativeTemplateScanner.Template> templates = new ArrayList<>();
        try {
            for (String path : HOME_ANCHORS) {
                templates.add(new NativeTemplateScanner.Template(path, path, store.readBitmap(path)));
            }
            NativeTemplateScanner.ScanResult match = NativeTemplateScanner.findAny(
                    frame,
                    templates,
                    HOME_STRIDE,
                    HOME_THRESHOLD
            );
            float tapX = DEFAULT_HOME_SORTIE_X;
            float tapY = DEFAULT_HOME_SORTIE_Y;
            if (match.valid) {
                NativeTemplateScanner.Template template = templateForPath(templates, match.path);
                if (template != null) {
                    tapX = normalized(match.x + template.bitmap.getWidth() / 2.0f, frame.getWidth());
                    tapY = normalized(match.y + template.bitmap.getHeight() / 2.0f, frame.getHeight());
                }
            }
            return new HomeScan(match.valid, match.score, tapX, tapY, formatScan("home", match));
        } catch (IOException e) {
            return new HomeScan(false, 0.0, DEFAULT_HOME_SORTIE_X, DEFAULT_HOME_SORTIE_Y, "homeScan=failed:" + e.getMessage());
        } finally {
            NativeTemplateScanner.recycleAll(templates);
        }
    }

    private static boolean isTabbedMapPage(Bitmap frame) {
        int blueCount = 0;
        int darkCount = 0;
        for (float[] probe : TAB_PROBES) {
            int pixel = sample(frame, probe[0], probe[1]);
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            if (near(r, g, b, TAB_BLUE_R, TAB_BLUE_G, TAB_BLUE_B, TAB_BLUE_TOLERANCE)) {
                blueCount++;
            } else if (Math.max(r, Math.max(g, b)) < TAB_DARK_MAX) {
                darkCount++;
            }
        }
        return blueCount == 1 && darkCount == TAB_PROBES.length - 1;
    }

    private static PageProbe isBattlePreparation(Bitmap frame) {
        return battlePageProbe(frame, MinimumBattleAdvancePolicy.Page.BATTLE_PREPARATION);
    }

    private static PageProbe isStartScreen(final Bitmap frame) {
        MinimumStartScreenSignatures.Result result = MinimumStartScreenSignatures.match(
                new MinimumStartScreenSignatures.Sampler() {
                    @Override
                    public int sample(float normalizedX, float normalizedY) {
                        return MinimumGameStateRecognizer.sample(frame, normalizedX, normalizedY);
                    }
                }
        );
        return new PageProbe(result.matched, result.detail);
    }

    private static PageProbe battlePageProbe(
            final Bitmap frame,
            MinimumBattleAdvancePolicy.Page page
    ) {
        MinimumBattlePageSignatures.Result result = MinimumBattlePageSignatures.match(
                page,
                new MinimumBattlePageSignatures.Sampler() {
                    @Override
                    public int sample(float normalizedX, float normalizedY) {
                        return MinimumGameStateRecognizer.sample(frame, normalizedX, normalizedY);
                    }
                }
        );
        return new PageProbe(result.matched, result.detail);
    }

    private static PageProbe isHomeSignature(Bitmap frame) {
        StringBuilder detail = new StringBuilder("homeSignature=");
        int matched = 0;
        for (int i = 0; i < HOME_SIGNATURE_RULES.length; i++) {
            float[] rule = HOME_SIGNATURE_RULES[i];
            int pixel = sample(frame, rule[0], rule[1]);
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
        detail.append(", matched=").append(matched).append('/').append(HOME_SIGNATURE_RULES.length);
        return new PageProbe(homeSignatureMatchedForCount(matched), detail.toString());
    }

    static boolean homeSignatureMatchedForCount(int matched) {
        return matched >= HOME_SIGNATURE_MIN_MATCHES;
    }

    private static int sample(Bitmap frame, float normalizedX, float normalizedY) {
        int x = Math.min(frame.getWidth() - 1, Math.max(0, Math.round(normalizedX * (frame.getWidth() - 1))));
        int y = Math.min(frame.getHeight() - 1, Math.max(0, Math.round(normalizedY * (frame.getHeight() - 1))));
        return frame.getPixel(x, y);
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

    private static NativeTemplateScanner.Template templateForPath(
            List<NativeTemplateScanner.Template> templates,
            String path
    ) {
        for (NativeTemplateScanner.Template template : templates) {
            if (template.path.equals(path)) {
                return template;
            }
        }
        return null;
    }

    private static float normalized(float value, int size) {
        if (size <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value / size));
    }

    private static String formatRecognition(String label, NativeCombatRecognizer.Recognition recognition) {
        return label + "=" + GuardedCombatStepper.formatRecognition(recognition);
    }

    private static String formatScan(String label, NativeTemplateScanner.ScanResult scan) {
        return label + "=" + (scan.valid ? "match" : "miss")
                + ", reason=" + scan.reason
                + ", key=" + scan.keyId
                + ", score=" + String.format(Locale.US, "%.4f", scan.score);
    }

    static final class Result {
        final MinimumGameStateClassifier.Result state;
        final String combatDetail;
        final String navigationDetail;
        final NativeCombatPhase phase;
        final float homeMapTapX;
        final float homeMapTapY;

        Result(
                MinimumGameStateClassifier.Result state,
                String combatDetail,
                String navigationDetail,
                NativeCombatPhase phase,
                float homeMapTapX,
                float homeMapTapY
        ) {
            this.state = state;
            this.combatDetail = combatDetail;
            this.navigationDetail = navigationDetail;
            this.phase = phase;
            this.homeMapTapX = homeMapTapX;
            this.homeMapTapY = homeMapTapY;
        }
    }

    static final class PageProbe {
        final boolean matched;
        final String detail;

        PageProbe(boolean matched, String detail) {
            this.matched = matched;
            this.detail = detail;
        }
    }

    private static final class HomeScan {
        final boolean matched;
        final double score;
        final float tapX;
        final float tapY;
        final String detail;

        HomeScan(boolean matched, double score, float tapX, float tapY, String detail) {
            this.matched = matched;
            this.score = score;
            this.tapX = tapX;
            this.tapY = tapY;
            this.detail = detail;
        }
    }
}
