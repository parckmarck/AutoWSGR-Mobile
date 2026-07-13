package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

final class ScreenFrameStore {
    private static final Object LOCK = new Object();

    private static int[] latestArgb;
    private static int width;
    private static int height;
    private static long lastFrameTimeMs;
    private static long frameCount;

    private ScreenFrameStore() {
    }

    static void update(Image image) {
        if (image == null || image.getPlanes().length == 0) {
            return;
        }
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        byte[] tight = FrameBufferCopier.copyRgba(
                buffer, imageWidth, imageHeight, pixelStride, rowStride
        );
        int[] pixels = new int[imageWidth * imageHeight];
        for (int i = 0; i < pixels.length; i++) {
            int offset = i * 4;
            int r = tight[offset] & 0xff;
            int g = tight[offset + 1] & 0xff;
            int b = tight[offset + 2] & 0xff;
            int a = tight[offset + 3] & 0xff;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        synchronized (LOCK) {
            latestArgb = pixels;
            width = imageWidth;
            height = imageHeight;
            lastFrameTimeMs = System.currentTimeMillis();
            frameCount++;
        }
    }

    static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(width, height, lastFrameTimeMs, frameCount,
                    latestArgb == null ? 0 : latestArgb.length * 4);
        }
    }

    static Bitmap latestBitmap() {
        int[] pixels;
        int bitmapWidth;
        int bitmapHeight;
        synchronized (LOCK) {
            if (latestArgb == null || width <= 0 || height <= 0) {
                return null;
            }
            pixels = latestArgb.clone();
            bitmapWidth = width;
            bitmapHeight = height;
        }

        return Bitmap.createBitmap(
                pixels, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888
        );
    }

    static PixelSample sampleNormalized(float normalizedX, float normalizedY) {
        synchronized (LOCK) {
            if (latestArgb == null || width <= 0 || height <= 0) {
                return PixelSample.unavailable("no frame");
            }
            int x = Math.min(width - 1, Math.max(0, Math.round(clamp(normalizedX) * (width - 1))));
            int y = Math.min(height - 1, Math.max(0, Math.round(clamp(normalizedY) * (height - 1))));
            int index = y * width + x;
            if (index < 0 || index >= latestArgb.length) {
                return PixelSample.unavailable("sample out of range");
            }
            int argb = latestArgb[index];
            return PixelSample.available(
                    x,
                    y,
                    (argb >>> 16) & 0xff,
                    (argb >>> 8) & 0xff,
                    argb & 0xff,
                    (argb >>> 24) & 0xff
            );
        }
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

    static final class Snapshot {
        final int width;
        final int height;
        final long lastFrameTimeMs;
        final long frameCount;
        final int byteCount;

        Snapshot(int width, int height, long lastFrameTimeMs, long frameCount, int byteCount) {
            this.width = width;
            this.height = height;
            this.lastFrameTimeMs = lastFrameTimeMs;
            this.frameCount = frameCount;
            this.byteCount = byteCount;
        }

        boolean hasFrame() {
            return frameCount > 0 && byteCount > 0;
        }

        String toStatusLine() {
            if (!hasFrame()) {
                return "Screen frame: waiting for MediaProjection frames";
            }
            long ageMs = Math.max(0L, System.currentTimeMillis() - lastFrameTimeMs);
            return "Screen frame: " + width + "x" + height + " RGBA, "
                    + byteCount + " bytes, frames=" + frameCount + ", age=" + ageMs + "ms";
        }
    }

    static final class PixelSample {
        final boolean available;
        final String reason;
        final int x;
        final int y;
        final int r;
        final int g;
        final int b;
        final int a;

        private PixelSample(
                boolean available,
                String reason,
                int x,
                int y,
                int r,
                int g,
                int b,
                int a
        ) {
            this.available = available;
            this.reason = reason;
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        static PixelSample available(int x, int y, int r, int g, int b, int a) {
            return new PixelSample(true, "", x, y, r, g, b, a);
        }

        static PixelSample unavailable(String reason) {
            return new PixelSample(false, reason, 0, 0, 0, 0, 0, 0);
        }
    }
}
