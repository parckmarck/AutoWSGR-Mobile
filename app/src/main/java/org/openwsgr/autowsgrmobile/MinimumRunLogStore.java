package org.openwsgr.autowsgrmobile;

import java.io.File;

final class MinimumRunLogStore {
    private MinimumRunLogStore() {
    }

    static File logFile(File baseDir, String runId) {
        return logFile(baseDir, "minimum-normal", runId);
    }

    static File logFile(File baseDir, String prefix, String runId) {
        return new File(new File(baseDir, "runs"), safeId(prefix) + "-" + safeId(runId) + ".log");
    }

    static File failureScreenshotFile(File baseDir, String runId) {
        return failureScreenshotFile(baseDir, "minimum-normal", runId);
    }

    static File failureScreenshotFile(File baseDir, String prefix, String runId) {
        return new File(new File(baseDir, "failures"), safeId(prefix) + "-" + safeId(runId) + ".png");
    }

    static String line(String runId, String status, String detail) {
        return "run=" + clean(runId)
                + " status=" + clean(status)
                + " detail=" + clean(detail);
    }

    static String safeId(String value) {
        String cleaned = clean(value).replaceAll("[^A-Za-z0-9._-]", "-");
        while (cleaned.contains("--")) {
            cleaned = cleaned.replace("--", "-");
        }
        if (cleaned.length() == 0) {
            return "unknown";
        }
        return cleaned;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
