package org.openwsgr.autowsgrmobile;

final class AutoExpeditionConfig {
    static final long DEFAULT_INTERVAL_SECONDS = 900L;

    final long intervalSeconds;

    private AutoExpeditionConfig(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    static AutoExpeditionConfig parseText(String text) {
        long intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        String raw = text == null ? "" : text;
        String[] lines = raw.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.length() == 0) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = cleanScalar(line.substring(colon + 1).trim());
            if ("interval_seconds".equals(key)) {
                try {
                    intervalSeconds = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "invalid interval_seconds: " + value,
                            e
                    );
                }
            }
        }
        return new AutoExpeditionConfig(intervalSeconds);
    }

    static String defaultConfigText() {
        return "# AutoWSGR Android auto expedition config\n"
                + "interval_seconds: " + DEFAULT_INTERVAL_SECONDS + "\n";
    }

    boolean enabled() {
        return intervalSeconds > 0L;
    }

    private static String stripComment(String line) {
        String value = line == null ? "" : line;
        int comment = value.indexOf('#');
        if (comment >= 0) {
            return value.substring(0, comment);
        }
        return value;
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
