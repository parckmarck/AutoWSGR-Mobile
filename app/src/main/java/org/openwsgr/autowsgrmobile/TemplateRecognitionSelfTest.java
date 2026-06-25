package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.io.IOException;
import java.util.List;

final class TemplateRecognitionSelfTest {
    private TemplateRecognitionSelfTest() {
    }

    static Result run(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        append(detail, "mapped keys=" + NativeTemplateKey.values().length
                + ", mapped template files=" + NativeTemplateKey.mappedTemplateFileCount());
        ok = runSingleKeyProbe(store, detail) && ok;
        ok = runGroupKeyProbe(store, detail) && ok;

        return new Result(ok, detail.toString());
    }

    private static boolean runSingleKeyProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        List<NativeTemplateScanner.Template> templates = null;
        Bitmap synthetic = null;
        try {
            templates = NativeTemplateKey.PROCEED.loadTemplates(store);
            NativeTemplateScanner.Template template = templates.get(0);
            int x = 40;
            int y = 24;
            synthetic = syntheticScreen(template.bitmap, x, y);
            NativeTemplateScanner.ScanResult result =
                    NativeTemplateScanner.findAny(synthetic, templates, 4, 0.999);
            append(detail, "findAny proceed=" + formatResult(result));
            return result.valid
                    && "proceed".equals(result.keyId)
                    && result.x == x
                    && result.y == y
                    && result.score >= 0.999;
        } catch (IOException e) {
            append(detail, "findAny proceed failed: " + e.getMessage());
            return false;
        } finally {
            if (synthetic != null) {
                synthetic.recycle();
            }
            NativeTemplateScanner.recycleAll(templates);
        }
    }

    private static boolean runGroupKeyProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        List<NativeTemplateScanner.Template> templates = null;
        Bitmap synthetic = null;
        try {
            templates = NativeTemplateKey.GET_SHIP_OR_ITEM.loadTemplates(store);
            NativeTemplateScanner.Template target = templates.get(1);
            int x = 32;
            int y = 28;
            synthetic = syntheticScreen(target.bitmap, x, y);
            NativeTemplateScanner.ScanResult result =
                    NativeTemplateScanner.findAny(synthetic, templates, 4, 0.999);
            append(detail, "findAny get_ship_or_item=" + formatResult(result));
            return result.valid
                    && "get_ship_or_item".equals(result.keyId)
                    && result.path.endsWith("combat/get_item_540p.png")
                    && result.x == x
                    && result.y == y
                    && result.score >= 0.999;
        } catch (IOException e) {
            append(detail, "findAny get_ship_or_item failed: " + e.getMessage());
            return false;
        } finally {
            if (synthetic != null) {
                synthetic.recycle();
            }
            NativeTemplateScanner.recycleAll(templates);
        }
    }

    private static Bitmap syntheticScreen(Bitmap template, int x, int y) {
        int width = template.getWidth() + x + 48;
        int height = template.getHeight() + y + 48;
        Bitmap screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(screen);
        canvas.drawColor(0xFF101820);
        canvas.drawBitmap(template, x, y, null);
        return screen;
    }

    private static String formatResult(NativeTemplateScanner.ScanResult result) {
        String path = result.path;
        int index = path.indexOf("images/");
        if (index >= 0) {
            path = path.substring(index + "images/".length());
        }
        if (!result.valid) {
            return "miss reason=" + result.reason + ", best=" + result.keyId
                    + "@" + path + ", score=" + formatScore(result.score);
        }
        return "hit key=" + result.keyId + ", path=" + path + ", at=("
                + result.x + "," + result.y + "), score=" + formatScore(result.score);
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

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Template recognition self-test: " + (ok ? "passed" : "failed");
        }
    }
}
