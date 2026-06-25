package org.openwsgr.autowsgrmobile;

final class CalibrationSampleSetAnalyzer {
    private static final int MIN_RECOMMENDED_SAMPLES = 3;

    private static int sampleCount;
    private static int acceptedSamples;
    private static int unreliableShipSamples;
    private static int unreliableFormationSamples;

    private CalibrationSampleSetAnalyzer() {
    }

    static synchronized String noFrameSummary() {
        return detail(
                "needs-real-frame",
                "needs-real-frame",
                sampleCount == 0 ? "needs-real-frame" : multiSampleAdvice()
        );
    }

    static synchronized String record(
            EnemyCompositionRecognizer.Detection composition,
            EnemyFormationRecognizer.Detection formation
    ) {
        sampleCount++;
        boolean shipAccepted = composition != null && composition.reliable;
        boolean formationAccepted = formation != null && formation.reliable;
        if (shipAccepted && formationAccepted) {
            acceptedSamples++;
        }
        if (!shipAccepted) {
            unreliableShipSamples++;
        }
        if (!formationAccepted) {
            unreliableFormationSamples++;
        }
        return detail(shipAdvice(), formationAdvice(), multiSampleAdvice());
    }

    static synchronized void resetForTests() {
        sampleCount = 0;
        acceptedSamples = 0;
        unreliableShipSamples = 0;
        unreliableFormationSamples = 0;
    }

    private static String shipAdvice() {
        if (sampleCount == 0) {
            return "needs-real-frame";
        }
        if (unreliableShipSamples > 0) {
            return "review-distance-limit";
        }
        if (acceptedSamples < MIN_RECOMMENDED_SAMPLES) {
            return "needs-more-samples";
        }
        return "keep-current-thresholds";
    }

    private static String formationAdvice() {
        if (sampleCount == 0) {
            return "needs-real-frame";
        }
        if (unreliableFormationSamples > 0) {
            return "review-signature-thresholds";
        }
        if (acceptedSamples < MIN_RECOMMENDED_SAMPLES) {
            return "needs-more-samples";
        }
        return "keep-current-thresholds";
    }

    private static String multiSampleAdvice() {
        if (sampleCount == 0) {
            return "needs-real-frame";
        }
        if (unreliableShipSamples > 0 || unreliableFormationSamples > 0) {
            return "review-thresholds";
        }
        if (acceptedSamples < MIN_RECOMMENDED_SAMPLES) {
            return "needs-more-samples";
        }
        return "keep-current-thresholds";
    }

    private static String detail(
            String shipAdvice,
            String formationAdvice,
            String multiAdvice
    ) {
        return "calibrationSampleSet=v1"
                + "\nsampleCount=" + sampleCount
                + ", minRecommendedSamples=" + MIN_RECOMMENDED_SAMPLES
                + ", acceptedSamples=" + acceptedSamples
                + ", unreliableShipSamples=" + unreliableShipSamples
                + ", unreliableFormationSamples=" + unreliableFormationSamples
                + "\nshipPrototypeSampleAdvice=" + shipAdvice
                + ", formationSignatureSampleAdvice=" + formationAdvice
                + ", multiSampleAdvice=" + multiAdvice;
    }
}
