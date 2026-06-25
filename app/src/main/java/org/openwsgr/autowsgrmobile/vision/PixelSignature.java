package org.openwsgr.autowsgrmobile.vision;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PixelSignature {
    public final String name;
    public final List<PixelRule> rules;

    public PixelSignature(String name, List<PixelRule> rules) {
        this.name = name;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public boolean matchesAll(Bitmap bitmap) {
        return !rules.isEmpty() && matchedCount(bitmap) == rules.size();
    }

    public int matchedCount(Bitmap bitmap) {
        int count = 0;
        for (PixelRule rule : rules) {
            if (rule.matches(bitmap)) {
                count++;
            }
        }
        return count;
    }

    public int ruleCount() {
        return rules.size();
    }
}
