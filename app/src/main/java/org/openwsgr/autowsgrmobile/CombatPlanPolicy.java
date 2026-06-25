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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CombatPlanPolicy {
    private static final String INDEX_PATH = "asset-index.txt";
    private static final String PLAN_PREFIX = "autowsgr/data/plan/";
    static final String DEFAULT_PLAN = "autowsgr/data/plan/normal_fight/1-1.yaml";
    static final String COMPLEX_PLAN = "autowsgr/data/plan/normal_fight/9-2ADGHM.yaml";
    static final String EVENT_PLAN = "autowsgr/data/plan/event/E5ADE夜战.yaml";
    static final String RULE_PLAN = "autowsgr/data/plan/normal_fight/7-4胖次.yaml";
    static final String COMPLEX_RULE_PLAN = "autowsgr/data/plan/normal_fight/4周常.yaml";

    final String path;
    final String name;
    final String mode;
    final String eventName;
    final String chapter;
    final String mapId;
    final int fleetId;
    final int fightCondition;
    final String repairMode;
    final List<String> selectedNodes;
    final NodeDecision defaultNode;
    final LinkedHashMap<String, NodeDecision> nodes;

    private CombatPlanPolicy(
            String path,
            String name,
            String mode,
            String eventName,
            String chapter,
            String mapId,
            int fleetId,
            int fightCondition,
            String repairMode,
            List<String> selectedNodes,
            NodeDecision defaultNode,
            LinkedHashMap<String, NodeDecision> nodes
    ) {
        this.path = path;
        this.name = name;
        this.mode = mode;
        this.eventName = eventName;
        this.chapter = chapter;
        this.mapId = mapId;
        this.fleetId = fleetId;
        this.fightCondition = fightCondition;
        this.repairMode = repairMode;
        this.selectedNodes = Collections.unmodifiableList(selectedNodes);
        this.defaultNode = defaultNode;
        this.nodes = nodes;
    }

    static ProbeResult probe(Context context) {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        StringBuilder detail = new StringBuilder();
        boolean ok = true;

        List<String> planPaths;
        try {
            planPaths = listPlanPaths(context);
            append(detail, "plan files=" + planPaths.size()
                    + ", normal=" + countPrefix(planPaths, PLAN_PREFIX + "normal_fight/")
                    + ", event=" + countPrefix(planPaths, PLAN_PREFIX + "event/"));
        } catch (IOException e) {
            planPaths = Collections.emptyList();
            ok = false;
            append(detail, "plan index failed: " + e.getMessage());
        }

        ok &= parseAndAppend(store, DEFAULT_PLAN, detail);
        ok &= parseAndAppend(store, COMPLEX_PLAN, detail);
        ok &= parseAndAppend(store, EVENT_PLAN, detail);
        ok &= appendDecisionProbe(store, detail);
        ok &= appendEnemyRuleProbe(store, detail);
        GuardedCombatRunner.ProbeResult runnerSpotContext = GuardedCombatRunner.probeSpotContextDecision(context);
        append(detail, runnerSpotContext.detail);
        ok &= runnerSpotContext.ok;

        int parsed = 0;
        int failed = 0;
        String firstFailure = "";
        for (String path : planPaths) {
            try {
                parse(path, store.readText(path));
                parsed++;
            } catch (IOException e) {
                failed++;
                if (firstFailure.length() == 0) {
                    firstFailure = path + ": " + e.getMessage();
                }
            }
        }
        append(detail, "bulk parse=" + parsed + "/" + planPaths.size()
                + (failed == 0 ? "" : ", firstFailure=" + firstFailure));
        ok &= failed == 0 && !planPaths.isEmpty();

        return new ProbeResult(ok, detail.toString());
    }

    static String defaultRunnerPolicySummary(Context context) {
        try {
            CombatPlanPolicy policy = loadDefault(context);
            return policy.toRunnerSummary();
        } catch (IOException e) {
            return "planPolicy=unavailable, reason=" + e.getMessage();
        }
    }

    static CombatPlanPolicy loadDefault(Context context) throws IOException {
        return load(context, DEFAULT_PLAN);
    }

    static CombatPlanPolicy load(Context context, String path) throws IOException {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        return parse(path, store.readText(path));
    }

    static CombatPlanPolicy loadForNormalTarget(Context context, NormalSortieTarget target) throws IOException {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        String exactPlanText = null;
        try {
            exactPlanText = store.readText(target.exactPlanAssetPath());
        } catch (IOException ignored) {
            exactPlanText = null;
        }
        MapNodeData mapData = MapNodeData.load(context, target.mapAssetPath());
        return resolveNormalTarget(target, exactPlanText, mapData);
    }

    static CombatPlanPolicy loadForRunConfig(Context context, NormalSortieRunConfig config) throws IOException {
        AutoWsgrAssetStore store = new AutoWsgrAssetStore(context);
        String referencedPlanText = null;
        CombatPlanPolicy draft;
        if (config.hasPlanPath()) {
            referencedPlanText = store.readText(config.planPath);
            draft = parse(config.planPath, referencedPlanText);
        } else {
            draft = parse(config.sourcePath, config.planText);
        }
        MapNodeData mapData = MapNodeData.load(context, MapNodeData.mapPathForPlan(draft));
        return resolveRunConfig(config, referencedPlanText, mapData);
    }

    static CombatPlanPolicy resolveRunConfig(
            NormalSortieRunConfig config,
            String referencedPlanText,
            MapNodeData mapData
    ) throws IOException {
        if (config == null) {
            throw new IOException("missing run config");
        }
        CombatPlanPolicy policy;
        if (config.hasPlanPath()) {
            if (referencedPlanText == null || referencedPlanText.trim().length() == 0) {
                throw new IOException("config plan_path requires readable plan text: " + config.planPath);
            }
            policy = parse(config.planPath, referencedPlanText);
        } else {
            policy = parse(config.sourcePath, config.planText);
        }
        validateNormalRunConfig(policy, mapData);
        return policy;
    }

    static CombatPlanPolicy resolveNormalTarget(
            NormalSortieTarget target,
            String exactPlanText,
            MapNodeData mapData
    ) throws IOException {
        if (exactPlanText != null && exactPlanText.trim().length() > 0) {
            return parse(target.exactPlanAssetPath(), exactPlanText);
        }
        return generatedForNormalTarget(target, mapData);
    }

    PlanDecision decisionForPhase(NativeCombatPhase phase, int routeIndex) {
        return decisionForPhase(
                phase,
                routeIndex,
                ShipDamagePolicy.allNormal("policy probe default")
        );
    }

    PlanDecision decisionForPhase(
            NativeCombatPhase phase,
            int routeIndex,
            ShipDamagePolicy.DamageSnapshot damageSnapshot
    ) {
        return decisionForPhase(phase, routeIndex, damageSnapshot, 0);
    }

    PlanDecision decisionForPhase(
            NativeCombatPhase phase,
            int routeIndex,
            ShipDamagePolicy.DamageSnapshot damageSnapshot,
            int pendingRuleFormation
    ) {
        return decisionForPhaseWithRuleAction(
                phase,
                routeIndex,
                damageSnapshot,
                null,
                pendingRuleFormation
        );
    }

    PlanDecision decisionForPhase(
            NativeCombatPhase phase,
            int routeIndex,
            ShipDamagePolicy.DamageSnapshot damageSnapshot,
            int pendingRuleFormation,
            EnemyRuleEngine.RuleAction spotRuleAction
    ) {
        return decisionForPhaseWithRuleAction(
                phase,
                routeIndex,
                damageSnapshot,
                spotRuleAction,
                pendingRuleFormation
        );
    }

    PlanDecision decisionForSpotRuleAction(
            int routeIndex,
            EnemyRuleEngine.RuleAction ruleAction
    ) {
        return decisionForPhaseWithRuleAction(
                NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                routeIndex,
                ShipDamagePolicy.allNormal("spot rule probe"),
                ruleAction,
                0
        );
    }

    PlanDecision decisionForSpotEnemySnapshot(
            int routeIndex,
            EnemyRuleEngine.EnemySnapshot snapshot
    ) {
        return decisionForSpotEnemyContext(routeIndex, snapshot, "");
    }

    PlanDecision decisionForSpotEnemyContext(
            int routeIndex,
            EnemyRuleEngine.EnemySnapshot snapshot,
            String enemyFormation
    ) {
        NodeDecision decision = decisionForRouteIndex(routeIndex);
        EnemyRuleEngine.RuleAction action = decision.evaluateSpotRules(snapshot, enemyFormation);
        return decisionForPhaseWithRuleAction(
                NativeCombatPhase.SPOT_ENEMY_SUCCESS,
                routeIndex,
                ShipDamagePolicy.allNormal("spot enemy context probe"),
                action,
                0
        );
    }

    private PlanDecision decisionForPhaseWithRuleAction(
            NativeCombatPhase phase,
            int routeIndex,
            ShipDamagePolicy.DamageSnapshot damageSnapshot,
            EnemyRuleEngine.RuleAction spotRuleAction,
            int pendingRuleFormation
    ) {
        String node = nodeAt(routeIndex);
        NodeDecision decision = decisionForRouteIndex(routeIndex);
        String actionId;
        String actionReason;
        boolean routeAdvances = false;
        boolean routeEnds = false;
        int ruleFormation = 0;
        boolean carriesRuleFormation = false;
        boolean consumesRuleFormation = false;

        if (phase == NativeCombatPhase.PROCEED) {
            boolean routeHasNext = hasNextNode(routeIndex);
            ShipDamagePolicy.Evaluation damage = ShipDamagePolicy.evaluate(
                    damageSnapshot,
                    decision.proceedStop
            );
            boolean shouldProceed = decision.proceed && routeHasNext && damage.canProceed;
            actionId = shouldProceed ? "proceed_yes" : "proceed_no";
            actionReason = "node " + node
                    + " proceed=" + decision.proceed
                    + ", routeHasNext=" + routeHasNext
                    + ", " + damage.detail;
            routeAdvances = shouldProceed;
            routeEnds = !shouldProceed;
        } else if (phase == NativeCombatPhase.FIGHT_CONDITION) {
            int selectedCondition = clampFightCondition(fightCondition);
            actionId = "fight_condition_" + selectedCondition;
            actionReason = "plan fightCondition=" + selectedCondition
                    + ", yamlFightCondition=" + fightCondition;
        } else if (phase == NativeCombatPhase.NIGHT_PROMPT) {
            actionId = decision.night ? "night_yes" : "night_no";
            actionReason = "node " + node + " night=" + decision.night;
        } else if (phase == NativeCombatPhase.FORMATION) {
            int selectedFormation = pendingRuleFormation > 0
                    ? clampFormation(pendingRuleFormation)
                    : clampFormation(decision.formation);
            actionId = "formation_" + selectedFormation;
            actionReason = "node " + node
                    + " formation=" + selectedFormation
                    + (pendingRuleFormation > 0
                    ? ", ruleFormationOverride=" + selectedFormation
                    : ", yamlFormation=" + decision.formation);
            consumesRuleFormation = pendingRuleFormation > 0;
        } else if (phase == NativeCombatPhase.MISSILE_ANIMATION) {
            actionId = "missile_skip";
            actionReason = "phase missile_animation uses original skip animation double-tap";
        } else if (phase == NativeCombatPhase.SPOT_ENEMY_SUCCESS) {
            EnemyRuleEngine.RuleAction ruleAction = spotRuleAction == null
                    ? EnemyRuleEngine.RuleAction.noAction()
                    : spotRuleAction;
            if (ruleAction.kind == EnemyRuleEngine.RuleAction.Kind.RETREAT
                    || ruleAction.kind == EnemyRuleEngine.RuleAction.Kind.DETOUR) {
                actionId = ruleAction.actionId();
            } else {
                actionId = "spot_fight";
            }
            if (ruleAction.kind == EnemyRuleEngine.RuleAction.Kind.FORMATION) {
                ruleFormation = clampFormation(ruleAction.formation);
                carriesRuleFormation = true;
            }
            actionReason = "node " + node
                    + " selected=" + isSelectedNode(node)
                    + ", enemyRules=" + decision.enemyRuleCount()
                    + ", formationRules=" + decision.formationRuleCount()
                    + ", ruleEngine=ready"
                    + ", ruleAction=" + ruleAction.shortLabel()
                    + (carriesRuleFormation
                    ? ", carryFormation=" + ruleFormation
                    : "")
                    + (spotRuleAction == null
                    ? ", liveEnemyRecognition=unavailable"
                    : ", source=rule evaluation");
        } else if (phase == NativeCombatPhase.RESULT) {
            actionId = "result_continue";
            actionReason = "node " + node + " result continue";
        } else {
            actionId = "";
            actionReason = "phase " + (phase == null ? "none" : phase.id) + " is not guarded by plan policy";
        }

        return new PlanDecision(
                routeIndex,
                node,
                decision,
                phase,
                actionId,
                actionReason,
                routeAdvances,
                routeEnds,
                ruleFormation,
                carriesRuleFormation,
                consumesRuleFormation
        );
    }

    int routeSize() {
        return selectedNodes.isEmpty() ? 1 : selectedNodes.size();
    }

    String routeSummary(int routeIndex) {
        return "routeCursor=" + safeRouteIndex(routeIndex) + "/" + Math.max(0, routeSize() - 1)
                + ", node=" + nodeAt(routeIndex)
                + ", route=" + (selectedNodes.isEmpty() ? "[default]" : selectedNodes);
    }

    int routeIndexForNode(String node, int fallback) {
        if (node == null || selectedNodes.isEmpty()) {
            return safeRouteIndex(fallback);
        }
        for (int i = 0; i < selectedNodes.size(); i++) {
            if (node.equals(selectedNodes.get(i))) {
                return i;
            }
        }
        return safeRouteIndex(fallback);
    }

    boolean isRouteNodeAllowed(String node) {
        if (node == null || node.length() == 0) {
            return false;
        }
        return "0".equals(node) || selectedNodes.isEmpty() || selectedNodes.contains(node);
    }

    private static boolean parseAndAppend(
            AutoWsgrAssetStore store,
            String path,
            StringBuilder detail
    ) {
        try {
            CombatPlanPolicy policy = parse(path, store.readText(path));
            append(detail, policy.toDetailLine());
            return true;
        } catch (IOException e) {
            append(detail, path + " failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean appendDecisionProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        try {
            CombatPlanPolicy defaultPlan = parse(DEFAULT_PLAN, store.readText(DEFAULT_PLAN));
            CombatPlanPolicy complexPlan = parse(COMPLEX_PLAN, store.readText(COMPLEX_PLAN));
            CombatPlanPolicy eventPlan = parse(EVENT_PLAN, store.readText(EVENT_PLAN));
            String defaultFightCondition = defaultPlan
                    .decisionForPhase(NativeCombatPhase.FIGHT_CONDITION, 0)
                    .actionId;
            String complexFightCondition = complexPlan
                    .decisionForPhase(NativeCombatPhase.FIGHT_CONDITION, 0)
                    .actionId;
            String eventFightCondition = eventPlan
                    .decisionForPhase(NativeCombatPhase.FIGHT_CONDITION, 0)
                    .actionId;

            append(detail, "decision probe="
                    + defaultPlan.name + "{"
                    + decisionIds(defaultPlan, 0)
                    + "}; "
                    + complexPlan.name + "{firstProceed="
                    + complexPlan.decisionForPhase(NativeCombatPhase.PROCEED, 0).actionId
                    + ", lastProceed="
                    + complexPlan.decisionForPhase(NativeCombatPhase.PROCEED, complexPlan.routeSize() - 1).actionId
                    + ", formation="
                    + complexPlan.decisionForPhase(NativeCombatPhase.FORMATION, 0).actionId
                    + ", fightCondition=" + complexFightCondition
                    + "}; "
                    + eventPlan.name + "{"
                    + decisionIds(eventPlan, 0)
                    + "}"
                    + ", actionSpecs={fight_condition_4="
                    + GuardedCombatStepper.hasActionSpec("fight_condition_4")
                    + "}");
            append(detail, "damage probe="
                    + complexPlan.name + "{normal="
                    + complexPlan.decisionForPhase(
                            NativeCombatPhase.PROCEED,
                            0,
                            ShipDamagePolicy.allNormal("probe")
                    ).actionId
                    + ", severe="
                    + complexPlan.decisionForPhase(
                            NativeCombatPhase.PROCEED,
                            0,
                            ShipDamagePolicy.withDamage("probe", 0, ShipDamagePolicy.SEVERE)
                    ).actionId
                    + ", unknown="
                    + complexPlan.decisionForPhase(
                            NativeCombatPhase.PROCEED,
                            0,
                            ShipDamagePolicy.unknown("probe")
                    ).actionId
                    + "}; " + defaultPlan.name + "{unknown="
                    + defaultPlan.decisionForPhase(
                            NativeCombatPhase.PROCEED,
                            0,
                            ShipDamagePolicy.unknown("probe")
                    ).actionId
                    + "}");
            return "fight_condition_4".equals(defaultFightCondition)
                    && "fight_condition_4".equals(complexFightCondition)
                    && "fight_condition_4".equals(eventFightCondition)
                    && GuardedCombatStepper.hasActionSpec("fight_condition_4");
        } catch (IOException e) {
            append(detail, "decision probe failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean appendEnemyRuleProbe(AutoWsgrAssetStore store, StringBuilder detail) {
        try {
            CombatPlanPolicy complexPlan = parse(COMPLEX_PLAN, store.readText(COMPLEX_PLAN));
            CombatPlanPolicy rulePlan = parse(RULE_PLAN, store.readText(RULE_PLAN));
            CombatPlanPolicy complexRulePlan = parse(COMPLEX_RULE_PLAN, store.readText(COMPLEX_RULE_PLAN));

            EnemyRuleEngine.RuleAction detour = complexPlan
                    .decisionForRouteIndex(0)
                    .evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot.of("AP missing", "AP", 0));
            EnemyRuleEngine.RuleAction fight = complexPlan
                    .decisionForRouteIndex(0)
                    .evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot.of("AP present", "AP", 1));
            EnemyRuleEngine.RuleAction retreat = rulePlan
                    .decisionForRouteIndex(rulePlan.routeIndexForNode("A", 0))
                    .evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot.of("destroyer screen", "DD", 3, "CL", 0));
            EnemyRuleEngine.RuleAction formation = rulePlan
                    .decisionForRouteIndex(rulePlan.routeIndexForNode("C", 0))
                    .evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot.of(
                            "light escort",
                            "DD", 1,
                            "CL", 0,
                            "CVL", 0,
                            "CV", 0
                    ));
            EnemyRuleEngine.RuleAction orFormation = complexRulePlan
                    .decisionForRouteIndex(0)
                    .evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot.of(
                            "or expression",
                            "DD", 1,
                            "CL", 0,
                            "CVL", 0,
                            "CV", 0
                    ));
            NodeDecision priorityDecision = NodeDecision.defaults();
            priorityDecision.addListItem("enemy_formation_rules", "[轮型阵, 4]");
            priorityDecision.addListItem("enemy_rules", "[AP != 1, retreat]");
            EnemyRuleEngine.RuleAction formationFirst = priorityDecision.evaluateSpotRules(
                    EnemyRuleEngine.EnemySnapshot.of("AP missing", "AP", 0),
                    "轮型阵"
            );
            EnemyRuleEngine.RuleAction enemyFallback = priorityDecision.evaluateSpotRules(
                    EnemyRuleEngine.EnemySnapshot.of("AP missing", "AP", 0),
                    ""
            );
            PlanDecision carrySpot = rulePlan.decisionForSpotRuleAction(
                    rulePlan.routeIndexForNode("C", 0),
                    formation
            );
            PlanDecision carryFormation = rulePlan.decisionForPhase(
                    NativeCombatPhase.FORMATION,
                    rulePlan.routeIndexForNode("C", 0),
                    ShipDamagePolicy.allNormal("rule carry probe"),
                    carrySpot.ruleFormation
            );

            boolean ok = detour.isDetour()
                    && fight.kind == EnemyRuleEngine.RuleAction.Kind.NO_ACTION
                    && retreat.isRetreat()
                    && formation.isFormation(4)
                    && orFormation.isFormation(4)
                    && GuardedCombatStepper.hasActionSpec(detour.actionId())
                    && GuardedCombatStepper.hasActionSpec(retreat.actionId())
                    && GuardedCombatStepper.hasActionSpec(fight.actionId())
                    && GuardedCombatStepper.hasActionSpec(formation.actionId())
                    && carrySpot.actionId.equals("spot_fight")
                    && carrySpot.carriesRuleFormation
                    && carrySpot.ruleFormation == 4
                    && carryFormation.actionId.equals("formation_4")
                    && formationFirst.isFormation(4)
                    && enemyFallback.isRetreat()
                    && carryFormation.consumesRuleFormation;
            append(detail, "enemy rule probe=" + (ok ? "passed" : "failed")
                    + ", " + complexPlan.name
                    + "{AP0=" + detour.shortLabel()
                    + ", AP1=" + fight.shortLabel()
                    + "}; " + rulePlan.name
                    + "{A_DD3=" + retreat.shortLabel()
                    + ", C_light=" + formation.shortLabel()
                    + "}; " + complexRulePlan.name
                    + "{or=" + orFormation.shortLabel() + "}"
                    + ", actionSpecs={"
                    + detour.actionId() + "=" + GuardedCombatStepper.hasActionSpec(detour.actionId())
                    + ", " + retreat.actionId() + "=" + GuardedCombatStepper.hasActionSpec(retreat.actionId())
                    + ", " + fight.actionId() + "=" + GuardedCombatStepper.hasActionSpec(fight.actionId())
                    + ", " + formation.actionId() + "=" + GuardedCombatStepper.hasActionSpec(formation.actionId())
                    + "}"
                    + ", ruleFormationCarry={spot=" + carrySpot.actionId
                    + ", carries=" + carrySpot.carriesRuleFormation
                    + ", formation=" + carrySpot.ruleFormation
                    + ", next=" + carryFormation.actionId
                    + ", consumes=" + carryFormation.consumesRuleFormation
                    + "}"
                    + ", formationPriority={formationFirst=" + formationFirst.shortLabel()
                    + ", enemyFallback=" + enemyFallback.shortLabel()
                    + "}");
            return true;
        } catch (IOException e) {
            append(detail, "enemy rule probe failed: " + e.getMessage());
            return false;
        }
    }

    private static String decisionIds(CombatPlanPolicy policy, int routeIndex) {
        return "fightCondition=" + policy.decisionForPhase(NativeCombatPhase.FIGHT_CONDITION, routeIndex).actionId
                + ", formation=" + policy.decisionForPhase(NativeCombatPhase.FORMATION, routeIndex).actionId
                + ", night=" + policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, routeIndex).actionId
                + ", proceed=" + policy.decisionForPhase(NativeCombatPhase.PROCEED, routeIndex).actionId;
    }

    static CombatPlanPolicy parseText(String path, String text) throws IOException {
        return parse(path, text);
    }

    NormalSortieTarget normalTarget() throws IOException {
        if (eventName.length() > 0 || "event".equals(mode)) {
            throw new IOException("only normal sortie plans are supported: " + path);
        }
        try {
            return NormalSortieTarget.of(Integer.parseInt(chapter), Integer.parseInt(mapId));
        } catch (NumberFormatException e) {
            throw new IOException("invalid normal target in plan " + path
                    + ": chapter=" + chapter + ", map=" + mapId);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid normal target in plan " + path + ": " + e.getMessage());
        }
    }

    private static void validateNormalRunConfig(CombatPlanPolicy policy, MapNodeData mapData) throws IOException {
        NormalSortieTarget target = policy.normalTarget();
        if (mapData == null) {
            throw new IOException("missing map data for config target: " + target.label());
        }
        String expectedMap = target.mapAssetPath();
        if (!expectedMap.equals(mapData.path)) {
            throw new IOException("config map mismatch: expected=" + expectedMap + ", actual=" + mapData.path);
        }
        String missingSelected = mapData.missingSelected(policy.selectedNodes);
        if (missingSelected.length() > 0) {
            throw new IOException("config route references nodes missing from map "
                    + target.label() + missingSelected);
        }
    }

    private static CombatPlanPolicy generatedForNormalTarget(
            NormalSortieTarget target,
            MapNodeData mapData
    ) {
        ArrayList<String> selectedNodes = new ArrayList<>();
        String firstNode = mapData == null ? "" : mapData.firstRouteNode();
        if (firstNode.length() > 0) {
            selectedNodes.add(firstNode);
        }

        NodeDecision defaultNode = NodeDecision.defaults();
        defaultNode.night = false;
        defaultNode.proceed = false;

        LinkedHashMap<String, NodeDecision> nodes = new LinkedHashMap<>();
        for (String node : selectedNodes) {
            nodes.put(node, defaultNode.copy());
        }

        return new CombatPlanPolicy(
                target.generatedPlanPath(),
                stem(target.generatedPlanPath()),
                "normal",
                "",
                String.valueOf(target.chapter),
                String.valueOf(target.map),
                1,
                4,
                "2",
                selectedNodes,
                defaultNode,
                nodes
        );
    }

    private static CombatPlanPolicy parse(String path, String text) throws IOException {
        String mode = "normal";
        String eventName = "";
        String chapter = "1";
        String mapId = "1";
        int fleetId = 1;
        int fightCondition = 4;
        String repairMode = "2";
        ArrayList<String> selectedNodes = new ArrayList<>();
        NodeDecision defaultNode = NodeDecision.defaults();
        LinkedHashMap<String, NodeDecision> nodes = new LinkedHashMap<>();

        Section section = Section.TOP;
        String currentNode = "";
        String activeListKey = "";

        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).replace('\t', ' ');
            if (line.trim().length() == 0) {
                continue;
            }

            int indent = leadingSpaces(line);
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                if (activeListKey.length() > 0) {
                    NodeDecision target = section == Section.NODE_ARGS
                            ? nodes.get(currentNode)
                            : defaultNode;
                    if (target != null) {
                        target.addListItem(activeListKey, trimmed.substring(2).trim());
                    }
                }
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }

            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            activeListKey = "";

            if (indent == 0) {
                currentNode = "";
                if ("node_defaults".equals(key)) {
                    section = Section.NODE_DEFAULTS;
                    continue;
                }
                if ("node_args".equals(key)) {
                    section = Section.NODE_ARGS;
                    continue;
                }
                section = Section.TOP;
                if ("mode".equals(key)) {
                    mode = cleanScalar(value);
                } else if ("event".equals(key)) {
                    eventName = cleanScalar(value);
                    if (eventName.length() > 0) {
                        mode = "event";
                    }
                } else if ("chapter".equals(key)) {
                    chapter = cleanScalar(value);
                } else if ("map".equals(key)) {
                    mapId = cleanScalar(value);
                } else if ("fleet_id".equals(key)) {
                    fleetId = parseInt(value, 1);
                } else if ("fight_condition".equals(key)) {
                    fightCondition = parseInt(value, 4);
                } else if ("repair_mode".equals(key)) {
                    repairMode = cleanScalar(value);
                } else if ("selected_nodes".equals(key)) {
                    selectedNodes = parseList(value);
                }
                continue;
            }

            if (section == Section.NODE_DEFAULTS && indent >= 2) {
                if (value.length() == 0 && isDecisionList(key)) {
                    activeListKey = key;
                } else {
                    defaultNode.apply(key, value);
                }
                continue;
            }

            if (section == Section.NODE_ARGS) {
                if (indent == 2 && value.length() == 0) {
                    currentNode = key;
                    nodes.put(currentNode, defaultNode.copy());
                    continue;
                }
                NodeDecision current = nodes.get(currentNode);
                if (current != null && indent >= 4) {
                    if (value.length() == 0 && isDecisionList(key)) {
                        activeListKey = key;
                    } else {
                        current.apply(key, value);
                    }
                }
            }
        }

        for (String node : selectedNodes) {
            if (!nodes.containsKey(node)) {
                nodes.put(node, defaultNode.copy());
            }
        }

        if (eventName.length() > 0) {
            mode = "event";
        }
        return new CombatPlanPolicy(
                path,
                stem(path),
                mode,
                eventName,
                chapter,
                mapId,
                fleetId,
                fightCondition,
                repairMode,
                selectedNodes,
                defaultNode,
                nodes
        );
    }

    private String toDetailLine() {
        return "plan=" + name
                + ", mode=" + mode
                + ", map=" + mapLabel()
                + ", fleet=" + fleetId
                + ", fightCondition=" + fightCondition
                + ", repairMode=" + repairMode
                + ", selectedNodes=" + selectedNodes
                + ", nodes=" + describeNodes(4);
    }

    String toRunnerSummary() {
        return "planPolicy=" + name
                + ", mode=" + mode
                + ", map=" + mapLabel()
                + ", selectedNodes=" + selectedNodes
                + ", default=" + defaultNode.shortLabel()
                + ", supported=v0.70 route cursor/live damage/route deviation guard/enemy rule engine/spot formation rules/spot rule actions/fight condition action/missile skip action/rule formation carry/enemy ship-type prototype backend/calibrated slot sampler/enemy formation signature OCR hook/runner spot-context integration probe/live spot diagnostics companion/sampled diagnostics self-test/copyable calibration records/simplified Chinese main UI/live spot calibration loop/calibration sample set advice/clipboard calibration record import analysis/threshold patch preview/calibration review report/minimum runnable normal battle/night-prompt choice/front-end normal map selection/selected-map sortie navigation/selected policy propagation/config-driven normal sortie/fast result-page recognition/landscape phase-scan frame/pixel-first guarded result recognition/original result-handler double click/source-aligned result transitions/config map target verification/result handler runner-phase guard/original start-screen enter/get-ship pixel fallback/original get-ship handler click/candidate pixel pass before templates/proceed supply prompt pixel fallback/scaled proceed prompt probes/pixel miss diagnostics/home signature threshold calibration/final timeout frame scan/proceed return-port prompt fallback/normal map-page pixel fallback/get-ship dialog fallback";
    }

    private NodeDecision decisionForRouteIndex(int routeIndex) {
        String node = nodeAt(routeIndex);
        NodeDecision decision = nodes.get(node);
        return decision == null ? defaultNode : decision;
    }

    private String nodeAt(int routeIndex) {
        if (selectedNodes.isEmpty()) {
            return "default";
        }
        return selectedNodes.get(safeRouteIndex(routeIndex));
    }

    private boolean hasNextNode(int routeIndex) {
        return !selectedNodes.isEmpty() && safeRouteIndex(routeIndex) < selectedNodes.size() - 1;
    }

    private int safeRouteIndex(int routeIndex) {
        if (selectedNodes.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(routeIndex, selectedNodes.size() - 1));
    }

    private boolean isSelectedNode(String node) {
        return selectedNodes.isEmpty() || selectedNodes.contains(node);
    }

    private static int clampFormation(int formation) {
        return Math.max(1, Math.min(formation, 5));
    }

    private static int clampFightCondition(int condition) {
        return Math.max(1, Math.min(condition, 5));
    }

    private String mapLabel() {
        if (eventName.length() > 0) {
            return eventName + "/" + chapter + "-" + mapId;
        }
        return chapter + "-" + mapId;
    }

    private String describeNodes(int limit) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, NodeDecision> entry : nodes.entrySet()) {
            if (count >= limit) {
                out.append("...");
                break;
            }
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(entry.getKey()).append(":").append(entry.getValue().shortLabel());
            count++;
        }
        return out.length() == 0 ? "default:" + defaultNode.shortLabel() : out.toString();
    }

    private static List<String> listPlanPaths(Context context) throws IOException {
        ArrayList<String> paths = new ArrayList<>();
        AssetManager assets = context.getAssets();
        InputStream stream = assets.open(INDEX_PATH);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(PLAN_PREFIX) && trimmed.endsWith(".yaml")) {
                    paths.add(trimmed);
                }
            }
        } finally {
            stream.close();
        }
        return paths;
    }

    private static int countPrefix(List<String> paths, String prefix) {
        int count = 0;
        for (String path : paths) {
            if (path.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isDecisionList(String key) {
        return "enemy_rules".equals(key) || "enemy_formation_rules".equals(key);
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        char quote = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (c == '#' && !quoted) {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int leadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static String cleanScalar(String raw) {
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(cleanScalar(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String raw, boolean fallback) {
        String value = cleanScalar(raw);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static ArrayList<String> parseList(String raw) {
        ArrayList<String> values = new ArrayList<>();
        String value = raw.trim();
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return values;
        }
        String body = value.substring(1, value.length() - 1);
        if (body.trim().length() == 0) {
            return values;
        }
        String[] parts = body.split(",");
        for (String part : parts) {
            String item = cleanScalar(part.trim());
            if (item.length() > 0) {
                values.add(item);
            }
        }
        return values;
    }

    private static String stem(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return file.endsWith(".yaml") ? file.substring(0, file.length() - 5) : file;
    }

    private static void append(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private enum Section {
        TOP,
        NODE_DEFAULTS,
        NODE_ARGS
    }

    static final class ProbeResult {
        final boolean ok;
        final String detail;

        ProbeResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        String toStatusLine() {
            return "Combat plan policy: " + (ok ? "passed" : "failed");
        }
    }

    static final class PlanDecision {
        final int routeIndex;
        final String node;
        final NodeDecision nodeDecision;
        final NativeCombatPhase phase;
        final String actionId;
        final String reason;
        final boolean routeAdvances;
        final boolean routeEnds;
        final int ruleFormation;
        final boolean carriesRuleFormation;
        final boolean consumesRuleFormation;

        PlanDecision(
                int routeIndex,
                String node,
                NodeDecision nodeDecision,
                NativeCombatPhase phase,
                String actionId,
                String reason,
                boolean routeAdvances,
                boolean routeEnds
        ) {
            this(routeIndex, node, nodeDecision, phase, actionId, reason, routeAdvances, routeEnds, 0, false, false);
        }

        PlanDecision(
                int routeIndex,
                String node,
                NodeDecision nodeDecision,
                NativeCombatPhase phase,
                String actionId,
                String reason,
                boolean routeAdvances,
                boolean routeEnds,
                int ruleFormation,
                boolean carriesRuleFormation,
                boolean consumesRuleFormation
        ) {
            this.routeIndex = routeIndex;
            this.node = node;
            this.nodeDecision = nodeDecision;
            this.phase = phase;
            this.actionId = actionId;
            this.reason = reason;
            this.routeAdvances = routeAdvances;
            this.routeEnds = routeEnds;
            this.ruleFormation = ruleFormation;
            this.carriesRuleFormation = carriesRuleFormation;
            this.consumesRuleFormation = consumesRuleFormation;
        }

        boolean hasAction() {
            return actionId.length() > 0;
        }

        String detail() {
            return "planDecision=node=" + node
                    + ", phase=" + (phase == null ? "none" : phase.id)
                    + ", action=" + (actionId.length() == 0 ? "none" : actionId)
                    + ", routeIndex=" + routeIndex
                    + ", routeAdvances=" + routeAdvances
                    + ", routeEnds=" + routeEnds
                    + ", ruleFormation=" + (ruleFormation > 0 ? ruleFormation : "none")
                    + ", carriesRuleFormation=" + carriesRuleFormation
                    + ", consumesRuleFormation=" + consumesRuleFormation
                    + ", " + reason
                    + ", nodePolicy=" + nodeDecision.shortLabel();
        }
    }

    static final class NodeDecision {
        int formation;
        boolean night;
        boolean proceed;
        String proceedStop;
        boolean detour;
        boolean longMissileSupport;
        boolean slWhenSpotEnemyFails;
        boolean slWhenDetourFails;
        boolean slWhenEnterFight;
        ArrayList<String> enemyRuleItems = new ArrayList<>();
        ArrayList<String> formationRuleItems = new ArrayList<>();
        EnemyRuleEngine enemyRules = EnemyRuleEngine.emptyEnemyRules();
        EnemyRuleEngine formationRules = EnemyRuleEngine.emptyFormationRules();

        static NodeDecision defaults() {
            NodeDecision decision = new NodeDecision();
            decision.formation = 2;
            decision.night = false;
            decision.proceed = true;
            decision.proceedStop = "2";
            decision.detour = false;
            decision.longMissileSupport = false;
            decision.slWhenSpotEnemyFails = false;
            decision.slWhenDetourFails = true;
            decision.slWhenEnterFight = false;
            return decision;
        }

        NodeDecision copy() {
            NodeDecision copy = new NodeDecision();
            copy.formation = formation;
            copy.night = night;
            copy.proceed = proceed;
            copy.proceedStop = proceedStop;
            copy.detour = detour;
            copy.longMissileSupport = longMissileSupport;
            copy.slWhenSpotEnemyFails = slWhenSpotEnemyFails;
            copy.slWhenDetourFails = slWhenDetourFails;
            copy.slWhenEnterFight = slWhenEnterFight;
            copy.enemyRuleItems = new ArrayList<>(enemyRuleItems);
            copy.formationRuleItems = new ArrayList<>(formationRuleItems);
            copy.enemyRules = enemyRules;
            copy.formationRules = formationRules;
            return copy;
        }

        void apply(String key, String value) {
            if ("formation".equals(key)) {
                formation = parseInt(value, formation);
            } else if ("night".equals(key)) {
                night = parseBool(value, night);
            } else if ("proceed".equals(key)) {
                proceed = parseBool(value, proceed);
            } else if ("proceed_stop".equals(key)) {
                proceedStop = cleanScalar(value);
            } else if ("detour".equals(key)) {
                detour = parseBool(value, detour);
            } else if ("long_missile_support".equals(key)) {
                longMissileSupport = parseBool(value, longMissileSupport);
            } else if ("SL_when_spot_enemy_fails".equals(key)) {
                slWhenSpotEnemyFails = parseBool(value, slWhenSpotEnemyFails);
            } else if ("SL_when_detour_fails".equals(key)) {
                slWhenDetourFails = parseBool(value, slWhenDetourFails);
            } else if ("SL_when_enter_fight".equals(key)) {
                slWhenEnterFight = parseBool(value, slWhenEnterFight);
            }
        }

        void addListItem(String listKey, String item) throws IOException {
            if ("enemy_rules".equals(listKey)) {
                enemyRuleItems.add(item);
                enemyRules = EnemyRuleEngine.fromEnemyRuleItems(enemyRuleItems);
            } else if ("enemy_formation_rules".equals(listKey)) {
                formationRuleItems.add(item);
                formationRules = EnemyRuleEngine.fromFormationRuleItems(formationRuleItems);
            }
        }

        int enemyRuleCount() {
            return enemyRules.size();
        }

        int formationRuleCount() {
            return formationRules.size();
        }

        EnemyRuleEngine.RuleAction evaluateEnemyRules(EnemyRuleEngine.EnemySnapshot snapshot) {
            return enemyRules.evaluate(snapshot);
        }

        EnemyRuleEngine.RuleAction evaluateFormationRules(String enemyFormation) {
            return formationRules.evaluateFormation(enemyFormation);
        }

        EnemyRuleEngine.RuleAction evaluateSpotRules(EnemyRuleEngine.EnemySnapshot snapshot, String enemyFormation) {
            EnemyRuleEngine.RuleAction formationAction = evaluateFormationRules(enemyFormation);
            if (formationAction.kind != EnemyRuleEngine.RuleAction.Kind.NO_ACTION) {
                return formationAction;
            }
            if (snapshot == null) {
                return EnemyRuleEngine.RuleAction.noAction();
            }
            return evaluateEnemyRules(snapshot);
        }

        String shortLabel() {
            return "formation=" + formation
                    + ", night=" + night
                    + ", proceed=" + proceed
                    + ", stop=" + proceedStop
                    + ", rules=" + enemyRuleCount() + "/" + formationRuleCount();
        }
    }
}
