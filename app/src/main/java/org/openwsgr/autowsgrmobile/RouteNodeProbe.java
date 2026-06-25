package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;

final class RouteNodeProbe {
    private RouteNodeProbe() {
    }

    static Result run(Context context) {
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        ok &= appendPlanMap(context, CombatPlanPolicy.DEFAULT_PLAN, detail);
        ok &= appendPlanMap(context, CombatPlanPolicy.COMPLEX_PLAN, detail);
        ok &= appendPlanMap(context, CombatPlanPolicy.EVENT_PLAN, detail);
        ok &= appendSyntheticTracker(context, detail);
        append(detail, scanSyntheticYellowShip().detail);
        append(detail, scanLiveYellowShip().detail);

        return new Result(ok, detail.toString());
    }

    private static boolean appendPlanMap(Context context, String planPath, StringBuilder detail) {
        try {
            CombatPlanPolicy policy = CombatPlanPolicy.load(context, planPath);
            MapNodeData map = MapNodeData.loadForPlan(context, policy);
            int selectedCount = policy.selectedNodes.size();
            int selectedCoverage = map.selectedCoverage(policy.selectedNodes);
            boolean selectedOk = selectedCoverage == selectedCount;
            boolean routableOk = policy.eventName.length() > 0 || map.routable;
            append(detail, "map probe=" + policy.name
                    + ", source=" + map.path
                    + ", nodes=" + map.size()
                    + ", routable=" + map.routable
                    + ", selectedCoverage=" + selectedCoverage + "/" + selectedCount
                    + map.missingSelected(policy.selectedNodes)
                    + ", " + map.startSummary()
                    + ", " + map.selectedSummary(policy.selectedNodes));
            return selectedOk && routableOk;
        } catch (java.io.IOException e) {
            append(detail, "map probe failed=" + planPath + ", reason=" + e.getMessage());
            return false;
        }
    }

    private static boolean appendSyntheticTracker(Context context, StringBuilder detail) {
        try {
            CombatPlanPolicy policy = CombatPlanPolicy.load(context, CombatPlanPolicy.COMPLEX_PLAN);
            MapNodeData map = MapNodeData.loadForPlan(context, policy);
            RouteNodeTracker tracker = new RouteNodeTracker(map);
            StringBuilder sequence = new StringBuilder();
            appendNode(sequence, tracker.updateWithShipPosition(0.062, 0.333).currentNode);
            appendNode(sequence, tracker.updateWithShipPosition(0.227, 0.430).currentNode);
            appendNode(sequence, tracker.updateWithShipPosition(0.382, 0.571).currentNode);
            appendNode(sequence, tracker.updateWithShipPosition(0.461, 0.847).currentNode);
            appendNode(sequence, tracker.updateWithShipPosition(0.586, 0.583).currentNode);
            RouteNodeTracker.RouteUpdate finalUpdate = tracker.updateWithShipPosition(0.711, 0.806);
            appendNode(sequence, finalUpdate.currentNode);
            int routeIndex = policy.routeIndexForNode(finalUpdate.currentNode, -1);
            RouteNodeTracker divergingTracker = new RouteNodeTracker(map);
            divergingTracker.updateWithShipPosition(0.062, 0.333);
            RouteNodeTracker.RouteUpdate divergingUpdate = divergingTracker.updateWithShipPosition(0.266, 0.266);
            boolean routeGuardBlocks = divergingUpdate.acceptedPosition
                    && !policy.isRouteNodeAllowed(divergingUpdate.currentNode);
            boolean ok = "M".equals(finalUpdate.currentNode)
                    && routeIndex == 4
                    && routeGuardBlocks;
            append(detail, "synthetic tracker=" + (ok ? "passed" : "failed")
                    + ", plan=" + policy.name
                    + ", sequence=[" + sequence + "]"
                    + ", final=" + finalUpdate.currentNode
                    + ", routeIndex=" + routeIndex + "/" + Math.max(0, policy.routeSize() - 1)
                    + ", guardNode=" + divergingUpdate.currentNode
                    + ", guardBlocked=" + routeGuardBlocks
                    + ", finalDetail=" + finalUpdate.detail);
            return ok;
        } catch (java.io.IOException e) {
            append(detail, "synthetic tracker failed: " + e.getMessage());
            return false;
        }
    }

    private static YellowShipScan scanSyntheticYellowShip() {
        Bitmap bitmap = syntheticYellowShipBitmap();
        try {
            RouteNodeTracker.ShipPositionDetection detection = RouteNodeTracker.detectShipPosition(bitmap);
            return new YellowShipScan(
                    detection.accepted,
                    "synthetic yellow ship="
                            + (detection.accepted ? "passed" : "failed")
                            + ", " + detection.detail
            );
        } finally {
            bitmap.recycle();
        }
    }

    private static YellowShipScan scanLiveYellowShip() {
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return YellowShipScan.unavailable("live route node tracker unavailable: missing MediaProjection frame");
        }
        try {
            RouteNodeTracker.ShipPositionDetection detection = RouteNodeTracker.detectShipPosition(liveFrame);
            return new YellowShipScan(
                    detection.accepted,
                    "live route node tracker: " + detection.detail
            );
        } finally {
            liveFrame.recycle();
        }
    }

    private static Bitmap syntheticYellowShipBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff102030);
        int cx = Math.round(0.30f * bitmap.getWidth());
        int cy = Math.round(0.50f * bitmap.getHeight());
        for (int y = cy - 8; y <= cy + 8; y++) {
            for (int x = cx - 42; x <= cx + 42; x++) {
                if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                    bitmap.setPixel(x, y, 0xffe7de65);
                }
            }
        }
        bitmap.setPixel(cx, cy, 0xffefdb6a);
        return bitmap;
    }

    private static void appendNode(StringBuilder builder, String node) {
        if (builder.length() > 0) {
            builder.append(",");
        }
        builder.append(node);
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Route node probe: " + (ok ? "passed" : "failed");
        }
    }

    private static final class YellowShipScan {
        final boolean accepted;
        final String detail;

        YellowShipScan(boolean accepted, String detail) {
            this.accepted = accepted;
            this.detail = detail;
        }

        static YellowShipScan unavailable(String detail) {
            return new YellowShipScan(false, detail);
        }
    }
}
