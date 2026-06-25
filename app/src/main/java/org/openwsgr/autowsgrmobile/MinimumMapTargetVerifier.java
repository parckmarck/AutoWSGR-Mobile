package org.openwsgr.autowsgrmobile;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinimumMapTargetVerifier {
    private static final Pattern COMPACT_TITLE =
            Pattern.compile("(\\d{1,2})\\s*[-—–]+\\s*(\\d)\\s*/?\\s*(.*)");
    private static final Pattern WIDE_TITLE =
            Pattern.compile("(\\d+)\\s*[-—–]+\\s*(\\d+)\\s*/?\\s*(.*)");

    private MinimumMapTargetVerifier() {
    }

    static TitleIdentity parseTitle(String rawText) {
        String text = normalizeOcrText(rawText);
        if (text.length() == 0) {
            return null;
        }

        Matcher compact = COMPACT_TITLE.matcher(text);
        if (compact.find()) {
            return new TitleIdentity(
                    parseInt(compact.group(1), -1),
                    parseInt(compact.group(2), -1),
                    compact.group(3).trim(),
                    rawText
            );
        }

        Matcher wide = WIDE_TITLE.matcher(text);
        if (!wide.find()) {
            return null;
        }
        return new TitleIdentity(
                parseInt(wide.group(1), -1),
                parseInt(wide.group(2), -1),
                wide.group(3).trim(),
                rawText
        );
    }

    static Result verify(
            CombatPlanPolicy policy,
            MapNodeData mapData,
            TitleSignal titleSignal,
            boolean mapPageMatched
    ) {
        if (policy == null) {
            return blocked("policy-missing", "target=unavailable");
        }

        NormalSortieTarget target;
        try {
            target = policy.normalTarget();
        } catch (IOException e) {
            return blocked("target-unavailable", "reason=" + e.getMessage());
        }

        String base = baseDetail(target, policy, mapData, mapPageMatched);
        if (mapData == null) {
            return blocked("map-data-missing", base);
        }
        String expectedMap = target.mapAssetPath();
        if (!expectedMap.equals(mapData.path)) {
            return blocked(
                    "map-data-mismatch",
                    base + ", expectedMap=" + expectedMap + ", actualMap=" + mapData.path
            );
        }
        String missingSelected = mapData.missingSelected(policy.selectedNodes);
        if (missingSelected.length() > 0) {
            return blocked("route-node-missing", base + missingSelected);
        }
        if (!mapPageMatched) {
            return blocked("map-page-not-recognized", base);
        }

        TitleSignal signal = titleSignal == null
                ? TitleSignal.unavailable("android map-title OCR backend unavailable")
                : titleSignal;
        if (!signal.available) {
            return new Result(
                    true,
                    false,
                    "config-verified",
                    "ocr-unavailable",
                    "mapTargetVerification=config-verified, visualConfirmed=false"
                            + ", ocr=unavailable"
                            + ", reason=" + signal.detail
                            + ", " + base
            );
        }

        TitleIdentity recognized = signal.identity == null
                ? parseTitle(signal.rawText)
                : signal.identity;
        if (recognized == null || !recognized.valid()) {
            return blocked(
                    "map-title-unrecognized",
                    base + ", ocr=recognized, rawTitle=" + sanitize(signal.rawText)
            );
        }
        if (recognized.chapter != target.chapter || recognized.mapNum != target.map) {
            return new Result(
                    false,
                    false,
                    "mismatch",
                    "map-title-mismatch",
                    "mapTargetVerification=mismatch, visualConfirmed=false"
                            + ", target=" + target.label()
                            + ", recognized=" + recognized.label()
                            + ", rawTitle=" + sanitize(signal.rawText)
                            + ", " + base
            );
        }

        return new Result(
                true,
                true,
                "confirmed",
                "map-title-match",
                "mapTargetVerification=confirmed, visualConfirmed=true"
                        + ", source=map-title"
                        + ", target=" + target.label()
                        + ", recognized=" + recognized.label()
                        + ", rawTitle=" + sanitize(signal.rawText)
                        + ", " + base
        );
    }

    private static Result blocked(String reason, String detail) {
        return new Result(
                false,
                false,
                reason.contains("mismatch") ? "mismatch" : "blocked",
                reason,
                "mapTargetVerification="
                        + (reason.contains("mismatch") ? "mismatch" : "blocked")
                        + ", visualConfirmed=false"
                        + ", reason=" + reason
                        + ", " + detail
        );
    }

    private static String baseDetail(
            NormalSortieTarget target,
            CombatPlanPolicy policy,
            MapNodeData mapData,
            boolean mapPageMatched
    ) {
        int selectedCount = policy == null ? 0 : policy.selectedNodes.size();
        int coverage = mapData == null || policy == null ? 0 : mapData.selectedCoverage(policy.selectedNodes);
        String mapPath = mapData == null ? "unavailable" : mapData.path;
        String selected = mapData == null || policy == null
                ? "selectedRoute=unavailable"
                : mapData.selectedSummary(policy.selectedNodes);
        return "target=" + (target == null ? "unavailable" : target.label())
                + ", plan=" + (policy == null ? "unavailable" : policy.path)
                + ", map=" + mapPath
                + ", selectedCoverage=" + coverage + "/" + selectedCount
                + ", mapPageMatched=" + mapPageMatched
                + ", " + selected;
    }

    private static String normalizeOcrText(String rawText) {
        if (rawText == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rawText.length(); i++) {
            char c = rawText.charAt(i);
            if (c >= '０' && c <= '９') {
                out.append((char) ('0' + (c - '０')));
            } else if (c == '－' || c == '–' || c == '—') {
                out.append('-');
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static final class TitleIdentity {
        final int chapter;
        final int mapNum;
        final String name;
        final String rawText;

        TitleIdentity(int chapter, int mapNum, String name, String rawText) {
            this.chapter = chapter;
            this.mapNum = mapNum;
            this.name = name == null ? "" : name;
            this.rawText = rawText == null ? "" : rawText;
        }

        boolean valid() {
            return chapter > 0 && mapNum > 0;
        }

        String label() {
            return String.format(Locale.US, "%d-%d", chapter, mapNum);
        }
    }

    static final class TitleSignal {
        final boolean available;
        final String rawText;
        final TitleIdentity identity;
        final String detail;

        private TitleSignal(boolean available, String rawText, TitleIdentity identity, String detail) {
            this.available = available;
            this.rawText = rawText == null ? "" : rawText;
            this.identity = identity;
            this.detail = detail == null ? "" : detail;
        }

        static TitleSignal recognized(String rawText) {
            TitleIdentity identity = parseTitle(rawText);
            return new TitleSignal(true, rawText, identity, "map-title OCR text available");
        }

        static TitleSignal unavailable(String detail) {
            return new TitleSignal(false, "", null, detail);
        }
    }

    static final class Result {
        final boolean canProceed;
        final boolean visualConfirmed;
        final String status;
        final String reason;
        final String detail;

        Result(boolean canProceed, boolean visualConfirmed, String status, String reason, String detail) {
            this.canProceed = canProceed;
            this.visualConfirmed = visualConfirmed;
            this.status = status;
            this.reason = reason;
            this.detail = detail;
        }
    }
}
