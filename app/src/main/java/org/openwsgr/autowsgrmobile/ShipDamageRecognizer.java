package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.openwsgr.autowsgrmobile.vision.VisionColor;

import java.util.Locale;

final class ShipDamageRecognizer {
    private static final double MAX_REFERENCE_DISTANCE = 80.0;

    private static final float[][] PREPARATION_PROBES = new float[][]{
            {0.0583f, 0.5963f},
            {0.1750f, 0.5963f},
            {0.2917f, 0.5963f},
            {0.4083f, 0.5963f},
            {0.5250f, 0.5963f},
            {0.6417f, 0.5963f}
    };

    private static final float[][] RESULT_PROBES = new float[][]{
            {60f / 960f, 142f / 540f},
            {60f / 960f, 217f / 540f},
            {60f / 960f, 292f / 540f},
            {60f / 960f, 367f / 540f},
            {60f / 960f, 442f / 540f},
            {60f / 960f, 517f / 540f}
    };

    private static final Reference[] BLOOD_REFERENCES = new Reference[]{
            new Reference("green", new VisionColor(75, 168, 118), ShipDamagePolicy.NORMAL),
            new Reference("yellow", new VisionColor(246, 184, 51), ShipDamagePolicy.MODERATE),
            new Reference("red", new VisionColor(171, 18, 17), ShipDamagePolicy.SEVERE),
            new Reference("red_prepare", new VisionColor(230, 58, 89), ShipDamagePolicy.SEVERE),
            new Reference("empty", new VisionColor(58, 60, 62), ShipDamagePolicy.SEVERE),
            new Reference("no_ship", new VisionColor(43, 87, 112), ShipDamagePolicy.NO_SHIP)
    };

    private ShipDamageRecognizer() {
    }

    static ProbeResult probe() {
        StringBuilder detail = new StringBuilder();
        Bitmap syntheticBitmap = syntheticPreparationBitmap();
        Detection synthetic;
        try {
            synthetic = detectPreparation(syntheticBitmap);
        } finally {
            syntheticBitmap.recycle();
        }
        append(detail, "synthetic preparation=" + synthetic.detailLine());
        boolean ok = synthetic.reliable
                && synthetic.snapshot.stateAt(0) == ShipDamagePolicy.NORMAL
                && synthetic.snapshot.stateAt(1) == ShipDamagePolicy.MODERATE
                && synthetic.snapshot.stateAt(2) == ShipDamagePolicy.SEVERE
                && synthetic.snapshot.stateAt(3) == ShipDamagePolicy.NO_SHIP
                && synthetic.snapshot.stateAt(4) == ShipDamagePolicy.SEVERE
                && synthetic.snapshot.stateAt(5) == ShipDamagePolicy.SEVERE;

        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            append(detail, "live damage unavailable: missing MediaProjection frame");
            return new ProbeResult(ok, detail.toString());
        }

        Bitmap gameFrame = null;
        try {
            gameFrame = GameFrameNormalizer.toLandscapeGameFrame(liveFrame);
            Detection preparation = detectPreparation(gameFrame);
            Detection result = detectResult(gameFrame, null);
            Detection best = better(preparation, result);
            append(detail, "live preparation=" + preparation.detailLine());
            append(detail, "live result=" + result.detailLine());
            append(detail, "live selected=" + best.mode
                    + ", reliable=" + best.reliable
                    + ", snapshot=" + best.snapshot.shortLabel());
            return new ProbeResult(ok, detail.toString());
        } finally {
            if (gameFrame != null) {
                gameFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    static Detection detectPreparation(Bitmap bitmap) {
        return detect(bitmap, "preparation", PREPARATION_PROBES, null);
    }

    static Detection detectResult(
            Bitmap bitmap,
            ShipDamagePolicy.DamageSnapshot previousSnapshot
    ) {
        return detect(bitmap, "result", RESULT_PROBES, previousSnapshot);
    }

    private static Detection detect(
            Bitmap bitmap,
            String mode,
            float[][] probes,
            ShipDamagePolicy.DamageSnapshot previousSnapshot
    ) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Detection.unavailable(mode, "missing bitmap");
        }

        int[] states = new int[probes.length];
        double maxDistance = 0.0;
        double totalDistance = 0.0;
        int matched = 0;
        StringBuilder samples = new StringBuilder();

        for (int i = 0; i < probes.length; i++) {
            if (previousSnapshot != null && previousSnapshot.stateAt(i) == ShipDamagePolicy.NO_SHIP) {
                states[i] = ShipDamagePolicy.NO_SHIP;
                matched++;
                appendSample(samples, i, "preserve_no_ship", 0.0);
                continue;
            }

            int x = normalizedX(bitmap, probes[i][0]);
            int y = normalizedY(bitmap, probes[i][1]);
            VisionColor actual = VisionColor.fromArgb(bitmap.getPixel(x, y));
            Match match = closestReference(actual);
            totalDistance += match.distance;
            maxDistance = Math.max(maxDistance, match.distance);
            if (match.distance <= MAX_REFERENCE_DISTANCE) {
                states[i] = match.reference.state;
                matched++;
            } else {
                states[i] = ShipDamagePolicy.UNKNOWN;
            }
            appendSample(samples, i, match.reference.name, match.distance);
        }

        boolean reliable = matched == probes.length;
        ShipDamagePolicy.DamageSnapshot snapshot = ShipDamagePolicy.fromStates(
                mode + " damage probe",
                states,
                reliable
        );
        return new Detection(
                mode,
                reliable,
                snapshot,
                matched,
                probes.length,
                maxDistance,
                totalDistance / probes.length,
                samples.toString()
        );
    }

    private static Detection better(Detection first, Detection second) {
        if (first.reliable && !second.reliable) {
            return first;
        }
        if (second.reliable && !first.reliable) {
            return second;
        }
        if (first.matchedSlots != second.matchedSlots) {
            return first.matchedSlots > second.matchedSlots ? first : second;
        }
        return first.averageDistance <= second.averageDistance ? first : second;
    }

    private static Match closestReference(VisionColor actual) {
        Reference best = BLOOD_REFERENCES[0];
        double bestDistance = actual.distanceTo(best.color);
        for (int i = 1; i < BLOOD_REFERENCES.length; i++) {
            double distance = actual.distanceTo(BLOOD_REFERENCES[i].color);
            if (distance < bestDistance) {
                best = BLOOD_REFERENCES[i];
                bestDistance = distance;
            }
        }
        return new Match(best, bestDistance);
    }

    private static Bitmap syntheticPreparationBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff102030);
        setProbe(bitmap, PREPARATION_PROBES[0], new VisionColor(75, 168, 118));
        setProbe(bitmap, PREPARATION_PROBES[1], new VisionColor(246, 184, 51));
        setProbe(bitmap, PREPARATION_PROBES[2], new VisionColor(171, 18, 17));
        setProbe(bitmap, PREPARATION_PROBES[3], new VisionColor(43, 87, 112));
        setProbe(bitmap, PREPARATION_PROBES[4], new VisionColor(230, 58, 89));
        setProbe(bitmap, PREPARATION_PROBES[5], new VisionColor(58, 60, 62));
        return bitmap;
    }

    private static void setProbe(Bitmap bitmap, float[] probe, VisionColor color) {
        bitmap.setPixel(normalizedX(bitmap, probe[0]), normalizedY(bitmap, probe[1]), color.toArgb());
    }

    private static int normalizedX(Bitmap bitmap, float normalized) {
        return Math.min(bitmap.getWidth() - 1, Math.max(0, (int) (normalized * bitmap.getWidth())));
    }

    private static int normalizedY(Bitmap bitmap, float normalized) {
        return Math.min(bitmap.getHeight() - 1, Math.max(0, (int) (normalized * bitmap.getHeight())));
    }

    private static String formatDistance(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private static void appendSample(StringBuilder builder, int slot, String label, double distance) {
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(slot + 1).append(":").append(label).append("@").append(formatDistance(distance));
    }

    private static final class Reference {
        final String name;
        final VisionColor color;
        final int state;

        Reference(String name, VisionColor color, int state) {
            this.name = name;
            this.color = color;
            this.state = state;
        }
    }

    private static final class Match {
        final Reference reference;
        final double distance;

        Match(Reference reference, double distance) {
            this.reference = reference;
            this.distance = distance;
        }
    }

    static final class Detection {
        final String mode;
        final boolean reliable;
        final ShipDamagePolicy.DamageSnapshot snapshot;
        final int matchedSlots;
        final int slotCount;
        final double maxDistance;
        final double averageDistance;
        final String samples;

        private Detection(
                String mode,
                boolean reliable,
                ShipDamagePolicy.DamageSnapshot snapshot,
                int matchedSlots,
                int slotCount,
                double maxDistance,
                double averageDistance,
                String samples
        ) {
            this.mode = mode;
            this.reliable = reliable;
            this.snapshot = snapshot;
            this.matchedSlots = matchedSlots;
            this.slotCount = slotCount;
            this.maxDistance = maxDistance;
            this.averageDistance = averageDistance;
            this.samples = samples;
        }

        static Detection unavailable(String mode, String reason) {
            return new Detection(
                    mode,
                    false,
                    ShipDamagePolicy.unknown(reason),
                    0,
                    6,
                    0.0,
                    0.0,
                    reason
            );
        }

        String detailLine() {
            return "mode=" + mode
                    + ", reliable=" + reliable
                    + ", matched=" + matchedSlots + "/" + slotCount
                    + ", maxDist=" + formatDistance(maxDistance)
                    + ", avgDist=" + formatDistance(averageDistance)
                    + ", snapshot=" + snapshot.shortLabel()
                    + ", samples=[" + samples + "]";
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
            return "Ship damage probe: " + (ok ? "passed" : "failed");
        }
    }
}
