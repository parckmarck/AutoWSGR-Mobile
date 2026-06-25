package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;

import java.io.IOException;
import java.util.List;

final class NativeCombatRecognizer {
    private NativeCombatRecognizer() {
    }

    static Recognition identifyCurrent(
            Bitmap screen,
            AutoWsgrAssetStore store,
            NativeCombatPhase[] candidates,
            int stride,
            double threshold
    ) {
        return identifyCurrent(screen, store, candidates, stride, threshold, null);
    }

    static Recognition identifyCurrent(
            Bitmap screen,
            AutoWsgrAssetStore store,
            NativeCombatPhase[] candidates,
            int stride,
            double threshold,
            TemplateMatcher.ScanControl control
    ) {
        if (screen == null) {
            return Recognition.missing("missing screen");
        }
        if (candidates == null || candidates.length == 0) {
            return Recognition.missing("no candidates");
        }

        StringBuilder pixelMisses = new StringBuilder();
        for (NativeCombatPhase phase : candidates) {
            if (phase.hasPixelSignature()) {
                NativePhasePixelSignatures.PixelMatch match = phase.matchPixelSignature(screen);
                if (match.matched) {
                    return Recognition.foundPixel(phase, match);
                }
                appendPixelMiss(pixelMisses, phase, match);
            }
            if (shouldStop(control)) {
                return Recognition.missing(control.stopReason());
            }
        }

        Recognition bestBelowThreshold = null;
        for (NativeCombatPhase phase : candidates) {
            if (shouldStop(control)) {
                return Recognition.missing(control.stopReason());
            }
            if (phase.hasTemplateSignature()) {
                List<NativeTemplateScanner.Template> templates = null;
                try {
                    templates = phase.templateKey.loadTemplates(store);
                    NativeTemplateScanner.ScanResult match =
                            NativeTemplateScanner.findAny(screen, templates, stride, threshold, control);
                    if (shouldStop(control)) {
                        return Recognition.interrupted(phase, match);
                    }
                    if (match.valid) {
                        return Recognition.foundTemplate(phase, match);
                    }
                    if (match.score > 0.0
                            && (bestBelowThreshold == null || match.score > bestBelowThreshold.score)) {
                        bestBelowThreshold = Recognition.belowThreshold(
                                phase,
                                match,
                                pixelMisses.toString()
                        );
                    }
                } catch (IOException e) {
                    return Recognition.missing(withPixelDiagnostics(
                            "template load failed for " + phase.id + ": " + e.getMessage(),
                            pixelMisses.toString()
                    ));
                } finally {
                    NativeTemplateScanner.recycleAll(templates);
                }
            }
        }

        if (bestBelowThreshold != null) {
            return bestBelowThreshold;
        }
        return Recognition.missing(withPixelDiagnostics("no candidate matched", pixelMisses.toString()));
    }

    private static boolean shouldStop(TemplateMatcher.ScanControl control) {
        return control != null && control.shouldStop();
    }

    private static void appendPixelMiss(
            StringBuilder builder,
            NativeCombatPhase phase,
            NativePhasePixelSignatures.PixelMatch match
    ) {
        if (builder.length() > 0) {
            builder.append(';');
        }
        builder.append(phase.id)
                .append('=')
                .append(match.matchedRules)
                .append('/')
                .append(match.totalRules);
    }

    static String withPixelDiagnostics(String reason, String pixelDiagnostics) {
        if (pixelDiagnostics == null || pixelDiagnostics.length() == 0) {
            return reason;
        }
        return reason + ", pixelMisses=" + pixelDiagnostics;
    }

    static String signatureScanOrder(NativeCombatPhase phase) {
        if (phase == null || !phase.hasAnySignature()) {
            return "none";
        }
        if (phase.hasPixelSignature() && phase.hasTemplateSignature()) {
            return "pixel,template";
        }
        if (phase.hasPixelSignature()) {
            return "pixel";
        }
        return "template";
    }

    static String candidateScanOrder(NativeCombatPhase[] candidates) {
        if (candidates == null || candidates.length == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (NativeCombatPhase phase : candidates) {
            if (phase != null && phase.hasPixelSignature()) {
                appendOrder(builder, "pixel:" + phase.id);
            }
        }
        for (NativeCombatPhase phase : candidates) {
            if (phase != null && phase.hasTemplateSignature()) {
                appendOrder(builder, "template:" + phase.id);
            }
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static void appendOrder(StringBuilder builder, String item) {
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(item);
    }

    static final class Recognition {
        final boolean matched;
        final String reason;
        final String signatureType;
        final NativeCombatPhase phase;
        final String templateKeyId;
        final String path;
        final String pixelSignatureName;
        final String matchedPixelSignatureName;
        final int matchedRules;
        final int totalRules;
        final int signatureVariants;
        final int x;
        final int y;
        final double score;

        private Recognition(
                boolean matched,
                String reason,
                String signatureType,
                NativeCombatPhase phase,
                String templateKeyId,
                String path,
                String pixelSignatureName,
                String matchedPixelSignatureName,
                int matchedRules,
                int totalRules,
                int signatureVariants,
                int x,
                int y,
                double score
        ) {
            this.matched = matched;
            this.reason = reason;
            this.signatureType = signatureType;
            this.phase = phase;
            this.templateKeyId = templateKeyId;
            this.path = path;
            this.pixelSignatureName = pixelSignatureName;
            this.matchedPixelSignatureName = matchedPixelSignatureName;
            this.matchedRules = matchedRules;
            this.totalRules = totalRules;
            this.signatureVariants = signatureVariants;
            this.x = x;
            this.y = y;
            this.score = score;
        }

        static Recognition foundTemplate(NativeCombatPhase phase, NativeTemplateScanner.ScanResult match) {
            return new Recognition(
                    true,
                    "",
                    "template",
                    phase,
                    match.keyId,
                    match.path,
                    "",
                    "",
                    0,
                    0,
                    0,
                    match.x,
                    match.y,
                    match.score
            );
        }

        static Recognition foundPixel(NativeCombatPhase phase, NativePhasePixelSignatures.PixelMatch match) {
            return new Recognition(
                    true,
                    "",
                    "pixel",
                    phase,
                    "",
                    "",
                    match.signatureName,
                    match.matchedSignatureName,
                    match.matchedRules,
                    match.totalRules,
                    match.signatureVariants,
                    0,
                    0,
                    1.0
            );
        }

        static Recognition belowThreshold(
                NativeCombatPhase phase,
                NativeTemplateScanner.ScanResult match,
                String pixelDiagnostics
        ) {
            return new Recognition(
                    false,
                    withPixelDiagnostics(match.reason, pixelDiagnostics),
                    "template",
                    phase,
                    match.keyId,
                    match.path,
                    "",
                    "",
                    0,
                    0,
                    0,
                    match.x,
                    match.y,
                    match.score
            );
        }

        static Recognition missing(String reason) {
            return new Recognition(
                    false,
                    reason,
                    "",
                    null,
                    "",
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0
            );
        }

        static Recognition interrupted(NativeCombatPhase phase, NativeTemplateScanner.ScanResult match) {
            return new Recognition(
                    false,
                    match.reason,
                    "template",
                    phase,
                    match.keyId,
                    match.path,
                    "",
                    "",
                    0,
                    0,
                    0,
                    match.x,
                    match.y,
                    match.score
            );
        }
    }
}
