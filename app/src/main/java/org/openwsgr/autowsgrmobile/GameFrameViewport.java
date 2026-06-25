package org.openwsgr.autowsgrmobile;

import java.util.Locale;

final class GameFrameViewport {
    private static final float TARGET_ASPECT = 16f / 9f;

    final int frameWidth;
    final int frameHeight;
    final int left;
    final int top;
    final int width;
    final int height;

    private GameFrameViewport(int frameWidth, int frameHeight, int left, int top, int width, int height) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    static GameFrameViewport fromFrame(int frameWidth, int frameHeight) {
        int safeWidth = Math.max(1, frameWidth);
        int safeHeight = Math.max(1, frameHeight);
        float aspect = safeWidth / (float) safeHeight;
        if (Math.abs(aspect - TARGET_ASPECT) < 0.01f) {
            return new GameFrameViewport(safeWidth, safeHeight, 0, 0, safeWidth, safeHeight);
        }
        if (aspect < TARGET_ASPECT) {
            int gameHeight = Math.max(1, Math.round(safeWidth * 9f / 16f));
            int top = Math.max(0, (safeHeight - gameHeight) / 2);
            return new GameFrameViewport(safeWidth, safeHeight, 0, top, safeWidth, Math.min(gameHeight, safeHeight));
        }
        int gameWidth = Math.max(1, Math.round(safeHeight * 16f / 9f));
        int left = Math.max(0, (safeWidth - gameWidth) / 2);
        return new GameFrameViewport(safeWidth, safeHeight, left, 0, Math.min(gameWidth, safeWidth), safeHeight);
    }

    float mapX(float gameX) {
        return clamp((left + clamp(gameX) * width) / (float) frameWidth);
    }

    float mapY(float gameY) {
        return clamp((top + clamp(gameY) * height) / (float) frameHeight);
    }

    String detail() {
        return "viewport=frame " + frameWidth + "x" + frameHeight
                + ", game=[" + left + "," + top + " " + width + "x" + height + "]"
                + ", aspect=" + String.format(Locale.US, "%.4f", width / (float) height);
    }

    private static float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
