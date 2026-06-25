package org.openwsgr.autowsgrmobile;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class NormalSortieConfigStore {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "normal-sortie.yaml";

    private NormalSortieConfigStore() {
    }

    static String defaultConfigText(NormalSortieTarget target) {
        NormalSortieTarget safeTarget = target == null ? NormalSortieTarget.of(1, 1) : target;
        return "# AutoWSGR Android normal sortie config\n"
                + "plan_path: " + safeTarget.exactPlanAssetPath() + "\n";
    }

    static File configFile(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external == null ? context.getFilesDir() : external;
        return new File(new File(base, CONFIG_DIR), CONFIG_FILE);
    }

    static LoadResult loadOrCreateDefault(Context context) throws IOException {
        return loadOrCreateDefault(context, NormalSortieTarget.of(1, 1));
    }

    static LoadResult loadOrCreateDefault(Context context, NormalSortieTarget target) throws IOException {
        File file = configFile(context);
        boolean created = false;
        if (!file.exists()) {
            writeText(file, defaultConfigText(target));
            created = true;
        }
        String text = readText(file);
        NormalSortieRunConfig config = NormalSortieRunConfig.parse(file.getAbsolutePath(), text);
        return new LoadResult(file, config, created);
    }

    static LoadResult writeDefault(Context context) throws IOException {
        return writeDefault(context, NormalSortieTarget.of(1, 1));
    }

    static LoadResult writeDefault(Context context, NormalSortieTarget target) throws IOException {
        File file = configFile(context);
        writeText(file, defaultConfigText(target));
        String text = readText(file);
        NormalSortieRunConfig config = NormalSortieRunConfig.parse(file.getAbsolutePath(), text);
        return new LoadResult(file, config, true);
    }

    static String statusLine(Context context) {
        File file = configFile(context);
        if (!file.exists()) {
            return "出征配置：未创建，默认 1-1";
        }
        try {
            NormalSortieRunConfig config = NormalSortieRunConfig.parse(file.getAbsolutePath(), readText(file));
            return "出征配置：" + file.getAbsolutePath()
                    + (config.hasPlanPath() ? "，计划：" + config.planPath : "，内联计划");
        } catch (IOException e) {
            return "出征配置：读取失败，" + e.getMessage();
        }
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
        final NormalSortieRunConfig config;
        final boolean created;
        final String detail;

        LoadResult(File file, NormalSortieRunConfig config, boolean created) {
            this.file = file;
            this.config = config;
            this.created = created;
            this.detail = "config=" + file.getAbsolutePath()
                    + ", created=" + created
                    + (config.hasPlanPath() ? ", planPath=" + config.planPath : ", inlinePlan=true");
        }
    }
}
