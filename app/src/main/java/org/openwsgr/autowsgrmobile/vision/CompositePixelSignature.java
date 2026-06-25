package org.openwsgr.autowsgrmobile.vision;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompositePixelSignature {
    public final String name;
    public final List<PixelSignature> signatures;

    public CompositePixelSignature(String name, List<PixelSignature> signatures) {
        this.name = name;
        this.signatures = Collections.unmodifiableList(new ArrayList<>(signatures));
    }

    public Match match(Bitmap bitmap) {
        int matchedBefore = 0;
        int totalBefore = 0;
        for (PixelSignature signature : signatures) {
            int matched = signature.matchedCount(bitmap);
            int total = signature.ruleCount();
            if (total > 0 && matched == total) {
                return new Match(true, name, signature.name, matchedBefore + matched, totalRuleCount());
            }
            matchedBefore += matched;
            totalBefore += total;
        }
        return new Match(false, name, "", matchedBefore, totalBefore);
    }

    public int totalRuleCount() {
        int count = 0;
        for (PixelSignature signature : signatures) {
            count += signature.ruleCount();
        }
        return count;
    }

    public int signatureCount() {
        return signatures.size();
    }

    public static final class Match {
        public final boolean matched;
        public final String compositeName;
        public final String matchedSignatureName;
        public final int matchedRules;
        public final int totalRules;

        Match(
                boolean matched,
                String compositeName,
                String matchedSignatureName,
                int matchedRules,
                int totalRules
        ) {
            this.matched = matched;
            this.compositeName = compositeName;
            this.matchedSignatureName = matchedSignatureName;
            this.matchedRules = matchedRules;
            this.totalRules = totalRules;
        }
    }
}
