package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

final class ScreenFrameStore {
    private static final Object LOCK = new Object();

    private static byte[] latestRgba;
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
        byte[] tight = new byte[imageWidth * imageHeight * 4];

        for (int y = 0; y < imageHeight; y++) {
            int srcRow = y * rowStride;
            int dstRow = y * imageWidth * 4;
            for (int x = 0; x < imageWidth; x++) {
                int src = srcRow + x * pixelStride;
                int dst = dstRow + x * 4;
                tight[dst] = buffer.get(src);
                tight[dst + 1] = buffer.get(src + 1);
                tight[dst + 2] = buffer.get(src + 2);
                tight[dst + 3] = buffer.get(src + 3);
            }
        }

        synchronized (LOCK) {
            latestRgba = tight;
            width = imageWidth;
            height = imageHeight;
            lastFrameTimeMs = System.currentTimeMillis();
            frameCount++;
        }
    }

    static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(width, height, lastFrameTimeMs, frameCount, latestRgba == null ? 0 : latestRgba.length);
        }
    }

    static Bitmap latestBitmap() {
        byte[] rgba;
        int bitmapWidth;
        int bitmapHeight;
        synchronized (LOCK) {
            if (latestRgba == null || width <= 0 || height <= 0) {
                return null;
            }
            rgba = latestRgba.clone();
            bitmapWidth = width;
            bitmapHeight = height;
        }

        int[] pixels = new int[bitmapWidth * bitmapHeight];
        for (int i = 0; i < pixels.length; i++) {
            int offset = i * 4;
            int r = rgba[offset] & 0xff;
            int g = rgba[offset + 1] & 0xff;
            int b = rgba[offset + 2] & 0xff;
            int a = rgba[offset + 3] & 0xff;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight);
        return bitmap;
    }

    static PixelSample sampleNormalized(float normalizedX, float normalizedY) {
        synchronized (LOCK) {
            if (latestRgba == null || width <= 0 || height <= 0) {
                return PixelSample.unavailable("no frame");
            }
            int x = Math.min(width - 1, Math.max(0, Math.round(clamp(normalizedX) * (width - 1))));
            int y = Math.min(height - 1, Math.max(0, Math.round(clamp(normalizedY) * (height - 1))));
            int offset = (y * width + x) * 4;
            if (offset + 3 >= latestRgba.length) {
                return PixelSample.unavailable("sample out of range");
            }
            return PixelSample.available(
                    x,
                    y,
                    latestRgba[offset] & 0xff,
                    latestRgba[offset + 1] & 0xff,
                    latestRgba[offset + 2] & 0xff,
                    latestRgba[offset + 3] & 0xff
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
