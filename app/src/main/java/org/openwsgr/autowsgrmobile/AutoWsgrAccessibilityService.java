package org.openwsgr.autowsgrmobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

public final class AutoWsgrAccessibilityService extends AccessibilityService {
    private static WeakReference<AutoWsgrAccessibilityService> activeService =
            new WeakReference<>(null);
    private static volatile String latestForegroundPackage = "";

    public static boolean isActive() {
        return activeService.get() != null;
    }

    public static String latestForegroundPackage() {
        return latestForegroundPackage;
    }

    public static boolean tap(float x, float y) {
        AutoWsgrAccessibilityService service = activeService.get();
        return service != null && service.dispatchTap(x, y);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        AutoWsgrAccessibilityService service = activeService.get();
        return service != null && service.dispatchSwipe(x1, y1, x2, y2, durationMs);
    }

    public static DisplaySize currentDisplaySize() {
        AutoWsgrAccessibilityService service = activeService.get();
        if (service == null) {
            return new DisplaySize(0, 0);
        }
        return service.readTouchDisplaySize();
    }

    @Override
    protected void onServiceConnected() {
        activeService = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            latestForegroundPackage = event.getPackageName().toString();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        activeService.clear();
        return super.onUnbind(intent);
    }

    private boolean dispatchTap(float x, float y) {
        float[] point = toPixels(x, y);
        Path path = new Path();
        path.moveTo(point[0], point[1]);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, 80L);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private boolean dispatchSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        float[] start = toPixels(x1, y1);
        float[] end = toPixels(x2, y2);
        Path path = new Path();
        path.moveTo(start[0], start[1]);
        path.lineTo(end[0], end[1]);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, Math.max(1L, durationMs));
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private float[] toPixels(float normalizedX, float normalizedY) {
        DisplaySize size = readTouchDisplaySize();
        float x = clamp(normalizedX) * size.width;
        float y = clamp(normalizedY) * size.height;
        return new float[]{x, y};
    }

    private DisplaySize readTouchDisplaySize() {
        DisplaySize raw = readRawDisplaySize();
        TouchDisplaySizeMapper.Size effective = TouchDisplaySizeMapper.effective(
                raw.width,
                raw.height,
                latestForegroundPackage,
                TargetGameLauncher.PACKAGE_NAME
        );
        return new DisplaySize(effective.width, effective.height);
    }

    private DisplaySize readRawDisplaySize() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager != null) {
            Display display = manager.getDefaultDisplay();
            if (display != null) {
                display.getRealMetrics(metrics);
            }
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            DisplayMetrics fallback = getResources().getDisplayMetrics();
            metrics.widthPixels = fallback.widthPixels;
            metrics.heightPixels = fallback.heightPixels;
        }
        return new DisplaySize(metrics.widthPixels, metrics.heightPixels);
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    static final class DisplaySize {
        final int width;
        final int height;

        DisplaySize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
