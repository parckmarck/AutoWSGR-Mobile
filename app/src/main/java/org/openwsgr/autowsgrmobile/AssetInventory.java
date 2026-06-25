package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AssetInventory {
    private static final String INDEX_PATH = "asset-index.txt";

    private AssetInventory() {
    }

    static Summary load(Context context) {
        List<String> paths = new ArrayList<>();
        AssetManager assets = context.getAssets();
        try {
            InputStream stream = assets.open(INDEX_PATH);
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)
                );
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        paths.add(trimmed);
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            return Summary.error(e.getMessage());
        }
        return Summary.from(paths);
    }

    static final class Summary {
        final int total;
        final int yamlCount;
        final int imageCount;
        final int jarCount;
        final String error;
        final List<String> samples;

        private Summary(
                int total,
                int yamlCount,
                int imageCount,
                int jarCount,
                String error,
                List<String> samples
        ) {
            this.total = total;
            this.yamlCount = yamlCount;
            this.imageCount = imageCount;
            this.jarCount = jarCount;
            this.error = error;
            this.samples = Collections.unmodifiableList(samples);
        }

        static Summary error(String message) {
            return new Summary(0, 0, 0, 0, message, Collections.<String>emptyList());
        }

        static Summary from(List<String> paths) {
            int yaml = 0;
            int image = 0;
            int jar = 0;
            List<String> sample = new ArrayList<>();
            for (String path : paths) {
                if (path.endsWith(".yaml")) {
                    yaml++;
                } else if (path.endsWith(".png")) {
                    image++;
                } else if (path.endsWith(".jar")) {
                    jar++;
                }
                if (sample.size() < 4) {
                    sample.add(path);
                }
            }
            return new Summary(paths.size(), yaml, image, jar, null, sample);
        }

        String toStatusLine() {
            if (error != null) {
                return "Assets: failed to load index: " + error;
            }
            return "Assets: " + total + " files, " + yamlCount + " YAML, "
                    + imageCount + " PNG templates, " + jarCount + " binary";
        }
    }
}
