package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.openwsgr.autowsgrmobile.vision.PixelRule;
import org.openwsgr.autowsgrmobile.vision.PixelSignature;

import java.io.IOException;
import java.util.List;

final class CombatFlowSelfTest {
    private CombatFlowSelfTest() {
    }

    static Result run(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        NativeCombatTransitions.Graph mapGraph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.MAP,
                NativeCombatPhase.MAP_PAGE
        );
        NativeCombatTransitions.Graph singleGraph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.SINGLE,
                NativeCombatPhase.EXERCISE_PAGE
        );

        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        append(detail, "map transitions=" + mapGraph.transitionCount()
                + ", start=" + mapGraph.describeSuccessors(NativeCombatPhase.START_FIGHT, ""));
        append(detail, "single transitions=" + singleGraph.transitionCount()
                + ", result=" + singleGraph.describeSuccessors(NativeCombatPhase.RESULT, ""));

        ok = runMapStartProbe(store, mapGraph, detail) && ok;
        ok = runSpotFightPixelProbe(store, mapGraph, detail) && ok;
        ok = runProceedNoProbe(store, mapGraph, detail) && ok;
        ok = runSingleRetreatProbe(store, singleGraph, detail) && ok;
        ok = runFallbackBranchProbe(mapGraph, detail) && ok;
        ok = runGuardedActionCatalogProbe(detail) && ok;

        return new Result(ok, detail.toString());
    }

    private static boolean runMapStartProbe(
            AutoWsgrAssetStore store,
            NativeCombatTransitions.Graph graph,
            StringBuilder detail
    ) {
        Bitmap template = null;
        Bitmap screen = null;
        try {
            template = loadFirstTemplate(store, NativeTemplateKey.FIGHT_CONDITION);
            screen = syntheticTemplateScreen(template, 32, 24);
            NativeCombatFlowRecognizer.Step step = NativeCombatFlowRecognizer.identifyNext(
                    screen,
                    store,
                    graph,
                    NativeCombatPhase.START_FIGHT,
                    "",
                    4,
                    0.999
            );
            append(detail, "flow map start=" + formatStep(step));
            return step.recognition.matched
                    && step.recognition.phase == NativeCombatPhase.FIGHT_CONDITION;
        } catch (IOException e) {
            append(detail, "flow map start failed: " + e.getMessage());
            return false;
        } finally {
            recycle(template);
            recycle(screen);
        }
    }

    private static boolean runSpotFightPixelProbe(
            AutoWsgrAssetStore store,
            NativeCombatTransitions.Graph graph,
            StringBuilder detail
    ) {
        Bitmap screen = syntheticPixelScreen();
        try {
            paintSignature(
                    screen,
                    NativePhasePixelSignatures.testSignature(
                            NativePhasePixelSignatures.Kind.CHOOSE_FORMATION,
                            0
                    )
            );
            NativeCombatFlowRecognizer.Step step = NativeCombatFlowRecognizer.identifyNext(
                    screen,
                    store,
                    graph,
                    NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                    "fight",
                    4,
                    0.999
            );
            append(detail, "flow spot fight=" + formatStep(step));
            return step.recognition.matched
                    && step.recognition.phase == NativeCombatPhase.FORMATION
                    && "pixel".equals(step.recognition.signatureType);
        } finally {
            recycle(screen);
        }
    }

    private static boolean runProceedNoProbe(
            AutoWsgrAssetStore store,
            NativeCombatTransitions.Graph graph,
            StringBuilder detail
    ) {
        Bitmap template = null;
        Bitmap screen = null;
        try {
            template = loadFirstTemplate(store, NativeTemplateKey.END_MAP_PAGE);
            screen = syntheticTemplateScreen(template, 28, 20);
            NativeCombatFlowRecognizer.Step step = NativeCombatFlowRecognizer.identifyNext(
                    screen,
                    store,
                    graph,
                    NativeCombatPhase.PROCEED,
                    "no",
                    4,
                    0.999
            );
            append(detail, "flow proceed no=" + formatStep(step));
            return step.recognition.matched
                    && step.recognition.phase == NativeCombatPhase.MAP_PAGE
                    && step.candidates.length == 1;
        } catch (IOException e) {
            append(detail, "flow proceed no failed: " + e.getMessage());
            return false;
        } finally {
            recycle(template);
            recycle(screen);
        }
    }

    private static boolean runSingleRetreatProbe(
            AutoWsgrAssetStore store,
            NativeCombatTransitions.Graph graph,
            StringBuilder detail
    ) {
        Bitmap template = null;
        Bitmap screen = null;
        try {
            template = loadFirstTemplate(store, NativeTemplateKey.END_EXERCISE_PAGE);
            screen = syntheticTemplateScreen(template, 24, 28);
            NativeCombatFlowRecognizer.Step step = NativeCombatFlowRecognizer.identifyNext(
                    screen,
                    store,
                    graph,
                    NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                    "retreat",
                    4,
                    0.999
            );
            append(detail, "flow single retreat=" + formatStep(step));
            return step.recognition.matched
                    && step.recognition.phase == NativeCombatPhase.EXERCISE_PAGE
                    && step.candidates.length == 1;
        } catch (IOException e) {
            append(detail, "flow single retreat failed: " + e.getMessage());
            return false;
        } finally {
            recycle(template);
            recycle(screen);
        }
    }

    private static boolean runFallbackBranchProbe(
            NativeCombatTransitions.Graph graph,
            StringBuilder detail
    ) {
        NativeCombatPhase[] successors = graph.successors(NativeCombatPhase.SPOT_ENEMY_SUCCESS, "unknown");
        append(detail, "flow branch fallback="
                + NativeCombatTransitions.format(successors)
                + " for spot_enemy_success/unknown");
        return successors.length == 3
                && successors[0] == NativeCombatPhase.FORMATION
                && successors[1] == NativeCombatPhase.FIGHT_PERIOD
                && successors[2] == NativeCombatPhase.MISSILE_ANIMATION;
    }

    private static boolean runGuardedActionCatalogProbe(StringBuilder detail) {
        boolean hasMissileSpec = GuardedCombatStepper.hasActionSpec("missile_skip");
        boolean phaseDefault = "missile_skip".equals(
                GuardedCombatStepper.actionIdForPhase(NativeCombatPhase.MISSILE_ANIMATION)
        );
        boolean guardedPhase = containsPhase(
                GuardedCombatStepper.guardedPhases(),
                NativeCombatPhase.MISSILE_ANIMATION
        );
        append(detail, "guarded action probe=missile_skip{spec=" + hasMissileSpec
                + ", default=" + phaseDefault
                + ", phase=" + guardedPhase + "}");
        return hasMissileSpec && phaseDefault && guardedPhase;
    }

    private static boolean containsPhase(NativeCombatPhase[] phases, NativeCombatPhase expected) {
        for (NativeCombatPhase phase : phases) {
            if (phase == expected) {
                return true;
            }
        }
        return false;
    }

    private static Bitmap loadFirstTemplate(AutoWsgrAssetStore store, NativeTemplateKey key) throws IOException {
        List<NativeTemplateScanner.Template> templates = null;
        try {
            templates = key.loadTemplates(store);
            NativeTemplateScanner.Template first = templates.get(0);
            return first.bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            NativeTemplateScanner.recycleAll(templates);
        }
    }

    private static Bitmap syntheticTemplateScreen(Bitmap template, int x, int y) {
        int width = template.getWidth() + x + 48;
        int height = template.getHeight() + y + 48;
        Bitmap screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(screen);
        canvas.drawColor(0xFF182028);
        canvas.drawBitmap(template, x, y, null);
        return screen;
    }

    private static Bitmap syntheticPixelScreen() {
        Bitmap screen = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        screen.eraseColor(0xFF182028);
        return screen;
    }

    private static void paintSignature(Bitmap screen, PixelSignature signature) {
        for (PixelRule rule : signature.rules) {
            screen.setPixel(rule.pixelX(screen), rule.pixelY(screen), rule.expected.toArgb());
        }
    }

    private static String formatStep(NativeCombatFlowRecognizer.Step step) {
        return "current=" + step.current.id
                + ", action=" + step.lastAction
                + ", candidates=" + NativeCombatTransitions.format(step.candidates)
                + ", " + formatRecognition(step.recognition);
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

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }
    }

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Combat flow self-test: " + (ok ? "passed" : "failed");
        }
    }
}
