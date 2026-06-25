package org.openwsgr.autowsgrmobile.vision;

import android.graphics.Bitmap;

public final class TemplateMatcher {
    private TemplateMatcher() {
    }

    public static MatchResult findBest(Bitmap screen, Bitmap template, int stride) {
        return findBest(screen, template, stride, null);
    }

    public static MatchResult findBest(Bitmap screen, Bitmap template, int stride, ScanControl control) {
        if (screen == null || template == null) {
            return MatchResult.missing("missing bitmap");
        }
        if (template.getWidth() > screen.getWidth() || template.getHeight() > screen.getHeight()) {
            return MatchResult.missing("template larger than screen");
        }
        int safeStride = Math.max(1, stride);
        double bestScore = -1.0;
        int bestX = 0;
        int bestY = 0;
        for (int y = 0; y <= screen.getHeight() - template.getHeight(); y += safeStride) {
            for (int x = 0; x <= screen.getWidth() - template.getWidth(); x += safeStride) {
                if (shouldStop(control)) {
                    return MatchResult.stopped(control.stopReason(), bestX, bestY, Math.max(0.0, bestScore));
                }
                double score = similarityAt(screen, template, x, y);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                    if (score >= 1.0) {
                        return MatchResult.found(bestX, bestY, score);
                    }
                }
            }
        }
        return MatchResult.found(bestX, bestY, bestScore);
    }

    private static boolean shouldStop(ScanControl control) {
        return control != null && control.shouldStop();
    }

    public static double similarityAt(Bitmap screen, Bitmap template, int startX, int startY) {
        long totalDistance = 0L;
        long samples = 0L;
        int stepX = Math.max(1, template.getWidth() / 64);
        int stepY = Math.max(1, template.getHeight() / 32);
        for (int y = 0; y < template.getHeight(); y += stepY) {
            for (int x = 0; x < template.getWidth(); x += stepX) {
                int screenArgb = screen.getPixel(startX + x, startY + y);
                int templateArgb = template.getPixel(x, y);
                int dr = ((screenArgb >> 16) & 0xff) - ((templateArgb >> 16) & 0xff);
                int dg = ((screenArgb >> 8) & 0xff) - ((templateArgb >> 8) & 0xff);
                int db = (screenArgb & 0xff) - (templateArgb & 0xff);
                totalDistance += Math.abs(dr) + Math.abs(dg) + Math.abs(db);
                samples += 3L;
            }
        }
        if (samples == 0L) {
            return 0.0;
        }
        double meanDistance = totalDistance / (double) samples;
        double score = 1.0 - (meanDistance / 255.0);
        if (score < 0.0) {
            return 0.0;
        }
        if (score > 1.0) {
            return 1.0;
        }
        return score;
    }

    public interface ScanControl {
        boolean shouldStop();

        String stopReason();
    }

    public static final class MatchResult {
        public final boolean valid;
        public final String reason;
        public final int x;
        public final int y;
        public final double score;

        private MatchResult(boolean valid, String reason, int x, int y, double score) {
            this.valid = valid;
            this.reason = reason;
            this.x = x;
            this.y = y;
            this.score = score;
        }

        static MatchResult found(int x, int y, double score) {
            return new MatchResult(true, "", x, y, score);
        }

        static MatchResult missing(String reason) {
            return new MatchResult(false, reason, 0, 0, 0.0);
        }

        static MatchResult stopped(String reason, int x, int y, double score) {
            return new MatchResult(false, reason, x, y, score);
        }
    }
}
