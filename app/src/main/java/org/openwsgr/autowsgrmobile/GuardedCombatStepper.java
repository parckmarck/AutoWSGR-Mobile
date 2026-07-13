package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.openwsgr.autowsgrmobile.vision.TemplateMatcher;

final class GuardedCombatStepper {
    private static final long PHASE_GUARD_TIMEOUT_MS = 3500L;
    private static final int GUARD_STRIDE = 24;
    private static final double GUARD_THRESHOLD = 0.999;

    private static final ActionSpec PROCEED_YES = new ActionSpec(
            "proceed_yes",
            "click_proceed(go_forward=true)",
            "autowsgr/combat/actions.py Coords.PROCEED_YES",
            NativeCombatPhase.PROCEED,
            "map_proceed_yes",
            NativeCombatPhase.PROCEED,
            "yes",
            0.339f,
            0.648f
    );
    private static final ActionSpec PROCEED_NO = new ActionSpec(
            "proceed_no",
            "click_proceed(go_forward=false)",
            "autowsgr/combat/actions.py Coords.PROCEED_NO",
            NativeCombatPhase.PROCEED,
            "map_proceed_no",
            NativeCombatPhase.PROCEED,
            "no",
            0.641f,
            0.648f
    );
    private static final ActionSpec SPOT_FIGHT = new ActionSpec(
            "spot_fight",
            "click_enter_fight()",
            "autowsgr/combat/actions.py Coords.ENTER_FIGHT",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "spot_fight",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "fight",
            0.891f,
            0.928f
    );
    private static final ActionSpec SPOT_RETREAT = new ActionSpec(
            "spot_retreat",
            "click_retreat()",
            "autowsgr/combat/actions.py Coords.RETREAT",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "spot_retreat",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "retreat",
            0.705f,
            0.911f
    );
    private static final ActionSpec SPOT_DETOUR = new ActionSpec(
            "spot_detour",
            "click_image(TemplateKey.BYPASS)",
            "autowsgr/combat/handlers.py TemplateKey.BYPASS",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "spot_detour",
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            "detour",
            NativeTemplateKey.BYPASS
    );
    private static final ActionSpec FIGHT_CONDITION_1 = fightConditionSpec(
            1,
            "steady_advance",
            0.215f,
            0.409f
    );
    private static final ActionSpec FIGHT_CONDITION_2 = fightConditionSpec(
            2,
            "firepower_forever",
            0.461f,
            0.531f
    );
    private static final ActionSpec FIGHT_CONDITION_3 = fightConditionSpec(
            3,
            "caution",
            0.783f,
            0.362f
    );
    private static final ActionSpec FIGHT_CONDITION_4 = fightConditionSpec(
            4,
            "aim",
            0.198f,
            0.764f
    );
    private static final ActionSpec FIGHT_CONDITION_5 = fightConditionSpec(
            5,
            "search_formation",
            0.763f,
            0.740f
    );
    private static final ActionSpec FORMATION_1 = formationSpec(1, "single_column");
    private static final ActionSpec FORMATION_2 = formationSpec(2, "double_column");
    private static final ActionSpec FORMATION_3 = formationSpec(3, "circular");
    private static final ActionSpec FORMATION_4 = formationSpec(4, "wedge");
    private static final ActionSpec FORMATION_5 = formationSpec(5, "single_horizontal");
    private static final ActionSpec NIGHT_YES = new ActionSpec(
            "night_yes",
            "click_night_battle(pursue=true)",
            "autowsgr/combat/actions.py Coords.NIGHT_YES",
            NativeCombatPhase.NIGHT_PROMPT,
            "night_yes",
            NativeCombatPhase.NIGHT_PROMPT,
            "yes",
            0.339f,
            0.648f
    );
    private static final ActionSpec NIGHT_NO = new ActionSpec(
            "night_no",
            "click_night_battle(pursue=false)",
            "autowsgr/combat/actions.py Coords.NIGHT_NO",
            NativeCombatPhase.NIGHT_PROMPT,
            "night_no",
            NativeCombatPhase.NIGHT_PROMPT,
            "no",
            0.641f,
            0.648f
    );
    private static final ActionSpec RESULT_CONTINUE = new ActionSpec(
            "result_continue",
            "click_result(); sleep(0.25); click_result()",
            "autowsgr/combat/handlers.py _handle_result double click; autowsgr/combat/actions.py Coords.CLICK_RESULT",
            NativeCombatPhase.RESULT,
            "result_continue",
            NativeCombatPhase.RESULT,
            "",
            0.953f,
            0.954f,
            2,
            1000L,
            250L
    );
    private static final ActionSpec GET_SHIP_CONTINUE = new ActionSpec(
            "get_ship_continue",
            "click_result()",
            "autowsgr/combat/handlers.py _handle_get_ship; autowsgr/combat/actions.py Coords.CLICK_RESULT",
            NativeCombatPhase.GET_SHIP,
            "get_ship_continue",
            NativeCombatPhase.GET_SHIP,
            "get_ship_continue",
            0.953f,
            0.954f
    );
    private static final ActionSpec MISSILE_SKIP = new ActionSpec(
            "missile_skip",
            "click_skip_missile_animation()",
            "autowsgr/combat/actions.py Coords.SPEED_UP_BATTLE",
            NativeCombatPhase.MISSILE_ANIMATION,
            "missile_skip",
            NativeCombatPhase.MISSILE_ANIMATION,
            "skip_animation",
            0.396f,
            0.963f,
            2
    );
    private static final ActionSpec[] ACTION_SPECS = new ActionSpec[]{
            PROCEED_YES,
            PROCEED_NO,
            SPOT_FIGHT,
            SPOT_RETREAT,
            SPOT_DETOUR,
            FIGHT_CONDITION_1,
            FIGHT_CONDITION_2,
            FIGHT_CONDITION_3,
            FIGHT_CONDITION_4,
            FIGHT_CONDITION_5,
            FORMATION_1,
            FORMATION_2,
            FORMATION_3,
            FORMATION_4,
            FORMATION_5,
            NIGHT_YES,
            NIGHT_NO,
            RESULT_CONTINUE,
            GET_SHIP_CONTINUE,
            MISSILE_SKIP
    };
    private static final NativeCombatPhase[] GUARDED_PHASES = new NativeCombatPhase[]{
            NativeCombatPhase.PROCEED,
            NativeCombatPhase.SPOT_ENEMY_SUCCESS,
            NativeCombatPhase.FIGHT_CONDITION,
            NativeCombatPhase.FORMATION,
            NativeCombatPhase.MISSILE_ANIMATION,
            NativeCombatPhase.NIGHT_PROMPT,
            NativeCombatPhase.RESULT,
            NativeCombatPhase.GET_SHIP,
            NativeCombatPhase.DOCK_FULL
    };

    private final Context appContext;
    private final Handler mainHandler;
    private final Callback callback;
    private final boolean dryRun;
    private final boolean executeGesture;
    private final ActionSpec spec;
    private final String planDetail;
    private final AutomationControl control = new AutomationControl();
    private final OneShotStartGate startGate = new OneShotStartGate();
    private final WaiterLifecycle waiterLifecycle = new WaiterLifecycle();
    private final PublicationGate publicationGate = new PublicationGate();
    private Thread worker;

    private GuardedCombatStepper(
            Context context,
            ActionSpec spec,
            boolean dryRun,
            boolean executeGesture,
            Callback callback
    ) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callback = callback;
        this.spec = spec;
        this.dryRun = dryRun;
        this.executeGesture = executeGesture;
        this.planDetail = buildPlan();
    }

    static GuardedCombatStepper proceedNoDryRun(Context context, Callback callback) {
        return dryRun(context, PROCEED_NO, callback);
    }

    static GuardedCombatStepper proceedNoExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, PROCEED_NO, callback);
    }

    static GuardedCombatStepper spotFightDryRun(Context context, Callback callback) {
        return dryRun(context, SPOT_FIGHT, callback);
    }

    static GuardedCombatStepper spotFightExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, SPOT_FIGHT, callback);
    }

    static GuardedCombatStepper spotRetreatDryRun(Context context, Callback callback) {
        return dryRun(context, SPOT_RETREAT, callback);
    }

    static GuardedCombatStepper spotRetreatExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, SPOT_RETREAT, callback);
    }

    static GuardedCombatStepper spotDetourDryRun(Context context, Callback callback) {
        return dryRun(context, SPOT_DETOUR, callback);
    }

    static GuardedCombatStepper spotDetourExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, SPOT_DETOUR, callback);
    }

    static GuardedCombatStepper fightConditionAimDryRun(Context context, Callback callback) {
        return dryRun(context, FIGHT_CONDITION_4, callback);
    }

    static GuardedCombatStepper fightConditionAimExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, FIGHT_CONDITION_4, callback);
    }

    static GuardedCombatStepper formationSingleColumnDryRun(Context context, Callback callback) {
        return dryRun(context, FORMATION_1, callback);
    }

    static GuardedCombatStepper formationSingleColumnExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, FORMATION_1, callback);
    }

    static GuardedCombatStepper nightNoDryRun(Context context, Callback callback) {
        return dryRun(context, NIGHT_NO, callback);
    }

    static GuardedCombatStepper nightNoExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, NIGHT_NO, callback);
    }

    static GuardedCombatStepper resultContinueDryRun(Context context, Callback callback) {
        return dryRun(context, RESULT_CONTINUE, callback);
    }

    static GuardedCombatStepper resultContinueExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, RESULT_CONTINUE, callback);
    }

    static GuardedCombatStepper missileSkipDryRun(Context context, Callback callback) {
        return dryRun(context, MISSILE_SKIP, callback);
    }

    static GuardedCombatStepper missileSkipExecuteGuarded(Context context, Callback callback) {
        return executeGuarded(context, MISSILE_SKIP, callback);
    }

    static GuardedCombatStepper executeGuardedForPhase(
            Context context,
            NativeCombatPhase phase,
            Callback callback
    ) {
        ActionSpec spec = specForPhase(phase);
        if (spec == null) {
            return null;
        }
        return executeGuarded(context, spec, callback);
    }

    static GuardedCombatStepper executeGuardedForActionId(
            Context context,
            String actionId,
            Callback callback
    ) {
        ActionSpec spec = specForActionId(actionId);
        if (spec == null) {
            return null;
        }
        return executeGuarded(context, spec, callback);
    }

    static NativeCombatPhase[] guardedPhases() {
        return GUARDED_PHASES.clone();
    }

    static String actionIdForPhase(NativeCombatPhase phase) {
        ActionSpec spec = specForPhase(phase);
        return spec == null ? "none" : spec.id;
    }

    static String guardedActionCatalog() {
        return "proceed->{proceed_yes,proceed_no}, "
                + "spot_enemy_success->{spot_fight,spot_retreat,spot_detour}, "
                + "fight_condition->{fight_condition_1..fight_condition_5}, "
                + "formation->{formation_1..formation_5}, "
                + "missile_animation->{missile_skip}, "
                + "night_prompt->{night_yes,night_no}, "
                + "result->{result_continue}, "
                + "get_ship->{get_ship_continue}";
    }

    static boolean hasActionSpec(String actionId) {
        return specForActionId(actionId) != null;
    }

    static int tapRepeatForActionId(String actionId) {
        ActionSpec spec = specForActionId(actionId);
        return spec == null ? 0 : spec.tapRepeat;
    }

    static long preTapDelayForActionId(String actionId) {
        ActionSpec spec = specForActionId(actionId);
        return spec == null ? 0L : spec.preTapDelayMs;
    }

    static long betweenTapDelayForActionId(String actionId) {
        ActionSpec spec = specForActionId(actionId);
        return spec == null ? 0L : spec.betweenTapDelayMs;
    }

    static boolean requiresFreshPhaseGuardForActionId(String actionId) {
        ActionSpec spec = specForActionId(actionId);
        return spec != null && spec.requiresFreshPhaseGuard();
    }

    static String actionSourceForActionId(String actionId) {
        ActionSpec spec = specForActionId(actionId);
        return spec == null ? "" : spec.source;
    }

    private static ActionSpec specForPhase(NativeCombatPhase phase) {
        if (phase == null) {
            return null;
        }
        if (phase == NativeCombatPhase.PROCEED) {
            return PROCEED_NO;
        }
        if (phase == NativeCombatPhase.SPOT_ENEMY_SUCCESS) {
            return SPOT_FIGHT;
        }
        if (phase == NativeCombatPhase.FIGHT_CONDITION) {
            return FIGHT_CONDITION_4;
        }
        if (phase == NativeCombatPhase.FORMATION) {
            return FORMATION_1;
        }
        if (phase == NativeCombatPhase.MISSILE_ANIMATION) {
            return MISSILE_SKIP;
        }
        if (phase == NativeCombatPhase.NIGHT_PROMPT) {
            return NIGHT_NO;
        }
        if (phase == NativeCombatPhase.RESULT) {
            return RESULT_CONTINUE;
        }
        if (phase == NativeCombatPhase.GET_SHIP) {
            return GET_SHIP_CONTINUE;
        }
        return null;
    }

    private static ActionSpec specForActionId(String actionId) {
        if (actionId == null) {
            return null;
        }
        for (ActionSpec spec : ACTION_SPECS) {
            if (spec.id.equals(actionId)) {
                return spec;
            }
        }
        return null;
    }

    private static ActionSpec fightConditionSpec(int value, String name, float x, float y) {
        return new ActionSpec(
                "fight_condition_" + value,
                "click_fight_condition(" + name + ")",
                "autowsgr/types.py FightCondition." + name + ".relative_click_position",
                NativeCombatPhase.FIGHT_CONDITION,
                "fight_condition_" + value,
                NativeCombatPhase.FIGHT_CONDITION,
                String.valueOf(value),
                x,
                y
        );
    }

    private static ActionSpec formationSpec(int value, String name) {
        return new ActionSpec(
                "formation_" + value,
                "click_formation(" + name + ")",
                "autowsgr/types.py Formation." + name + ".relative_position",
                NativeCombatPhase.FORMATION,
                "formation_" + value,
                NativeCombatPhase.FORMATION,
                String.valueOf(value),
                0.597f,
                value * 0.185f - 0.037f
        );
    }

    private static GuardedCombatStepper dryRun(
            Context context,
            ActionSpec spec,
            Callback callback
    ) {
        return new GuardedCombatStepper(context, spec, true, false, callback);
    }

    private static GuardedCombatStepper executeGuarded(
            Context context,
            ActionSpec spec,
            Callback callback
    ) {
        return new GuardedCombatStepper(context, spec, false, true, callback);
    }

    boolean start() {
        OneShotStartGate.Decision decision = startGate.claim(control.cancelled());
        if (decision == OneShotStartGate.Decision.REJECTED) {
            return false;
        }
        if (decision == OneShotStartGate.Decision.START && !waiterLifecycle.begin()) {
            decision = OneShotStartGate.Decision.PRE_CANCELLED;
        }
        if (decision == OneShotStartGate.Decision.PRE_CANCELLED) {
            finish(
                    StepOutcome.CANCELLED,
                    "Guarded stepper: cancelled",
                    planDetail + "\npreStart=cancelled; gesture=not dispatched"
            );
            return true;
        }
        postRunning(Update.running(
                "Guarded stepper: starting " + spec.id,
                planDetail + "\n" + preflightDetail()
        ));

        if (dryRun) {
            startWaiter();
            return true;
        }

        Thread createdWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runGuardAndMaybeExecute();
                } catch (Throwable failure) {
                    if (control.cancelled()) {
                        finish(StepOutcome.CANCELLED, "Guarded stepper: cancelled",
                                planDetail + "\nuncaught-exception-after-cancel=" + failure);
                    } else {
                        finish(StepOutcome.FAILED, "Guarded stepper: failed " + spec.id,
                                planDetail + "\nuncaught-exception=" + failure);
                    }
                }
            }
        }, "AutoWSGR-guarded-stepper");
        createdWorker.setPriority(Thread.MIN_PRIORITY);
        synchronized (this) {
            worker = createdWorker;
        }
        createdWorker.start();
        return true;
    }

    void cancel() {
        final WaiterHandle[] waiterToCancel = new WaiterHandle[1];
        publicationGate.cancelWith(new Runnable() {
            @Override public void run() {
                waiterToCancel[0] = waiterLifecycle.claimCancellationWith(new Runnable() {
                    @Override public void run() {
                        control.cancel();
                    }
                });
            }
        });
        if (waiterToCancel[0] != null) {
            waiterToCancel[0].cancel();
        }
        Thread active;
        synchronized (this) {
            active = worker;
        }
        if (active != null) {
            active.interrupt();
        }
    }

    synchronized void pause() {
        control.pause();
    }

    synchronized void resume() {
        control.resume();
    }

    boolean isRunning() {
        return waiterLifecycle.isRunning();
    }

    private void runGuardAndMaybeExecute() {
        if (!executeGesture) {
            startWaiter();
            return;
        }

        if (spec.requiresFreshPhaseGuard()) {
            postRunning(Update.running(
                    "Guarded stepper: phase guard " + spec.id,
                    planDetail + "\n" + preflightDetail()
                            + "\nphaseGuard=checking expected=" + spec.guardPhase.id
            ));

            NativeCombatRecognizer.Recognition recognition = recognizeGuardPhase();
            if (control.cancelled()) {
                finish(StepOutcome.CANCELLED, "Guarded stepper: cancelled", planDetail + "\nphaseGuard=cancelled");
                return;
            }
            if (!recognition.matched) {
                finish(
                        StepOutcome.BLOCKED,
                        "Guarded stepper: blocked by phase guard",
                        planDetail + "\nphaseGuard=blocked expected=" + spec.guardPhase.id
                                + ", latest=" + formatRecognition(recognition)
                );
                return;
            }

            postRunning(Update.running(
                    "Guarded stepper: phase guard passed " + spec.id,
                    planDetail + "\nphaseGuard=passed, latest=" + formatRecognition(recognition)
            ));
        } else {
            postRunning(Update.running(
                    "Guarded stepper: phase guard skipped " + spec.id,
                    planDetail + "\n" + preflightDetail()
                            + "\nphaseGuard=skipped, source=runner phase scan"
            ));
        }

        GestureDispatch dispatch = dispatchGesture();
        if (!dispatch.dispatched) {
            finish(
                    control.cancelled() ? StepOutcome.CANCELLED : StepOutcome.BLOCKED,
                    "Guarded stepper: blocked",
                    planDetail + "\nphaseGuard="
                            + (spec.requiresFreshPhaseGuard() ? "passed" : "skipped")
                            + "\ngesture=blocked, " + dispatch.detail
            );
            return;
        }

        postRunning(Update.running(
                "Guarded stepper: gesture dispatched " + spec.id,
                planDetail + "\nphaseGuard="
                        + (spec.requiresFreshPhaseGuard() ? "passed" : "skipped")
                        + "\ngesture=dispatched, " + dispatch.detail
                        + "\nwaiting for " + spec.waitScenarioId
        ));
        startWaiter();
    }

    private GestureDispatch dispatchGesture() {
        if (spec.templateTapKey == null) {
            if (spec.preTapDelayMs > 0L) {
                SystemClock.sleep(spec.preTapDelayMs);
            }
            for (int i = 0; i < spec.tapRepeat; i++) {
                boolean tapped = performTap(spec.tapX, spec.tapY);
                if (!tapped) {
                    return GestureDispatch.blocked(control.cancelled()
                            ? "cancelled before tap " + (i + 1)
                            : "accessibility dispatch failed at tap " + (i + 1));
                }
                if (i + 1 < spec.tapRepeat) {
                    SystemClock.sleep(spec.betweenTapDelayMs);
                }
            }
            return GestureDispatch.dispatched("tap=(" + spec.tapX + "," + spec.tapY + "), count="
                    + spec.tapRepeat + ", preDelayMs=" + spec.preTapDelayMs
                    + ", betweenDelayMs=" + spec.betweenTapDelayMs);
        }
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return GestureDispatch.blocked("template tap unavailable: missing MediaProjection frame");
        }
        java.util.List<NativeTemplateScanner.Template> templates = null;
        Bitmap scanFrame = null;
        try {
            scanFrame = GameFrameNormalizer.toPhaseScanFrame(liveFrame);
            AutoWsgrAssetStore store = new AutoWsgrAssetStore(appContext);
            templates = spec.templateTapKey.loadTemplates(store);
            NativeTemplateScanner.ScanResult match = NativeTemplateScanner.findAny(
                    scanFrame,
                    templates,
                    GUARD_STRIDE,
                    GUARD_THRESHOLD,
                    new PhaseGuardDeadline()
            );
            if (!match.valid) {
                return GestureDispatch.blocked("template tap unavailable: " + formatScan(match));
            }
            NativeTemplateScanner.Template template = matchingTemplate(templates, match.path);
            int width = template == null ? 0 : template.bitmap.getWidth();
            int height = template == null ? 0 : template.bitmap.getHeight();
            float x = normalized(match.x + width / 2.0f, scanFrame.getWidth());
            float y = normalized(match.y + height / 2.0f, scanFrame.getHeight());
            boolean tapped = performTap(x, y);
            return tapped
                    ? GestureDispatch.dispatched("templateTap=" + spec.templateTapKey.id
                    + ", " + formatScan(match)
                    + ", normalizedTap=(" + x + "," + y + ")")
                    : GestureDispatch.blocked(control.cancelled()
                    ? "cancelled before template tap"
                    : "templateTap=" + spec.templateTapKey.id + ", accessibility dispatch failed");
        } catch (java.io.IOException e) {
            return GestureDispatch.blocked("template tap load failed: " + e.getMessage());
        } finally {
            NativeTemplateScanner.recycleAll(templates);
            if (scanFrame != null) {
                scanFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private static NativeTemplateScanner.Template matchingTemplate(
            java.util.List<NativeTemplateScanner.Template> templates,
            String path
    ) {
        if (templates == null) {
            return null;
        }
        for (NativeTemplateScanner.Template template : templates) {
            if (template.path.equals(path)) {
                return template;
            }
        }
        return templates.isEmpty() ? null : templates.get(0);
    }

    private static float normalized(float value, int size) {
        if (size <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value / size));
    }

    private static String formatScan(NativeTemplateScanner.ScanResult match) {
        if (match.valid) {
            return "match key=" + match.keyId
                    + ", path=" + match.path
                    + ", at=(" + match.x + "," + match.y + ")"
                    + ", score=" + formatScore(match.score);
        }
        return "miss reason=" + match.reason
                + ", key=" + match.keyId
                + ", path=" + match.path
                + ", at=(" + match.x + "," + match.y + ")"
                + ", score=" + formatScore(match.score);
    }

    private void startWaiter() {
        final WaiterHandle[] publishedHandle = new WaiterHandle[1];
        final LiveCombatFlowWaiter waiter = LiveCombatFlowWaiter.mapCustom(
                appContext,
                new LiveCombatFlowWaiter.Callback() {
                    @Override
                    public void onUpdate(LiveCombatFlowWaiter.Update update) {
                        WaiterHandle source = publishedHandle[0];
                        if (source != null) {
                            handleWaitUpdate(source, update);
                        }
                    }
                },
                spec.waitScenarioId,
                spec.waitCurrent,
                spec.lastAction
        );
        WaiterHandle handle = new WaiterHandle() {
            @Override public boolean start() {
                return waiter.start();
            }

            @Override public void cancel() {
                waiter.cancel();
            }
        };
        publishedHandle[0] = handle;
        WaiterStartOutcome outcome = waiterLifecycle.publishAndStart(handle);
        if (outcome == WaiterStartOutcome.CANCELLED) {
            finishWaiter(
                    handle,
                    StepOutcome.CANCELLED,
                    "Guarded stepper: cancelled",
                    planDetail + "\nbackground wait cancelled before or during publication"
            );
        } else if (outcome == WaiterStartOutcome.REJECTED) {
            finish(
                    StepOutcome.BLOCKED,
                    "Guarded stepper: blocked",
                    planDetail + "\nbackground wait already running"
            );
        }
    }

    private NativeCombatRecognizer.Recognition recognizeGuardPhase() {
        Bitmap liveFrame = ScreenFrameStore.latestBitmap();
        if (liveFrame == null) {
            return NativeCombatRecognizer.Recognition.missing("missing MediaProjection frame");
        }
        Bitmap scanFrame = null;
        try {
            scanFrame = GameFrameNormalizer.toPhaseScanFrame(liveFrame);
            return NativeCombatRecognizer.identifyCurrent(
                    scanFrame,
                    new AutoWsgrAssetStore(appContext),
                    new NativeCombatPhase[]{spec.guardPhase},
                    GUARD_STRIDE,
                    GUARD_THRESHOLD,
                    new PhaseGuardDeadline()
            );
        } finally {
            if (scanFrame != null) {
                scanFrame.recycle();
            }
            liveFrame.recycle();
        }
    }

    private void finish(StepOutcome outcome, String status, String detail) {
        if (!waiterLifecycle.completeWithoutWaiter()) {
            return;
        }
        postTerminal(outcome, status, detail);
    }

    private void finishWaiter(
            WaiterHandle source,
            StepOutcome outcome,
            String status,
            String detail
    ) {
        if (!waiterLifecycle.completeWaiter(source)) {
            return;
        }
        postTerminal(outcome, status, detail);
    }

    private void handleWaitUpdate(
            WaiterHandle source,
            LiveCombatFlowWaiter.Update waitUpdate
    ) {
        String detail = planDetail + "\n" + waitUpdate.detail;
        if (waitUpdate.running) {
            if (!waiterLifecycle.acceptsRunning(source)) {
                return;
            }
            postRunning(Update.running("Guarded stepper: waiting " + spec.id, detail));
            return;
        }

        if (waitUpdate.matched) {
            finishWaiter(source, StepOutcome.MATCHED,
                    "Guarded stepper: wait matched " + spec.id, detail);
            return;
        }
        if (waitUpdate.cancelled) {
            finishWaiter(source, StepOutcome.CANCELLED,
                    "Guarded stepper: cancelled", detail);
            return;
        }
        if (waitUpdate.timedOut) {
            finishWaiter(source, StepOutcome.TIMED_OUT,
                    "Guarded stepper: wait timed out " + spec.id, detail);
            return;
        }
        if (waitUpdate.failed) {
            finishWaiter(source, StepOutcome.FAILED,
                    "Guarded stepper: failed " + spec.id, detail);
            return;
        }
        finishWaiter(source, StepOutcome.FAILED,
                "Guarded stepper: invalid waiter terminal " + spec.id, detail);
    }

    private String buildPlan() {
        NativeCombatTransitions.Graph graph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.MAP,
                NativeCombatPhase.MAP_PAGE
        );
        NativeCombatPhase[] candidates = graph.successors(spec.waitCurrent, spec.lastAction);
        return "step=" + spec.id + ", mode=" + (dryRun ? "dry_run" : "execute")
                + "\naction=" + spec.action
                + "\nsource=" + spec.source
                + "\nnormalizedTap=" + spec.tapLabel()
                + "\ngesture=" + (dryRun ? "not dispatched" : "dispatch via AccessibilityService")
                + "\nphaseGuard=" + (spec.requiresFreshPhaseGuard()
                ? "expected " + spec.guardPhase.id + " before gesture"
                : "uses runner phase scan; no fresh guard")
                + "\nphaseScanInput=" + GameFrameNormalizer.phaseScanInputLabel()
                + "\nnextWait=" + spec.waitScenarioId
                + ", current=" + spec.waitCurrent.id
                + ", action=" + spec.lastAction
                + ", candidates=" + NativeCombatTransitions.format(candidates);
    }

    private String preflightDetail() {
        return "preflight=" + AndroidControllerBridge.captureStatus()
                + "\npreflight=" + AndroidControllerBridge.touchStatus();
    }

    static String formatRecognition(NativeCombatRecognizer.Recognition recognition) {
        if (!recognition.matched) {
            return "miss reason=" + recognition.reason
                    + ", bestPhase=" + (recognition.phase == null ? "none" : recognition.phase.id)
                    + ", score=" + formatScore(recognition.score);
        }
        if ("template".equals(recognition.signatureType)) {
            return "match phase=" + recognition.phase.id
                    + ", key=" + recognition.templateKeyId
                    + ", path=" + recognition.path
                    + ", at=(" + recognition.x + "," + recognition.y + ")"
                    + ", score=" + formatScore(recognition.score);
        }
        return "match phase=" + recognition.phase.id
                + ", type=" + recognition.signatureType
                + ", score=" + formatScore(recognition.score);
    }

    private static String formatScore(double score) {
        return String.format(java.util.Locale.US, "%.4f", score);
    }

    private boolean checkpoint() {
        try {
            return control.checkpoint();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean performTap(final float x, final float y) {
        try {
            return control.performAction(new AutomationControl.Action() {
                @Override public boolean run() {
                    return AndroidControllerBridge.tap(x, y);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void postRunning(final Update update) {
        final Runnable delivery = new Runnable() {
            @Override public void run() {
                callback.onUpdate(update);
            }
        };
        PublicationResult result = publicationGate.publishRunning(new PublicationAction() {
            @Override public boolean enqueue() {
                return mainHandler.post(delivery);
            }
        });
        if (result == PublicationResult.FALLBACK_CLAIMED) {
            delivery.run();
        }
    }

    private void postTerminal(final StepOutcome outcome, final String status,
                              final String detail) {
        final Runnable[] delivery = new Runnable[1];
        PublicationResult result = publicationGate.publishTerminal(new PublicationAction() {
            @Override public boolean enqueue() {
                StepOutcome publishedOutcome = publicationGate.cancelled()
                        ? StepOutcome.CANCELLED : outcome;
                String publishedStatus = publishedOutcome == StepOutcome.CANCELLED
                        ? "Guarded stepper: cancelled" : status;
                String publishedDetail = publishedOutcome == outcome
                        ? detail : detail + "\ncancellation=won terminal race";
                final Update update = Update.terminal(
                        publishedOutcome, publishedStatus, publishedDetail
                );
                delivery[0] = new Runnable() {
                    @Override public void run() {
                        callback.onUpdate(update);
                    }
                };
                return mainHandler.post(delivery[0]);
            }
        });
        if (result == PublicationResult.FALLBACK_CLAIMED && delivery[0] != null) {
            delivery[0].run();
        }
    }

    private final class PhaseGuardDeadline implements TemplateMatcher.ScanControl {
        private final long startedAtMs = SystemClock.elapsedRealtime();

        @Override
        public boolean shouldStop() {
            return control.cancelled() || SystemClock.elapsedRealtime() - startedAtMs >= PHASE_GUARD_TIMEOUT_MS;
        }

        @Override
        public String stopReason() {
            return control.cancelled() ? "phase guard cancelled" : "phase guard timed out";
        }
    }

    interface PublicationAction {
        boolean enqueue();
    }

    enum PublicationResult {
        REJECTED,
        ENQUEUED,
        FALLBACK_CLAIMED
    }

    static final class PublicationGate {
        private boolean cancelled;
        private boolean terminalClaimed;

        synchronized PublicationResult publishRunning(PublicationAction action) {
            requireAction(action);
            if (cancelled || terminalClaimed) {
                return PublicationResult.REJECTED;
            }
            return enqueueOrClaimFallback(action);
        }

        synchronized PublicationResult publishTerminal(PublicationAction action) {
            requireAction(action);
            if (terminalClaimed) {
                return PublicationResult.REJECTED;
            }
            terminalClaimed = true;
            return enqueueOrClaimFallback(action);
        }

        void cancel() {
            cancelWith(null);
        }

        synchronized void cancelWith(Runnable cancellationBarrier) {
            if (cancellationBarrier != null) {
                cancellationBarrier.run();
            }
            cancelled = true;
        }

        synchronized boolean cancelled() {
            return cancelled;
        }

        private static PublicationResult enqueueOrClaimFallback(PublicationAction action) {
            try {
                return action.enqueue()
                        ? PublicationResult.ENQUEUED
                        : PublicationResult.FALLBACK_CLAIMED;
            } catch (RuntimeException handlerFailure) {
                return PublicationResult.FALLBACK_CLAIMED;
            }
        }

        private static void requireAction(PublicationAction action) {
            if (action == null) {
                throw new IllegalArgumentException("publication action is required");
            }
        }
    }

    interface WaiterHandle {
        boolean start();
        void cancel();
    }

    enum WaiterStartOutcome {
        STARTED,
        CANCELLED,
        REJECTED
    }

    static final class WaiterLifecycle {
        private WaiterHandle waiter;
        private WaiterHandle cancelDeliveredTo;
        private boolean cancelled;
        private boolean running;
        private boolean terminalCompleted;
        private boolean startAccepted;

        synchronized boolean begin() {
            if (running || terminalCompleted || cancelled) {
                return false;
            }
            running = true;
            return true;
        }

        WaiterStartOutcome publishAndStart(WaiterHandle candidate) {
            if (candidate == null) {
                throw new IllegalArgumentException("waiter is required");
            }
            boolean cancelBeforeStart;
            boolean deliverCancellation = false;
            synchronized (this) {
                if (terminalCompleted || waiter != null) {
                    return WaiterStartOutcome.REJECTED;
                }
                if (!running && !cancelled) {
                    running = true;
                }
                waiter = candidate;
                startAccepted = false;
                cancelBeforeStart = cancelled;
                if (cancelBeforeStart && cancelDeliveredTo != candidate) {
                    cancelDeliveredTo = candidate;
                    deliverCancellation = true;
                }
            }

            if (cancelBeforeStart) {
                if (deliverCancellation) {
                    candidate.cancel();
                }
                return WaiterStartOutcome.CANCELLED;
            }

            boolean accepted = candidate.start();
            synchronized (this) {
                if (terminalCompleted || waiter != candidate) {
                    return cancelled
                            ? WaiterStartOutcome.CANCELLED
                            : WaiterStartOutcome.STARTED;
                }
                startAccepted = accepted;
                if (cancelled) {
                    return WaiterStartOutcome.CANCELLED;
                }
                if (!accepted) {
                    waiter = null;
                    startAccepted = false;
                    return WaiterStartOutcome.REJECTED;
                }
                return WaiterStartOutcome.STARTED;
            }
        }

        void cancel() {
            cancelWith(null);
        }

        void cancelWith(Runnable cancellationBarrier) {
            WaiterHandle published = claimCancellationWith(cancellationBarrier);
            if (published != null) {
                published.cancel();
            }
        }

        synchronized WaiterHandle claimCancellationWith(Runnable cancellationBarrier) {
            WaiterHandle published = null;
            if (cancellationBarrier != null) {
                cancellationBarrier.run();
            }
            cancelled = true;
            if (waiter != null && cancelDeliveredTo != waiter) {
                cancelDeliveredTo = waiter;
                published = waiter;
            }
            return published;
        }

        synchronized boolean hasActiveWaiter() {
            return running && waiter != null && startAccepted
                    && !cancelled && !terminalCompleted;
        }

        synchronized boolean isCurrentWaiter(WaiterHandle candidate) {
            return running && !terminalCompleted && waiter == candidate;
        }

        synchronized boolean acceptsRunning(WaiterHandle candidate) {
            return running && !terminalCompleted && !cancelled && waiter == candidate;
        }

        synchronized boolean completeWaiter(WaiterHandle candidate) {
            if (candidate == null || terminalCompleted || waiter != candidate) {
                return false;
            }
            terminalCompleted = true;
            running = false;
            waiter = null;
            startAccepted = false;
            return true;
        }

        synchronized boolean completeWithoutWaiter() {
            if (terminalCompleted || waiter != null) {
                return false;
            }
            terminalCompleted = true;
            running = false;
            startAccepted = false;
            return true;
        }

        synchronized boolean isRunning() {
            return running && !terminalCompleted;
        }

        synchronized boolean cancelled() {
            return cancelled;
        }
    }

    interface Callback {
        void onUpdate(Update update);
    }

    enum StepOutcome {
        RUNNING,
        MATCHED,
        BLOCKED,
        TIMED_OUT,
        CANCELLED,
        FAILED
    }

    static final class Update {
        final StepOutcome outcome;
        final String status;
        final String detail;

        private Update(StepOutcome outcome, String status, String detail) {
            this.outcome = outcome;
            this.status = status;
            this.detail = detail;
        }

        static Update running(String status, String detail) {
            return new Update(StepOutcome.RUNNING, status, detail);
        }

        static Update terminal(StepOutcome outcome, String status, String detail) {
            if (outcome == StepOutcome.RUNNING) {
                throw new IllegalArgumentException("terminal step required");
            }
            return new Update(outcome, status, detail);
        }

        boolean running() {
            return outcome == StepOutcome.RUNNING;
        }
    }

    private static final class ActionSpec {
        final String id;
        final String action;
        final String source;
        final NativeCombatPhase guardPhase;
        final String waitScenarioId;
        final NativeCombatPhase waitCurrent;
        final String lastAction;
        final float tapX;
        final float tapY;
        final int tapRepeat;
        final long preTapDelayMs;
        final long betweenTapDelayMs;
        final NativeTemplateKey templateTapKey;

        ActionSpec(
                String id,
                String action,
                String source,
                NativeCombatPhase guardPhase,
                String waitScenarioId,
                NativeCombatPhase waitCurrent,
                String lastAction,
                float tapX,
                float tapY
        ) {
            this(id, action, source, guardPhase, waitScenarioId, waitCurrent, lastAction, tapX, tapY, 1);
        }

        ActionSpec(
                String id,
                String action,
                String source,
                NativeCombatPhase guardPhase,
                String waitScenarioId,
                NativeCombatPhase waitCurrent,
                String lastAction,
                float tapX,
                float tapY,
                int tapRepeat
        ) {
            this(id, action, source, guardPhase, waitScenarioId, waitCurrent, lastAction,
                    tapX, tapY, tapRepeat, 0L, 200L);
        }

        ActionSpec(
                String id,
                String action,
                String source,
                NativeCombatPhase guardPhase,
                String waitScenarioId,
                NativeCombatPhase waitCurrent,
                String lastAction,
                float tapX,
                float tapY,
                int tapRepeat,
                long preTapDelayMs,
                long betweenTapDelayMs
        ) {
            this.id = id;
            this.action = action;
            this.source = source;
            this.guardPhase = guardPhase;
            this.waitScenarioId = waitScenarioId;
            this.waitCurrent = waitCurrent;
            this.lastAction = lastAction;
            this.tapX = tapX;
            this.tapY = tapY;
            this.tapRepeat = Math.max(1, tapRepeat);
            this.preTapDelayMs = Math.max(0L, preTapDelayMs);
            this.betweenTapDelayMs = Math.max(0L, betweenTapDelayMs);
            this.templateTapKey = null;
        }

        ActionSpec(
                String id,
                String action,
                String source,
                NativeCombatPhase guardPhase,
                String waitScenarioId,
                NativeCombatPhase waitCurrent,
                String lastAction,
                NativeTemplateKey templateTapKey
        ) {
            this.id = id;
            this.action = action;
            this.source = source;
            this.guardPhase = guardPhase;
            this.waitScenarioId = waitScenarioId;
            this.waitCurrent = waitCurrent;
            this.lastAction = lastAction;
            this.tapX = 0f;
            this.tapY = 0f;
            this.tapRepeat = 1;
            this.preTapDelayMs = 0L;
            this.betweenTapDelayMs = 200L;
            this.templateTapKey = templateTapKey;
        }

        String tapLabel() {
            if (templateTapKey != null) {
                return "template(" + templateTapKey.id + ")";
            }
            return "(" + tapX + "," + tapY + ")" + (tapRepeat > 1 ? " x" + tapRepeat : "");
        }

        boolean requiresFreshPhaseGuard() {
            return !"result_continue".equals(id);
        }
    }

    private static final class GestureDispatch {
        final boolean dispatched;
        final String detail;

        private GestureDispatch(boolean dispatched, String detail) {
            this.dispatched = dispatched;
            this.detail = detail;
        }

        static GestureDispatch dispatched(String detail) {
            return new GestureDispatch(true, detail);
        }

        static GestureDispatch blocked(String detail) {
            return new GestureDispatch(false, detail);
        }
    }
}
