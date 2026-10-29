package org.openwsgr.autowsgrmobile;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

final class AndroidAutomationSessionSink implements AutomationSession.Sink {
    private final File baseDir;
    private final String flowPrefix;
    private final File logFile;

    private AndroidAutomationSessionSink(File baseDir, String flowPrefix, String runId) {
        this.baseDir = baseDir;
        this.flowPrefix = MinimumRunLogStore.safeId(flowPrefix);
        this.logFile = MinimumRunLogStore.logFile(baseDir, this.flowPrefix, runId);
    }

    static AndroidAutomationSessionSink create(Context context, String flowPrefix, String runId) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }
        return new AndroidAutomationSessionSink(
                applicationContext.getFilesDir(), flowPrefix, runId
        );
    }

    @Override public synchronized void append(String line) throws IOException {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create log directory: " + parent);
        }
        FileWriter writer = new FileWriter(logFile, true);
        try {
            writer.write(line == null ? "" : line);
            writer.write('\n');
        } finally {
            writer.close();
        }
    }

    @Override public String logPath() {
        return logFile.getAbsolutePath();
    }

    @Override public synchronized String saveFailure(String runId) {
        FailureEvidenceStore.SaveResult result = FailureEvidenceStore.saveLatestFailure(
                baseDir, runId, flowPrefix
        );
        return result.saved && result.file != null ? result.file.getAbsolutePath() : "";
    }
}
