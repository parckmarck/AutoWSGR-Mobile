package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

final class GameFrameNormalizer {
    static final int NORMALIZED_WIDTH = 960;
    static final int NORMALIZED_HEIGHT = 540;

    private GameFrameNormalizer() {
    }

    static Bitmap toLandscapeGameFrame(Bitmap source) {
        GameFrameViewport viewport = GameFrameViewport.fromFrame(source.getWidth(), source.getHeight());
        // Bitmap.createBitmap may return `source` itself when the crop covers the whole frame
        // (e.g. an exact 16:9 capture). Track aliasing so we never recycle or hand back the
        // caller-owned source bitmap, which would otherwise lead to a double recycle.
        Bitmap cropped = Bitmap.createBitmap(source, viewport.left, viewport.top, viewport.width, viewport.height);
        boolean croppedIsOwned = cropped != source;
        if (cropped.getWidth() == NORMALIZED_WIDTH && cropped.getHeight() == NORMALIZED_HEIGHT) {
            if (croppedIsOwned) {
                return cropped;
            }
            return copyOf(source);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, NORMALIZED_WIDTH, NORMALIZED_HEIGHT, true);
        if (croppedIsOwned && scaled != cropped) {
            cropped.recycle();
        }
        return scaled;
    }

    private static Bitmap copyOf(Bitmap source) {
        Bitmap.Config config = source.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        return source.copy(config, false);
    }

    static Bitmap toPhaseScanFrame(Bitmap source) {
        return toLandscapeGameFrame(source);
    }

    static String phaseScanInputLabel() {
        return "landscape-game-frame";
    }

    static float mapGameX(float gameX) {
        ScreenFrameStore.Snapshot snapshot = ScreenFrameStore.snapshot();
        if (snapshot.width <= 0 || snapshot.height <= 0) {
            return gameX;
        }
        AutoWsgrAccessibilityService.DisplaySize touch = AutoWsgrAccessibilityService.currentDisplaySize();
        return GameTouchMapper.map(gameX, 0f, snapshot.width, snapshot.height, touch.width, touch.height).x;
    }

    static float mapGameY(float gameY) {
        ScreenFrameStore.Snapshot snapshot = ScreenFrameStore.snapshot();
        if (snapshot.width <= 0 || snapshot.height <= 0) {
            return gameY;
        }
        AutoWsgrAccessibilityService.DisplaySize touch = AutoWsgrAccessibilityService.currentDisplaySize();
        return GameTouchMapper.map(0f, gameY, snapshot.width, snapshot.height, touch.width, touch.height).y;
    }
}
