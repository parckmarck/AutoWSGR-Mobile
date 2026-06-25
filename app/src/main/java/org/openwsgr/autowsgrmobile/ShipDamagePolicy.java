package org.openwsgr.autowsgrmobile;

import java.util.Arrays;

final class ShipDamagePolicy {
    static final int NO_SHIP = -1;
    static final int NORMAL = 0;
    static final int MODERATE = 1;
    static final int SEVERE = 2;
    static final int UNKNOWN = 99;
    private static final int FLEET_SIZE = 6;

    private ShipDamagePolicy() {
    }

    static DamageSnapshot unknown(String source) {
        int[] states = new int[FLEET_SIZE];
        Arrays.fill(states, UNKNOWN);
        return new DamageSnapshot(false, source, states);
    }

    static DamageSnapshot allNormal(String source) {
        int[] states = new int[FLEET_SIZE];
        Arrays.fill(states, NORMAL);
        return new DamageSnapshot(true, source, states);
    }

    static DamageSnapshot withDamage(String source, int position, int damageState) {
        int[] states = new int[FLEET_SIZE];
        Arrays.fill(states, NORMAL);
        if (position >= 0 && position < states.length) {
            states[position] = damageState;
        }
        return new DamageSnapshot(true, source, states);
    }

    static DamageSnapshot fromStates(String source, int[] states, boolean reliable) {
        int[] normalized = new int[FLEET_SIZE];
        Arrays.fill(normalized, UNKNOWN);
        if (states != null) {
            for (int i = 0; i < Math.min(states.length, normalized.length); i++) {
                normalized[i] = normalizeState(states[i]);
            }
        }
        return new DamageSnapshot(reliable, source, normalized);
    }

    static Evaluation evaluate(DamageSnapshot snapshot, String proceedStop) {
        int[] rules = parseRules(proceedStop);
        if (snapshot == null || !snapshot.reliable) {
            return new Evaluation(
                    false,
                    "damage=unknown, proceedStop=" + formatRules(rules)
                            + ", conservative=return"
            );
        }

        for (int i = 0; i < FLEET_SIZE; i++) {
            int state = snapshot.stateAt(i);
            int rule = rules[i];
            if (state == NO_SHIP) {
                continue;
            }
            if (state == UNKNOWN) {
                return new Evaluation(
                        false,
                        "damage=unknown at pos" + (i + 1)
                                + ", proceedStop=" + formatRules(rules)
                                + ", conservative=return"
                );
            }
            if (state >= rule) {
                return new Evaluation(
                        false,
                        "damage blocked pos" + (i + 1)
                                + "=" + stateLabel(state)
                                + " >= " + repairModeLabel(rule)
                                + ", proceedStop=" + formatRules(rules)
                );
            }
        }
        return new Evaluation(
                true,
                "damage ok " + snapshot.shortLabel()
                        + ", proceedStop=" + formatRules(rules)
        );
    }

    private static int[] parseRules(String proceedStop) {
        int[] rules = new int[FLEET_SIZE];
        Arrays.fill(rules, SEVERE);
        String raw = proceedStop == null ? "" : proceedStop.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            String body = raw.substring(1, raw.length() - 1);
            String[] parts = body.split(",");
            for (int i = 0; i < Math.min(parts.length, rules.length); i++) {
                rules[i] = parseRule(parts[i], SEVERE);
            }
            return rules;
        }
        int rule = parseRule(raw, SEVERE);
        Arrays.fill(rules, rule);
        return rules;
    }

    private static int parseRule(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < MODERATE || value > 3) {
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatRules(int[] rules) {
        boolean same = true;
        for (int i = 1; i < rules.length; i++) {
            if (rules[i] != rules[0]) {
                same = false;
                break;
            }
        }
        if (same) {
            return repairModeLabel(rules[0]);
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < rules.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(repairModeLabel(rules[i]));
        }
        out.append("]");
        return out.toString();
    }

    static String repairModeLabel(int value) {
        if (value == MODERATE) {
            return "moderate";
        }
        if (value == SEVERE) {
            return "severe";
        }
        if (value == 3) {
            return "repairing";
        }
        return "unknown";
    }

    static String stateLabel(int value) {
        if (value == NO_SHIP) {
            return "no_ship";
        }
        if (value == NORMAL) {
            return "normal";
        }
        if (value == MODERATE) {
            return "moderate";
        }
        if (value == SEVERE) {
            return "severe";
        }
        return "unknown";
    }

    private static int normalizeState(int state) {
        if (state == NO_SHIP
                || state == NORMAL
                || state == MODERATE
                || state == SEVERE
                || state == UNKNOWN) {
            return state;
        }
        return UNKNOWN;
    }

    static final class DamageSnapshot {
        final boolean reliable;
        final String source;
        private final int[] states;

        private DamageSnapshot(boolean reliable, String source, int[] states) {
            this.reliable = reliable;
            this.source = source;
            this.states = states.clone();
        }

        int stateAt(int index) {
            if (index < 0 || index >= states.length) {
                return UNKNOWN;
            }
            return states[index];
        }

        String shortLabel() {
            if (!reliable) {
                return "unavailable(" + source + ")";
            }
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < states.length; i++) {
                if (i > 0) {
                    out.append(",");
                }
                out.append(stateLabel(states[i]));
            }
            out.append("]");
            return out.toString();
        }
    }

    static final class Evaluation {
        final boolean canProceed;
        final String detail;

        private Evaluation(boolean canProceed, String detail) {
            this.canProceed = canProceed;
            this.detail = detail;
        }
    }
}
