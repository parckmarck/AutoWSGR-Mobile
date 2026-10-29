package org.openwsgr.autowsgrmobile;

import java.nio.ByteBuffer;

final class FrameBufferCopier {
    private static final int RGBA_BYTES_PER_PIXEL = 4;

    private FrameBufferCopier() {
    }

    static byte[] copyRgba(
            ByteBuffer source,
            int width,
            int height,
            int pixelStride,
            int rowStride
    ) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        if (pixelStride < RGBA_BYTES_PER_PIXEL) {
            throw new IllegalArgumentException("pixelStride must be at least four");
        }

        long visibleRowBytes = (long) width * pixelStride;
        if (visibleRowBytes > Integer.MAX_VALUE || rowStride < visibleRowBytes) {
            throw new IllegalArgumentException("rowStride is smaller than the visible row");
        }

        long pixelCount = (long) width * height;
        if (pixelCount > Integer.MAX_VALUE / RGBA_BYTES_PER_PIXEL) {
            throw new IllegalArgumentException("tight RGBA output is too large");
        }

        long requiredBytes = (long) (height - 1) * rowStride
                + (long) (width - 1) * pixelStride
                + RGBA_BYTES_PER_PIXEL;
        if (requiredBytes > Integer.MAX_VALUE
                || requiredBytes > source.capacity()
                || requiredBytes > source.limit()) {
            throw new IllegalArgumentException("source does not expose the required RGBA bounds");
        }

        byte[] result = new byte[(int) pixelCount * RGBA_BYTES_PER_PIXEL];
        ByteBuffer reader = source.duplicate();
        if (pixelStride == RGBA_BYTES_PER_PIXEL) {
            int rowBytes = width * RGBA_BYTES_PER_PIXEL;
            for (int y = 0; y < height; y++) {
                reader.position(y * rowStride);
                reader.get(result, y * rowBytes, rowBytes);
            }
            return result;
        }

        for (int y = 0; y < height; y++) {
            int sourceRow = y * rowStride;
            int targetRow = y * width * RGBA_BYTES_PER_PIXEL;
            for (int x = 0; x < width; x++) {
                int sourceOffset = sourceRow + x * pixelStride;
                int targetOffset = targetRow + x * RGBA_BYTES_PER_PIXEL;
                result[targetOffset] = reader.get(sourceOffset);
                result[targetOffset + 1] = reader.get(sourceOffset + 1);
                result[targetOffset + 2] = reader.get(sourceOffset + 2);
                result[targetOffset + 3] = reader.get(sourceOffset + 3);
            }
        }
        return result;
    }
}
