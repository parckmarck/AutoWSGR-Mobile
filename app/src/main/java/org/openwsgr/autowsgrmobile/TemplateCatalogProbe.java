package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class TemplateCatalogProbe {
    private static final String INDEX_PATH = "asset-index.txt";
    private static final String IMAGE_PREFIX = "autowsgr/data/images/";

    private static final List<String> REQUIRED_COMBAT_TEMPLATES = Collections.unmodifiableList(Arrays.asList(
            "autowsgr/data/images/combat/formation_540p.png",
            "autowsgr/data/images/combat/spot_enemy_540p.png",
            "autowsgr/data/images/combat/result_540p.png",
            "autowsgr/data/images/combat/flagship_damage_540p.png",
            "autowsgr/data/images/combat/proceed_540p.png",
            "autowsgr/data/images/combat/night_battle_540p.png",
            "autowsgr/data/images/combat/fight_condition_540p.png",
            "autowsgr/data/images/combat/bypass_540p.png",
            "autowsgr/data/images/combat/result_page_540p.png",
            "autowsgr/data/images/combat/missile_support_540p.png",
            "autowsgr/data/images/combat/missile_animation_540p.png",
            "autowsgr/data/images/combat/fight_period_540p.png",
            "autowsgr/data/images/combat/get_ship_540p.png",
            "autowsgr/data/images/combat/get_item_540p.png",
            "autowsgr/data/images/combat/end_map_page_540p.png",
            "autowsgr/data/images/combat/end_battle_page_540p.png",
            "autowsgr/data/images/combat/end_exercise_page_540p.png",
            "autowsgr/data/images/build/ship_full_depot_540p.png",
            "autowsgr/data/images/combat/result/ss_540p.png",
            "autowsgr/data/images/combat/result/s_540p.png",
            "autowsgr/data/images/combat/result/a_540p.png",
            "autowsgr/data/images/combat/result/b_540p.png",
            "autowsgr/data/images/combat/result/c_540p.png",
            "autowsgr/data/images/combat/result/d_540p.png",
            "autowsgr/data/images/combat/result/loot_540p.png"
    ));

    private TemplateCatalogProbe() {
    }

    static Result run(Context context) {
        List<String> pngPaths;
        try {
            pngPaths = readPngPaths(context.getAssets());
        } catch (IOException e) {
            return new Result(false, "Template catalog: failed", "asset index failed: " + e.getMessage());
        }

        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        Map<String, Integer> categoryCounts = new TreeMap<>();
        int decoded = 0;
        int decodeFailures = 0;
        int totalPixels = 0;
        String largestPath = "";
        int largestWidth = 0;
        int largestHeight = 0;

        StringBuilder failures = new StringBuilder();
        for (String path : pngPaths) {
            countCategory(categoryCounts, path);
            Bitmap bitmap = null;
            try {
                bitmap = store.readBitmap(path);
                decoded++;
                int pixels = bitmap.getWidth() * bitmap.getHeight();
                totalPixels += pixels;
                if (pixels > largestWidth * largestHeight) {
                    largestPath = shortImagePath(path);
                    largestWidth = bitmap.getWidth();
                    largestHeight = bitmap.getHeight();
                }
            } catch (IOException e) {
                decodeFailures++;
                if (failures.length() < 180) {
                    if (failures.length() > 0) {
                        failures.append("; ");
                    }
                    failures.append(shortImagePath(path)).append(": ").append(e.getMessage());
                }
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        }

        int covered = 0;
        List<String> missingRequired = new ArrayList<>();
        for (String required : REQUIRED_COMBAT_TEMPLATES) {
            if (pngPaths.contains(required)) {
                covered++;
            } else {
                missingRequired.add(shortImagePath(required));
            }
        }

        StringBuilder detail = new StringBuilder();
        append(detail, "png entries=" + pngPaths.size() + ", decoded=" + decoded
                + ", failures=" + decodeFailures + ", totalPixels=" + totalPixels);
        append(detail, "categories=" + formatCategories(categoryCounts));
        append(detail, "combat TemplateKey coverage=" + covered + "/"
                + REQUIRED_COMBAT_TEMPLATES.size());
        if (largestWidth > 0) {
            append(detail, "largest=" + largestPath + " " + largestWidth + "x" + largestHeight);
        }
        if (!missingRequired.isEmpty()) {
            append(detail, "missing required=" + join(missingRequired));
        }
        if (failures.length() > 0) {
            append(detail, "decode failures=" + failures);
        }

        boolean ok = !pngPaths.isEmpty()
                && decoded == pngPaths.size()
                && missingRequired.isEmpty();
        return new Result(ok, "Template catalog: " + (ok ? "passed" : "failed"), detail.toString());
    }

    private static List<String> readPngPaths(AssetManager assets) throws IOException {
        List<String> paths = new ArrayList<>();
        InputStream stream = assets.open(INDEX_PATH);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                String path = line.trim();
                if (path.startsWith(IMAGE_PREFIX) && path.endsWith(".png")) {
                    paths.add(path);
                }
            }
        } finally {
            stream.close();
        }
        return paths;
    }

    private static void countCategory(Map<String, Integer> categoryCounts, String path) {
        String rest = path.substring(IMAGE_PREFIX.length());
        int slash = rest.indexOf('/');
        String category = slash > 0 ? rest.substring(0, slash) : "root";
        Integer current = categoryCounts.get(category);
        categoryCounts.put(category, current == null ? 1 : current + 1);
    }

    private static String shortImagePath(String path) {
        if (path.startsWith(IMAGE_PREFIX)) {
            return path.substring(IMAGE_PREFIX.length());
        }
        return path;
    }

    private static String formatCategories(Map<String, Integer> categoryCounts) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            parts.add(entry.getKey() + ":" + entry.getValue());
        }
        return join(parts);
    }

    private static String join(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(items.get(i));
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    static final class Result {
        final boolean ok;
        final String status;
        final String detail;

        Result(boolean ok, String status, String detail) {
            this.ok = ok;
            this.status = status;
            this.detail = detail;
        }
    }
}
