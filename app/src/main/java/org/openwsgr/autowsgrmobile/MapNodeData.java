package org.openwsgr.autowsgrmobile;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MapNodeData {
    private static final String NORMAL_PREFIX = "autowsgr/data/map/normal/";
    private static final String EVENT_PREFIX = "autowsgr/data/map/event/";

    final String path;
    final LinkedHashMap<String, Node> nodes;
    final boolean routable;

    private MapNodeData(String path, LinkedHashMap<String, Node> nodes) {
        this.path = path;
        this.nodes = nodes;
        this.routable = hasRoutes(nodes);
    }

    static MapNodeData loadForPlan(Context context, CombatPlanPolicy policy) throws IOException {
        return load(context, mapPathForPlan(policy));
    }

    static MapNodeData load(Context context, String path) throws IOException {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        return parse(path, store.readText(path));
    }

    static MapNodeData parseText(String path, String text) throws IOException {
        return parse(path, text);
    }

    static String mapPathForPlan(CombatPlanPolicy policy) {
        if (policy.eventName.length() > 0 || "event".equals(policy.mode)) {
            return EVENT_PREFIX
                    + policy.eventName
                    + "/"
                    + policy.chapter
                    + "-"
                    + policy.mapId
                    + ".yaml";
        }
        return NORMAL_PREFIX + policy.chapter + "-" + policy.mapId + ".yaml";
    }

    int size() {
        return nodes.size();
    }

    Node get(String name) {
        return nodes.get(name);
    }

    String startSummary() {
        Node start = nodes.get("0");
        if (start == null) {
            return "start=missing";
        }
        return "start=0->" + start.nextNodes;
    }

    String firstRouteNode() {
        Node start = nodes.get("0");
        if (start != null && !start.nextNodes.isEmpty()) {
            return start.nextNodes.get(0);
        }
        for (String node : nodes.keySet()) {
            if (!"0".equals(node)) {
                return node;
            }
        }
        return "";
    }

    int selectedCoverage(List<String> selectedNodes) {
        int covered = 0;
        for (String selectedNode : selectedNodes) {
            if (nodes.containsKey(selectedNode)) {
                covered++;
            }
        }
        return covered;
    }

    String selectedSummary(List<String> selectedNodes) {
        if (selectedNodes.isEmpty()) {
            return "selectedRoute=[default]";
        }
        StringBuilder out = new StringBuilder();
        for (String selectedNode : selectedNodes) {
            if (out.length() > 0) {
                out.append("; ");
            }
            Node node = nodes.get(selectedNode);
            if (node == null) {
                out.append(selectedNode).append("(missing)");
            } else {
                out.append(node.shortLabel());
            }
        }
        return "selectedRoute=" + out;
    }

    String missingSelected(List<String> selectedNodes) {
        StringBuilder missing = new StringBuilder();
        for (String selectedNode : selectedNodes) {
            if (!nodes.containsKey(selectedNode)) {
                if (missing.length() > 0) {
                    missing.append(",");
                }
                missing.append(selectedNode);
            }
        }
        return missing.length() == 0 ? "" : ", missingSelected=[" + missing + "]";
    }

    private static MapNodeData parse(String path, String text) throws IOException {
        LinkedHashMap<String, NodeBuilder> builders = new LinkedHashMap<>();
        NodeBuilder current = null;
        String activeList = "";

        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).replace('\t', ' ');
            if (line.trim().length() == 0) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                if (current != null) {
                    current.addListItem(activeList, trimmed.substring(2).trim());
                }
                continue;
            }

            int indent = leadingSpaces(line);
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = cleanScalar(trimmed.substring(0, colon).trim());
            String value = trimmed.substring(colon + 1).trim();
            activeList = "";

            if (indent == 0) {
                current = builders.get(key);
                if (current == null) {
                    current = new NodeBuilder(key);
                    builders.put(key, current);
                }
                current.addInlinePosition(value);
                continue;
            }

            if (current == null) {
                continue;
            }
            if ("position".equals(key)) {
                activeList = "position";
                current.addInlinePosition(value);
            } else if ("next".equals(key)) {
                activeList = "next";
                current.addInlineNext(value);
            }
        }

        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, NodeBuilder> entry : builders.entrySet()) {
            Node node = entry.getValue().toNode();
            if (node != null) {
                nodes.put(entry.getKey(), node);
            }
        }
        if (nodes.isEmpty()) {
            throw new IOException("no map nodes parsed: " + path);
        }
        return new MapNodeData(path, nodes);
    }

    private static boolean hasRoutes(LinkedHashMap<String, Node> nodes) {
        for (Node node : nodes.values()) {
            if (!node.nextNodes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static double normalizeCoordinate(double value, boolean xAxis) {
        if (value > 1.0) {
            return value / (xAxis ? 960.0 : 540.0);
        }
        return value;
    }

    private static List<String> parseInlineList(String raw) {
        String value = raw.trim();
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return Collections.emptyList();
        }
        String body = value.substring(1, value.length() - 1).trim();
        if (body.length() == 0) {
            return Collections.emptyList();
        }
        ArrayList<String> values = new ArrayList<>();
        String[] parts = body.split(",");
        for (String part : parts) {
            String item = cleanScalar(part.trim());
            if (item.length() > 0) {
                values.add(item);
            }
        }
        return values;
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.valueOf(cleanScalar(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        char quote = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (c == '#' && !quoted) {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int leadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static String cleanScalar(String raw) {
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static final class NodeBuilder {
        final String name;
        final ArrayList<Double> positionValues = new ArrayList<>();
        final ArrayList<String> nextNodes = new ArrayList<>();

        NodeBuilder(String name) {
            this.name = name;
        }

        void addListItem(String activeList, String raw) {
            if ("position".equals(activeList) || activeList.length() == 0) {
                Double value = parseDouble(raw);
                if (value != null) {
                    positionValues.add(value);
                    return;
                }
            }
            if ("next".equals(activeList)) {
                String value = cleanScalar(raw);
                if (value.length() > 0) {
                    nextNodes.add(value);
                }
            }
        }

        void addInlinePosition(String raw) {
            for (String item : parseInlineList(raw)) {
                Double value = parseDouble(item);
                if (value != null) {
                    positionValues.add(value);
                }
            }
        }

        void addInlineNext(String raw) {
            for (String item : parseInlineList(raw)) {
                if (item.length() > 0) {
                    nextNodes.add(item);
                }
            }
        }

        Node toNode() {
            if (positionValues.size() < 2) {
                return null;
            }
            double x = normalizeCoordinate(positionValues.get(0), true);
            double y = normalizeCoordinate(positionValues.get(1), false);
            return new Node(name, x, y, nextNodes);
        }
    }

    static final class Node {
        final String name;
        final double x;
        final double y;
        final List<String> nextNodes;

        Node(String name, double x, double y, List<String> nextNodes) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.nextNodes = Collections.unmodifiableList(new ArrayList<>(nextNodes));
        }

        String shortLabel() {
            return name
                    + "(" + format(x) + "," + format(y) + ")"
                    + "->" + nextNodes;
        }
    }
}
