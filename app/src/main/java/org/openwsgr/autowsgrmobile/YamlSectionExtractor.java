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
                } else if (!line.trim().startsWith("#")) {
                    throw new IOException(location + ": invalid indentation at line "
                            + (index + 1));
                }
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }

            if (currentKey != null && !currentScalar) {
                values.put(currentKey, currentBody.toString());
            }
            String trimmedLine = line.trim();
            if (startsIndicator(trimmedLine, '-') || startsIndicator(trimmedLine, '?')) {
                throw new IOException(location + ": unsupported top-level YAML construct at line "
                        + (index + 1));
            }
            int colon = mappingColon(line);
            String rawKey = colon < 0 ? "" : line.substring(0, colon).trim();
            if (colon < 0 || rawKey.isEmpty()) {
                throw new IOException(location + ": invalid top-level mapping at line " + (index + 1));
            }
            String key = decodeKey(location, index + 1, rawKey);
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

    private static int mappingColon(String line) {
        int first = 0;
        while (first < line.length() && Character.isWhitespace(line.charAt(first))) {
            first++;
        }
        if (first < line.length() && (line.charAt(first) == '\'' || line.charAt(first) == '"')) {
            return quotedMappingColon(line, first, line.charAt(first));
        }
        return plainMappingColon(line, first);
    }

    private static int plainMappingColon(String line, int first) {
        for (int index = first; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '#' && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return -1;
            }
            if (character == ':'
                    && (index + 1 == line.length()
                    || Character.isWhitespace(line.charAt(index + 1)))) {
                return index;
            }
        }
        return -1;
    }

    private static int quotedMappingColon(String line, int first, char quote) {
        int closingQuote = -1;
        for (int index = first + 1; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quote == '"' && character == '\\' && index + 1 < line.length()) {
                index++;
                continue;
            }
            if (character != quote) {
                continue;
            }
            if (quote == '\'' && index + 1 < line.length()
                    && line.charAt(index + 1) == '\'') {
                index++;
                continue;
            }
            closingQuote = index;
            break;
        }
        if (closingQuote < 0) {
            return -1;
        }
        return plainMappingColon(line, closingQuote + 1);
    }

    private static boolean startsIndicator(String line, char indicator) {
        return !line.isEmpty()
                && line.charAt(0) == indicator
                && (line.length() == 1 || Character.isWhitespace(line.charAt(1)));
    }

    private static String decodeKey(String source, int lineNumber, String rawKey) throws IOException {
        char quote = rawKey.charAt(0);
        if (quote != '\'' && quote != '"') {
            return rawKey;
        }
        if (rawKey.length() < 2 || rawKey.charAt(rawKey.length() - 1) != quote) {
            throw invalidQuotedKey(source, lineNumber);
        }
        StringBuilder decoded = new StringBuilder();
        int end = rawKey.length() - 1;
        for (int index = 1; index < end; index++) {
            char character = rawKey.charAt(index);
            if (quote == '\'') {
                if (character != '\'') {
                    decoded.append(character);
                    continue;
                }
                if (index + 1 < end && rawKey.charAt(index + 1) == '\'') {
                    decoded.append('\'');
                    index++;
                    continue;
                }
                throw invalidQuotedKey(source, lineNumber);
            }
            if (character == '"') {
                throw invalidQuotedKey(source, lineNumber);
            }
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (++index >= end) {
                throw invalidQuotedKey(source, lineNumber);
            }
            char escaped = rawKey.charAt(index);
            if (escaped == '"' || escaped == '\\' || escaped == '/') {
                decoded.append(escaped);
            } else if (escaped == 'n') {
                decoded.append('\n');
            } else if (escaped == 'r') {
                decoded.append('\r');
            } else if (escaped == 't') {
                decoded.append('\t');
            } else if (escaped == 'b') {
                decoded.append('\b');
            } else if (escaped == 'f') {
                decoded.append('\f');
            } else {
                throw invalidQuotedKey(source, lineNumber);
            }
        }
        if (decoded.length() == 0) {
            throw invalidQuotedKey(source, lineNumber);
        }
        return decoded.toString();
    }

    private static IOException invalidQuotedKey(String source, int lineNumber) {
        return new IOException(source + ": invalid or unsupported quoted key at line " + lineNumber);
    }
}
