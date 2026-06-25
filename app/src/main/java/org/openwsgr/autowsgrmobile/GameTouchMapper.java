package org.openwsgr.autowsgrmobile;

final class GameTouchMapper {
    private GameTouchMapper() {
    }

    static Mapping map(
            float gameX,
            float gameY,
            int captureWidth,
            int captureHeight,
            int touchWidth,
            int touchHeight
    ) {
        if (touchWidth > touchHeight) {
            return new Mapping(clamp(gameX), clamp(gameY), "touch=landscape-direct");
        }
        GameFrameViewport viewport = GameFrameViewport.fromFrame(captureWidth, captureHeight);
        return new Mapping(viewport.mapX(gameX), viewport.mapY(gameY), "touch=portrait-capture-viewport");
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

    static final class Mapping {
        final float x;
        final float y;
        final String detail;

        Mapping(float x, float y, String detail) {
            this.x = x;
            this.y = y;
            this.detail = detail;
        }
    }
}
