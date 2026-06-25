package org.openwsgr.autowsgrmobile;

final class LiveSpotCalibrationTuner {
    private LiveSpotCalibrationTuner() {
    }

    static Result noFrameBaseline() {
        return new Result(detail(
                false,
                "needs-real-frame",
                "no composition sample; authorize capture and open spot_enemy_success",
                false,
                "needs-real-frame",
                "no formation ROI sample; authorize capture and open spot_enemy_success"
        ));
    }

    static Result forDetections(
            EnemyCompositionRecognizer.Detection composition,
            EnemyFormationRecognizer.Detection formation
    ) {
        boolean shipReady = composition != null && composition.reliable;
        boolean hasShipSample = composition != null && composition.sampleSummary.length() > 0;
        String shipStatus = shipReady
                ? "accepted"
                : (hasShipSample ? "needs-threshold-review" : "needs-real-frame");
        String shipReason = composition == null
                ? "missing composition detection"
                : composition.reason + ", recognized=" + composition.recognizedSlots
                        + "/" + composition.inputSlots
                        + ", unknown=" + composition.unknownSlots;

        boolean formationReady = formation != null && formation.reliable;
        boolean hasFormationSample = formation != null && formation.sampleSummary.length() > 0;
        String formationStatus = formationReady
                ? "accepted"
                : (hasFormationSample ? "needs-signature-review" : "needs-real-frame");
        String formationReason = formation == null
                ? "missing formation detection"
                : formation.reason + ", formation="
                        + (formation.formation.length() == 0 ? "none" : formation.formation);

        return new Result(detail(
                shipReady,
                shipStatus,
                shipReason,
                formationReady,
                formationStatus,
                formationReason
        ));
    }

    private static String detail(
            boolean shipReady,
            String shipStatus,
            String shipReason,
            boolean formationReady,
            String formationStatus,
            String formationReason
    ) {
        return "calibrationTuning=v1"
                + "\nshipPrototypeTuningReady=" + shipReady
                + ", shipPrototypeStatus=" + shipStatus
                + ", shipPrototypeDistanceLimit=" + EnemyCompositionRecognizer.classifierDistanceLimit()
                + ", shipPrototypeReason=" + sanitize(shipReason)
                + "\nshipPrototypeThresholds=" + EnemyCompositionRecognizer.classifierPrototypeSummary()
                + "\nformationSignatureTuningReady=" + formationReady
                + ", formationSignatureStatus=" + formationStatus
                + ", formationSignatureReason=" + sanitize(formationReason)
                + "\nformationSignatureThresholds=" + EnemyFormationRecognizer.formationSignatureSummary();
    }

    private static String sanitize(String text) {
        if (text == null || text.trim().length() == 0) {
            return "none";
        }
        return text.replace('\n', ' ').trim();
    }

    static final class Result {
        final String detail;

        Result(String detail) {
            this.detail = detail;
        }
    }
}
