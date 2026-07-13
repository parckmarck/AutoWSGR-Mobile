package org.openwsgr.autowsgrmobile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class YamlSectionExtractor {
    private YamlSectionExtractor() {
    }

    static Map<String, String> topLevelMappings(String source, String yaml) throws IOException {
        String location = source == null ? "<unknown>" : source;
        String normalized = yaml == null ? "" : yaml.replace("\r\n", "\n");
        if (normalized.indexOf('\t') >= 0) {
            throw new IOException(location + ": tab indentation is not supported");
        }

        String[] lines = normalized.split("\n", -1);
        Map<String, String> values = new LinkedHashMap<>();
        String currentKey = null;
        boolean currentScalar = false;
        StringBuilder currentBody = null;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.trim().isEmpty()) {
                if (currentKey != null && !currentScalar && index < lines.length - 1) {
                    currentBody.append('\n');
                }
                continue;
            }
            int indentation = leadingSpaces(line);
            if (indentation > 0) {
                if (currentKey != null && !currentScalar) {
                    int dedent = Math.min(2, indentation);
                    currentBody.append(line.substring(dedent)).append('\n');
                }
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }

            if (currentKey != null && !currentScalar) {
                values.put(currentKey, currentBody.toString());
            }
            int colon = line.indexOf(':');
            String key = colon < 0 ? "" : line.substring(0, colon).trim();
            if (colon < 0 || key.isEmpty()) {
                throw new IOException(location + ": invalid top-level mapping at line " + (index + 1));
            }
            if (values.containsKey(key) || key.equals(currentKey)) {
                throw new IOException(location + ": duplicate top-level key " + key
                        + " at line " + (index + 1));
            }

            String scalar = line.substring(colon + 1).trim();
            currentKey = key;
            currentScalar = !scalar.isEmpty() && !scalar.startsWith("#");
            currentBody = new StringBuilder();
            if (currentScalar) {
                values.put(key, scalar);
            }
        }
        if (currentKey != null && !currentScalar) {
            values.put(currentKey, currentBody.toString());
        }
        return values;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
}
