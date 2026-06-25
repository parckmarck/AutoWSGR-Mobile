package org.openwsgr.autowsgrmobile;

final class CalibrationRecordImportAnalyzerTest {
    public static void main(String[] args) {
        verifiesNoFramePreviewIsBlocked();
        verifiesOneReliableSampleStillNeedsMoreSamples();
        verifiesThreeReliableSamplesProduceReadOnlyPreview();
        verifiesUnreliableSampleBlocksPreview();
        verifiesMissingThresholdDataBlocksPreview();
        verifiesEmptyClipboardRemainsUnavailable();
    }

    private static void verifiesNoFramePreviewIsBlocked() {
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(noFrameRecord());
        assertContains(result.detail, "calibrationImport=v1");
        assertContains(result.detail, "thresholdPatchPreview=v1");
        assertContains(result.detail, "previewReady=false");
        assertContains(result.detail, "previewBlockedReason=needs-real-frame");
        assertContains(result.detail, "applyPatch=false");
        assertContains(result.detail, "requiresManualConfirmation=true");
        assertContains(result.detail, "thresholdMutation=disabled");
        assertContains(result.detail, "calibrationReviewReport=v1");
        assertContains(result.detail, "reportReady=false");
        assertContains(result.detail, "reportFormat=markdown-text");
        assertContains(result.detail, "reportBlockedReason=needs-real-frame");
        assertContains(result.detail, "reportMarkdown=# AutoWSGR Calibration Review\\nStatus: blocked\\nReason: needs-real-frame");
        assertContains(result.detail, "Imported records: 1");
        assertContains(result.detail, "Sampled frames: 0");
        assertContains(result.detail, "No-frame records: 1");
        assertContains(result.detail, "Safety: applyPatch=false, requiresManualConfirmation=true, thresholdMutation=disabled");
    }

    private static void verifiesOneReliableSampleStillNeedsMoreSamples() {
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(reliableRecord(1));
        assertContains(result.detail, "importedRecords=1");
        assertContains(result.detail, "importedSampleCount=1");
        assertContains(result.detail, "thresholdPatchPreview=v1");
        assertContains(result.detail, "previewReady=false");
        assertContains(result.detail, "previewBlockedReason=needs-more-samples");
    }

    private static void verifiesThreeReliableSamplesProduceReadOnlyPreview() {
        String clipboard = reliableRecord(1) + "\n\n" + reliableRecord(2) + "\n\n" + reliableRecord(3);
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(clipboard);
        assertContains(result.detail, "importedRecords=3");
        assertContains(result.detail, "importedSampleCount=3");
        assertContains(result.detail, "thresholdPatchPreview=v1");
        assertContains(result.detail, "previewReady=true");
        assertContains(result.detail, "previewBlockedReason=none");
        assertContains(result.detail, "shipPrototypeDistanceLimitPatch=preserve 14");
        assertContains(result.detail, "formationSignatureThresholdPatch=review");
        assertContains(result.detail, "candidatePatchText=");
        assertContains(result.detail, "EnemyCompositionRecognizer.CLASSIFIER_DISTANCE_LIMIT");
        assertContains(result.detail, "EnemyFormationRecognizer.SignatureFormationBackend");
        assertContains(result.detail, "calibrationReviewReport=v1");
        assertContains(result.detail, "reportReady=true");
        assertContains(result.detail, "reportFormat=markdown-text");
        assertContains(result.detail, "reportBlockedReason=none");
        assertContains(result.detail, "Status: ready for manual review");
        assertContains(result.detail, "Ship threshold source: EnemyCompositionRecognizer.CLASSIFIER_DISTANCE_LIMIT");
        assertContains(result.detail, "Ship prototype distance limit: preserve 14");
        assertContains(result.detail, "Formation threshold source: EnemyFormationRecognizer.SignatureFormationBackend");
        assertContains(result.detail, "Formation signature thresholds: review wheelHash=e0e01f1f1fe0e0e0, mean=100-125, sd=30-46, hashDistance<=6");
        assertContains(result.detail, "Candidate patch text: review EnemyCompositionRecognizer.CLASSIFIER_DISTANCE_LIMIT=14");
        assertContains(result.detail, "Safety: applyPatch=false, requiresManualConfirmation=true, thresholdMutation=disabled");
        assertContains(result.detail, "applyPatch=false");
        assertContains(result.detail, "requiresManualConfirmation=true");
        if (!result.ready) {
            throw new AssertionError("three reliable samples should make the import ready");
        }
    }

    private static void verifiesUnreliableSampleBlocksPreview() {
        String unreliable = reliableRecord(1)
                .replace("compositionReliable=true", "compositionReliable=false")
                .replace("runnerSpotContextReady=true", "runnerSpotContextReady=false");
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(unreliable + "\n\n" + reliableRecord(2) + "\n\n" + reliableRecord(3));
        assertContains(result.detail, "thresholdPatchPreview=v1");
        assertContains(result.detail, "previewReady=false");
        assertContains(result.detail, "previewBlockedReason=unreliable-imported-samples");
    }

    private static void verifiesMissingThresholdDataBlocksPreview() {
        String missingThresholds = reliableRecord(1)
                .replace("shipPrototypeDistanceLimit=14", "")
                .replace("shipPrototypeThresholds=fight prototypes=DD{mean=49,sd=14,edge=0,hash=aa55aaaa55aaaa55}", "")
                .replace("formationSignatureThresholds=wheelHash=e0e01f1f1fe0e0e0, mean=100-125, sd=30-46, hashDistance<=6", "");
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(missingThresholds + "\n\n" + reliableRecord(2) + "\n\n" + reliableRecord(3));
        assertContains(result.detail, "thresholdPatchPreview=v1");
        assertContains(result.detail, "previewReady=false");
        assertContains(result.detail, "previewBlockedReason=missing-threshold-data");
    }

    private static void verifiesEmptyClipboardRemainsUnavailable() {
        CalibrationRecordImportAnalyzer.Result empty =
                CalibrationRecordImportAnalyzer.analyze("");
        assertContains(empty.detail, "calibrationImport=unavailable");
        assertContains(empty.detail, "reason=empty clipboard");
        if (empty.recordCount != 0) {
            throw new AssertionError("empty clipboard should import zero records");
        }
        assertNotContains(empty.detail, "calibrationReviewReport=v1");
        assertNotContains(empty.detail, "reportReady=true");
    }

    private static String noFrameRecord() {
        return "autowsgrLiveSpotCalibration=v1\n"
                + "source=last-live-spot-diagnostics\n"
                + "status=Live spot diagnostics: no frame\n"
                + "live spot diagnostics=unavailable, reason=missing MediaProjection frame\n"
                + "calibrationTuning=v1\n"
                + "shipPrototypeTuningReady=false, shipPrototypeStatus=needs-real-frame, shipPrototypeDistanceLimit=14\n"
                + "shipPrototypeThresholds=fight prototypes=DD{mean=49,sd=14,edge=0,hash=aa55aaaa55aaaa55}\n"
                + "formationSignatureTuningReady=false, formationSignatureStatus=needs-real-frame\n"
                + "formationSignatureThresholds=wheelHash=e0e01f1f1fe0e0e0, mean=100-125, sd=30-46, hashDistance<=6\n"
                + "calibrationSampleSet=v1\n"
                + "sampleCount=0, minRecommendedSamples=3, acceptedSamples=0\n";
    }

    private static String reliableRecord(int sampleNumber) {
        return "autowsgrLiveSpotCalibration=v1\n"
                + "source=last-live-spot-diagnostics\n"
                + "status=Live spot diagnostics: ready\n"
                + "live spot diagnostics=sampled, compositionReliable=true, formationReliable=true, runnerSpotContextReady=true\n"
                + "calibrationTuning=v1\n"
                + "shipPrototypeTuningReady=true, shipPrototypeStatus=accepted, shipPrototypeDistanceLimit=14\n"
                + "shipPrototypeThresholds=fight prototypes=DD{mean=49,sd=14,edge=0,hash=aa55aaaa55aaaa55}\n"
                + "formationSignatureTuningReady=true, formationSignatureStatus=accepted\n"
                + "formationSignatureThresholds=wheelHash=e0e01f1f1fe0e0e0, mean=100-125, sd=30-46, hashDistance<=6\n"
                + "calibrationSampleSet=v1\n"
                + "sampleCount=" + sampleNumber + ", minRecommendedSamples=3, acceptedSamples=" + sampleNumber + "\n";
    }

    private static void assertContains(String text, String expected) {
        if (text == null || !text.contains(expected)) {
            throw new AssertionError("missing: " + expected + "\n" + text);
        }
    }

    private static void assertNotContains(String text, String unexpected) {
        if (text != null && text.contains(unexpected)) {
            throw new AssertionError("unexpected: " + unexpected + "\n" + text);
        }
    }
}
