package org.openwsgr.autowsgrmobile;

import java.io.IOException;

final class NormalSortieRunConfig {
    final String sourcePath;
    final String planPath;
    final String planText;
    final String chapter;
    final String mapId;

    private NormalSortieRunConfig(
            String sourcePath,
            String planPath,
            String planText,
            String chapter,
            String mapId
    ) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.planPath = planPath == null ? "" : planPath;
        this.planText = planText == null ? "" : planText;
        this.chapter = chapter == null ? "" : chapter;
        this.mapId = mapId == null ? "" : mapId;
    }

    static NormalSortieRunConfig parse(String sourcePath, String text) throws IOException {
        String raw = text == null ? "" : text;
        String planPath = "";
        String chapter = "";
        String mapId = "";

        String[] lines = raw.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).replace('\t', ' ');
            if (line.trim().length() == 0 || leadingSpaces(line) != 0) {
                continue;
            }
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = cleanScalar(trimmed.substring(colon + 1).trim());
            if ("plan_path".equals(key)) {
                planPath = value;
            } else if ("chapter".equals(key)) {
                chapter = value;
            } else if ("map".equals(key)) {
                mapId = value;
            }
        }

        if (planPath.length() == 0 && (chapter.length() == 0 || mapId.length() == 0)) {
            throw new IOException("config requires plan_path or inline chapter/map: " + sourcePath);
        }
        return new NormalSortieRunConfig(sourcePath, normalizePlanPath(planPath), raw, chapter, mapId);
    }

    boolean hasPlanPath() {
        return planPath.length() > 0;
    }

    private static String normalizePlanPath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
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
}
