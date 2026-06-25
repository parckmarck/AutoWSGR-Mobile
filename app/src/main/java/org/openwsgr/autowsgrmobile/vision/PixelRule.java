package org.openwsgr.autowsgrmobile.vision;

import android.graphics.Bitmap;

public final class PixelRule {
    public final float x;
    public final float y;
    public final VisionColor expected;
    public final double tolerance;

    public PixelRule(float x, float y, VisionColor expected, double tolerance) {
        this.x = clamp(x);
        this.y = clamp(y);
        this.expected = expected;
        this.tolerance = tolerance;
    }

    public boolean matches(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return false;
        }
        VisionColor actual = VisionColor.fromArgb(bitmap.getPixel(pixelX(bitmap), pixelY(bitmap)));
        return actual.near(expected, tolerance);
    }

    public int pixelX(Bitmap bitmap) {
        return Math.min(bitmap.getWidth() - 1, Math.max(0, (int) (x * bitmap.getWidth())));
    }

    public int pixelY(Bitmap bitmap) {
        return Math.min(bitmap.getHeight() - 1, Math.max(0, (int) (y * bitmap.getHeight())));
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
