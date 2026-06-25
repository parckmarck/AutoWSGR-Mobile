package org.openwsgr.autowsgrmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NativeCombatTransitions {
    private NativeCombatTransitions() {
    }

    static Graph build(ModeCategory category, NativeCombatPhase endPage) {
        if (category == ModeCategory.MAP) {
            return buildMap(endPage);
        }
        return buildSingle(endPage);
    }

    private static Graph buildMap(NativeCombatPhase endPage) {
        EnumMap<NativeCombatPhase, Branch> transitions = new EnumMap<>(NativeCombatPhase.class);
        List<NativeCombatPhase> coreNav = list(
                NativeCombatPhase.FIGHT_CONDITION,
                NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                NativeCombatPhase.FORMATION,
                NativeCombatPhase.FIGHT_PERIOD
        );
        List<NativeCombatPhase> fightTargets = list(
                NativeCombatPhase.FORMATION,
                NativeCombatPhase.FIGHT_PERIOD,
                NativeCombatPhase.MISSILE_ANIMATION
        );

        List<NativeCombatPhase> afterResult = new ArrayList<>();
        afterResult.add(NativeCombatPhase.PROCEED);
        if (endPage != null) {
            afterResult.add(endPage);
        }
        afterResult.add(NativeCombatPhase.GET_SHIP);
        afterResult.add(NativeCombatPhase.FLAGSHIP_SEVERE_DAMAGE);

        List<NativeCombatPhase> start = new ArrayList<>(coreNav);
        if (endPage != null) {
            start.add(endPage);
        }
        start.add(NativeCombatPhase.DOCK_FULL);
        transitions.put(NativeCombatPhase.START_FIGHT, Branch.fixed(start));
        transitions.put(NativeCombatPhase.DOCK_FULL, Branch.fixed(list()));

        List<NativeCombatPhase> proceedYes = new ArrayList<>(coreNav);
        if (endPage != null) {
            proceedYes.add(endPage);
        }
        LinkedHashMap<String, List<NativeCombatPhase>> proceed = new LinkedHashMap<>();
        proceed.put("yes", immutable(proceedYes));
        proceed.put("no", endPage == null ? list() : list(endPage));
        transitions.put(NativeCombatPhase.PROCEED, Branch.byAction(proceed));

        transitions.put(NativeCombatPhase.FIGHT_CONDITION, Branch.fixed(
                list(NativeCombatPhase.SPOT_ENEMY_SUCCESS, NativeCombatPhase.FORMATION,
                        NativeCombatPhase.FIGHT_PERIOD)
        ));

        LinkedHashMap<String, List<NativeCombatPhase>> spot = new LinkedHashMap<>();
        spot.put("fight", fightTargets);
        spot.put("detour", coreNav);
        spot.put("retreat", endPage == null ? list() : list(endPage));
        transitions.put(NativeCombatPhase.SPOT_ENEMY_SUCCESS, Branch.byAction(spot));

        transitions.put(NativeCombatPhase.FORMATION, Branch.fixed(
                list(NativeCombatPhase.FIGHT_PERIOD, NativeCombatPhase.MISSILE_ANIMATION)
        ));
        transitions.put(NativeCombatPhase.MISSILE_ANIMATION, Branch.fixed(
                list(NativeCombatPhase.FIGHT_PERIOD, NativeCombatPhase.RESULT)
        ));
        transitions.put(NativeCombatPhase.FIGHT_PERIOD, Branch.fixed(
                list(NativeCombatPhase.NIGHT_PROMPT, NativeCombatPhase.RESULT)
        ));

        LinkedHashMap<String, List<NativeCombatPhase>> night = new LinkedHashMap<>();
        night.put("yes", list(NativeCombatPhase.RESULT));
        night.put("no", list(NativeCombatPhase.RESULT));
        transitions.put(NativeCombatPhase.NIGHT_PROMPT, Branch.byAction(night));

        transitions.put(NativeCombatPhase.RESULT, Branch.fixed(afterResult));
        List<NativeCombatPhase> afterDrop = new ArrayList<>();
        for (NativeCombatPhase phase : afterResult) {
            if (phase != NativeCombatPhase.GET_SHIP) {
                afterDrop.add(phase);
            }
        }
        transitions.put(NativeCombatPhase.GET_SHIP, Branch.fixed(afterDrop));
        if (endPage != null) {
            transitions.put(NativeCombatPhase.FLAGSHIP_SEVERE_DAMAGE, Branch.fixed(list(endPage)));
        }

        return new Graph(ModeCategory.MAP, endPage, transitions);
    }

    private static Graph buildSingle(NativeCombatPhase endPage) {
        EnumMap<NativeCombatPhase, Branch> transitions = new EnumMap<>(NativeCombatPhase.class);
        List<NativeCombatPhase> core = list(
                NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                NativeCombatPhase.FORMATION,
                NativeCombatPhase.FIGHT_PERIOD
        );

        List<NativeCombatPhase> start = new ArrayList<>(core);
        start.add(NativeCombatPhase.DOCK_FULL);
        transitions.put(NativeCombatPhase.START_FIGHT, Branch.fixed(start));
        transitions.put(NativeCombatPhase.DOCK_FULL, Branch.fixed(list()));

        LinkedHashMap<String, List<NativeCombatPhase>> spot = new LinkedHashMap<>();
        spot.put("fight", list(NativeCombatPhase.FORMATION, NativeCombatPhase.FIGHT_PERIOD));
        spot.put("retreat", endPage == null ? list() : list(endPage));
        transitions.put(NativeCombatPhase.SPOT_ENEMY_SUCCESS, Branch.byAction(spot));

        transitions.put(NativeCombatPhase.FORMATION, Branch.fixed(list(NativeCombatPhase.FIGHT_PERIOD)));
        transitions.put(NativeCombatPhase.FIGHT_PERIOD, Branch.fixed(
                list(NativeCombatPhase.NIGHT_PROMPT, NativeCombatPhase.RESULT)
        ));

        LinkedHashMap<String, List<NativeCombatPhase>> night = new LinkedHashMap<>();
        night.put("yes", list(NativeCombatPhase.RESULT));
        night.put("no", list(NativeCombatPhase.RESULT));
        transitions.put(NativeCombatPhase.NIGHT_PROMPT, Branch.byAction(night));

        if (endPage != null) {
            transitions.put(NativeCombatPhase.RESULT, Branch.fixed(list(endPage)));
        }
        return new Graph(ModeCategory.SINGLE, endPage, transitions);
    }

    private static List<NativeCombatPhase> list(NativeCombatPhase... phases) {
        ArrayList<NativeCombatPhase> result = new ArrayList<>();
        Collections.addAll(result, phases);
        return Collections.unmodifiableList(result);
    }

    private static List<NativeCombatPhase> immutable(List<NativeCombatPhase> phases) {
        return Collections.unmodifiableList(new ArrayList<>(phases));
    }

    enum ModeCategory {
        MAP,
        SINGLE
    }

    static final class Graph {
        final ModeCategory category;
        final NativeCombatPhase endPage;
        private final EnumMap<NativeCombatPhase, Branch> transitions;

        private Graph(
                ModeCategory category,
                NativeCombatPhase endPage,
                EnumMap<NativeCombatPhase, Branch> transitions
        ) {
            this.category = category;
            this.endPage = endPage;
            this.transitions = transitions;
        }

        NativeCombatPhase[] successors(NativeCombatPhase phase, String lastAction) {
            Branch branch = transitions.get(phase);
            if (branch == null) {
                return new NativeCombatPhase[0];
            }
            List<NativeCombatPhase> successors = branch.resolve(lastAction);
            return successors.toArray(new NativeCombatPhase[successors.size()]);
        }

        int transitionCount() {
            return transitions.size();
        }

        String describeSuccessors(NativeCombatPhase phase, String lastAction) {
            return format(successors(phase, lastAction));
        }
    }

    private static final class Branch {
        private final List<NativeCombatPhase> fixed;
        private final LinkedHashMap<String, List<NativeCombatPhase>> byAction;

        private Branch(List<NativeCombatPhase> fixed, LinkedHashMap<String, List<NativeCombatPhase>> byAction) {
            this.fixed = fixed;
            this.byAction = byAction;
        }

        static Branch fixed(List<NativeCombatPhase> phases) {
            return new Branch(immutable(phases), null);
        }

        static Branch byAction(LinkedHashMap<String, List<NativeCombatPhase>> branches) {
            LinkedHashMap<String, List<NativeCombatPhase>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<NativeCombatPhase>> entry : branches.entrySet()) {
                copy.put(entry.getKey(), immutable(entry.getValue()));
            }
            return new Branch(null, copy);
        }

        List<NativeCombatPhase> resolve(String lastAction) {
            if (fixed != null) {
                return fixed;
            }
            List<NativeCombatPhase> exact = byAction.get(lastAction);
            if (exact != null) {
                return exact;
            }
            if (byAction.isEmpty()) {
                return list();
            }
            return byAction.entrySet().iterator().next().getValue();
        }
    }

    static String format(NativeCombatPhase[] phases) {
        if (phases == null || phases.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < phases.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(phases[i].id);
        }
        builder.append(']');
        return builder.toString();
    }
}
