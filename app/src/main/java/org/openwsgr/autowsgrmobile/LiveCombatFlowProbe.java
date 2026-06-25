package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;

final class LiveCombatFlowProbe {
    private LiveCombatFlowProbe() {
    }

    static Result run(Context context) {
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return new Result(false, "live frame unavailable: authorize screen capture first");
        }

        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        NativeCombatTransitions.Graph graph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.MAP,
                NativeCombatPhase.MAP_PAGE
        );
        NativeCombatPhase current = NativeCombatPhase.SPOT_ENEMY_SUCCESS;
        String action = "fight";
        NativeCombatPhase[] successors = graph.successors(current, action);
        NativeCombatPhase[] pixelCandidates = pixelOnly(successors);
        int skipped = successors.length - pixelCandidates.length;

        try {
            NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                    liveFrame,
                    store,
                    pixelCandidates,
                    4,
                    0.999
            );

            StringBuilder detail = new StringBuilder();
            append(detail, "live frame=" + liveFrame.getWidth() + "x" + liveFrame.getHeight());
            append(detail, "flow current=" + current.id
                    + ", action=" + action
                    + ", successors=" + NativeCombatTransitions.format(successors));
            append(detail, "pixel candidates=" + NativeCombatTransitions.format(pixelCandidates)
                    + ", template lanes skipped=" + skipped);
            append(detail, "live recognition=" + formatRecognition(recognition));

            return new Result(pixelCandidates.length > 0, detail.toString());
        } finally {
            liveFrame.recycle();
        }
    }

    private static NativeCombatPhase[] pixelOnly(NativeCombatPhase[] phases) {
        ArrayList<NativeCombatPhase> result = new ArrayList<>();
        for (NativeCombatPhase phase : phases) {
            if (phase.hasPixelSignature()) {
                result.add(phase);
            }
        }
        return result.toArray(new NativeCombatPhase[result.size()]);
    }

    private static String formatRecognition(NativeCombatRecognizer.Recognition recognition) {
        if (!recognition.matched) {
            return "miss reason=" + recognition.reason + ", bestPhase="
                    + (recognition.phase == null ? "none" : recognition.phase.id)
                    + ", score=" + formatScore(recognition.score);
        }
        if ("pixel".equals(recognition.signatureType)) {
            return "hit phase=" + recognition.phase.id
                    + ", type=pixel"
                    + ", signature=" + recognition.pixelSignatureName
                    + ", matched=" + recognition.matchedPixelSignatureName
                    + ", rules=" + recognition.matchedRules + "/" + recognition.totalRules;
        }
        return "hit phase=" + recognition.phase.id
                + ", type=template"
                + ", key=" + recognition.templateKeyId
                + ", at=(" + recognition.x + "," + recognition.y + ")"
                + ", score=" + formatScore(recognition.score);
    }

    private static String formatScore(double value) {
        return String.format(java.util.Locale.US, "%.4f", value);
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Live combat flow probe: " + (ok ? "passed" : "failed");
        }
    }
}
