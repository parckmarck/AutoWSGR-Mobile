package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;

import org.openwsgr.autowsgrmobile.vision.PixelRule;
import org.openwsgr.autowsgrmobile.vision.PixelSignature;
import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;
import org.openwsgr.autowsgrmobile.vision.VisionColor;

import java.io.IOException;
import java.util.Arrays;

final class VisionSelfTest {
    private static final String TEMPLATE = "autowsgr/data/images/combat/proceed_540p.png";

    private VisionSelfTest() {
    }

    static Result run(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        try {
            Bitmap template = store.readBitmap(TEMPLATE);
            try {
                int centerArgb = template.getPixel(template.getWidth() / 2, template.getHeight() / 2);
                PixelSignature signature = new PixelSignature(
                        "template-center",
                        Arrays.asList(new PixelRule(0.5f, 0.5f, VisionColor.fromArgb(centerArgb), 0.0))
                );
                boolean pixelOk = signature.matchesAll(template);
                append(detail, "pixel signature self-match=" + pixelOk);
                ok = ok && pixelOk;

                double exactScore = TemplateMatcher.similarityAt(template, template, 0, 0);
                TemplateMatcher.MatchResult result = TemplateMatcher.findBest(template, template, 1);
                append(detail, "template exact score=" + formatScore(exactScore)
                        + ", best=(" + result.x + "," + result.y + "), score=" + formatScore(result.score));
                ok = ok && result.valid && result.x == 0 && result.y == 0 && result.score >= 0.999;
            } finally {
                template.recycle();
            }
        } catch (IOException e) {
            ok = false;
            append(detail, "vision template failed: " + e.getMessage());
        }

        ScreenFrameStore.PixelSample sample = ScreenFrameStore.sampleNormalized(0.5f, 0.5f);
        if (sample.available) {
            append(detail, "live frame pixel=(" + sample.r + "," + sample.g + ","
                    + sample.b + "," + sample.a + ")");
        } else {
            append(detail, "live frame pixel unavailable: " + sample.reason);
        }

        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame != null) {
            try {
                int cropWidth = Math.min(64, liveFrame.getWidth());
                int cropHeight = Math.min(64, liveFrame.getHeight());
                int cropX = Math.max(0, (liveFrame.getWidth() - cropWidth) / 2);
                int cropY = Math.max(0, (liveFrame.getHeight() - cropHeight) / 2);
                Bitmap crop = Bitmap.createBitmap(liveFrame, cropX, cropY, cropWidth, cropHeight);
                try {
                    double liveScore = TemplateMatcher.similarityAt(liveFrame, crop, cropX, cropY);
                    append(detail, "live crop score=" + formatScore(liveScore)
                            + " at (" + cropX + "," + cropY + "), size=" + cropWidth + "x" + cropHeight);
                    ok = ok && liveScore >= 0.999;
                } finally {
                    crop.recycle();
                }
            } finally {
                liveFrame.recycle();
            }
        } else {
            append(detail, "live crop unavailable: no frame");
        }

        return new Result(ok, detail.toString());
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
            return "Vision self-test: " + (ok ? "passed" : "failed");
        }
    }
}
