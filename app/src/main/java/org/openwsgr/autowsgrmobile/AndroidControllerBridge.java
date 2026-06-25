package org.openwsgr.autowsgrmobile;

final class AndroidControllerBridge {
    private AndroidControllerBridge() {
    }

    static String captureStatus() {
        return ScreenFrameStore.snapshot().toStatusLine();
    }

    static String centerPixelStatus() {
        ScreenFrameStore.PixelSample sample = ScreenFrameStore.sampleNormalized(0.5f, 0.5f);
        if (!sample.available) {
            return "Center pixel: unavailable (" + sample.reason + ")";
        }
        return "Center pixel: x=" + sample.x + ", y=" + sample.y + ", rgba=("
                + sample.r + "," + sample.g + "," + sample.b + "," + sample.a + ")";
    }

    static String touchStatus() {
        return "Accessibility touch bridge: "
                + (AutoWsgrAccessibilityService.isActive() ? "enabled" : "disabled");
    }

    static boolean tap(float x, float y) {
        return AutoWsgrAccessibilityService.tap(x, y);
    }

    static boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        return AutoWsgrAccessibilityService.swipe(x1, y1, x2, y2, durationMs);
    }
}
