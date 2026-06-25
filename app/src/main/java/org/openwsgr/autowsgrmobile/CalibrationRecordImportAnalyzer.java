package org.openwsgr.autowsgrmobile;

import java.util.ArrayList;
import java.util.List;

final class CalibrationRecordImportAnalyzer {
    private static final String RECORD_MARKER = "autowsgrLiveSpotCalibration=v1";
    private static final int MIN_RECOMMENDED_SAMPLES = 3;

    private CalibrationRecordImportAnalyzer() {
    }

    static Result analyze(CharSequence clipboardText) {
        String text = clipboardText == null ? "" : clipboardText.toString();
        if (text.trim().length() == 0) {
            return unavailable("empty clipboard");
        }

        List<String> records = recordsIn(text);
        if (records.isEmpty()) {
            return unavailable("no autowsgr live spot calibration records");
        }

        int sampledRecords = 0;
        int noFrameRecords = 0;
        int acceptedReliableSamples = 0;
        int unreliableShipSamples = 0;
        int unreliableFormationSamples = 0;

        ArrayList<RecordSummary> summaries = new ArrayList<>();
        for (String record : records) {
            boolean sampled = containsAny(
                    record,
                    "live spot diagnostics=sampled",
                    "runnerSpotContextReady=true",
                    "status=Live spot diagnostics: ready"
            );
            boolean noFrame = containsAny(
                    record,
                    "missing MediaProjection frame",
                    "Live spot diagnostics: no frame",
                    "needs-real-frame"
            ) && !sampled;
            boolean reliableShip = record.contains("compositionReliable=true");
            boolean reliableFormation = record.contains("formationReliable=true");
            boolean unreliableShip = containsAny(
                    record,
                    "compositionReliable=false",
                    "shipPrototypeSampleAdvice=review-distance-limit"
            );
            boolean unreliableFormation = containsAny(
                    record,
                    "formationReliable=false",
                    "formationSignatureSampleAdvice=review-signature-thresholds"
            );
            RecordSummary summary = new RecordSummary(
                    sampled,
                    noFrame,
                    reliableShip,
                    reliableFormation,
                    valueAfter(record, "shipPrototypeDistanceLimit="),
                    lineAfter(record, "shipPrototypeThresholds="),
                    lineAfter(record, "formationSignatureThresholds=")
            );
            summaries.add(summary);

            if (sampled) {
                sampledRecords++;
                if (reliableShip && reliableFormation) {
                    acceptedReliableSamples++;
                }
                if (!reliableShip || unreliableShip) {
                    unreliableShipSamples++;
                }
                if (!reliableFormation || unreliableFormation) {
                    unreliableFormationSamples++;
                }
            }
            if (noFrame) {
                noFrameRecords++;
            }
        }

        String shipCandidate = shipCandidate(sampledRecords, unreliableShipSamples);
        String formationCandidate = formationCandidate(sampledRecords, unreliableFormationSamples);
        boolean importReady = sampledRecords >= MIN_RECOMMENDED_SAMPLES
                && unreliableShipSamples == 0
                && unreliableFormationSamples == 0;
        Preview preview = previewFor(summaries, sampledRecords, unreliableShipSamples, unreliableFormationSamples);

        String detail = "calibrationImport=v1"
                + "\nimportedRecords=" + records.size()
                + ", importedSampleCount=" + sampledRecords
                + ", noFrameRecords=" + noFrameRecords
                + ", minRecommendedSamples=" + MIN_RECOMMENDED_SAMPLES
                + "\nacceptedReliableSamples=" + acceptedReliableSamples
                + ", unreliableShipSamples=" + unreliableShipSamples
                + ", unreliableFormationSamples=" + unreliableFormationSamples
                + "\nimportReady=" + importReady
                + "\nshipPrototypePatchCandidate=" + shipCandidate
                + ", formationSignaturePatchCandidate=" + formationCandidate
                + "\nthresholdPatchRequiresConfirmation=true"
                + "\n" + previewDetail(preview)
                + "\n" + reviewReportDetail(records.size(), sampledRecords, noFrameRecords, preview)
                + "\nthresholdMutation=disabled";
        return new Result(preview.ready, records.size(), sampledRecords, detail);
    }

    private static Result unavailable(String reason) {
        return new Result(
                false,
                0,
                0,
                "calibrationImport=unavailable, reason=" + reason
                        + "\nthresholdPatchRequiresConfirmation=true"
                        + "\nthresholdMutation=disabled"
        );
    }

    private static List<String> recordsIn(String text) {
        ArrayList<String> records = new ArrayList<>();
        int index = text.indexOf(RECORD_MARKER);
        while (index >= 0) {
            int next = text.indexOf(RECORD_MARKER, index + RECORD_MARKER.length());
            int end = next >= 0 ? next : text.length();
            String record = text.substring(index, end).trim();
            if (record.length() > RECORD_MARKER.length()) {
                records.add(record);
            }
            index = next;
        }
        return records;
    }

    private static String previewDetail(Preview preview) {
        StringBuilder detail = new StringBuilder();
        detail.append("thresholdPatchPreview=v1")
                .append('\n')
                .append("previewReady=").append(preview.ready)
                .append('\n')
                .append("previewBlockedReason=").append(preview.blockedReason)
                .append('\n')
                .append("applyPatch=false")
                .append('\n')
                .append("requiresManualConfirmation=true");
        if (preview.ready) {
            detail.append('\n')
                    .append("shipPrototypeDistanceLimitPatch=preserve ")
                    .append(preview.shipDistanceLimit)
                    .append('\n')
                    .append("formationSignatureThresholdPatch=review ")
                    .append(preview.formationThresholds)
                    .append('\n')
                    .append("candidatePatchText=")
                    .append(preview.candidatePatchText);
        }
        return detail.toString();
    }

    private static String reviewReportDetail(
            int importedRecords,
            int sampledRecords,
            int noFrameRecords,
            Preview preview
    ) {
        Preview reportPreview = safeReportPreview(preview);
        String blockedReason = safeBlockedReason(reportPreview);
        String markdown = buildReportMarkdown(
                importedRecords,
                sampledRecords,
                noFrameRecords,
                reportPreview,
                blockedReason
        );

        return "calibrationReviewReport=v1"
                + "\nreportReady=" + reportPreview.ready
                + "\nreportFormat=markdown-text"
                + "\nreportBlockedReason=" + blockedReason
                + "\nreportMarkdown=" + escapeReportMarkdown(markdown);
    }

    private static Preview safeReportPreview(Preview preview) {
        if (preview == null) {
            return Preview.blocked("missing-threshold-data");
        }
        if (preview.ready
                && (!hasText(preview.shipDistanceLimit)
                || !hasText(preview.formationThresholds)
                || !hasText(preview.candidatePatchText))) {
            return Preview.blocked("missing-threshold-data");
        }
        return preview;
    }

    private static String safeBlockedReason(Preview preview) {
        String reason = sanitizeOneLine(preview.blockedReason);
        if (reason.length() > 0) {
            return reason;
        }
        return preview.ready ? "none" : "missing-threshold-data";
    }

    private static String buildReportMarkdown(
            int importedRecords,
            int sampledRecords,
            int noFrameRecords,
            Preview preview,
            String blockedReason
    ) {
        StringBuilder report = new StringBuilder();
        appendReportLine(report, "# AutoWSGR Calibration Review");
        appendReportLine(report, preview.ready ? "Status: ready for manual review" : "Status: blocked");
        appendReportLine(report, "Reason: " + blockedReason);
        appendReportLine(report, "Imported records: " + importedRecords);
        appendReportLine(report, "Sampled frames: " + sampledRecords);
        appendReportLine(report, "No-frame records: " + noFrameRecords);
        if (preview.ready) {
            appendReportLine(report, "Ship threshold source: EnemyCompositionRecognizer.CLASSIFIER_DISTANCE_LIMIT");
            appendReportLine(report, "Ship prototype distance limit: preserve "
                    + sanitizeOneLine(preview.shipDistanceLimit));
            appendReportLine(report, "Formation threshold source: EnemyFormationRecognizer.SignatureFormationBackend");
            appendReportLine(report, "Formation signature thresholds: review "
                    + sanitizeOneLine(preview.formationThresholds));
            appendReportLine(report, "Candidate patch text: "
                    + sanitizeOneLine(preview.candidatePatchText));
        }
        appendReportLine(report, "Safety: applyPatch=false, requiresManualConfirmation=true, thresholdMutation=disabled");
        return report.toString();
    }

    private static Preview previewFor(
            List<RecordSummary> summaries,
            int sampledRecords,
            int unreliableShipSamples,
            int unreliableFormationSamples
    ) {
        if (sampledRecords == 0) {
            return Preview.blocked("needs-real-frame");
        }
        if (unreliableShipSamples > 0 || unreliableFormationSamples > 0) {
            return Preview.blocked("unreliable-imported-samples");
        }
        if (sampledRecords < MIN_RECOMMENDED_SAMPLES) {
            return Preview.blocked("needs-more-samples");
        }

        String distanceLimit = "";
        String shipThresholds = "";
        String formationThresholds = "";
        for (RecordSummary summary : summaries) {
            if (!summary.sampled) {
                continue;
            }
            if (summary.shipDistanceLimit.length() == 0
                    || summary.shipThresholds.length() == 0
                    || summary.formationThresholds.length() == 0) {
                return Preview.blocked("missing-threshold-data");
            }
            if (distanceLimit.length() == 0) {
                distanceLimit = summary.shipDistanceLimit;
            } else if (!distanceLimit.equals(summary.shipDistanceLimit)) {
                return Preview.blocked("missing-threshold-data");
            }
            if (shipThresholds.length() == 0) {
                shipThresholds = summary.shipThresholds;
            }
            if (formationThresholds.length() == 0) {
                formationThresholds = summary.formationThresholds;
            }
        }
        if (distanceLimit.length() == 0 || shipThresholds.length() == 0 || formationThresholds.length() == 0) {
            return Preview.blocked("missing-threshold-data");
        }
        String candidateText = "review EnemyCompositionRecognizer.CLASSIFIER_DISTANCE_LIMIT="
                + distanceLimit
                + "; baseline ship thresholds "
                + shipThresholds
                + "; review EnemyFormationRecognizer.SignatureFormationBackend gates "
                + formationThresholds;
        return Preview.ready(distanceLimit, shipThresholds, formationThresholds, sanitizeOneLine(candidateText));
    }

    private static String shipCandidate(int sampledRecords, int unreliableShipSamples) {
        if (sampledRecords == 0) {
            return "needs-real-frame";
        }
        if (unreliableShipSamples > 0) {
            return "review-distance-limit";
        }
        if (sampledRecords < MIN_RECOMMENDED_SAMPLES) {
            return "needs-more-samples";
        }
        return "keep-current-thresholds";
    }

    private static String formationCandidate(int sampledRecords, int unreliableFormationSamples) {
        if (sampledRecords == 0) {
            return "needs-real-frame";
        }
        if (unreliableFormationSamples > 0) {
            return "review-signature-thresholds";
        }
        if (sampledRecords < MIN_RECOMMENDED_SAMPLES) {
            return "needs-more-samples";
        }
        return "keep-current-thresholds";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String valueAfter(String text, String key) {
        String line = lineAfter(text, key);
        if (line.length() == 0) {
            return "";
        }
        int comma = line.indexOf(',');
        return comma >= 0 ? line.substring(0, comma).trim() : line.trim();
    }

    private static String lineAfter(String text, String key) {
        int index = text.indexOf(key);
        if (index < 0) {
            return "";
        }
        int valueStart = index + key.length();
        int newline = text.indexOf('\n', valueStart);
        int end = newline >= 0 ? newline : text.length();
        return text.substring(valueStart, end).trim();
    }

    private static String sanitizeOneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void appendReportLine(StringBuilder report, String line) {
        if (report.length() > 0) {
            report.append('\n');
        }
        report.append(line);
    }

    private static boolean hasText(String text) {
        return text != null && text.trim().length() > 0;
    }

    private static String escapeReportMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "").replace("\n", "\\n").trim();
    }

    private static final class RecordSummary {
        final boolean sampled;
        final boolean noFrame;
        final boolean reliableShip;
        final boolean reliableFormation;
        final String shipDistanceLimit;
        final String shipThresholds;
        final String formationThresholds;

        RecordSummary(
                boolean sampled,
                boolean noFrame,
                boolean reliableShip,
                boolean reliableFormation,
                String shipDistanceLimit,
                String shipThresholds,
                String formationThresholds
        ) {
            this.sampled = sampled;
            this.noFrame = noFrame;
            this.reliableShip = reliableShip;
            this.reliableFormation = reliableFormation;
            this.shipDistanceLimit = shipDistanceLimit;
            this.shipThresholds = shipThresholds;
            this.formationThresholds = formationThresholds;
        }
    }

    private static final class Preview {
        final boolean ready;
        final String blockedReason;
        final String shipDistanceLimit;
        final String shipThresholds;
        final String formationThresholds;
        final String candidatePatchText;

        private Preview(
                boolean ready,
                String blockedReason,
                String shipDistanceLimit,
                String shipThresholds,
                String formationThresholds,
                String candidatePatchText
        ) {
            this.ready = ready;
            this.blockedReason = blockedReason;
            this.shipDistanceLimit = shipDistanceLimit;
            this.shipThresholds = shipThresholds;
            this.formationThresholds = formationThresholds;
            this.candidatePatchText = candidatePatchText;
        }

        static Preview blocked(String reason) {
            return new Preview(false, reason, "", "", "", "");
        }

        static Preview ready(
                String shipDistanceLimit,
                String shipThresholds,
                String formationThresholds,
                String candidatePatchText
        ) {
            return new Preview(
                    true,
                    "none",
                    shipDistanceLimit,
                    shipThresholds,
                    formationThresholds,
                    candidatePatchText
            );
        }
    }

    static final class Result {
        final boolean ready;
        final int recordCount;
        final int sampleCount;
        final String detail;

        Result(boolean ready, int recordCount, int sampleCount, String detail) {
            this.ready = ready;
            this.recordCount = recordCount;
            this.sampleCount = sampleCount;
            this.detail = detail;
        }
    }
}
