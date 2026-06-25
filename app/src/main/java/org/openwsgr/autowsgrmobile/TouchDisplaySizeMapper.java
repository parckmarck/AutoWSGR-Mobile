package org.openwsgr.autowsgrmobile;

final class TouchDisplaySizeMapper {
    private TouchDisplaySizeMapper() {
    }

    static Size effective(
            int rawWidth,
            int rawHeight,
            String foregroundPackage,
            String targetPackage
    ) {
        int width = Math.max(0, rawWidth);
        int height = Math.max(0, rawHeight);
        return new Size(width, height, "display-raw");
    }

    static final class Size {
        final int width;
        final int height;
        final String detail;

        Size(int width, int height, String detail) {
            this.width = width;
            this.height = height;
            this.detail = detail;
        }
    }
}
