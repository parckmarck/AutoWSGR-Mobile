package org.openwsgr.autowsgrmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NormalMapCatalog {
    private static final Pattern NORMAL_MAP_PATTERN = Pattern.compile(
            "^autowsgr/data/map/normal/(\\d+)-(\\d+)\\.yaml$"
    );

    private final TreeMap<Integer, List<NormalSortieTarget>> byChapter;

    private NormalMapCatalog(TreeMap<Integer, List<NormalSortieTarget>> byChapter) {
        this.byChapter = byChapter;
    }

    static NormalMapCatalog fromAssetPaths(List<String> paths) {
        TreeMap<Integer, TreeMap<Integer, NormalSortieTarget>> grouped = new TreeMap<>();
        if (paths != null) {
            for (String rawPath : paths) {
                NormalSortieTarget target = parsePath(rawPath);
                if (target == null) {
                    continue;
                }
                TreeMap<Integer, NormalSortieTarget> maps = grouped.get(target.chapter);
                if (maps == null) {
                    maps = new TreeMap<>();
                    grouped.put(target.chapter, maps);
                }
                maps.put(target.map, target);
            }
        }

        TreeMap<Integer, List<NormalSortieTarget>> immutable = new TreeMap<>();
        for (Map.Entry<Integer, TreeMap<Integer, NormalSortieTarget>> entry : grouped.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue().values()))
            );
        }
        return new NormalMapCatalog(immutable);
    }

    static NormalMapCatalog fromAssetIndexText(String text) {
        ArrayList<String> paths = new ArrayList<>();
        if (text != null) {
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() > 0) {
                    paths.add(trimmed);
                }
            }
        }
        return fromAssetPaths(paths);
    }

    int chapterCount() {
        return byChapter.size();
    }

    List<Integer> chapters() {
        return Collections.unmodifiableList(new ArrayList<>(byChapter.keySet()));
    }

    List<NormalSortieTarget> mapsForChapter(int chapter) {
        List<NormalSortieTarget> maps = byChapter.get(chapter);
        return maps == null ? Collections.<NormalSortieTarget>emptyList() : maps;
    }

    boolean contains(int chapter, int map) {
        for (NormalSortieTarget target : mapsForChapter(chapter)) {
            if (target.map == map) {
                return true;
            }
        }
        return false;
    }

    NormalSortieTarget defaultTarget() {
        for (List<NormalSortieTarget> maps : byChapter.values()) {
            if (!maps.isEmpty()) {
                return maps.get(0);
            }
        }
        return NormalSortieTarget.of(1, 1);
    }

    String[] chapterLabels() {
        List<Integer> chapters = chapters();
        String[] labels = new String[chapters.size()];
        for (int i = 0; i < chapters.size(); i++) {
            labels[i] = String.valueOf(chapters.get(i));
        }
        return labels;
    }

    String[] mapLabelsForChapter(int chapter) {
        List<NormalSortieTarget> maps = mapsForChapter(chapter);
        String[] labels = new String[maps.size()];
        for (int i = 0; i < maps.size(); i++) {
            labels[i] = String.valueOf(maps.get(i).map);
        }
        return labels;
    }

    String chapterSummary(int chapter) {
        List<NormalSortieTarget> maps = mapsForChapter(chapter);
        if (maps.isEmpty()) {
            return chapter + "-?, maps=0";
        }
        StringBuilder labels = new StringBuilder();
        for (NormalSortieTarget target : maps) {
            if (labels.length() > 0) {
                labels.append(",");
            }
            labels.append(target.label());
        }
        return labels + ", maps=" + maps.size();
    }

    private static NormalSortieTarget parsePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String path = rawPath.trim().replace('\\', '/');
        Matcher matcher = NORMAL_MAP_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return NormalSortieTarget.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
