package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

import org.openwsgr.autowsgrmobile.vision.CompositePixelSignature;
import org.openwsgr.autowsgrmobile.vision.PixelRule;
import org.openwsgr.autowsgrmobile.vision.PixelSignature;
import org.openwsgr.autowsgrmobile.vision.VisionColor;

import java.util.Arrays;

final class NativePhasePixelSignatures {
    private static final PixelSignature CHOOSE_FORMATION = new PixelSignature(
            "choose_formation",
            Arrays.asList(
                    rule(0.9413f, 0.2011f, 227, 227, 227, 30.0),
                    rule(0.9375f, 0.3644f, 227, 227, 227, 30.0),
                    rule(0.9569f, 0.5278f, 227, 227, 227, 30.0),
                    rule(0.5050f, 0.5678f, 24, 101, 181, 30.0),
                    rule(0.5019f, 0.9478f, 26, 103, 183, 30.0),
                    rule(0.8531f, 0.9367f, 32, 134, 219, 30.0)
            )
    );

    private static final PixelSignature EVENT_MAP_BASE = new PixelSignature(
            "event_map_page",
            Arrays.asList(
                    rule(0.8422f, 0.0500f, 209, 211, 232, 30.0),
                    rule(0.9047f, 0.0528f, 217, 217, 225, 30.0),
                    rule(0.9352f, 0.8861f, 211, 208, 225, 30.0)
            )
    );

    private static final PixelSignature EVENT_MAP_OVERLAY = new PixelSignature(
            "event_map_overlay",
            Arrays.asList(
                    rule(0.2672f, 0.0889f, 34, 143, 246, 30.0),
                    rule(0.7734f, 0.8514f, 29, 124, 214, 30.0),
                    rule(0.7719f, 0.5917f, 237, 237, 237, 30.0),
                    rule(0.6133f, 0.8556f, 212, 212, 212, 30.0)
            )
    );

    private static final CompositePixelSignature EVENT_MAP_COMPOSITE =
            new CompositePixelSignature(
                    "event_map_page_composite",
                    Arrays.asList(EVENT_MAP_BASE, EVENT_MAP_OVERLAY)
            );
    private static final PixelSignature MAP_PAGE_NORMAL_SORTIE = new PixelSignature(
            "normal_map_sortie_page",
            Arrays.asList(
                    rule(0.1539f, 0.0472f, 16, 132, 228, 45.0),
                    rule(0.2719f, 0.0625f, 21, 36, 60, 45.0),
                    rule(0.4039f, 0.0528f, 23, 38, 63, 45.0),
                    rule(0.5359f, 0.0500f, 23, 37, 63, 45.0),
                    rule(0.6641f, 0.0500f, 22, 38, 63, 45.0),
                    rule(0.530f, 0.500f, 36, 54, 69, 55.0)
            )
    );
    private static final CompositePixelSignature MAP_PAGE_COMPOSITE =
            new CompositePixelSignature(
                    "map_page",
                    Arrays.asList(MAP_PAGE_NORMAL_SORTIE)
            );
    private static final PixelSignature PROCEED_SUPPLY_PROMPT = new PixelSignature(
            "proceed_supply_prompt",
            Arrays.asList(
                    rule(0.440f, 0.055f, 13, 44, 74, 35.0),
                    rule(0.560f, 0.055f, 30, 56, 79, 60.0),
                    rule(0.500f, 0.420f, 237, 237, 237, 35.0),
                    rule(0.490f, 0.500f, 64, 161, 109, 45.0),
                    rule(0.305f, 0.620f, 164, 1, 1, 60.0),
                    rule(0.620f, 0.620f, 40, 151, 255, 60.0)
            )
    );
    private static final PixelSignature PROCEED_RETURN_PORT_PROMPT = new PixelSignature(
            "proceed_return_port_prompt",
            Arrays.asList(
                    rule(0.440f, 0.055f, 13, 44, 74, 45.0),
                    rule(0.500f, 0.420f, 237, 237, 237, 45.0),
                    rule(0.500f, 0.500f, 64, 161, 109, 55.0),
                    rule(0.500f, 0.552f, 64, 161, 109, 55.0),
                    rule(0.323f, 0.653f, 151, 8, 8, 75.0),
                    rule(0.655f, 0.653f, 33, 141, 243, 75.0)
            )
    );
    private static final CompositePixelSignature PROCEED_PAGE_COMPOSITE =
            new CompositePixelSignature(
                    "proceed_page",
                    Arrays.asList(PROCEED_SUPPLY_PROMPT, PROCEED_RETURN_PORT_PROMPT)
            );
    private static final PixelSignature RESULT_PAGE_SUMMARY = new PixelSignature(
            "combat_result_summary",
            Arrays.asList(
                    rule(0.545f, 0.092f, 255, 255, 255, 55.0),
                    rule(0.916f, 0.915f, 255, 255, 255, 55.0),
                    rule(0.620f, 0.389f, 226, 182, 66, 75.0),
                    rule(0.700f, 0.389f, 230, 188, 68, 75.0),
                    rule(0.200f, 0.265f, 46, 174, 66, 80.0),
                    rule(0.500f, 0.500f, 15, 18, 22, 45.0)
            )
    );
    private static final CompositePixelSignature RESULT_PAGE_COMPOSITE =
            new CompositePixelSignature(
                    "combat_result_page",
                    Arrays.asList(RESULT_PAGE_SUMMARY)
            );
    private static final PixelSignature GET_SHIP_DROP = new PixelSignature(
            "get_ship_drop",
            Arrays.asList(
                    rule(0.815f, 0.285f, 171, 118, 67, 95.0),
                    rule(0.200f, 0.780f, 38, 98, 185, 75.0),
                    rule(0.185f, 0.830f, 16, 41, 60, 45.0),
                    rule(0.835f, 0.830f, 14, 35, 51, 45.0),
                    rule(0.050f, 0.170f, 31, 38, 64, 45.0),
                    rule(0.940f, 0.700f, 28, 81, 124, 60.0)
            )
    );
    private static final PixelSignature GET_SHIP_DIALOG = new PixelSignature(
            "get_ship_dialog",
            Arrays.asList(
                    rule(0.792f, 0.318f, 255, 226, 141, 100.0),
                    rule(0.872f, 0.273f, 28, 51, 82, 75.0),
                    rule(0.066f, 0.705f, 35, 72, 110, 60.0),
                    rule(0.066f, 0.802f, 20, 40, 63, 60.0),
                    rule(0.220f, 0.815f, 99, 116, 129, 80.0),
                    rule(0.520f, 0.790f, 45, 44, 43, 75.0)
            )
    );
    private static final CompositePixelSignature GET_SHIP_PAGE_COMPOSITE =
            new CompositePixelSignature(
                    "get_ship_page",
                    Arrays.asList(GET_SHIP_DROP, GET_SHIP_DIALOG)
            );

    private NativePhasePixelSignatures() {
    }

    static PixelMatch match(Kind kind, Bitmap bitmap) {
        switch (kind) {
            case CHOOSE_FORMATION:
                return matchSingle(CHOOSE_FORMATION, bitmap);
            case EVENT_MAP_PAGE:
                CompositePixelSignature.Match match = EVENT_MAP_COMPOSITE.match(bitmap);
                return new PixelMatch(
                        match.matched,
                        EVENT_MAP_COMPOSITE.name,
                        match.matchedSignatureName,
                        match.matchedRules,
                        match.totalRules,
                        EVENT_MAP_COMPOSITE.signatureCount()
                );
            case MAP_PAGE:
                CompositePixelSignature.Match mapMatch = MAP_PAGE_COMPOSITE.match(bitmap);
                return new PixelMatch(
                        mapMatch.matched,
                        MAP_PAGE_COMPOSITE.name,
                        mapMatch.matchedSignatureName,
                        mapMatch.matchedRules,
                        mapMatch.totalRules,
                        MAP_PAGE_COMPOSITE.signatureCount()
                );
            case PROCEED_PAGE:
                CompositePixelSignature.Match proceedMatch = PROCEED_PAGE_COMPOSITE.match(bitmap);
                return new PixelMatch(
                        proceedMatch.matched,
                        PROCEED_PAGE_COMPOSITE.name,
                        proceedMatch.matchedSignatureName,
                        proceedMatch.matchedRules,
                        proceedMatch.totalRules,
                        PROCEED_PAGE_COMPOSITE.signatureCount()
                );
            case RESULT_PAGE:
                CompositePixelSignature.Match resultMatch = RESULT_PAGE_COMPOSITE.match(bitmap);
                return new PixelMatch(
                        resultMatch.matched,
                        RESULT_PAGE_COMPOSITE.name,
                        resultMatch.matchedSignatureName,
                        resultMatch.matchedRules,
                        resultMatch.totalRules,
                        RESULT_PAGE_COMPOSITE.signatureCount()
                );
            case GET_SHIP_PAGE:
                CompositePixelSignature.Match getShipMatch = GET_SHIP_PAGE_COMPOSITE.match(bitmap);
                return new PixelMatch(
                        getShipMatch.matched,
                        GET_SHIP_PAGE_COMPOSITE.name,
                        getShipMatch.matchedSignatureName,
                        getShipMatch.matchedRules,
                        getShipMatch.totalRules,
                        GET_SHIP_PAGE_COMPOSITE.signatureCount()
                );
            default:
                return PixelMatch.missing("unknown pixel signature");
        }
    }

    static String signatureName(Kind kind) {
        switch (kind) {
            case CHOOSE_FORMATION:
                return CHOOSE_FORMATION.name;
            case EVENT_MAP_PAGE:
                return EVENT_MAP_COMPOSITE.name;
            case MAP_PAGE:
                return MAP_PAGE_COMPOSITE.name;
            case PROCEED_PAGE:
                return PROCEED_PAGE_COMPOSITE.name;
            case RESULT_PAGE:
                return RESULT_PAGE_COMPOSITE.name;
            case GET_SHIP_PAGE:
                return GET_SHIP_PAGE_COMPOSITE.name;
            default:
                return "";
        }
    }

    static int totalRuleCount(Kind kind) {
        switch (kind) {
            case CHOOSE_FORMATION:
                return CHOOSE_FORMATION.ruleCount();
            case EVENT_MAP_PAGE:
                return EVENT_MAP_COMPOSITE.totalRuleCount();
            case MAP_PAGE:
                return MAP_PAGE_COMPOSITE.totalRuleCount();
            case PROCEED_PAGE:
                return PROCEED_PAGE_COMPOSITE.totalRuleCount();
            case RESULT_PAGE:
                return RESULT_PAGE_COMPOSITE.totalRuleCount();
            case GET_SHIP_PAGE:
                return GET_SHIP_PAGE_COMPOSITE.totalRuleCount();
            default:
                return 0;
        }
    }

    static int signatureVariantCount(Kind kind) {
        switch (kind) {
            case EVENT_MAP_PAGE:
                return EVENT_MAP_COMPOSITE.signatureCount();
            case MAP_PAGE:
                return MAP_PAGE_COMPOSITE.signatureCount();
            case PROCEED_PAGE:
                return PROCEED_PAGE_COMPOSITE.signatureCount();
            case RESULT_PAGE:
                return RESULT_PAGE_COMPOSITE.signatureCount();
            case GET_SHIP_PAGE:
                return GET_SHIP_PAGE_COMPOSITE.signatureCount();
            case CHOOSE_FORMATION:
                return 1;
            default:
                return 0;
        }
    }

    static PixelSignature testSignature(Kind kind, int variantIndex) {
        switch (kind) {
            case CHOOSE_FORMATION:
                return CHOOSE_FORMATION;
            case EVENT_MAP_PAGE:
                return variantIndex == 1 ? EVENT_MAP_OVERLAY : EVENT_MAP_BASE;
            case MAP_PAGE:
                return MAP_PAGE_NORMAL_SORTIE;
            case PROCEED_PAGE:
                return variantIndex == 1 ? PROCEED_RETURN_PORT_PROMPT : PROCEED_SUPPLY_PROMPT;
            case RESULT_PAGE:
                return RESULT_PAGE_SUMMARY;
            case GET_SHIP_PAGE:
                return variantIndex == 1 ? GET_SHIP_DIALOG : GET_SHIP_DROP;
            default:
                throw new IllegalArgumentException("unknown pixel signature kind");
        }
    }

    private static PixelMatch matchSingle(PixelSignature signature, Bitmap bitmap) {
        int matched = signature.matchedCount(bitmap);
        int total = signature.ruleCount();
        return new PixelMatch(matched == total && total > 0, signature.name, signature.name, matched, total, 1);
    }

    private static PixelRule rule(float x, float y, int r, int g, int b, double tolerance) {
        return new PixelRule(x, y, new VisionColor(r, g, b), tolerance);
    }

    enum Kind {
        CHOOSE_FORMATION,
        EVENT_MAP_PAGE,
        MAP_PAGE,
        PROCEED_PAGE,
        RESULT_PAGE,
        GET_SHIP_PAGE
    }

    static final class PixelMatch {
        final boolean matched;
        final String signatureName;
        final String matchedSignatureName;
        final int matchedRules;
        final int totalRules;
        final int signatureVariants;
        final String reason;

        private PixelMatch(
                boolean matched,
                String signatureName,
                String matchedSignatureName,
                int matchedRules,
                int totalRules,
                int signatureVariants
        ) {
            this(matched, signatureName, matchedSignatureName, matchedRules, totalRules, signatureVariants,
                    matched ? "" : "pixel signature did not match");
        }

        private PixelMatch(
                boolean matched,
                String signatureName,
                String matchedSignatureName,
                int matchedRules,
                int totalRules,
                int signatureVariants,
                String reason
        ) {
            this.matched = matched;
            this.signatureName = signatureName;
            this.matchedSignatureName = matchedSignatureName;
            this.matchedRules = matchedRules;
            this.totalRules = totalRules;
            this.signatureVariants = signatureVariants;
            this.reason = reason;
        }

        static PixelMatch missing(String reason) {
            return new PixelMatch(false, "", "", 0, 0, 0, reason);
        }
    }
}
