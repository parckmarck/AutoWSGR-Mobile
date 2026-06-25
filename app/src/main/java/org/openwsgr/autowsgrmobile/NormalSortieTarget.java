package org.openwsgr.autowsgrmobile;

import java.util.Locale;

final class NormalSortieTarget {
    private static final String MAP_PREFIX = "autowsgr/data/map/normal/";
    private static final String PLAN_PREFIX = "autowsgr/data/plan/normal_fight/";

    final int chapter;
    final int map;

    private NormalSortieTarget(int chapter, int map) {
        this.chapter = chapter;
        this.map = map;
    }

    static NormalSortieTarget of(int chapter, int map) {
        if (chapter < 1) {
            throw new IllegalArgumentException("chapter must be >= 1: " + chapter);
        }
        if (map < 1) {
            throw new IllegalArgumentException("map must be >= 1: " + map);
        }
        return new NormalSortieTarget(chapter, map);
    }

    String label() {
        return chapter + "-" + map;
    }

    String mapAssetPath() {
        return MAP_PREFIX + label() + ".yaml";
    }

    String exactPlanAssetPath() {
        return PLAN_PREFIX + label() + ".yaml";
    }

    String generatedPlanPath() {
        return PLAN_PREFIX + "generated-" + label() + ".yaml";
    }

    String runnerSummary() {
        return String.format(
                Locale.US,
                "target=%s, map=%s, exactPlan=%s",
                label(),
                mapAssetPath(),
                exactPlanAssetPath()
        );
    }

    @Override
    public String toString() {
        return label();
    }
}
