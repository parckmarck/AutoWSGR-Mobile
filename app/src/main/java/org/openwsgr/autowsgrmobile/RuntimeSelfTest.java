package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.IOException;

final class RuntimeSelfTest {
    private static final String MISSIONS = "autowsgr/data/missions.yaml";
    private static final String SHIPNAMES = "autowsgr/data/shipnames.yaml";
    private static final String TEMPLATE = "autowsgr/data/images/combat/proceed_540p.png";

    private RuntimeSelfTest() {
    }

    static Result run(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        try {
            String missions = store.readText(MISSIONS);
            append(detail, "missions.yaml chars=" + missions.length()
                    + ", has chapter data=" + missions.contains("daily"));
        } catch (IOException e) {
            ok = false;
            append(detail, "missions.yaml failed: " + e.getMessage());
        }

        try {
            String shipnames = store.readText(SHIPNAMES);
            append(detail, "shipnames.yaml chars=" + shipnames.length()
                    + ", has ships=" + shipnames.contains("列克星敦"));
        } catch (IOException e) {
            ok = false;
            append(detail, "shipnames.yaml failed: " + e.getMessage());
        }

        try {
            Bitmap bitmap = store.readBitmap(TEMPLATE);
            append(detail, "template decoded=" + bitmap.getWidth() + "x" + bitmap.getHeight());
            bitmap.recycle();
        } catch (IOException e) {
            ok = false;
            append(detail, "template decode failed: " + e.getMessage());
        }

        ScreenFrameStore.PixelSample sample = ScreenFrameStore.sampleNormalized(0.5f, 0.5f);
        if (sample.available) {
            append(detail, "center pixel rgba=(" + sample.r + "," + sample.g + ","
                    + sample.b + "," + sample.a + ")");
        } else {
            append(detail, "center pixel unavailable: " + sample.reason);
        }

        return new Result(ok, detail.toString());
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
            return "Runtime self-test: " + (ok ? "passed" : "failed");
        }
    }
}
