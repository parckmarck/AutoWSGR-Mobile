package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.openwsgr.autowsgrmobile.vision.PixelRule;
import org.openwsgr.autowsgrmobile.vision.PixelSignature;

import java.io.IOException;
import java.util.List;

final class CombatPhaseSelfTest {
    private CombatPhaseSelfTest() {
    }

    static Result run(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        append(detail, "combat phases=" + NativeCombatPhase.values().length
                + ", template-backed=" + NativeCombatPhase.templateBackedCount()
                + ", pixel-backed=" + NativeCombatPhase.pixelBackedCount()
                + ", recognizable=" + NativeCombatPhase.recognizableCount());
        ok = runResultProbe(store, detail) && ok;
        ok = runDropProbe(store, detail) && ok;
        ok = runFormationPixelProbe(store, detail) && ok;
        ok = runEventMapBaseProbe(store, detail) && ok;
        ok = runEventMapOverlayProbe(store, detail) && ok;
        ok = runBlankPixelMissProbe(store, detail) && ok;

        return new Result(ok, detail.toString());
    }

    private static boolean runResultProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = null;
        Bitmap template = null;
        try {
            template = loadFirstTemplate(store, NativeTemplateKey.RESULT);
            int x = 36;
            int y = 28;
            synthetic = syntheticScreen(template, x, y);
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.PROCEED,
                            NativeCombatPhase.RESULT,
                            NativeCombatPhase.GET_SHIP
                    },
                    4,
                    0.999
            );
            append(detail, "identify result=" + formatRecognition(recognition));
            return recognition.matched
                    && recognition.phase == NativeCombatPhase.RESULT
                    && recognition.x == x
                    && recognition.y == y
                    && recognition.score >= 0.999;
        } catch (IOException e) {
            append(detail, "identify result failed: " + e.getMessage());
            return false;
        } finally {
            recycle(template);
            recycle(synthetic);
        }
    }

    private static boolean runDropProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = null;
        Bitmap template = null;
        try {
            template = store.readBitmap("autowsgr/data/images/combat/get_item_540p.png");
            int x = 40;
            int y = 32;
            synthetic = syntheticScreen(template, x, y);
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.RESULT,
                            NativeCombatPhase.GET_SHIP,
                            NativeCombatPhase.MAP_PAGE
                    },
                    4,
                    0.999
            );
            append(detail, "identify drop=" + formatRecognition(recognition));
            return recognition.matched
                    && recognition.phase == NativeCombatPhase.GET_SHIP
                    && recognition.templateKeyId.equals("get_ship_or_item")
                    && recognition.path.endsWith("combat/get_item_540p.png")
                    && recognition.x == x
                    && recognition.y == y
                    && recognition.score >= 0.999;
        } catch (IOException e) {
            append(detail, "identify drop failed: " + e.getMessage());
            return false;
        } finally {
            recycle(template);
            recycle(synthetic);
        }
    }

    private static boolean runFormationPixelProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = syntheticPixelScreen();
        try {
            paintSignature(
                    synthetic,
                    NativePhasePixelSignatures.testSignature(
                            NativePhasePixelSignatures.Kind.CHOOSE_FORMATION,
                            0
                    )
            );
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.FORMATION,
                            NativeCombatPhase.EVENT_MAP_PAGE
                    },
                    4,
                    0.999
            );
            append(detail, "identify formation=" + formatRecognition(recognition));
            return recognition.matched
                    && recognition.phase == NativeCombatPhase.FORMATION
                    && recognition.signatureType.equals("pixel")
                    && recognition.pixelSignatureName.equals("choose_formation")
                    && recognition.matchedRules == 6
                    && recognition.totalRules == 6;
        } finally {
            recycle(synthetic);
        }
    }

    private static boolean runEventMapBaseProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = syntheticPixelScreen();
        try {
            paintSignature(
                    synthetic,
                    NativePhasePixelSignatures.testSignature(
                            NativePhasePixelSignatures.Kind.EVENT_MAP_PAGE,
                            0
                    )
            );
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.FORMATION,
                            NativeCombatPhase.EVENT_MAP_PAGE
                    },
                    4,
                    0.999
            );
            append(detail, "identify event base=" + formatRecognition(recognition));
            return recognition.matched
                    && recognition.phase == NativeCombatPhase.EVENT_MAP_PAGE
                    && recognition.signatureType.equals("pixel")
                    && recognition.pixelSignatureName.equals("event_map_page_composite")
                    && recognition.matchedPixelSignatureName.equals("event_map_page")
                    && recognition.matchedRules == 3
                    && recognition.totalRules == 7;
        } finally {
            recycle(synthetic);
        }
    }

    private static boolean runEventMapOverlayProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = syntheticPixelScreen();
        try {
            paintSignature(
                    synthetic,
                    NativePhasePixelSignatures.testSignature(
                            NativePhasePixelSignatures.Kind.EVENT_MAP_PAGE,
                            1
                    )
            );
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.EVENT_MAP_PAGE
                    },
                    4,
                    0.999
            );
            append(detail, "identify event overlay=" + formatRecognition(recognition));
            return recognition.matched
                    && recognition.phase == NativeCombatPhase.EVENT_MAP_PAGE
                    && recognition.signatureType.equals("pixel")
                    && recognition.pixelSignatureName.equals("event_map_page_composite")
                    && recognition.matchedPixelSignatureName.equals("event_map_overlay")
                    && recognition.matchedRules == 4
                    && recognition.totalRules == 7;
        } finally {
            recycle(synthetic);
        }
    }

    private static boolean runBlankPixelMissProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        Bitmap synthetic = syntheticPixelScreen();
        try {
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    synthetic,
                    store,
                    new NativeCombatPhase[]{
                            NativeCombatPhase.START_FIGHT,
                            NativeCombatPhase.FORMATION,
                            NativeCombatPhase.EVENT_MAP_PAGE
                    },
                    4,
                    0.999
            );
            append(detail, "blank pixel candidates=" + formatRecognition(recognition));
            return !recognition.matched;
        } finally {
            recycle(synthetic);
        }
    }

    private static Bitmap loadFirstTemplate(AutoWsgrAssetStore store, NativeTemplateKey key) throws IOException {
        List<NativeTemplateScanner.Template> templates = null;
        try {
            templates = key.loadTemplates(store);
            NativeTemplateScanner.Template first = templates.get(0);
            return first.bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            NativeTemplateScanner.recycleAll(templates);
        }
    }

    private static Bitmap syntheticScreen(Bitmap template, int x, int y) {
        int width = template.getWidth() + x + 48;
        int height = template.getHeight() + y + 48;
        Bitmap screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(screen);
        canvas.drawColor(0xFF182028);
        canvas.drawBitmap(template, x, y, null);
        return screen;
    }

    private static Bitmap syntheticPixelScreen() {
        Bitmap screen = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        screen.eraseColor(0xFF182028);
        return screen;
    }

    private static void paintSignature(Bitmap screen, PixelSignature signature) {
        for (PixelRule rule : signature.rules) {
            screen.setPixel(rule.pixelX(screen), rule.pixelY(screen), rule.expected.toArgb());
        }
    }

    private static String formatRecognition(NativeCombatRecognizer.Recognition recognition) {
        if (!recognition.matched) {
            return "miss reason=" + recognition.reason + ", bestPhase="
                    + (recognition.phase == null ? "none" : recognition.phase.id)
                    + ", score=" + formatScore(recognition.score);
        }
        if ("pixel".equals(recognition.signatureType)) {
            return "hit phase=" + recognition.phase.id
                    + ", type=pixel"
                    + ", signature=" + recognition.pixelSignatureName
                    + ", matched=" + recognition.matchedPixelSignatureName
                    + ", rules=" + recognition.matchedRules + "/" + recognition.totalRules
                    + ", variants=" + recognition.signatureVariants;
        }
        return "hit phase=" + recognition.phase.id
                + ", type=template"
                + ", key=" + recognition.templateKeyId
                + ", path=" + shortImagePath(recognition.path)
                + ", at=(" + recognition.x + "," + recognition.y + ")"
                + ", score=" + formatScore(recognition.score);
    }

    private static String shortImagePath(String path) {
        int index = path.indexOf("images/");
        if (index >= 0) {
            return path.substring(index + "images/".length());
        }
        return path;
    }

    private static String formatScore(double value) {
        return String.format(java.util.Locale.US, "%.4f", value);
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }
    }

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Combat phase self-test: " + (ok ? "passed" : "failed");
        }
    }
}
