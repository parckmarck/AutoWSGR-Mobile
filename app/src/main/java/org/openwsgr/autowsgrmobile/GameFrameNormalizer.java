package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

final class GameFrameNormalizer {
    static final int NORMALIZED_WIDTH = 960;
    static final int NORMALIZED_HEIGHT = 540;

    private GameFrameNormalizer() {
    }

    static Bitmap toLandscapeGameFrame(Bitmap source) {
        GameFrameViewport viewport = GameFrameViewport.fromFrame(source.getWidth(), source.getHeight());
        Bitmap cropped = Bitmap.createBitmap(source, viewport.left, viewport.top, viewport.width, viewport.height);
        if (cropped.getWidth() == NORMALIZED_WIDTH && cropped.getHeight() == NORMALIZED_HEIGHT) {
            return cropped;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, NORMALIZED_WIDTH, NORMALIZED_HEIGHT, true);
        cropped.recycle();
        return scaled;
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
