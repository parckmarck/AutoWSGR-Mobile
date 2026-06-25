package org.openwsgr.autowsgrmobile;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AutoExpeditionConfigStore {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "auto-expedition.yaml";

    private AutoExpeditionConfigStore() {
    }

    static LoadResult loadOrCreateDefault(Context context) throws IOException {
        File file = configFile(context);
        boolean created = false;
        if (!file.exists()) {
            writeText(file, AutoExpeditionConfig.defaultConfigText());
            created = true;
        }
        String text = readText(file);
        AutoExpeditionConfig config;
        try {
            config = AutoExpeditionConfig.parseText(text);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid auto-expedition config: " + e.getMessage(), e);
        }
        return new LoadResult(file, config, created);
    }

    static File configFile(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external == null ? context.getFilesDir() : external;
        return new File(new File(base, CONFIG_DIR), CONFIG_FILE);
    }

    private static String readText(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                output.write(buffer, 0, n);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    static final class LoadResult {
        final File file;
        final AutoExpeditionConfig config;
        final String detail;

        LoadResult(File file, AutoExpeditionConfig config, boolean created) {
            this.file = file;
            this.config = config;
            this.detail = "config=" + file.getAbsolutePath()
                    + ", created=" + created
                    + ", intervalSeconds=" + config.intervalSeconds
                    + ", enabled=" + config.enabled();
        }
    }
}
