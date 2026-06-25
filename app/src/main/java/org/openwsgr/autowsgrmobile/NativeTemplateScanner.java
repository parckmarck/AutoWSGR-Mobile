package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;

import java.util.List;

final class NativeTemplateScanner {
    private NativeTemplateScanner() {
    }

    static ScanResult findAny(
            Bitmap screen,
            List<Template> templates,
            int stride,
            double threshold
    ) {
        return findAny(screen, templates, stride, threshold, null);
    }

    static ScanResult findAny(
            Bitmap screen,
            List<Template> templates,
            int stride,
            double threshold,
            TemplateMatcher.ScanControl control
    ) {
        if (screen == null) {
            return ScanResult.missing("missing screen");
        }
        if (templates == null || templates.isEmpty()) {
            return ScanResult.missing("no templates");
        }

        ScanResult best = ScanResult.missing("no valid match");
        for (Template template : templates) {
            if (shouldStop(control)) {
                return ScanResult.missing(control.stopReason());
            }
            TemplateMatcher.MatchResult match =
                    TemplateMatcher.findBest(screen, template.bitmap, stride, control);
            if (!match.valid) {
                if (shouldStop(control)) {
                    return ScanResult.interrupted(match.reason, template.keyId, template.path, match.x, match.y, match.score);
                }
                continue;
            }
            if (!best.valid || match.score > best.score) {
                best = ScanResult.found(template.keyId, template.path, match.x, match.y, match.score);
            }
            if (match.score >= 1.0) {
                return best;
            }
        }

        if (!best.valid) {
            return best;
        }
        if (best.score < threshold) {
            return ScanResult.belowThreshold(best, threshold);
        }
        return best;
    }

    private static boolean shouldStop(TemplateMatcher.ScanControl control) {
        return control != null && control.shouldStop();
    }

    static void recycleAll(List<Template> templates) {
        if (templates == null) {
            return;
        }
        for (Template template : templates) {
            template.bitmap.recycle();
        }
    }

    static final class Template {
        final String keyId;
        final String path;
        final Bitmap bitmap;

        Template(String keyId, String path, Bitmap bitmap) {
            this.keyId = keyId;
            this.path = path;
            this.bitmap = bitmap;
        }
    }

    static final class ScanResult {
        final boolean valid;
        final String reason;
        final String keyId;
        final String path;
        final int x;
        final int y;
        final double score;

        private ScanResult(
                boolean valid,
                String reason,
                String keyId,
                String path,
                int x,
                int y,
                double score
        ) {
            this.valid = valid;
            this.reason = reason;
            this.keyId = keyId;
            this.path = path;
            this.x = x;
            this.y = y;
            this.score = score;
        }

        static ScanResult found(String keyId, String path, int x, int y, double score) {
            return new ScanResult(true, "", keyId, path, x, y, score);
        }

        static ScanResult missing(String reason) {
            return new ScanResult(false, reason, "", "", 0, 0, 0.0);
        }

        static ScanResult belowThreshold(ScanResult best, double threshold) {
            return new ScanResult(
                    false,
                    "best score below threshold " + threshold,
                    best.keyId,
                    best.path,
                    best.x,
                    best.y,
                    best.score
            );
        }

        static ScanResult interrupted(String reason, String keyId, String path, int x, int y, double score) {
            return new ScanResult(false, reason, keyId, path, x, y, score);
        }
    }
}
