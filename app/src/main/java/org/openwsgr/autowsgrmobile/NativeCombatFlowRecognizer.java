package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

final class NativeCombatFlowRecognizer {
    private NativeCombatFlowRecognizer() {
    }

    static Step identifyNext(
            Bitmap screen,
            AutoWsgrAssetStore store,
            NativeCombatTransitions.Graph graph,
            NativeCombatPhase current,
            String lastAction,
            int stride,
            double threshold
    ) {
        NativeCombatPhase[] candidates = graph.successors(current, lastAction);
        NativeCombatRecognizer.Recognition recognition = NativeCombatRecognizer.identifyCurrent(
                screen,
                store,
                candidates,
                stride,
                threshold
        );
        return new Step(current, lastAction, candidates, recognition);
    }

    static final class Step {
        final NativeCombatPhase current;
        final String lastAction;
        final NativeCombatPhase[] candidates;
        final NativeCombatRecognizer.Recognition recognition;

        Step(
                NativeCombatPhase current,
                String lastAction,
                NativeCombatPhase[] candidates,
                NativeCombatRecognizer.Recognition recognition
        ) {
            this.current = current;
            this.lastAction = lastAction;
            this.candidates = candidates;
            this.recognition = recognition;
        }
    }
}
