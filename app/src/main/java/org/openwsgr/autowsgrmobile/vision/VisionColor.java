package org.openwsgr.autowsgrmobile.vision;

public final class VisionColor {
    public final int r;
    public final int g;
    public final int b;

    public VisionColor(int r, int g, int b) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
    }

    public static VisionColor fromArgb(int argb) {
        return new VisionColor((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff);
    }

    public int toArgb() {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public double distanceTo(VisionColor other) {
        int dr = r - other.r;
        int dg = g - other.g;
        int db = b - other.b;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    public boolean near(VisionColor other, double tolerance) {
        return distanceTo(other) <= tolerance;
    }

    private static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
