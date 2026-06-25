package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RouteNodeTracker {
    private static final int YELLOW_SCAN_STRIDE = 2;
    private static final int MIN_YELLOW_CLUSTER_AREA = 200;
    private static final double SHIP_PIXEL_TOLERANCE = 40.0;
    private static final double RAY_HIT_THRESHOLD = 0.01;

    private final MapNodeData mapData;
    private String currentNode = "0";
    private Position shipPosition;
    private Position lastShipPosition;

    RouteNodeTracker(MapNodeData mapData) {
        this.mapData = mapData;
    }

    String currentNode() {
        return currentNode;
    }

    RouteUpdate updateFromFrame(Bitmap bitmap) {
        ShipPositionDetection detection = detectShipPosition(bitmap);
        if (!detection.accepted) {
            return RouteUpdate.noUpdate(
                    currentNode,
                    "routeNodeScan=" + detection.detail
            );
        }
        RouteUpdate update = updateWithShipPosition(detection.x, detection.y);
        return update.withPrefix("routeNodeScan=" + detection.detail + ", ");
    }

    RouteUpdate updateWithShipPosition(double x, double y) {
        if (mapData == null) {
            return RouteUpdate.noUpdate(currentNode, "routeNodeTracker=unavailable missing map data");
        }
        if (!mapData.routable) {
            return RouteUpdate.noUpdate(currentNode, "routeNodeTracker=unavailable map is not routable");
        }

        shipPosition = new Position(x, y);
        if (lastShipPosition != null && distance(shipPosition, lastShipPosition) <= 1e-9) {
            return RouteUpdate.acceptedNoUpdate(
                    currentNode,
                    "routeNodeTracker=stable node=" + currentNode
                            + ", position=(" + format(x) + "," + format(y) + ")"
            );
        }

        Position previousPosition = lastShipPosition;
        lastShipPosition = shipPosition;
        if (previousPosition == null) {
            return RouteUpdate.acceptedNoUpdate(
                    currentNode,
                    "routeNodeTracker=primed node=" + currentNode
                            + ", position=(" + format(x) + "," + format(y) + ")"
                            + ", need second frame for movement ray"
            );
        }

        double vx = shipPosition.x - previousPosition.x;
        double vy = shipPosition.y - previousPosition.y;
        boolean hasRay = (vx * vx + vy * vy) > 1e-12;
        if (!hasRay) {
            return RouteUpdate.acceptedNoUpdate(
                    currentNode,
                    "routeNodeTracker=stable node=" + currentNode
                            + ", position=(" + format(x) + "," + format(y) + ")"
                            + ", reason=no movement ray"
            );
        }

        MapNodeData.Node current = mapData.get(currentNode);
        if (current == null || current.nextNodes.isEmpty()) {
            return RouteUpdate.acceptedNoUpdate(
                    currentNode,
                    "routeNodeTracker=blocked node=" + currentNode
                            + ", reason=missing next nodes"
            );
        }

        List<String> candidates = candidateNames(current);
        String bestNode = currentNode;
        double bestRayDistance = Double.POSITIVE_INFINITY;
        double bestEuclideanDistance = Double.POSITIVE_INFINITY;
        ArrayList<CandidateDistance> distances = new ArrayList<>();
        StringBuilder metrics = new StringBuilder();

        for (String candidateName : candidates) {
            MapNodeData.Node candidate = mapData.get(candidateName);
            if (candidate == null) {
                continue;
            }
            double euclidean = euclideanDistance(shipPosition.x, shipPosition.y, candidate.x, candidate.y);
            double rayDistance = pointToRayDistance(
                    candidate.x,
                    candidate.y,
                    shipPosition.x,
                    shipPosition.y,
                    vx,
                    vy
            );
            distances.add(new CandidateDistance(candidateName, rayDistance, euclidean));
            if (metrics.length() > 0) {
                metrics.append("; ");
            }
            metrics.append(candidateName)
                    .append("(ray=")
                    .append(format(rayDistance))
                    .append(", euclid=")
                    .append(format(euclidean))
                    .append(")");
        }

        CandidateDistance bestHit = null;
        for (CandidateDistance candidate : distances) {
            if (candidate.rayDistance < RAY_HIT_THRESHOLD) {
                if (bestHit == null
                        || candidate.euclideanDistance < bestHit.euclideanDistance
                        || (candidate.euclideanDistance == bestHit.euclideanDistance
                        && candidate.rayDistance < bestHit.rayDistance)) {
                    bestHit = candidate;
                }
            }
        }
        if (bestHit == null) {
            for (CandidateDistance candidate : distances) {
                if (bestHit == null
                        || candidate.rayDistance < bestHit.rayDistance
                        || (candidate.rayDistance == bestHit.rayDistance
                        && candidate.euclideanDistance < bestHit.euclideanDistance)) {
                    bestHit = candidate;
                }
            }
        }

        if (bestHit != null) {
            bestNode = bestHit.name;
            bestRayDistance = bestHit.rayDistance;
            bestEuclideanDistance = bestHit.euclideanDistance;
        }

        String previousNode = currentNode;
        boolean updated = !previousNode.equals(bestNode);
        if (updated) {
            currentNode = bestNode;
        }
        return new RouteUpdate(
                true,
                updated,
                previousNode,
                currentNode,
                "routeNodeTracker=node=" + currentNode
                        + ", previous=" + previousNode
                        + ", updated=" + updated
                        + ", position=(" + format(x) + "," + format(y) + ")"
                        + ", velocity=(" + format(vx) + "," + format(vy) + ")"
                        + ", bestRay=" + format(bestRayDistance)
                        + ", bestEuclid=" + format(bestEuclideanDistance)
                        + ", candidates=[" + metrics + "]"
        );
    }

    static ShipPositionDetection detectShipPosition(Bitmap bitmap) {
        if (bitmap == null) {
            return ShipPositionDetection.unavailable("yellow ship unavailable: missing bitmap");
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return ShipPositionDetection.unavailable("yellow ship unavailable: empty frame");
        }

        int gridWidth = (width + YELLOW_SCAN_STRIDE - 1) / YELLOW_SCAN_STRIDE;
        int gridHeight = (height + YELLOW_SCAN_STRIDE - 1) / YELLOW_SCAN_STRIDE;
        boolean[] mask = new boolean[gridWidth * gridHeight];
        boolean[] visited = new boolean[mask.length];
        int yellowSamples = 0;

        for (int gy = 0; gy < gridHeight; gy++) {
            int y = Math.min(height - 1, gy * YELLOW_SCAN_STRIDE);
            for (int gx = 0; gx < gridWidth; gx++) {
                int x = Math.min(width - 1, gx * YELLOW_SCAN_STRIDE);
                if (isShipYellow(bitmap.getPixel(x, y))) {
                    mask[gy * gridWidth + gx] = true;
                    yellowSamples++;
                }
            }
        }

        int[] queue = new int[mask.length];
        int bestCount = 0;
        long bestSumX = 0;
        long bestSumY = 0;
        int bestMinX = 0;
        int bestMaxX = 0;
        int bestMinY = 0;
        int bestMaxY = 0;

        for (int i = 0; i < mask.length; i++) {
            if (!mask[i] || visited[i]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = i;
            visited[i] = true;
            int count = 0;
            long sumX = 0;
            long sumY = 0;
            int minX = width - 1;
            int maxX = 0;
            int minY = height - 1;
            int maxY = 0;

            while (head < tail) {
                int cell = queue[head++];
                int gx = cell % gridWidth;
                int gy = cell / gridWidth;
                int x = Math.min(width - 1, gx * YELLOW_SCAN_STRIDE);
                int y = Math.min(height - 1, gy * YELLOW_SCAN_STRIDE);
                count++;
                sumX += x;
                sumY += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                for (int dy = -1; dy <= 1; dy++) {
                    int ny = gy + dy;
                    if (ny < 0 || ny >= gridHeight) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = gx + dx;
                        if (nx < 0 || nx >= gridWidth) {
                            continue;
                        }
                        int next = ny * gridWidth + nx;
                        if (mask[next] && !visited[next]) {
                            visited[next] = true;
                            queue[tail++] = next;
                        }
                    }
                }
            }

            if (count > bestCount) {
                bestCount = count;
                bestSumX = sumX;
                bestSumY = sumY;
                bestMinX = minX;
                bestMaxX = maxX;
                bestMinY = minY;
                bestMaxY = maxY;
            }
        }

        int estimatedArea = bestCount * YELLOW_SCAN_STRIDE * YELLOW_SCAN_STRIDE;
        if (estimatedArea < MIN_YELLOW_CLUSTER_AREA) {
            return ShipPositionDetection.unavailable("yellow ship not found"
                    + ", yellowSamples=" + yellowSamples
                    + ", bestArea=" + estimatedArea
                    + ", stride=" + YELLOW_SCAN_STRIDE);
        }

        double cx = bestSumX / (double) bestCount / (double) width;
        double cy = bestSumY / (double) bestCount / (double) height;
        boolean pixelVerified = verifyShipPixels(bitmap, cx, cy);
        String detail = "yellowCluster="
                + (pixelVerified ? "accepted" : "rejected")
                + ", center=(" + format(cx) + "," + format(cy) + ")"
                + ", area=" + estimatedArea
                + ", bounds=["
                + bestMinX + "," + bestMinY + "]["
                + bestMaxX + "," + bestMaxY + "]"
                + ", stride=" + YELLOW_SCAN_STRIDE
                + ", pixelVerified=" + pixelVerified;
        return new ShipPositionDetection(
                pixelVerified,
                cx,
                cy,
                detail
        );
    }

    private static List<String> candidateNames(MapNodeData.Node current) {
        ArrayList<String> candidates = new ArrayList<>();
        candidates.add(current.name);
        for (String nextNode : current.nextNodes) {
            if (!candidates.contains(nextNode)) {
                candidates.add(nextNode);
            }
        }
        return candidates;
    }

    private static boolean verifyShipPixels(Bitmap bitmap, double cx, double cy) {
        return colorNear(bitmap, cx, cy, 239, 219, 106)
                && colorNear(bitmap, cx - 0.03, cy, 231, 222, 101)
                && colorNear(bitmap, cx + 0.03, cy, 231, 222, 101);
    }

    private static boolean colorNear(Bitmap bitmap, double relX, double relY, int r, int g, int b) {
        int x = Math.min(bitmap.getWidth() - 1, Math.max(0, (int) Math.round(relX * (bitmap.getWidth() - 1))));
        int y = Math.min(bitmap.getHeight() - 1, Math.max(0, (int) Math.round(relY * (bitmap.getHeight() - 1))));
        int pixel = bitmap.getPixel(x, y);
        int pr = (pixel >> 16) & 0xff;
        int pg = (pixel >> 8) & 0xff;
        int pb = pixel & 0xff;
        double distance = Math.sqrt(square(pr - r) + square(pg - g) + square(pb - b));
        return distance <= SHIP_PIXEL_TOLERANCE;
    }

    private static boolean isShipYellow(int pixel) {
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = pixel & 0xff;
        return r > 200 && g > 180 && b > 50 && b < 150;
    }

    private static double pointToRayDistance(
            double px,
            double py,
            double ox,
            double oy,
            double dx,
            double dy
    ) {
        double vx = px - ox;
        double vy = py - oy;
        double dot = vx * dx + vy * dy;
        if (dot <= 0.0) {
            return Math.hypot(vx, vy);
        }
        double cross = Math.abs(vx * dy - vy * dx);
        double norm = Math.hypot(dx, dy);
        if (norm == 0.0) {
            return Math.hypot(vx, vy);
        }
        return cross / norm;
    }

    private static double euclideanDistance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    private static double distance(Position first, Position second) {
        return euclideanDistance(first.x, first.y, second.x, second.y);
    }

    private static double square(double value) {
        return value * value;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static final class Position {
        final double x;
        final double y;

        Position(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class CandidateDistance {
        final String name;
        final double rayDistance;
        final double euclideanDistance;

        CandidateDistance(String name, double rayDistance, double euclideanDistance) {
            this.name = name;
            this.rayDistance = rayDistance;
            this.euclideanDistance = euclideanDistance;
        }
    }

    static final class ShipPositionDetection {
        final boolean accepted;
        final double x;
        final double y;
        final String detail;

        private ShipPositionDetection(boolean accepted, double x, double y, String detail) {
            this.accepted = accepted;
            this.x = x;
            this.y = y;
            this.detail = detail;
        }

        static ShipPositionDetection unavailable(String detail) {
            return new ShipPositionDetection(false, 0.0, 0.0, detail);
        }
    }

    static final class RouteUpdate {
        final boolean acceptedPosition;
        final boolean nodeUpdated;
        final String previousNode;
        final String currentNode;
        final String detail;

        RouteUpdate(
                boolean acceptedPosition,
                boolean nodeUpdated,
                String previousNode,
                String currentNode,
                String detail
        ) {
            this.acceptedPosition = acceptedPosition;
            this.nodeUpdated = nodeUpdated;
            this.previousNode = previousNode;
            this.currentNode = currentNode;
            this.detail = detail;
        }

        static RouteUpdate noUpdate(String currentNode, String detail) {
            return new RouteUpdate(false, false, currentNode, currentNode, detail);
        }

        static RouteUpdate acceptedNoUpdate(String currentNode, String detail) {
            return new RouteUpdate(true, false, currentNode, currentNode, detail);
        }

        RouteUpdate withPrefix(String prefix) {
            return new RouteUpdate(
                    acceptedPosition,
                    nodeUpdated,
                    previousNode,
                    currentNode,
                    prefix + detail
            );
        }
    }
}
