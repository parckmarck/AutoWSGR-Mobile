package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;

final class EnemyCompositionRecognizer {
    private static final int BASE_WIDTH = 960;
    private static final int BASE_HEIGHT = 540;

    private static final Rect[] FIGHT_SCAN_AREAS_960 = new Rect[]{
            new Rect(39, 156, 71, 172),
            new Rect(322, 156, 354, 172),
            new Rect(39, 245, 71, 261),
            new Rect(322, 245, 354, 261),
            new Rect(39, 334, 71, 350),
            new Rect(322, 334, 354, 350)
    };

    private static final Rect[] EXERCISE_SCAN_AREAS_960 = new Rect[]{
            new Rect(277, 312, 309, 328),
            new Rect(380, 312, 412, 328),
            new Rect(483, 312, 515, 328),
            new Rect(587, 312, 619, 328),
            new Rect(690, 312, 722, 328),
            new Rect(793, 312, 825, 328)
    };

    private static final SlotCalibration FIGHT_CALIBRATION = new SlotCalibration(
            "fight-960-six-slot",
            BASE_WIDTH,
            BASE_HEIGHT,
            FIGHT_SCAN_AREAS_960
    );

    private static final SlotCalibration EXERCISE_CALIBRATION = new SlotCalibration(
            "exercise-960-six-slot",
            BASE_WIDTH,
            BASE_HEIGHT,
            EXERCISE_SCAN_AREAS_960
    );

    private static final ShipPrototype[] FIGHT_PROTOTYPES = new ShipPrototype[]{
            new ShipPrototype("DD", 49, 14, 0, 0xaa55aaaa55aaaa55L),
            new ShipPrototype("CL", 73, 14, 0, 0xaa55aaaa55aaaa55L),
            new ShipPrototype("CVL", 97, 14, 0, 0xaa55aaaa55aaaa55L),
            new ShipPrototype("CV", 121, 14, 0, 0xaa55aaaa55aaaa55L),
            new ShipPrototype("BB", 145, 14, 0, 0xaa55aaaa55aaaa55L),
            new ShipPrototype("AP", 169, 14, 0, 0xaa55aaaa55aaaa55L)
    };

    private static final int CLASSIFIER_DISTANCE_LIMIT = 14;

    private EnemyCompositionRecognizer() {
    }

    static Detection detectFight(Bitmap bitmap) {
        return detect(bitmap, "fight", FIGHT_CALIBRATION);
    }

    static Detection detectExercise(Bitmap bitmap) {
        return detect(bitmap, "exercise", EXERCISE_CALIBRATION);
    }

    static int classifierDistanceLimit() {
        return CLASSIFIER_DISTANCE_LIMIT;
    }

    static String classifierPrototypeSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("fight prototypes=");
        for (int i = 0; i < FIGHT_PROTOTYPES.length; i++) {
            ShipPrototype prototype = FIGHT_PROTOTYPES[i];
            if (i > 0) {
                builder.append('|');
            }
            builder.append(prototype.type)
                    .append("{mean=").append(prototype.meanLuma)
                    .append(",sd=").append(prototype.stdevLuma)
                    .append(",edge=").append(prototype.edgePermille)
                    .append(",hash=").append(hashString(prototype.hash64))
                    .append('}');
        }
        return builder.toString();
    }

    static Detection fromRecognizedText(String source, String text) {
        if (text == null || text.trim().length() == 0) {
            return Detection.unavailable(source, "empty recognized text");
        }
        return fromRecognizedTypes(source, text.split("[\\s,;，、/|]+"));
    }

    static Detection fromRecognizedTypes(String source, String... rawTypes) {
        LinkedHashMap<String, Integer> counts = emptyCounts();
        int recognized = 0;
        int unknown = 0;
        StringBuilder tokens = new StringBuilder();

        if (rawTypes != null) {
            for (String raw : rawTypes) {
                String canonical = normalizeShipType(raw);
                if (canonical.length() == 0) {
                    continue;
                }
                if (tokens.length() > 0) {
                    tokens.append(',');
                }
                tokens.append(raw).append("->").append(canonical);
                if ("UNKNOWN".equals(canonical)) {
                    unknown++;
                    continue;
                }
                addShip(counts, canonical);
                recognized++;
            }
        }

        boolean reliable = recognized > 0 && unknown == 0;
        EnemyRuleEngine.EnemySnapshot snapshot = EnemyRuleEngine.EnemySnapshot.fromCounts(
                source,
                counts
        );
        return new Detection(
                source,
                reliable,
                snapshot,
                recognized,
                unknown,
                rawTypes == null ? 0 : rawTypes.length,
                reliable ? "recognized ship-type tokens" : "ship-type tokens incomplete",
                tokens.toString(),
                ""
        );
    }

    static ProbeResult probe(Context context) {
        StringBuilder detail = new StringBuilder();
        Detection mixedTokens = fromRecognizedTypes(
                "synthetic OCR/native labels",
                "驱逐",
                "DD",
                "轻巡",
                "CVL",
                "航母",
                "补给"
        );
        append(detail, "synthetic labels=" + mixedTokens.detailLine());

        boolean ok = mixedTokens.reliable
                && mixedTokens.snapshot.count("DD") == 2
                && mixedTokens.snapshot.count("CL") == 1
                && mixedTokens.snapshot.count("CVL") == 1
                && mixedTokens.snapshot.count("CV") == 1
                && mixedTokens.snapshot.count("AP") == 1
                && mixedTokens.snapshot.count("NAP") == 1
                && mixedTokens.snapshot.count("ALL") == 6;

        Bitmap syntheticSlots = syntheticFightFrame();
        try {
            SlotSampleSet slotSamples = extractSlotSamples(
                    syntheticSlots,
                    "synthetic fight",
                    FIGHT_CALIBRATION
            );
            append(detail, "slot sampler=" + slotSamples.detailLine());
            ok &= slotSamples.available
                    && slotSamples.detailLine().contains("calibrationSummary=fight-960-six-slot{base=960x540, slots=6")
                    && slotSamples.samples.length == 6
                    && slotSamples.samples[0].scaledRect.left == 39
                    && slotSamples.samples[0].scaledRect.top == 156
                    && slotSamples.samples[0].scaledRect.width() == 32
                    && slotSamples.samples[0].scaledRect.height() == 16
                    && slotSamples.samples[5].meanLuma > slotSamples.samples[0].meanLuma;

            SlotSampleSet exerciseSamples = extractSlotSamples(
                    syntheticSlots,
                    "synthetic exercise calibration",
                    EXERCISE_CALIBRATION
            );
            append(detail, "exercise slot calibration=" + exerciseSamples.detailLine());
            ok &= exerciseSamples.available
                    && exerciseSamples.detailLine().contains("calibrationSummary=exercise-960-six-slot{base=960x540, slots=6")
                    && exerciseSamples.samples.length == 6
                    && exerciseSamples.samples[0].baseRect.left == 277
                    && exerciseSamples.samples[5].baseRect.right == 825;

            Detection syntheticDetection = detectFight(syntheticSlots);
            append(detail, "calibrated fight backend=" + syntheticDetection.detailLine());
            ok &= syntheticDetection.reliable
                    && syntheticDetection.recognizedSlots == 6
                    && syntheticDetection.unknownSlots == 0
                    && syntheticDetection.snapshot.count("DD") == 1
                    && syntheticDetection.snapshot.count("CL") == 1
                    && syntheticDetection.snapshot.count("CVL") == 1
                    && syntheticDetection.snapshot.count("CV") == 1
                    && syntheticDetection.snapshot.count("BB") == 1
                    && syntheticDetection.snapshot.count("AP") == 1
                    && syntheticDetection.snapshot.count("ALL") == 6;
        } finally {
            syntheticSlots.recycle();
        }

        try {
            CombatPlanPolicy plan = CombatPlanPolicy.load(
                    context,
                    "autowsgr/data/plan/normal_fight/7-4胖次.yaml"
            );
            CombatPlanPolicy.PlanDecision retreatDecision = plan.decisionForSpotEnemySnapshot(
                    plan.routeIndexForNode("A", 0),
                    mixedTokens.snapshot
            );
            append(detail, "policy linkage=" + retreatDecision.detail());
            ok &= "spot_retreat".equals(retreatDecision.actionId);

            Detection formationTokens = fromRecognizedText(
                    "synthetic formation labels",
                    "DD CVL CV BB BB SS"
            );
            CombatPlanPolicy.PlanDecision formationDecision = plan.decisionForSpotEnemySnapshot(
                    plan.routeIndexForNode("C", 0),
                    formationTokens.snapshot
            );
            append(detail, "formation linkage=" + formationDecision.detail());
            ok &= "spot_fight".equals(formationDecision.actionId)
                    && formationDecision.carriesRuleFormation
                    && formationDecision.ruleFormation == 4;
        } catch (IOException e) {
            ok = false;
            append(detail, "policy linkage failed: " + e.getMessage());
        }

        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            append(detail, "live enemy recognition unavailable: missing MediaProjection frame");
            return new ProbeResult(ok, detail.toString());
        }

        try {
            Detection live = detectFight(liveFrame);
            append(detail, "live fight=" + live.detailLine());
            return new ProbeResult(ok, detail.toString());
        } finally {
            liveFrame.recycle();
        }
    }

    private static Detection detect(Bitmap bitmap, String mode, SlotCalibration calibration) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Detection.unavailable(mode, "missing bitmap");
        }
        SlotSampleSet samples = extractSlotSamples(bitmap, mode, calibration);
        if (!samples.available) {
            return Detection.unavailable(mode, samples.reason, samples.detailLine());
        }

        String[] classifiedTypes = new String[samples.samples.length];
        StringBuilder classifier = new StringBuilder();
        for (int i = 0; i < samples.samples.length; i++) {
            Classification classification = classify(samples.samples[i]);
            classifiedTypes[i] = classification.type;
            if (classifier.length() > 0) {
                classifier.append(" | ");
            }
            classifier.append('#').append(i + 1)
                    .append('=').append(classification.type)
                    .append("(distance=").append(classification.distance).append(')');
        }

        Detection tokens = fromRecognizedTypes(mode + " calibrated prototype classifier", classifiedTypes);
        return new Detection(
                mode,
                tokens.reliable,
                tokens.snapshot,
                tokens.recognizedSlots,
                tokens.unknownSlots,
                tokens.inputSlots,
                tokens.reliable
                        ? "calibrated prototype classifier accepted, calibration=" + calibration.name
                        : "calibrated prototype classifier incomplete, calibration=" + calibration.name,
                tokens.tokens,
                "classifier=" + classifier
                        + ", " + samples.detailLine()
        );
    }

    private static SlotSampleSet extractSlotSamples(Bitmap bitmap, String mode, SlotCalibration calibration) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return SlotSampleSet.unavailable(mode, "missing bitmap");
        }
        if (calibration == null || calibration.scanAreas.length == 0) {
            return SlotSampleSet.unavailable(mode, "missing scan areas");
        }

        SlotSample[] samples = new SlotSample[calibration.scanAreas.length];
        for (int i = 0; i < calibration.scanAreas.length; i++) {
            Rect scaled = scaleRect(calibration.scanAreas[i], bitmap.getWidth(), bitmap.getHeight());
            samples[i] = sampleSlot(bitmap, i + 1, calibration.scanAreas[i], scaled);
        }
        return SlotSampleSet.available(mode, calibration, bitmap.getWidth(), bitmap.getHeight(), samples);
    }

    private static Classification classify(SlotSample sample) {
        ShipPrototype best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ShipPrototype prototype : FIGHT_PROTOTYPES) {
            int distance = prototype.distanceTo(sample);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = prototype;
            }
        }
        if (best == null || bestDistance > CLASSIFIER_DISTANCE_LIMIT) {
            return new Classification("UNKNOWN", bestDistance);
        }
        return new Classification(best.type, bestDistance);
    }

    private static Rect scaleRect(Rect source, int width, int height) {
        int left = Math.round(source.left * width / (float) BASE_WIDTH);
        int top = Math.round(source.top * height / (float) BASE_HEIGHT);
        int right = Math.round(source.right * width / (float) BASE_WIDTH);
        int bottom = Math.round(source.bottom * height / (float) BASE_HEIGHT);

        left = clampInt(left, 0, Math.max(0, width - 1));
        top = clampInt(top, 0, Math.max(0, height - 1));
        right = clampInt(right, left + 1, width);
        bottom = clampInt(bottom, top + 1, height);
        return new Rect(left, top, right, bottom);
    }

    private static SlotSample sampleSlot(Bitmap bitmap, int slot, Rect baseRect, Rect scaledRect) {
        int sampleWidth = scaledRect.width();
        int sampleHeight = scaledRect.height();
        int[] pixels = new int[sampleWidth * sampleHeight];
        bitmap.getPixels(
                pixels,
                0,
                sampleWidth,
                scaledRect.left,
                scaledRect.top,
                sampleWidth,
                sampleHeight
        );

        int[] lumas = new int[pixels.length];
        long sum = 0L;
        long sumSquares = 0L;
        int dark = 0;
        int bright = 0;
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int r = (argb >> 16) & 0xff;
            int g = (argb >> 8) & 0xff;
            int b = argb & 0xff;
            int luma = (299 * r + 587 * g + 114 * b) / 1000;
            lumas[i] = luma;
            sum += luma;
            sumSquares += (long) luma * luma;
            if (luma < 80) {
                dark++;
            }
            if (luma > 180) {
                bright++;
            }
        }

        int mean = pixels.length == 0 ? 0 : Math.round(sum / (float) pixels.length);
        int variance = 0;
        if (pixels.length > 0) {
            long averageSquares = sumSquares / pixels.length;
            variance = (int) Math.max(0L, averageSquares - (long) mean * mean);
        }
        int stdev = (int) Math.round(Math.sqrt(variance));

        int transitions = 0;
        int transitionTotal = 0;
        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                int current = lumas[y * sampleWidth + x];
                if (x + 1 < sampleWidth) {
                    transitionTotal++;
                    if (Math.abs(current - lumas[y * sampleWidth + x + 1]) >= 32) {
                        transitions++;
                    }
                }
                if (y + 1 < sampleHeight) {
                    transitionTotal++;
                    if (Math.abs(current - lumas[(y + 1) * sampleWidth + x]) >= 32) {
                        transitions++;
                    }
                }
            }
        }

        int darkPermille = pixels.length == 0 ? 0 : Math.round(dark * 1000f / pixels.length);
        int brightPermille = pixels.length == 0 ? 0 : Math.round(bright * 1000f / pixels.length);
        int edgePermille = transitionTotal == 0 ? 0 : Math.round(transitions * 1000f / transitionTotal);
        return new SlotSample(
                slot,
                new Rect(baseRect),
                scaledRect,
                mean,
                stdev,
                darkPermille,
                brightPermille,
                edgePermille,
                averageHash64(lumas, sampleWidth, sampleHeight)
        );
    }

    private static long averageHash64(int[] lumas, int width, int height) {
        if (lumas.length == 0 || width <= 0 || height <= 0) {
            return 0L;
        }
        int[] grid = new int[64];
        int total = 0;
        for (int gy = 0; gy < 8; gy++) {
            int y = Math.min(height - 1, (gy * height + height / 2) / 8);
            for (int gx = 0; gx < 8; gx++) {
                int x = Math.min(width - 1, (gx * width + width / 2) / 8);
                int value = lumas[y * width + x];
                grid[gy * 8 + gx] = value;
                total += value;
            }
        }
        int average = total / grid.length;
        long hash = 0L;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] >= average) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    private static Bitmap syntheticFightFrame() {
        Bitmap bitmap = Bitmap.createBitmap(BASE_WIDTH, BASE_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(gray(24));
        paintSyntheticFightSlots(bitmap);
        return bitmap;
    }

    static void paintSyntheticFightSlots(Bitmap bitmap) {
        paintSyntheticFightSlots(bitmap, "DD", "CL", "CVL", "CV", "BB", "AP");
    }

    static void paintSyntheticFightSlots(Bitmap bitmap, String... slotTypes) {
        if (bitmap == null) {
            return;
        }
        for (int i = 0; i < FIGHT_SCAN_AREAS_960.length; i++) {
            Rect rect = FIGHT_SCAN_AREAS_960[i];
            String slotType = slotTypes != null && i < slotTypes.length ? slotTypes[i] : "";
            int base = syntheticMeanForType(slotType, 45 + i * 24);
            for (int y = rect.top; y < rect.bottom; y++) {
                for (int x = rect.left; x < rect.right; x++) {
                    int stripe = ((x - rect.left) / 4 + (y - rect.top) / 3) % 2 == 0 ? 18 : -10;
                    bitmap.setPixel(x, y, gray(base + stripe));
                }
            }
        }
    }

    private static int syntheticMeanForType(String type, int fallback) {
        if ("DD".equals(type)) {
            return 45;
        }
        if ("CL".equals(type)) {
            return 69;
        }
        if ("CVL".equals(type)) {
            return 93;
        }
        if ("CV".equals(type)) {
            return 117;
        }
        if ("BB".equals(type)) {
            return 141;
        }
        if ("AP".equals(type)) {
            return 165;
        }
        return fallback;
    }

    private static int gray(int value) {
        int channel = clampInt(value, 0, 255);
        return 0xff000000 | (channel << 16) | (channel << 8) | channel;
    }

    private static LinkedHashMap<String, Integer> emptyCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        String[] keys = new String[]{
                "CV", "CVL", "AV", "BB", "BBV", "BC", "CA", "CAV", "CLT", "CL",
                "BM", "DD", "SSG", "SS", "SC", "AP", "NAP", "ASDG", "AADG",
                "KP", "CG", "CBG", "BG", "ALL"
        };
        for (String key : keys) {
            counts.put(key, 0);
        }
        return counts;
    }

    private static void addShip(LinkedHashMap<String, Integer> counts, String canonical) {
        increment(counts, canonical);
        if ("AP".equals(canonical)) {
            increment(counts, "NAP");
        }
        increment(counts, "ALL");
    }

    private static void increment(LinkedHashMap<String, Integer> counts, String key) {
        Integer current = counts.get(key);
        counts.put(key, current == null ? 1 : current + 1);
    }

    private static String normalizeShipType(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 0) {
            return "";
        }
        String compact = trimmed
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("舰", "");
        String upper = compact.toUpperCase(Locale.US);
        if ("NO".equals(upper) || "NONE".equals(upper) || "EMPTY".equals(upper) || "NULL".equals(upper)) {
            return "";
        }
        if ("CVL".equals(upper)) {
            return "CVL";
        }
        if ("CV".equals(upper)) {
            return "CV";
        }
        if ("BBV".equals(upper)) {
            return "BBV";
        }
        if ("BB".equals(upper)) {
            return "BB";
        }
        if ("CAV".equals(upper)) {
            return "CAV";
        }
        if ("CA".equals(upper)) {
            return "CA";
        }
        if ("CLT".equals(upper)) {
            return "CLT";
        }
        if ("CL".equals(upper)) {
            return "CL";
        }
        if ("SSG".equals(upper)) {
            return "SSG";
        }
        if ("SS".equals(upper)) {
            return "SS";
        }
        if ("NAP".equals(upper) || "AP".equals(upper)) {
            return "AP";
        }
        if ("AV".equals(upper) || "BC".equals(upper) || "BM".equals(upper)
                || "DD".equals(upper) || "SC".equals(upper) || "ASDG".equals(upper)
                || "AADG".equals(upper) || "KP".equals(upper) || "CG".equals(upper)
                || "CBG".equals(upper) || "BG".equals(upper)) {
            return upper;
        }

        if (compact.contains("轻母")) {
            return "CVL";
        }
        if (compact.contains("航母") || compact.contains("正航")) {
            return "CV";
        }
        if (compact.contains("装母")) {
            return "AV";
        }
        if (compact.contains("航战")) {
            return "BBV";
        }
        if (compact.contains("战列")) {
            return "BB";
        }
        if (compact.contains("战巡")) {
            return "BC";
        }
        if (compact.contains("航巡")) {
            return "CAV";
        }
        if (compact.contains("重巡")) {
            return "CA";
        }
        if (compact.contains("雷巡")) {
            return "CLT";
        }
        if (compact.contains("轻巡")) {
            return "CL";
        }
        if (compact.contains("重炮")) {
            return "BM";
        }
        if (compact.contains("驱逐")) {
            return "DD";
        }
        if (compact.contains("导潜")) {
            return "SSG";
        }
        if (compact.contains("潜艇")) {
            return "SS";
        }
        if (compact.contains("炮潜")) {
            return "SC";
        }
        if (compact.contains("补给")) {
            return "AP";
        }
        if (compact.contains("导驱")) {
            return "ASDG";
        }
        if (compact.contains("防驱")) {
            return "AADG";
        }
        if (compact.contains("导巡")) {
            return "KP";
        }
        if (compact.contains("防巡")) {
            return "CG";
        }
        if (compact.contains("大巡")) {
            return "CBG";
        }
        if (compact.contains("导战")) {
            return "BG";
        }
        return "UNKNOWN";
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String percent(int permille) {
        int tenths = permille / 10;
        return (tenths / 10) + "." + (tenths % 10) + "%";
    }

    private static String hashString(long hash) {
        String value = Long.toHexString(hash);
        StringBuilder builder = new StringBuilder();
        for (int i = value.length(); i < 16; i++) {
            builder.append('0');
        }
        builder.append(value);
        return builder.toString();
    }

    static final class SlotCalibration {
        final String name;
        final int baseWidth;
        final int baseHeight;
        final Rect[] scanAreas;

        SlotCalibration(String name, int baseWidth, int baseHeight, Rect[] scanAreas) {
            this.name = name;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.scanAreas = copyRects(scanAreas);
        }

        String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append(name)
                    .append("{base=").append(baseWidth).append('x').append(baseHeight)
                    .append(", slots=").append(scanAreas.length);
            if (scanAreas.length > 0) {
                Rect first = scanAreas[0];
                Rect last = scanAreas[scanAreas.length - 1];
                builder.append(", first=(").append(first.left).append(',').append(first.top)
                        .append(")-(").append(first.right).append(',').append(first.bottom)
                        .append("), last=(").append(last.left).append(',').append(last.top)
                        .append(")-(").append(last.right).append(',').append(last.bottom)
                        .append(')');
            }
            builder.append('}');
            return builder.toString();
        }

        private static Rect[] copyRects(Rect[] rects) {
            if (rects == null) {
                return new Rect[0];
            }
            Rect[] copy = new Rect[rects.length];
            for (int i = 0; i < rects.length; i++) {
                copy[i] = new Rect(rects[i]);
            }
            return copy;
        }
    }

    private static final class ShipPrototype {
        final String type;
        final int meanLuma;
        final int stdevLuma;
        final int edgePermille;
        final long hash64;

        ShipPrototype(String type, int meanLuma, int stdevLuma, int edgePermille, long hash64) {
            this.type = type;
            this.meanLuma = meanLuma;
            this.stdevLuma = stdevLuma;
            this.edgePermille = edgePermille;
            this.hash64 = hash64;
        }

        int distanceTo(SlotSample sample) {
            int meanDistance = Math.abs(sample.meanLuma - meanLuma);
            int stdevDistance = Math.abs(sample.stdevLuma - stdevLuma) / 2;
            int edgeDistance = Math.abs(sample.edgePermille - edgePermille) / 50;
            int hashDistance = Long.bitCount(sample.hash64 ^ hash64) / 4;
            return meanDistance + stdevDistance + edgeDistance + hashDistance;
        }
    }

    private static final class Classification {
        final String type;
        final int distance;

        Classification(String type, int distance) {
            this.type = type;
            this.distance = distance;
        }
    }

    static final class SlotSampleSet {
        final boolean available;
        final String mode;
        final SlotCalibration calibration;
        final String reason;
        final int frameWidth;
        final int frameHeight;
        final SlotSample[] samples;

        private SlotSampleSet(
                boolean available,
                String mode,
                SlotCalibration calibration,
                String reason,
                int frameWidth,
                int frameHeight,
                SlotSample[] samples
        ) {
            this.available = available;
            this.mode = mode;
            this.calibration = calibration;
            this.reason = reason;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.samples = samples;
        }

        static SlotSampleSet available(
                String mode,
                SlotCalibration calibration,
                int frameWidth,
                int frameHeight,
                SlotSample[] samples
        ) {
            return new SlotSampleSet(true, mode, calibration, "", frameWidth, frameHeight, samples);
        }

        static SlotSampleSet unavailable(String mode, String reason) {
            return new SlotSampleSet(false, mode, null, reason, 0, 0, new SlotSample[0]);
        }

        String detailLine() {
            if (!available) {
                return "mode=" + mode + ", unavailable=" + reason;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("mode=").append(mode)
                    .append(", calibration=").append(calibration.name)
                    .append(", calibrationSummary=").append(calibration.summary())
                    .append(", frame=").append(frameWidth).append('x').append(frameHeight)
                    .append(", normalizedFrom=").append(calibration.baseWidth).append('x').append(calibration.baseHeight)
                    .append(", slots=").append(samples.length);
            for (SlotSample sample : samples) {
                builder.append(" | ").append(sample.detailLine());
            }
            return builder.toString();
        }
    }

    static final class SlotSample {
        final int slot;
        final Rect baseRect;
        final Rect scaledRect;
        final int meanLuma;
        final int stdevLuma;
        final int darkPermille;
        final int brightPermille;
        final int edgePermille;
        final long hash64;

        SlotSample(
                int slot,
                Rect baseRect,
                Rect scaledRect,
                int meanLuma,
                int stdevLuma,
                int darkPermille,
                int brightPermille,
                int edgePermille,
                long hash64
        ) {
            this.slot = slot;
            this.baseRect = baseRect;
            this.scaledRect = scaledRect;
            this.meanLuma = meanLuma;
            this.stdevLuma = stdevLuma;
            this.darkPermille = darkPermille;
            this.brightPermille = brightPermille;
            this.edgePermille = edgePermille;
            this.hash64 = hash64;
        }

        String detailLine() {
            return "#" + slot
                    + " base=(" + baseRect.left + "," + baseRect.top + ")-("
                    + baseRect.right + "," + baseRect.bottom + ")"
                    + " scaled=(" + scaledRect.left + "," + scaledRect.top + ")-("
                    + scaledRect.right + "," + scaledRect.bottom + ")"
                    + " size=" + scaledRect.width() + "x" + scaledRect.height()
                    + " mean=" + meanLuma
                    + " sd=" + stdevLuma
                    + " dark=" + percent(darkPermille)
                    + " bright=" + percent(brightPermille)
                    + " edge=" + percent(edgePermille)
                    + " hash=" + hashString(hash64);
        }
    }

    static final class Detection {
        final String source;
        final boolean reliable;
        final EnemyRuleEngine.EnemySnapshot snapshot;
        final int recognizedSlots;
        final int unknownSlots;
        final int inputSlots;
        final String reason;
        final String tokens;
        final String sampleSummary;

        Detection(
                String source,
                boolean reliable,
                EnemyRuleEngine.EnemySnapshot snapshot,
                int recognizedSlots,
                int unknownSlots,
                int inputSlots,
                String reason,
                String tokens,
                String sampleSummary
        ) {
            this.source = source;
            this.reliable = reliable;
            this.snapshot = snapshot;
            this.recognizedSlots = recognizedSlots;
            this.unknownSlots = unknownSlots;
            this.inputSlots = inputSlots;
            this.reason = reason;
            this.tokens = tokens;
            this.sampleSummary = sampleSummary;
        }

        static Detection unavailable(String source, String reason) {
            return unavailable(source, reason, "");
        }

        static Detection unavailable(String source, String reason, String sampleSummary) {
            return new Detection(
                    source,
                    false,
                    EnemyRuleEngine.EnemySnapshot.fromCounts(source, emptyCounts()),
                    0,
                    0,
                    0,
                    reason,
                    "",
                    sampleSummary
            );
        }

        String detailLine() {
            return "source=" + source
                    + ", reliable=" + reliable
                    + ", recognized=" + recognizedSlots + "/" + inputSlots
                    + ", unknown=" + unknownSlots
                    + ", snapshot=" + snapshot.summary()
                    + ", reason=" + reason
                    + (tokens.length() == 0 ? "" : ", tokens=" + tokens)
                    + (sampleSummary.length() == 0 ? "" : ", slotSamples=" + sampleSummary);
        }
    }

    static final class ProbeResult {
        final boolean ok;
        final String detail;

        ProbeResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Enemy composition probe: " + (ok ? "passed" : "failed");
        }
    }
}
