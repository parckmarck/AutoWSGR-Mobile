package org.openwsgr.autowsgrmobile;

import org.openwsgr.autowsgrmobile.vision.PixelRule;
import org.openwsgr.autowsgrmobile.vision.PixelSignature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

final class MinimumRunContractsTest {
    public static void main(String[] args) {
        preflightBlocksMissingFrame();
        preflightBlocksMissingAccessibility();
        preflightBlocksMissingPackage();
        classifierRequiresTargetPackageForHome();
        classifierPrefersBlockerOverHome();
        homeSignatureThresholdAcceptsCurrentHomeButRejectsProceedPrompt();
        classifierDetectsCombatAndMap();
        classifierDetectsStartScreenBeforeUnknown();
        startScreenSignatureMatchesOriginalStartupPage();
        startScreenEnterTapMatchesOriginalStartupPage();
        evidencePathsAreStableAndSafe();
        guardedRunnerOutcomesAreConservative();
        resultTransitionMatchesOriginalMapGraph();
        resultContinueMatchesOriginalDoubleClickHandler();
        resultContinueUsesRunnerPhaseScanWithoutFreshGuard();
        mapPagePhaseHasPixelFallbackForNormalSortieMap();
        mapPagePixelFallbackSamplesNormalSortieTabs();
        getShipTransitionMatchesOriginalMapGraph();
        getShipContinueMatchesOriginalDropHandler();
        getShipPhaseHasPixelFallbackForDropPage();
        getShipPixelFallbackHasDropAndDialogVariants();
        getShipDialogPixelFallbackUsesGenericUiChrome();
        getShipPixelFallbackRunsBeforeTemplateTimeout();
        proceedPhaseHasPixelFallbackForFleetSupplyPrompt();
        proceedPixelFallbackHasSupplyAndReturnPortPromptVariants();
        proceedPixelFallbackSamplesStableButtonInteriorsAfterScale();
        proceedReturnPortPixelFallbackSamplesLowerButtonInteriors();
        candidatePixelPassRunsBeforeTemplatePass();
        guardedPhaseScanChecksGetShipPixelBeforeResultTemplate();
        pixelMissDiagnosticsAreMergedIntoTemplateMissReason();
        liveWaiterPerformsFinalScanAtTimeoutBoundary();
        viewportMapsPortraitLetterboxToGameCoordinates();
        touchMapperUsesLandscapeDisplayWhenCaptureIsPortrait();
        accessibilityTouchKeepsRawLogicalDisplayForTargetForeground();
        guardedPhaseScanUsesLandscapeGameFrame();
        captureFrameFailurePolicyTreatsInaccessibleBuffersAsRecoverable();
        battlePrepTapTargetsUseButtonCentersAndDefaultFormation();
        battleAdvanceSequenceIncludesEnemyPreviewStartBeforeFormation();
        battleAdvanceSequenceWaitsForRequiredPagesBeforeTap();
        battleAdvancePageSignaturesDistinguishEnemyPreviewAndFormation();
        enemyPreviewSignatureToleratesDimmedTitleText();
        fleetSupplyPromptIsNightPromptAndDefaultsToAbandon();
        fastResultPageSignatureMatchesCurrentGameResult();
        resultPhaseHasPixelFallbackForFastBattles();
        resultPixelFallbackIsSingleResultPhase();
        resultPixelFallbackRunsBeforeTemplateTimeout();
        normalSortieTargetBuildsStablePaths();
        normalMapCatalogGroupsNormalMapsByChapter();
        selectedNormalPlanUsesExactPlanWhenAvailable();
        generatedNormalPlanUsesFirstStartNextNodeConservatively();
        normalMapNavigationTapPlanNormalizesThenSelectsTarget();
        runnerSummariesUseSelectedNormalPolicy();
        configPlanPathResolvesExactOriginalPlan();
        inlineConfigResolvesNormalPlanWithoutGuessing();
        invalidConfigWithoutPlanOrTargetIsRejected();
        defaultSortieConfigTextPointsAtSafePlan();
        runnerSummariesUseLoadedConfigAndResolvedPolicy();
        mapTitleParserMatchesOriginalSortieOcrFormats();
        mapTargetVerifierConfirmsMatchingTitleAndBlocksMismatch();
        mapTargetVerifierAllowsConfigVerifiedMvpWhenOcrUnavailable();
        expeditionSignaturesMatchOriginalProbes();
        expeditionNotificationDetectorsUseSourceColors();
        expeditionReadySlotDetectorReturnsFirstYellowSlot();
        mainPageOverlaySignaturesMatchOriginalDismissalCoords();
        mainPageOverlayDetectorMatchesNewsAndCheckbox();
        mainPageOverlayDetectorMatchesCurrentNewsVariantAndCheckbox();
        mainPageSignDismissPlanMatchesOriginalConfirmOperation();
        expeditionLogPathsUseSeparatePrefix();
        autoExpeditionConfigDefaultsToOriginalInterval();
        autoExpeditionSchedulerPolicyAttemptsImmediatelyAndThenWaits();
        autoExpeditionSchedulerPolicyDisablesNonPositiveIntervalsAndAvoidsOverlap();
        autoExpeditionSchedulerPolicyDisablesOverflowIntervals();
        autoExpeditionConfigReportsMalformedInterval();
        expeditionNavigatorTapPlanMatchesOriginalTargets();
        expeditionNavigatorRedispatchConfirmTargetsLeftButton();
        expeditionRunnerSummaryMentionsCollectOnlySemantics();
        expeditionRunnerWaitFailurePrefersCancelledOverBlocked();
        expeditionRunnerReturnHomeOutcomeDoesNotReportSuccessAfterCancelOrTapFailure();
        expeditionRunnerUsesSessionOwnedFinalization();
        autoExpeditionSchedulerSummaryUsesOriginalInterval();
        autoExpeditionSchedulerBoundsPassWaits();
        autoExpeditionSchedulerForwardsPassFinalAsRunningUntilSchedulerStops();
        autoExpeditionSchedulerWaitAndStopPreserveLatestEvidencePaths();
        autoExpeditionUsesRuntimeGlobalGate();
        expeditionSlotReadyVerificationHelperMatchesFindReadySlot();
        expeditionReadySlotDetectorToleratesSmallSpatialDrift();
        campaignCatalogMapsNamesToOriginalIndexAndDifficulty();
        campaignSignaturesMatchOriginalProbes();
        campaignDifficultyRecognizerUsesSourceColors();
        campaignBattleConfigParsesAllFieldsAndDefaults();
        campaignBattleConfigBuildsSingleNodePlanWithFormationAndNight();
        campaignNavigatorTapTargetsMatchOriginal();
        digitTemplateRecognizerMatchesSyntheticDigits();
        migratedRunnersPublishTypedTerminalOutcomes();
        migratedRunnersDelegateControlAndCheckpointActions();
        schedulerKeepsPassTerminalsRunningUntilSchedulerStops();
        mainActivityUsesApplicationRuntimeWithoutPairwiseFlowMatrix();
        mainActivityLifecycleReplaysWithoutDestroyCancellation();
    }

    private static void preflightBlocksMissingFrame() {
        MinimumRunPreflight.Result result = MinimumRunPreflight.validate(false, true, true);
        assertFalse(result.ready, "missing frame should block");
        assertEquals("missing-screen-capture", result.reason);
    }

    private static void preflightBlocksMissingAccessibility() {
        MinimumRunPreflight.Result result = MinimumRunPreflight.validate(true, false, true);
        assertFalse(result.ready, "missing accessibility should block");
        assertEquals("accessibility-disabled", result.reason);
    }

    private static void preflightBlocksMissingPackage() {
        MinimumRunPreflight.Result result = MinimumRunPreflight.validate(true, true, false);
        assertFalse(result.ready, "missing target package should block");
        assertEquals("target-package-missing", result.reason);
    }

    private static void classifierRequiresTargetPackageForHome() {
        MinimumGameStateClassifier.Input input = new MinimumGameStateClassifier.Input(
                true,
                false,
                true,
                false,
                false,
                false,
                0.99
        );
        MinimumGameStateClassifier.Result result = MinimumGameStateClassifier.classify(input);
        assertEquals(MinimumGameStateClassifier.State.UNKNOWN, result.state);
        assertContains(result.detail, "targetForeground=false");
    }

    private static void classifierPrefersBlockerOverHome() {
        MinimumGameStateClassifier.Input input = new MinimumGameStateClassifier.Input(
                true,
                true,
                true,
                true,
                false,
                false,
                0.99
        );
        MinimumGameStateClassifier.Result result = MinimumGameStateClassifier.classify(input);
        assertEquals(MinimumGameStateClassifier.State.BLOCKER, result.state);
        assertEquals("blocker-detected", result.reason);
    }

    private static void homeSignatureThresholdAcceptsCurrentHomeButRejectsProceedPrompt() {
        assertTrue(
                MinimumGameStateRecognizer.homeSignatureMatchedForCount(3),
                "current home screenshots can provide 3 stable signature points after scaling"
        );
        assertFalse(
                MinimumGameStateRecognizer.homeSignatureMatchedForCount(2),
                "proceed prompt failures observed at 2/6 must not be classified as home"
        );
    }

    private static void classifierDetectsCombatAndMap() {
        MinimumGameStateClassifier.Result combat = MinimumGameStateClassifier.classify(
                new MinimumGameStateClassifier.Input(true, true, false, false, true, false, 0.0)
        );
        assertEquals(MinimumGameStateClassifier.State.COMBAT_PHASE, combat.state);

        MinimumGameStateClassifier.Result map = MinimumGameStateClassifier.classify(
                new MinimumGameStateClassifier.Input(true, true, false, false, false, true, 0.0)
        );
        assertEquals(MinimumGameStateClassifier.State.MAP_PAGE, map.state);
    }

    private static void classifierDetectsStartScreenBeforeUnknown() {
        MinimumGameStateClassifier.Result result = MinimumGameStateClassifier.classify(
                new MinimumGameStateClassifier.Input(true, true, false, false, false, false, true, 0.0)
        );
        assertEquals(MinimumGameStateClassifier.State.START_SCREEN, result.state);
        assertEquals("start-screen", result.reason);
    }

    private static void startScreenSignatureMatchesOriginalStartupPage() {
        final float[][] samples = new float[][]{
                {0.0508f, 0.8722f, 204f, 210f, 208f},
                {0.8125f, 0.8569f, 224f, 183f, 41f},
                {0.8695f, 0.9028f, 222f, 198f, 40f},
                {0.9641f, 0.8681f, 222f, 197f, 43f},
                {0.7914f, 0.7333f, 237f, 237f, 237f}
        };
        MinimumStartScreenSignatures.Sampler startScreen = new MinimumStartScreenSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                for (float[] item : samples) {
                    if (Math.abs(item[0] - normalizedX) < 0.0001f
                            && Math.abs(item[1] - normalizedY) < 0.0001f) {
                        return rgb((int) item[2], (int) item[3], (int) item[4]);
                    }
                }
                return rgb(0, 0, 0);
            }
        };
        MinimumStartScreenSignatures.Result result = MinimumStartScreenSignatures.match(startScreen);
        assertTrue(result.matched, "start screen signature should mirror original StartScreenPage");
        assertContains(result.detail, "startScreenSignature");
        assertContains(result.detail, "matched=5/5");
    }

    private static void startScreenEnterTapMatchesOriginalStartupPage() {
        List<MinimumNormalBattleNavigator.TapAction> actions =
                MinimumNormalBattleNavigator.startScreenEnterPlan();
        assertEquals(1, actions.size());
        assertEquals("start-screen-enter", actions.get(0).name);
        assertNear(0.900f, actions.get(0).gameX, 0.0001f);
        assertNear(0.850f, actions.get(0).gameY, 0.0001f);
    }

    private static void evidencePathsAreStableAndSafe() {
        File base = new File("base-dir");
        File log = MinimumRunLogStore.logFile(base, "run:1/2");
        File failure = MinimumRunLogStore.failureScreenshotFile(base, "run:1/2");
        assertContains(log.getPath().replace('\\', '/'), "runs/minimum-normal-run-1-2.log");
        assertContains(failure.getPath().replace('\\', '/'), "failures/minimum-normal-run-1-2.png");
        String line = MinimumRunLogStore.line("r1", "blocked", "reason=unknown\nscreen");
        assertEquals("run=r1 status=blocked detail=reason=unknown screen", line);
    }

    private static void guardedRunnerOutcomesAreConservative() {
        assertEquals(
                MinimumRunOutcomePolicy.Action.SUCCESS,
                MinimumRunOutcomePolicy.classifyGuardedFinal("Guarded runner: route complete", "").action
        );
        assertEquals(
                MinimumRunOutcomePolicy.Action.CONTINUE,
                MinimumRunOutcomePolicy.classifyGuardedFinal("Guarded runner: max steps reached", "").action
        );
        assertEquals(
                MinimumRunOutcomePolicy.Action.CANCELLED,
                MinimumRunOutcomePolicy.classifyGuardedFinal("Guarded runner: cancelled", "").action
        );
        assertEquals(
                MinimumRunOutcomePolicy.Action.BLOCKED,
                MinimumRunOutcomePolicy.classifyGuardedFinal("Guarded runner: no guarded phase", "").action
        );
        assertEquals(
                MinimumRunOutcomePolicy.Action.BLOCKED,
                MinimumRunOutcomePolicy.classifyGuardedFinal("Guarded runner: wait timed out start", "").action
        );
    }

    private static void resultTransitionMatchesOriginalMapGraph() {
        NativeCombatTransitions.Graph graph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.MAP,
                NativeCombatPhase.MAP_PAGE
        );
        assertEquals(
                "[proceed, map_page, get_ship, flagship_severe_damage]",
                graph.describeSuccessors(NativeCombatPhase.RESULT, "result_continue")
        );
    }

    private static void resultContinueMatchesOriginalDoubleClickHandler() {
        assertEquals(2, GuardedCombatStepper.tapRepeatForActionId("result_continue"));
        assertEquals(1000L, GuardedCombatStepper.preTapDelayForActionId("result_continue"));
        assertEquals(250L, GuardedCombatStepper.betweenTapDelayForActionId("result_continue"));
        assertContains(
                GuardedCombatStepper.actionSourceForActionId("result_continue"),
                "autowsgr/combat/handlers.py _handle_result double click"
        );
    }

    private static void resultContinueUsesRunnerPhaseScanWithoutFreshGuard() {
        assertFalse(
                GuardedCombatStepper.requiresFreshPhaseGuardForActionId("result_continue"),
                "result_continue should trust the runner phase scan like original _handle_result"
        );
        assertTrue(
                GuardedCombatStepper.requiresFreshPhaseGuardForActionId("proceed_no"),
                "interactive branch prompts should keep a fresh phase guard"
        );
    }

    private static void mapPagePhaseHasPixelFallbackForNormalSortieMap() {
        assertTrue(
                NativeCombatPhase.MAP_PAGE.hasTemplateSignature(),
                "map_page should keep original end-map template fallback"
        );
        assertTrue(
                NativeCombatPhase.MAP_PAGE.hasPixelSignature(),
                "normal sortie map page should be recognized after proceed_no returns to map"
        );
        assertEquals(
                "map_page",
                NativePhasePixelSignatures.signatureName(NativePhasePixelSignatures.Kind.MAP_PAGE)
        );
        assertEquals(
                "pixel,template",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.MAP_PAGE)
        );
    }

    private static void mapPagePixelFallbackSamplesNormalSortieTabs() {
        PixelSignature signature = NativePhasePixelSignatures.testSignature(
                NativePhasePixelSignatures.Kind.MAP_PAGE,
                0
        );
        assertEquals("normal_map_sortie_page", signature.name);
        assertEquals(6, signature.rules.size());

        PixelRule sortieTab = signature.rules.get(0);
        assertNear(0.1539f, sortieTab.x, 0.0001f);
        assertNear(0.0472f, sortieTab.y, 0.0001f);

        PixelRule mapOcean = signature.rules.get(5);
        assertNear(0.530f, mapOcean.x, 0.0001f);
        assertNear(0.500f, mapOcean.y, 0.0001f);
    }

    private static void getShipTransitionMatchesOriginalMapGraph() {
        NativeCombatTransitions.Graph graph = NativeCombatTransitions.build(
                NativeCombatTransitions.ModeCategory.MAP,
                NativeCombatPhase.MAP_PAGE
        );
        assertEquals(
                "[proceed, map_page, flagship_severe_damage]",
                graph.describeSuccessors(NativeCombatPhase.GET_SHIP, "get_ship_continue")
        );
    }

    private static void getShipContinueMatchesOriginalDropHandler() {
        assertTrue(
                Arrays.asList(GuardedCombatStepper.guardedPhases()).contains(NativeCombatPhase.GET_SHIP),
                "guarded runner should scan GET_SHIP so original _handle_get_ship can run"
        );
        assertEquals("get_ship_continue", GuardedCombatStepper.actionIdForPhase(NativeCombatPhase.GET_SHIP));
        assertEquals(1, GuardedCombatStepper.tapRepeatForActionId("get_ship_continue"));
        assertEquals(0L, GuardedCombatStepper.preTapDelayForActionId("get_ship_continue"));
        assertContains(
                GuardedCombatStepper.actionSourceForActionId("get_ship_continue"),
                "autowsgr/combat/handlers.py _handle_get_ship"
        );
        assertContains(
                GuardedCombatStepper.actionSourceForActionId("get_ship_continue"),
                "autowsgr/combat/actions.py Coords.CLICK_RESULT"
        );
        assertContains(GuardedCombatStepper.guardedActionCatalog(), "get_ship->{get_ship_continue}");
    }

    private static void getShipPhaseHasPixelFallbackForDropPage() {
        assertTrue(
                NativeCombatPhase.GET_SHIP.hasTemplateSignature(),
                "get_ship should keep original get_ship_or_item templates"
        );
        assertTrue(
                NativeCombatPhase.GET_SHIP.hasPixelSignature(),
                "drop page needs a pixel fallback when template score lands below the strict live threshold"
        );
        assertEquals(
                "get_ship_page",
                NativePhasePixelSignatures.signatureName(NativePhasePixelSignatures.Kind.GET_SHIP_PAGE)
        );
    }

    private static void getShipPixelFallbackHasDropAndDialogVariants() {
        assertEquals(
                2,
                NativePhasePixelSignatures.signatureVariantCount(NativePhasePixelSignatures.Kind.GET_SHIP_PAGE)
        );
        assertEquals(
                "get_ship_drop",
                NativePhasePixelSignatures.testSignature(NativePhasePixelSignatures.Kind.GET_SHIP_PAGE, 0).name
        );
        assertEquals(
                "get_ship_dialog",
                NativePhasePixelSignatures.testSignature(NativePhasePixelSignatures.Kind.GET_SHIP_PAGE, 1).name
        );
    }

    private static void getShipDialogPixelFallbackUsesGenericUiChrome() {
        PixelSignature signature = NativePhasePixelSignatures.testSignature(
                NativePhasePixelSignatures.Kind.GET_SHIP_PAGE,
                1
        );
        assertEquals(6, signature.rules.size());

        PixelRule orangeStar = signature.rules.get(0);
        assertNear(0.792f, orangeStar.x, 0.0001f);
        assertNear(0.318f, orangeStar.y, 0.0001f);

        PixelRule dialogLine = signature.rules.get(5);
        assertNear(0.520f, dialogLine.x, 0.0001f);
        assertNear(0.790f, dialogLine.y, 0.0001f);
    }

    private static void getShipPixelFallbackRunsBeforeTemplateTimeout() {
        assertEquals(
                "pixel,template",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.GET_SHIP)
        );
    }

    private static void proceedPhaseHasPixelFallbackForFleetSupplyPrompt() {
        assertTrue(
                NativeCombatPhase.PROCEED.hasTemplateSignature(),
                "proceed should keep original proceed templates"
        );
        assertTrue(
                NativeCombatPhase.PROCEED.hasPixelSignature(),
                "fleet supply status prompt should be recognized as original PROCEED"
        );
        assertEquals(
                "proceed_page",
                NativePhasePixelSignatures.signatureName(NativePhasePixelSignatures.Kind.PROCEED_PAGE)
        );
        assertEquals(
                "pixel,template",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.PROCEED)
        );
    }

    private static void proceedPixelFallbackHasSupplyAndReturnPortPromptVariants() {
        assertEquals(
                2,
                NativePhasePixelSignatures.signatureVariantCount(NativePhasePixelSignatures.Kind.PROCEED_PAGE)
        );
        assertEquals(
                "proceed_supply_prompt",
                NativePhasePixelSignatures.testSignature(NativePhasePixelSignatures.Kind.PROCEED_PAGE, 0).name
        );
        assertEquals(
                "proceed_return_port_prompt",
                NativePhasePixelSignatures.testSignature(NativePhasePixelSignatures.Kind.PROCEED_PAGE, 1).name
        );
    }

    private static void proceedPixelFallbackSamplesStableButtonInteriorsAfterScale() {
        PixelSignature signature = NativePhasePixelSignatures.testSignature(
                NativePhasePixelSignatures.Kind.PROCEED_PAGE,
                0
        );
        assertEquals(6, signature.rules.size());

        PixelRule titleRight = signature.rules.get(1);
        assertTrue(
                titleRight.tolerance >= 50.0,
                "right title probe should tolerate Android createScaledBitmap interpolation"
        );

        PixelRule redButton = signature.rules.get(4);
        assertNear(0.305f, redButton.x, 0.0001f);
        assertNear(0.620f, redButton.y, 0.0001f);
        assertTrue(
                redButton.tolerance >= 55.0,
                "red proceed button probe should not sit on the lower bevel after scaling"
        );

        PixelRule blueButton = signature.rules.get(5);
        assertNear(0.620f, blueButton.x, 0.0001f);
        assertNear(0.620f, blueButton.y, 0.0001f);
        assertTrue(
                blueButton.tolerance >= 55.0,
                "blue return-port button probe should not sit on the lower bevel after scaling"
        );
    }

    private static void proceedReturnPortPixelFallbackSamplesLowerButtonInteriors() {
        PixelSignature signature = NativePhasePixelSignatures.testSignature(
                NativePhasePixelSignatures.Kind.PROCEED_PAGE,
                1
        );
        assertEquals(6, signature.rules.size());

        PixelRule secondSupplyBar = signature.rules.get(3);
        assertNear(0.500f, secondSupplyBar.x, 0.0001f);
        assertNear(0.552f, secondSupplyBar.y, 0.0001f);

        PixelRule redButton = signature.rules.get(4);
        assertNear(0.323f, redButton.x, 0.0001f);
        assertNear(0.653f, redButton.y, 0.0001f);
        assertTrue(
                redButton.tolerance >= 70.0,
                "return-port prompt red button probe should tolerate lower-interior antialiasing"
        );

        PixelRule blueButton = signature.rules.get(5);
        assertNear(0.655f, blueButton.x, 0.0001f);
        assertNear(0.653f, blueButton.y, 0.0001f);
        assertTrue(
                blueButton.tolerance >= 70.0,
                "return-port prompt blue button probe should tolerate lower-interior antialiasing"
        );
    }

    private static void candidatePixelPassRunsBeforeTemplatePass() {
        assertEquals(
                "pixel:proceed,pixel:result,pixel:get_ship,template:proceed,template:result,template:get_ship",
                NativeCombatRecognizer.candidateScanOrder(new NativeCombatPhase[]{
                        NativeCombatPhase.PROCEED,
                        NativeCombatPhase.RESULT,
                        NativeCombatPhase.GET_SHIP
                })
        );
    }

    private static void guardedPhaseScanChecksGetShipPixelBeforeResultTemplate() {
        String scanOrder = NativeCombatRecognizer.candidateScanOrder(GuardedCombatStepper.guardedPhases());
        int getShipPixel = scanOrder.indexOf("pixel:get_ship");
        int resultTemplate = scanOrder.indexOf("template:result");
        assertTrue(getShipPixel >= 0, "guarded phase scan should include get_ship pixel signature");
        assertTrue(resultTemplate >= 0, "guarded phase scan should keep result template fallback");
        assertTrue(
                getShipPixel < resultTemplate,
                "get_ship pixel fallback must run before result template scan can consume the guard timeout"
        );
    }

    private static void pixelMissDiagnosticsAreMergedIntoTemplateMissReason() {
        assertEquals(
                "best score below threshold 0.999, pixelMisses=proceed=5/6;result=1/6",
                NativeCombatRecognizer.withPixelDiagnostics(
                        "best score below threshold 0.999",
                        "proceed=5/6;result=1/6"
                )
        );
        assertEquals(
                "no candidate matched",
                NativeCombatRecognizer.withPixelDiagnostics("no candidate matched", "")
        );
    }

    private static void liveWaiterPerformsFinalScanAtTimeoutBoundary() {
        assertFalse(
                LiveCombatFlowWaiter.shouldAttemptFinalScanAtTimeout(5999L, 6000L),
                "normal polling should continue before the timeout boundary"
        );
        assertTrue(
                LiveCombatFlowWaiter.shouldAttemptFinalScanAtTimeout(6000L, 6000L),
                "timeout boundary should still scan the latest stable frame before reporting failure"
        );
        assertEquals(
                7200L,
                LiveCombatFlowWaiter.finalScanDeadlineElapsedMs(6000L)
        );
    }

    private static void viewportMapsPortraitLetterboxToGameCoordinates() {
        GameFrameViewport portrait = GameFrameViewport.fromFrame(720, 1280);
        assertEquals(0, portrait.left);
        assertEquals(437, portrait.top);
        assertEquals(720, portrait.width);
        assertEquals(405, portrait.height);
        assertNear(0.9375f, portrait.mapX(0.9375f), 0.0001f);
        assertNear(0.6256f, portrait.mapY(0.8981f), 0.0010f);

        GameFrameViewport landscape = GameFrameViewport.fromFrame(960, 540);
        assertEquals(0, landscape.left);
        assertEquals(0, landscape.top);
        assertEquals(960, landscape.width);
        assertEquals(540, landscape.height);
        assertNear(0.8981f, landscape.mapY(0.8981f), 0.0001f);
    }

    private static void touchMapperUsesLandscapeDisplayWhenCaptureIsPortrait() {
        GameTouchMapper.Mapping landscapeTouch = GameTouchMapper.map(
                0.9444f,
                0.9556f,
                720,
                1280,
                1280,
                720
        );
        assertNear(0.9444f, landscapeTouch.x, 0.0001f);
        assertNear(0.9556f, landscapeTouch.y, 0.0001f);

        GameTouchMapper.Mapping portraitTouch = GameTouchMapper.map(
                0.9444f,
                0.9556f,
                720,
                1280,
                720,
                1280
        );
        assertNear(0.9444f, portraitTouch.x, 0.0001f);
        assertNear(0.6438f, portraitTouch.y, 0.0010f);
    }

    private static void accessibilityTouchKeepsRawLogicalDisplayForTargetForeground() {
        TouchDisplaySizeMapper.Size targetForeground = TouchDisplaySizeMapper.effective(
                720,
                1280,
                "com.huanmeng.zhanjian2",
                "com.huanmeng.zhanjian2"
        );
        assertEquals(720, targetForeground.width);
        assertEquals(1280, targetForeground.height);
        assertEquals("display-raw", targetForeground.detail);

        TouchDisplaySizeMapper.Size appForeground = TouchDisplaySizeMapper.effective(
                720,
                1280,
                "org.openwsgr.autowsgrmobile",
                "com.huanmeng.zhanjian2"
        );
        assertEquals(720, appForeground.width);
        assertEquals(1280, appForeground.height);
        assertEquals("display-raw", appForeground.detail);
    }

    private static void guardedPhaseScanUsesLandscapeGameFrame() {
        assertEquals("landscape-game-frame", GameFrameNormalizer.phaseScanInputLabel());
    }

    private static void captureFrameFailurePolicyTreatsInaccessibleBuffersAsRecoverable() {
        assertTrue(
                CaptureFrameFailurePolicy.isRecoverable(new IllegalStateException("buffer is inaccessible")),
                "inaccessible ImageReader buffers should be skipped, not crash the process"
        );
        assertTrue(
                CaptureFrameFailurePolicy.isRecoverable(new IllegalStateException("Image is already closed")),
                "closed ImageReader frames should be skipped during display transitions"
        );
        assertFalse(
                CaptureFrameFailurePolicy.isRecoverable(new NullPointerException("programming bug")),
                "unrelated bugs must still surface"
        );
    }

    private static void battlePrepTapTargetsUseButtonCentersAndDefaultFormation() {
        assertNear(0.945f, MinimumBattleTapTargets.START_BATTLE_X, 0.002f);
        assertNear(0.931f, MinimumBattleTapTargets.START_BATTLE_Y, 0.002f);
        assertNear(0.945f, MinimumBattleTapTargets.ENEMY_START_BATTLE_X, 0.002f);
        assertNear(0.931f, MinimumBattleTapTargets.ENEMY_START_BATTLE_Y, 0.002f);
        assertNear(0.924f, MinimumBattleTapTargets.DEFAULT_FORMATION_X, 0.004f);
        assertNear(0.264f, MinimumBattleTapTargets.DEFAULT_FORMATION_Y, 0.004f);
    }

    private static void battleAdvanceSequenceIncludesEnemyPreviewStartBeforeFormation() {
        MinimumBattleAdvancePolicy.Step[] steps = MinimumBattleAdvancePolicy.steps();
        assertEquals(3, steps.length);
        assertEquals(MinimumBattleAdvancePolicy.Step.START_SORTIE, steps[0]);
        assertEquals(MinimumBattleAdvancePolicy.Step.ENEMY_START_BATTLE, steps[1]);
        assertEquals(MinimumBattleAdvancePolicy.Step.DEFAULT_FORMATION, steps[2]);
    }

    private static void battleAdvanceSequenceWaitsForRequiredPagesBeforeTap() {
        MinimumBattleAdvancePolicy.Step[] steps = MinimumBattleAdvancePolicy.steps();
        assertEquals(MinimumBattleAdvancePolicy.Page.BATTLE_PREPARATION, steps[0].requiredPage());
        assertEquals(MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW, steps[1].requiredPage());
        assertEquals(MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION, steps[2].requiredPage());
    }

    private static void battleAdvancePageSignaturesDistinguishEnemyPreviewAndFormation() {
        MinimumBattlePageSignatures.Sampler enemyPreview = sampler(new float[][]{
                {0.050f, 0.080f, 245f, 245f, 245f},
                {0.735f, 0.920f, 104f, 11f, 14f},
                {0.840f, 0.920f, 193f, 173f, 42f},
                {0.930f, 0.920f, 190f, 168f, 42f}
        });
        MinimumBattlePageSignatures.Result enemyResult = MinimumBattlePageSignatures.match(
                MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW,
                enemyPreview
        );
        assertTrue(enemyResult.matched, "enemy preview signature should match enemy preview samples");
        assertFalse(
                MinimumBattlePageSignatures.match(
                        MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION,
                        enemyPreview
                ).matched,
                "formation signature must not match enemy preview"
        );

        MinimumBattlePageSignatures.Sampler formation = sampler(new float[][]{
                {0.620f, 0.220f, 28f, 103f, 183f},
                {0.700f, 0.220f, 227f, 227f, 227f},
                {0.920f, 0.220f, 227f, 227f, 227f},
                {0.560f, 0.360f, 29f, 105f, 188f},
                {0.620f, 0.500f, 227f, 227f, 227f}
        });
        MinimumBattlePageSignatures.Result formationResult = MinimumBattlePageSignatures.match(
                MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION,
                formation
        );
        assertTrue(formationResult.matched, "formation signature should match formation samples");
        assertFalse(
                MinimumBattlePageSignatures.match(
                        MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW,
                        formation
                ).matched,
                "enemy preview signature must not match formation"
        );
    }

    private static void enemyPreviewSignatureToleratesDimmedTitleText() {
        MinimumBattlePageSignatures.Sampler enemyPreview = sampler(new float[][]{
                {0.050f, 0.080f, 163f, 164f, 166f},
                {0.735f, 0.920f, 103f, 10f, 13f},
                {0.840f, 0.920f, 193f, 173f, 42f},
                {0.930f, 0.920f, 176f, 156f, 39f}
        });
        MinimumBattlePageSignatures.Result enemyResult = MinimumBattlePageSignatures.match(
                MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW,
                enemyPreview
        );
        assertTrue(enemyResult.matched, "enemy preview should match red/yellow button samples when title is dimmed");
    }

    private static void fleetSupplyPromptIsNightPromptAndDefaultsToAbandon() {
        MinimumBattlePageSignatures.Sampler supplyPrompt = sampler(new float[][]{
                {0.330f, 0.360f, 237f, 237f, 237f},
                {0.500f, 0.470f, 237f, 237f, 237f},
                {0.350f, 0.630f, 161f, 2f, 2f},
                {0.650f, 0.650f, 50f, 151f, 246f},
                {0.500f, 0.550f, 64f, 161f, 109f}
        });
        MinimumBattlePageSignatures.Result supplyResult = MinimumBattlePageSignatures.match(
                MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT,
                supplyPrompt
        );
        assertTrue(supplyResult.matched, "supply-status prompt should be recognized as the night prompt variant");
        assertEquals(
                "night_prompt",
                MinimumBattleAdvancePolicy.promptPhaseId(MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT)
        );
        assertFalse(
                MinimumBattleAdvancePolicy.isUnsupportedPrompt(
                        MinimumBattleAdvancePolicy.Page.FLEET_SUPPLY_PROMPT
                ),
                "supply-status prompt is the night battle choice, not an unsupported blocker"
        );
        assertEquals(
                "night_no",
                MinimumBattleAdvancePolicy.nightPromptAction(false)
        );
        assertNear(0.339f, MinimumBattleTapTargets.NIGHT_YES_X, 0.0001f);
        assertNear(0.648f, MinimumBattleTapTargets.NIGHT_YES_Y, 0.0001f);
        assertNear(0.641f, MinimumBattleTapTargets.NIGHT_NO_X, 0.0001f);
        assertNear(0.648f, MinimumBattleTapTargets.NIGHT_NO_Y, 0.0001f);
        assertFalse(
                MinimumBattlePageSignatures.match(
                        MinimumBattleAdvancePolicy.Page.ENEMY_PREVIEW,
                        supplyPrompt
                ).matched,
                "enemy preview signature must not match the night prompt"
        );
    }

    private static void fastResultPageSignatureMatchesCurrentGameResult() {
        MinimumBattlePageSignatures.Sampler resultPage = sampler(new float[][]{
                {0.545f, 0.092f, 255f, 255f, 255f},
                {0.916f, 0.915f, 255f, 255f, 255f},
                {0.620f, 0.389f, 226f, 182f, 66f},
                {0.700f, 0.389f, 230f, 188f, 68f},
                {0.200f, 0.265f, 46f, 174f, 66f},
                {0.500f, 0.500f, 15f, 18f, 22f}
        });
        MinimumBattlePageSignatures.Result result = MinimumBattlePageSignatures.match(
                MinimumBattleAdvancePolicy.Page.COMBAT_RESULT,
                resultPage
        );
        assertTrue(result.matched, "fast 1-1 battle result page should be recognized after formation");
        assertFalse(
                MinimumBattlePageSignatures.match(
                        MinimumBattleAdvancePolicy.Page.FORMATION_SELECTION,
                        resultPage
                ).matched,
                "formation signature must not match a completed result page"
        );
    }

    private static void resultPhaseHasPixelFallbackForFastBattles() {
        assertTrue(
                NativeCombatPhase.RESULT.hasTemplateSignature(),
                "result phase should keep original template recognizer"
        );
        assertTrue(
                NativeCombatPhase.RESULT.hasPixelSignature(),
                "result phase needs a pixel fallback for fast battles that skip directly to results"
        );
        assertEquals(
                "combat_result_page",
                NativePhasePixelSignatures.signatureName(NativePhasePixelSignatures.Kind.RESULT_PAGE)
        );
    }

    private static void resultPixelFallbackIsSingleResultPhase() {
        assertEquals(
                1,
                NativePhasePixelSignatures.signatureVariantCount(NativePhasePixelSignatures.Kind.RESULT_PAGE)
        );
        assertEquals(
                "combat_result_summary",
                NativePhasePixelSignatures.testSignature(NativePhasePixelSignatures.Kind.RESULT_PAGE, 0).name
        );
    }

    private static void resultPixelFallbackRunsBeforeTemplateTimeout() {
        assertEquals(
                "pixel,template",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.RESULT)
        );
        assertEquals(
                "template",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.SPOT_ENEMY_SUCCESS)
        );
        assertEquals(
                "pixel",
                NativeCombatRecognizer.signatureScanOrder(NativeCombatPhase.FORMATION)
        );
    }

    private static void normalSortieTargetBuildsStablePaths() {
        NormalSortieTarget target = NormalSortieTarget.of(2, 4);
        assertEquals(2, target.chapter);
        assertEquals(4, target.map);
        assertEquals("2-4", target.label());
        assertEquals("autowsgr/data/map/normal/2-4.yaml", target.mapAssetPath());
        assertEquals("autowsgr/data/plan/normal_fight/2-4.yaml", target.exactPlanAssetPath());
        assertContains(target.runnerSummary(), "target=2-4");
        assertContains(target.runnerSummary(), "map=autowsgr/data/map/normal/2-4.yaml");
    }

    private static void normalMapCatalogGroupsNormalMapsByChapter() {
        NormalMapCatalog catalog = NormalMapCatalog.fromAssetPaths(Arrays.asList(
                "autowsgr/data/map/normal/1-1.yaml",
                "autowsgr/data/map/normal/1-2.yaml",
                "autowsgr/data/map/normal/2-4.yaml",
                "autowsgr/data/map/normal/10-1.yaml",
                "autowsgr/data/map/event/E1/1-1.yaml",
                "autowsgr/data/plan/normal_fight/2-4.yaml"
        ));
        assertEquals(3, catalog.chapterCount());
        assertEquals(2, catalog.mapsForChapter(1).size());
        assertEquals("1-2", catalog.mapsForChapter(1).get(1).label());
        assertTrue(catalog.contains(2, 4), "catalog should expose 2-4 from normal map assets");
        assertTrue(catalog.contains(10, 1), "catalog should support chapter 10 normal maps");
        assertFalse(catalog.contains(3, 1), "catalog should not invent maps missing from assets");
        assertEquals("2-4, maps=1", catalog.chapterSummary(2));
    }

    private static void selectedNormalPlanUsesExactPlanWhenAvailable() {
        try {
            NormalSortieTarget target = NormalSortieTarget.of(2, 4);
            MapNodeData map = MapNodeData.parseText(
                    target.mapAssetPath(),
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [C]\n"
                            + "C:\n"
                            + "  position: [300, 240]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveNormalTarget(
                    target,
                    "chapter: 2\n"
                            + "map: 4\n"
                            + "selected_nodes: [C]\n"
                            + "node_defaults:\n"
                            + "  formation: 3\n"
                            + "  night: true\n"
                            + "node_args:\n"
                            + "  C:\n"
                            + "    proceed: false\n",
                    map
            );
            assertEquals(target.exactPlanAssetPath(), policy.path);
            assertEquals("2", policy.chapter);
            assertEquals("4", policy.mapId);
            assertEquals("C", policy.selectedNodes.get(0));
            assertEquals(
                    "formation_3",
                    policy.decisionForPhase(NativeCombatPhase.FORMATION, 0).actionId
            );
            assertEquals(
                    "night_yes",
                    policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId
            );
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void generatedNormalPlanUsesFirstStartNextNodeConservatively() {
        try {
            NormalSortieTarget target = NormalSortieTarget.of(3, 1);
            MapNodeData map = MapNodeData.parseText(
                    target.mapAssetPath(),
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [B, A]\n"
                            + "B:\n"
                            + "  position: [300, 240]\n"
                            + "A:\n"
                            + "  position: [220, 240]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveNormalTarget(target, null, map);
            assertEquals(target.generatedPlanPath(), policy.path);
            assertEquals("generated-3-1", policy.name);
            assertEquals("normal", policy.mode);
            assertEquals("3", policy.chapter);
            assertEquals("1", policy.mapId);
            assertEquals(1, policy.fleetId);
            assertEquals(4, policy.fightCondition);
            assertEquals("B", policy.selectedNodes.get(0));
            assertEquals(
                    "night_no",
                    policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId
            );
            assertEquals(
                    "proceed_no",
                    policy.decisionForPhase(NativeCombatPhase.PROCEED, 0).actionId
            );
            assertContains(policy.toRunnerSummary(), "map=3-1");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void normalMapNavigationTapPlanNormalizesThenSelectsTarget() {
        List<MinimumNormalBattleNavigator.TapAction> actions =
                MinimumNormalBattleNavigator.tapPlanForTarget(NormalSortieTarget.of(4, 3));
        assertEquals(13, actions.size());
        assertEquals("map-sortie-panel", actions.get(0).name);
        assertEquals("chapter-reset--3", actions.get(1).name);
        assertNear(0.100f, actions.get(1).gameX, 0.0001f);
        assertNear(0.196f, actions.get(1).gameY, 0.0001f);
        assertEquals("chapter-step+3", actions.get(5).name);
        assertNear(0.916f, actions.get(5).gameY, 0.0001f);
        assertEquals("map-prev-reset", actions.get(6).name);
        assertEquals("map-prev-reset", actions.get(10).name);
        assertEquals("map-next", actions.get(11).name);
        assertEquals("map-next", actions.get(12).name);

        List<MinimumNormalBattleNavigator.TapAction> enterActions =
                MinimumNormalBattleNavigator.enterSelectedMapPlan(NormalSortieTarget.of(4, 3));
        assertEquals(1, enterActions.size());
        assertEquals("enter-sortie-4-3", enterActions.get(0).name);
        assertNear(0.625f, enterActions.get(0).gameX, 0.0001f);
        assertNear(0.556f, enterActions.get(0).gameY, 0.0001f);
    }

    private static void runnerSummariesUseSelectedNormalPolicy() {
        try {
            NormalSortieTarget target = NormalSortieTarget.of(5, 2);
            MapNodeData map = MapNodeData.parseText(
                    target.mapAssetPath(),
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [A]\n"
                            + "A:\n"
                            + "  position: [300, 240]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveNormalTarget(target, null, map);
            String guarded = GuardedCombatRunner.policySummaryForRunner(null, policy);
            assertContains(guarded, "planPolicy=generated-5-2");
            assertContains(guarded, "map=5-2");

            String minimum = MinimumNormalBattleRunner.startupTargetSummary(target, policy);
            assertContains(minimum, "target=5-2");
            assertContains(minimum, "plan=autowsgr/data/plan/normal_fight/generated-5-2.yaml");
            assertContains(minimum, "fleet=1");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void configPlanPathResolvesExactOriginalPlan() {
        try {
            NormalSortieRunConfig config = NormalSortieRunConfig.parse(
                    "/storage/emulated/0/Android/data/org.openwsgr.autowsgrmobile/files/config/normal-sortie.yaml",
                    "plan_path: autowsgr/data/plan/normal_fight/9-2ADGHM.yaml\n"
            );
            MapNodeData map = MapNodeData.parseText(
                    "autowsgr/data/map/normal/9-2.yaml",
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [A]\n"
                            + "A:\n"
                            + "  position: [200, 180]\n"
                            + "D:\n"
                            + "  position: [300, 220]\n"
                            + "G:\n"
                            + "  position: [420, 260]\n"
                            + "H:\n"
                            + "  position: [540, 300]\n"
                            + "M:\n"
                            + "  position: [660, 340]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveRunConfig(
                    config,
                    "chapter: 9\n"
                            + "map: 2\n"
                            + "selected_nodes: [A, D, G, H, M]\n"
                            + "node_args:\n"
                            + "  M:\n"
                            + "    proceed: false\n",
                    map
            );
            assertTrue(config.hasPlanPath(), "plan_path config should be marked as referenced-plan mode");
            assertEquals("autowsgr/data/plan/normal_fight/9-2ADGHM.yaml", policy.path);
            assertEquals("9-2ADGHM", policy.name);
            assertEquals("9", policy.chapter);
            assertEquals("2", policy.mapId);
            assertEquals("9-2", policy.normalTarget().label());
            assertFalse(
                    policy.path.endsWith("/9-2.yaml"),
                    "config plan_path must not be replaced by chapter-map filename guessing"
            );
            assertEquals("M", policy.selectedNodes.get(policy.selectedNodes.size() - 1));
            assertEquals(
                    "proceed_no",
                    policy.decisionForPhase(NativeCombatPhase.PROCEED, policy.routeSize() - 1).actionId
            );
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void inlineConfigResolvesNormalPlanWithoutGuessing() {
        try {
            String inlinePlan = "chapter: 3\n"
                    + "map: 4\n"
                    + "fleet_id: 2\n"
                    + "selected_nodes: [B]\n"
                    + "node_args:\n"
                    + "  B:\n"
                    + "    formation: 5\n"
                    + "    night: true\n"
                    + "    proceed: false\n";
            NormalSortieRunConfig config = NormalSortieRunConfig.parse(
                    "external/config/inline-normal-sortie.yaml",
                    inlinePlan
            );
            MapNodeData map = MapNodeData.parseText(
                    "autowsgr/data/map/normal/3-4.yaml",
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [B]\n"
                            + "B:\n"
                            + "  position: [320, 260]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveRunConfig(config, null, map);
            assertFalse(config.hasPlanPath(), "inline config should not require an asset plan path");
            assertEquals("external/config/inline-normal-sortie.yaml", policy.path);
            assertEquals("3-4", policy.normalTarget().label());
            assertEquals(2, policy.fleetId);
            assertEquals("formation_5", policy.decisionForPhase(NativeCombatPhase.FORMATION, 0).actionId);
            assertEquals("night_yes", policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId);
            assertEquals("proceed_no", policy.decisionForPhase(NativeCombatPhase.PROCEED, 0).actionId);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void invalidConfigWithoutPlanOrTargetIsRejected() {
        try {
            NormalSortieRunConfig.parse(
                    "external/config/bad-normal-sortie.yaml",
                    "fleet_id: 1\n"
                            + "selected_nodes: [A]\n"
            );
            throw new AssertionError("config without plan_path or explicit chapter/map should be rejected");
        } catch (java.io.IOException e) {
            assertContains(e.getMessage(), "plan_path or inline chapter/map");
        }
    }

    private static void defaultSortieConfigTextPointsAtSafePlan() {
        try {
            String text = NormalSortieConfigStore.defaultConfigText(NormalSortieTarget.of(1, 1));
            assertContains(text, "plan_path: autowsgr/data/plan/normal_fight/1-1.yaml");
            NormalSortieRunConfig config = NormalSortieRunConfig.parse("normal-sortie.yaml", text);
            assertTrue(config.hasPlanPath(), "default config should reference an original plan asset");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void runnerSummariesUseLoadedConfigAndResolvedPolicy() {
        try {
            NormalSortieRunConfig config = NormalSortieRunConfig.parse(
                    "/storage/emulated/0/Android/data/org.openwsgr.autowsgrmobile/files/config/normal-sortie.yaml",
                    "plan_path: autowsgr/data/plan/normal_fight/9-2ADGHM.yaml\n"
            );
            MapNodeData map = MapNodeData.parseText(
                    "autowsgr/data/map/normal/9-2.yaml",
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [A]\n"
                            + "A:\n"
                            + "  position: [200, 180]\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.resolveRunConfig(
                    config,
                    "chapter: 9\n"
                            + "map: 2\n"
                            + "fleet_id: 3\n"
                            + "selected_nodes: [A]\n"
                            + "node_args:\n"
                            + "  A:\n"
                            + "    proceed: false\n",
                    map
            );
            String minimum = MinimumNormalBattleRunner.startupConfigSummary(config, policy);
            assertContains(minimum, "config=/storage/emulated/0/Android/data/org.openwsgr.autowsgrmobile/files/config/normal-sortie.yaml");
            assertContains(minimum, "target=9-2");
            assertContains(minimum, "plan=autowsgr/data/plan/normal_fight/9-2ADGHM.yaml");
            assertContains(minimum, "fleet=3");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void mapTitleParserMatchesOriginalSortieOcrFormats() {
        MinimumMapTargetVerifier.TitleIdentity compact =
                MinimumMapTargetVerifier.parseTitle("9-2西南群岛");
        assertEquals("9-2", compact.label());

        MinimumMapTargetVerifier.TitleIdentity spaced =
                MinimumMapTargetVerifier.parseTitle("9 - 2 / 西南群岛");
        assertEquals("9-2", spaced.label());

        MinimumMapTargetVerifier.TitleIdentity glued =
                MinimumMapTargetVerifier.parseTitle("9-21西南群岛");
        assertEquals(
                "9-2",
                glued.label(),
                "title parser should mirror original OCR correction where a name digit sticks after map id"
        );

        assertEquals(null, MinimumMapTargetVerifier.parseTitle("西南群岛"));
    }

    private static void mapTargetVerifierConfirmsMatchingTitleAndBlocksMismatch() {
        try {
            NormalSortieTarget target = NormalSortieTarget.of(9, 2);
            CombatPlanPolicy policy = CombatPlanPolicy.parseText(
                    "autowsgr/data/plan/normal_fight/9-2ADGHM.yaml",
                    "chapter: 9\n"
                            + "map: 2\n"
                            + "selected_nodes: [A, D]\n"
                            + "node_args:\n"
                            + "  A:\n"
                            + "    proceed: true\n"
                            + "  D:\n"
                            + "    proceed: false\n"
            );
            MapNodeData map = MapNodeData.parseText(
                    target.mapAssetPath(),
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [A]\n"
                            + "A:\n"
                            + "  position: [200, 180]\n"
                            + "  next: [D]\n"
                            + "D:\n"
                            + "  position: [300, 220]\n"
            );
            MinimumMapTargetVerifier.Result confirmed = MinimumMapTargetVerifier.verify(
                    policy,
                    map,
                    MinimumMapTargetVerifier.TitleSignal.recognized("9-2西南群岛"),
                    true
            );
            assertTrue(confirmed.canProceed, confirmed.detail);
            assertTrue(confirmed.visualConfirmed, confirmed.detail);
            assertContains(confirmed.detail, "mapTargetVerification=confirmed");
            assertContains(confirmed.detail, "target=9-2");
            assertContains(confirmed.detail, "selectedCoverage=2/2");

            MinimumMapTargetVerifier.Result mismatch = MinimumMapTargetVerifier.verify(
                    policy,
                    map,
                    MinimumMapTargetVerifier.TitleSignal.recognized("9-1西南群岛"),
                    true
            );
            assertFalse(mismatch.canProceed, mismatch.detail);
            assertContains(mismatch.detail, "mapTargetVerification=mismatch");
            assertContains(mismatch.detail, "recognized=9-1");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void mapTargetVerifierAllowsConfigVerifiedMvpWhenOcrUnavailable() {
        try {
            CombatPlanPolicy policy = CombatPlanPolicy.parseText(
                    "autowsgr/data/plan/normal_fight/1-1.yaml",
                    "chapter: 1\n"
                            + "map: 1\n"
                            + "selected_nodes: [A]\n"
                            + "node_args:\n"
                            + "  A:\n"
                            + "    proceed: false\n"
            );
            MapNodeData map = MapNodeData.parseText(
                    "autowsgr/data/map/normal/1-1.yaml",
                    "0:\n"
                            + "  position: [100, 100]\n"
                            + "  next: [A]\n"
                            + "A:\n"
                            + "  position: [200, 180]\n"
            );
            MinimumMapTargetVerifier.Result result = MinimumMapTargetVerifier.verify(
                    policy,
                    map,
                    MinimumMapTargetVerifier.TitleSignal.unavailable("android map-title OCR backend unavailable"),
                    true
            );
            assertTrue(result.canProceed, result.detail);
            assertFalse(result.visualConfirmed, result.detail);
            assertContains(result.detail, "mapTargetVerification=config-verified");
            assertContains(result.detail, "visualConfirmed=false");
            assertContains(result.detail, "ocr=unavailable");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void expeditionSignaturesMatchOriginalProbes() {
        assertNear(0.9719f, ExpeditionCollectionSignatures.MAIN_READY_X, 0.0001f);
        assertNear(0.8407f, ExpeditionCollectionSignatures.MAIN_READY_Y, 0.0001f);
        assertNear(0.4953f, ExpeditionCollectionSignatures.MAP_NOTIFICATION_X, 0.0001f);
        assertNear(0.0213f, ExpeditionCollectionSignatures.MAP_NOTIFICATION_Y, 0.0001f);
        assertEquals(4, ExpeditionCollectionSignatures.READY_SLOT_COUNT);
        assertNear(0.8516f, ExpeditionCollectionSignatures.readySlotX(0), 0.0001f);
        assertNear(0.2736f, ExpeditionCollectionSignatures.readySlotY(0), 0.0001f);
        assertNear(0.8547f, ExpeditionCollectionSignatures.readySlotX(3), 0.0001f);
        assertNear(0.8694f, ExpeditionCollectionSignatures.readySlotY(3), 0.0001f);
    }

    private static void expeditionNotificationDetectorsUseSourceColors() {
        ExpeditionCollectionSignatures.Sampler sampler = new ExpeditionCollectionSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - ExpeditionCollectionSignatures.MAIN_READY_X) < 0.0001f
                        && Math.abs(normalizedY - ExpeditionCollectionSignatures.MAIN_READY_Y) < 0.0001f) {
                    return rgb(255, 89, 45);
                }
                if (Math.abs(normalizedX - ExpeditionCollectionSignatures.MAP_NOTIFICATION_X) < 0.0001f
                        && Math.abs(normalizedY - ExpeditionCollectionSignatures.MAP_NOTIFICATION_Y) < 0.0001f) {
                    return rgb(245, 88, 47);
                }
                return rgb(0, 0, 0);
            }
        };
        assertTrue(ExpeditionCollectionSignatures.hasMainReadyNotification(sampler), "main ready notification should match original red probe");
        assertTrue(ExpeditionCollectionSignatures.hasMapExpeditionNotification(sampler), "map expedition notification should match original orange-red probe");
    }

    private static void expeditionReadySlotDetectorReturnsFirstYellowSlot() {
        ExpeditionCollectionSignatures.Sampler sampler = new ExpeditionCollectionSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - ExpeditionCollectionSignatures.readySlotX(1)) < 0.0001f
                        && Math.abs(normalizedY - ExpeditionCollectionSignatures.readySlotY(1)) < 0.0001f) {
                    return rgb(253, 228, 66);
                }
                if (Math.abs(normalizedX - ExpeditionCollectionSignatures.readySlotX(2)) < 0.0001f
                        && Math.abs(normalizedY - ExpeditionCollectionSignatures.readySlotY(2)) < 0.0001f) {
                    return rgb(253, 228, 66);
                }
                return rgb(38, 147, 250);
            }
        };
        assertEquals(1, ExpeditionCollectionSignatures.findReadySlot(sampler));
    }

    private static void mainPageOverlaySignaturesMatchOriginalDismissalCoords() {
        assertNear(0.0729f, MainPageOverlaySignatures.NEWS_NOT_SHOW_X, 0.0001f);
        assertNear(0.8981f, MainPageOverlaySignatures.NEWS_NOT_SHOW_Y, 0.0001f);
        assertNear(0.0313f, MainPageOverlaySignatures.NEWS_CLOSE_X, 0.0001f);
        assertNear(0.0556f, MainPageOverlaySignatures.NEWS_CLOSE_Y, 0.0001f);
        assertNear(0.4938f, MainPageOverlaySignatures.SIGN_CONFIRM_X, 0.0001f);
        assertNear(0.6611f, MainPageOverlaySignatures.SIGN_CONFIRM_Y, 0.0001f);
        assertNear(0.6180f, MainPageOverlaySignatures.BOOKING_X, 0.0001f);
        assertNear(0.5640f, MainPageOverlaySignatures.BOOKING_Y, 0.0001f);
        assertEquals(4, MainPageOverlaySignatures.ruleCount(MainPageOverlaySignatures.Kind.NEWS));
        assertEquals(2, MainPageOverlaySignatures.ruleCount(MainPageOverlaySignatures.Kind.NEWS_NOT_SHOW));
        assertEquals(
                "[news-not-show, news-close]",
                MainPageOverlayNavigator.dismissPlan(
                        MainPageOverlaySignatures.Kind.NEWS,
                        false
                ).toString()
        );
        assertEquals(
                "[news-close]",
                MainPageOverlayNavigator.dismissPlan(
                        MainPageOverlaySignatures.Kind.NEWS,
                        true
                ).toString()
        );
    }

    private static void mainPageOverlayDetectorMatchesNewsAndCheckbox() {
        MainPageOverlaySignatures.Sampler sampler = new MainPageOverlaySignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - 0.6523f) < 0.0001f
                        && Math.abs(normalizedY - 0.8292f) < 0.0001f) {
                    return rgb(148, 88, 86);
                }
                if (Math.abs(normalizedX - 0.7008f) < 0.0001f
                        && Math.abs(normalizedY - 0.8306f) < 0.0001f) {
                    return rgb(119, 67, 62);
                }
                if (Math.abs(normalizedX - 0.7844f) < 0.0001f
                        && Math.abs(normalizedY - 0.8278f) < 0.0001f) {
                    return rgb(135, 79, 73);
                }
                if (Math.abs(normalizedX - 0.8516f) < 0.0001f
                        && Math.abs(normalizedY - 0.8278f) < 0.0001f) {
                    return rgb(126, 72, 65);
                }
                if (Math.abs(normalizedX - 0.0714f) < 0.0001f
                        && Math.abs(normalizedY - 0.9065f) < 0.0001f) {
                    return rgb(49, 130, 211);
                }
                if (Math.abs(normalizedX - 0.0620f) < 0.0001f
                        && Math.abs(normalizedY - 0.9130f) < 0.0001f) {
                    return rgb(52, 130, 205);
                }
                return rgb(0, 0, 0);
            }
        };

        assertEquals(MainPageOverlaySignatures.Kind.NEWS, MainPageOverlaySignatures.detect(sampler));
        assertTrue(
                MainPageOverlaySignatures.isNewsNotShowChecked(sampler),
                "news checkbox should match original NEWS_NOT_SHOW blue probes"
        );
    }

    private static void mainPageOverlayDetectorMatchesCurrentNewsVariantAndCheckbox() {
        MainPageOverlaySignatures.Sampler sampler = new MainPageOverlaySignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - 0.1800f) < 0.0001f
                        && Math.abs(normalizedY - 0.7790f) < 0.0001f) {
                    return rgb(176, 114, 109);
                }
                if (Math.abs(normalizedX - 0.4200f) < 0.0001f
                        && Math.abs(normalizedY - 0.7790f) < 0.0001f) {
                    return rgb(167, 108, 103);
                }
                if (Math.abs(normalizedX - 0.7200f) < 0.0001f
                        && Math.abs(normalizedY - 0.7790f) < 0.0001f) {
                    return rgb(121, 68, 67);
                }
                if (Math.abs(normalizedX - 0.9000f) < 0.0001f
                        && Math.abs(normalizedY - 0.7790f) < 0.0001f) {
                    return rgb(149, 92, 90);
                }
                if (Math.abs(normalizedX - 0.0586f) < 0.0001f
                        && Math.abs(normalizedY - 0.9179f) < 0.0001f) {
                    return rgb(54, 129, 201);
                }
                if (Math.abs(normalizedX - 0.0665f) < 0.0001f
                        && Math.abs(normalizedY - 0.9040f) < 0.0001f) {
                    return rgb(54, 129, 201);
                }
                return rgb(0, 0, 0);
            }
        };

        assertEquals(MainPageOverlaySignatures.Kind.NEWS, MainPageOverlaySignatures.detect(sampler));
        assertTrue(
                MainPageOverlaySignatures.isNewsNotShowChecked(sampler),
                "current announcement checkbox should be recognized as already checked"
        );
    }

    private static void mainPageSignDismissPlanMatchesOriginalConfirmOperation() {
        assertNear(0.5000f, MainPageOverlaySignatures.SIGN_REWARD_CONFIRM_X, 0.0001f);
        assertNear(0.5680f, MainPageOverlaySignatures.SIGN_REWARD_CONFIRM_Y, 0.0001f);
        assertEquals(
                "[sign-confirm, sign-reward-confirm]",
                MainPageOverlayNavigator.dismissPlan(
                        MainPageOverlaySignatures.Kind.SIGN,
                        false
                ).toString()
        );
    }

    private static void expeditionLogPathsUseSeparatePrefix() {
        File base = new File("base-dir");
        File log = MinimumRunLogStore.logFile(base, "auto-expedition", "run:1/2");
        File failure = MinimumRunLogStore.failureScreenshotFile(base, "auto-expedition", "run:1/2");
        assertContains(log.getPath().replace('\\', '/'), "runs/auto-expedition-run-1-2.log");
        assertContains(failure.getPath().replace('\\', '/'), "failures/auto-expedition-run-1-2.png");

        File normalLog = MinimumRunLogStore.logFile(base, "run:1/2");
        assertContains(normalLog.getPath().replace('\\', '/'), "runs/minimum-normal-run-1-2.log");
    }

    private static void autoExpeditionConfigDefaultsToOriginalInterval() {
        AutoExpeditionConfig parsed = AutoExpeditionConfig.parseText("interval_seconds: 120\n");
        assertEquals(120L, parsed.intervalSeconds);
        assertTrue(parsed.enabled(), "positive interval should enable automatic expedition");

        AutoExpeditionConfig defaults = AutoExpeditionConfig.parseText("");
        assertEquals(900L, defaults.intervalSeconds);
        assertTrue(defaults.enabled(), "default interval should be the original scheduler 900 seconds");

        AutoExpeditionConfig disabled = AutoExpeditionConfig.parseText("interval_seconds: 0\n");
        assertFalse(disabled.enabled(), "zero interval should disable automatic expedition");
        assertContains(AutoExpeditionConfig.defaultConfigText(), "interval_seconds: 900");
    }

    private static void autoExpeditionSchedulerPolicyAttemptsImmediatelyAndThenWaits() {
        AutoExpeditionSchedulerPolicy.Decision first = AutoExpeditionSchedulerPolicy.decide(
                false,
                false,
                1000L,
                0L,
                900L
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.ATTEMPT, first.action);
        assertContains(first.detail, "first-pass");

        AutoExpeditionSchedulerPolicy.Decision wait = AutoExpeditionSchedulerPolicy.decide(
                true,
                false,
                1000L + 200000L,
                1000L,
                900L
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.WAIT, wait.action);
        assertEquals(700000L, wait.waitMs);

        AutoExpeditionSchedulerPolicy.Decision due = AutoExpeditionSchedulerPolicy.decide(
                true,
                false,
                1000L + 900000L,
                1000L,
                900L
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.ATTEMPT, due.action);
        assertContains(due.detail, "interval-due");
    }

    private static void autoExpeditionSchedulerPolicyDisablesNonPositiveIntervalsAndAvoidsOverlap() {
        AutoExpeditionSchedulerPolicy.Decision disabled = AutoExpeditionSchedulerPolicy.decide(
                false,
                false,
                1000L,
                0L,
                0L
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.DISABLED, disabled.action);

        AutoExpeditionSchedulerPolicy.Decision overlap = AutoExpeditionSchedulerPolicy.decide(
                true,
                true,
                1000L + 900000L,
                1000L,
                900L
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.WAIT, overlap.action);
        assertContains(overlap.detail, "pass-running");
    }

    private static void autoExpeditionSchedulerPolicyDisablesOverflowIntervals() {
        AutoExpeditionSchedulerPolicy.Decision result = AutoExpeditionSchedulerPolicy.decide(
                true,
                false,
                1000L,
                0L,
                Long.MAX_VALUE
        );
        assertEquals(AutoExpeditionSchedulerPolicy.Action.DISABLED, result.action);
        assertContains(result.detail, "interval-overflow");
    }

    private static void autoExpeditionConfigReportsMalformedInterval() {
        try {
            AutoExpeditionConfig.parseText("interval_seconds: abc\n");
            throw new AssertionError("malformed interval should fail");
        } catch (IllegalArgumentException e) {
            assertContains(e.getMessage(), "interval_seconds");
        }
    }

    private static void expeditionNavigatorTapPlanMatchesOriginalTargets() {
        ExpeditionCollectionNavigator.TapAction tab = ExpeditionCollectionNavigator.expeditionPanelTap();
        assertEquals("expedition-panel", tab.name);
        assertNear(0.4422f, tab.gameX, 0.0001f);
        assertNear(0.0514f, tab.gameY, 0.0001f);

        ExpeditionCollectionNavigator.TapAction slot = ExpeditionCollectionNavigator.readySlotTap(2);
        assertEquals("expedition-slot-2", slot.name);
        assertNear(0.8539f, slot.gameX, 0.0001f);
        assertNear(0.6667f, slot.gameY, 0.0001f);

        ExpeditionCollectionNavigator.TapAction skip = ExpeditionCollectionNavigator.skipResultTap();
        assertEquals("expedition-skip", skip.name);
        assertNear(0.76f, skip.gameX, 0.0001f);
        assertNear(0.5f, skip.gameY, 0.0001f);
    }

    private static void expeditionRunnerSummaryMentionsCollectOnlySemantics() {
        String summary = ExpeditionCollectionRunner.startupSummary(AutoExpeditionConfig.parseText("interval_seconds: 900\n"));
        assertContains(summary, "interval_seconds=900");
        assertContains(summary, "collect_only=false");
        assertContains(summary, "redispatch=true");
    }

    private static void expeditionNavigatorRedispatchConfirmTargetsLeftButton() {
        ExpeditionCollectionNavigator.TapAction confirm = ExpeditionCollectionNavigator.redispatchConfirmTap();
        assertEquals("expedition-redispatch-confirm", confirm.name);
        assertNear(0.381f, confirm.gameX, 0.0001f);
        assertNear(0.638f, confirm.gameY, 0.0001f);
    }

    private static void expeditionRunnerWaitFailurePrefersCancelledOverBlocked() {
        assertEquals("cancelled", ExpeditionCollectionRunner.waitFailureOutcome(true, "fresh-frame-timeout"));
        assertEquals("fresh-frame-timeout", ExpeditionCollectionRunner.waitFailureOutcome(false, "fresh-frame-timeout"));
    }

    private static void expeditionRunnerReturnHomeOutcomeDoesNotReportSuccessAfterCancelOrTapFailure() {
        assertEquals("cancelled", ExpeditionCollectionRunner.returnHomeOutcome(true, true, false));
        assertEquals("tap-return-home-failed", ExpeditionCollectionRunner.returnHomeOutcome(false, true, false));
        assertEquals("success", ExpeditionCollectionRunner.returnHomeOutcome(false, true, true));
    }

    private static void expeditionRunnerUsesSessionOwnedFinalization() {
        String source = productionSource("ExpeditionCollectionRunner.java");
        assertContains(source, "private final AutomationSession session;");
        assertNotContains(source, "shouldRunFinishSideEffects");
        assertNotContains(source, "private boolean finished;");
    }

    private static void autoExpeditionSchedulerSummaryUsesOriginalInterval() {
        String summary = AutoExpeditionSchedulerRunner.startupSummary(AutoExpeditionConfig.parseText(""));
        assertContains(summary, "interval_seconds=900");
        assertContains(summary, "app_scoped=true");
        assertContains(summary, "collect_only=false");
    }

    private static void autoExpeditionSchedulerBoundsPassWaits() {
        AutoExpeditionSchedulerRunner.PassWaitDecision decision = AutoExpeditionSchedulerRunner.passWaitDecision(
                true,
                1000L,
                1000L + AutoExpeditionSchedulerRunner.MAX_PASS_WAIT_MS + 1L
        );
        assertEquals(AutoExpeditionSchedulerRunner.PassWaitAction.TIMEOUT, decision.action);
        assertContains(decision.detail, "pass-timeout");
    }

    private static void autoExpeditionSchedulerForwardsPassFinalAsRunningUntilSchedulerStops() {
        AutomationUpdate passFinal = AutomationUpdate.terminal(
                "expedition",
                "pass-1",
                AutomationOutcome.SUCCESS,
                "自动远征：完成",
                "collected=0",
                "nested-pass.log",
                "nested-pass.png"
        );
        AutomationUpdate forwarded = AutoExpeditionSchedulerRunner.forwardedPassUpdate(
                passFinal,
                "scheduler-1",
                "scheduler.log"
        );
        assertEquals(AutomationOutcome.RUNNING, forwarded.outcome);
        assertContains(forwarded.message, "自动远征：等待下次检查");
        assertContains(forwarded.detail, "自动远征：完成");
        assertEquals("nested-pass.log", forwarded.logPath);
        assertEquals("nested-pass.png", forwarded.screenshotPath);
    }

    private static void autoExpeditionSchedulerWaitAndStopPreserveLatestEvidencePaths() {
        String source = productionSource("AutoExpeditionSchedulerRunner.java");
        assertContains(source, "private final AutomationSession session;");
        assertContains(source, "AndroidAutomationSessionSink.create(appContext, \"auto-expedition\", runId)");
        assertNotContains(source, "latestLogPath");
        assertNotContains(source, "latestScreenshotPath");
    }

    private static void autoExpeditionUsesRuntimeGlobalGate() {
        String source = productionSource("MainActivity.java");
        assertContains(source, "runtime.start(flow)");
        assertNotContains(source, "shouldBlockNewFlowForAutoExpedition");
    }

    private static void expeditionSlotReadyVerificationHelperMatchesFindReadySlot() {
        ExpeditionCollectionSignatures.Sampler sampler = new ExpeditionCollectionSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - ExpeditionCollectionSignatures.readySlotX(2)) < 0.0001f
                        && Math.abs(normalizedY - ExpeditionCollectionSignatures.readySlotY(2)) < 0.0001f) {
                    return rgb(253, 228, 66);
                }
                return rgb(38, 147, 250);
            }
        };
        assertEquals(2, ExpeditionCollectionSignatures.findReadySlot(sampler));
        assertTrue(
                ExpeditionCollectionSignatures.slotReady(sampler, 2),
                "slotReady should agree with findReadySlot for the completed slot"
        );
        assertFalse(
                ExpeditionCollectionSignatures.slotReady(sampler, 0),
                "slotReady should report a non-completed slot as not ready"
        );
        assertFalse(
                ExpeditionCollectionSignatures.slotReady(sampler, 9),
                "slotReady should reject out-of-range slot indices"
        );
    }

    private static void expeditionReadySlotDetectorToleratesSmallSpatialDrift() {
        final float driftX = ExpeditionCollectionSignatures.readySlotX(1) + 0.004f;
        final float driftY = ExpeditionCollectionSignatures.readySlotY(1) + 0.004f;
        ExpeditionCollectionSignatures.Sampler sampler = new ExpeditionCollectionSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                if (Math.abs(normalizedX - driftX) < 0.0001f
                        && Math.abs(normalizedY - driftY) < 0.0001f) {
                    return rgb(253, 228, 66);
                }
                return rgb(38, 147, 250);
            }
        };
        assertEquals(
                1,
                ExpeditionCollectionSignatures.findReadySlot(sampler),
                "neighbor sampling should tolerate a small calibration drift off the exact probe"
        );
    }

    private static void campaignCatalogMapsNamesToOriginalIndexAndDifficulty() {
        CampaignCatalog.Target hardSub = CampaignCatalog.parse("困难潜艇");
        assertEquals(5, hardSub.mapIndex);
        assertEquals("hard", hardSub.difficulty);
        CampaignCatalog.Target easyCarrier = CampaignCatalog.parse("简单航母");
        assertEquals(4, easyCarrier.mapIndex);
        assertEquals("easy", easyCarrier.difficulty);
        assertEquals(1, CampaignCatalog.parse("困难驱逐").mapIndex);
        assertEquals("困难潜艇", hardSub.label());
        try {
            CampaignCatalog.parse("无敌战列");
            throw new AssertionError("unknown campaign name should fail");
        } catch (IllegalArgumentException e) {
            assertContains(e.getMessage(), "battle_type");
        }
    }

    private static void campaignSignaturesMatchOriginalProbes() {
        assertNear(0.5750f, CampaignSignatures.BATTLE_PANEL_X, 0.0001f);
        assertNear(0.0458f, CampaignSignatures.BATTLE_PANEL_Y, 0.0001f);
        assertNear(0.800f, CampaignSignatures.CLICK_DIFFICULTY_X, 0.0001f);
        assertNear(0.130f, CampaignSignatures.CLICK_DIFFICULTY_Y, 0.0001f);
        assertEquals(5, CampaignSignatures.CAMPAIGN_COUNT);
        assertNear(0.17f, CampaignSignatures.campaignX(1), 0.0001f);
        assertNear(0.5f, CampaignSignatures.campaignY(1), 0.0001f);
        assertNear(0.85f, CampaignSignatures.campaignX(5), 0.0001f);
    }

    private static void campaignDifficultyRecognizerUsesSourceColors() {
        CampaignSignatures.Sampler blue = new CampaignSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                return rgb(29, 139, 234);
            }
        };
        CampaignSignatures.Sampler red = new CampaignSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                return rgb(141, 46, 52);
            }
        };
        assertEquals("hard", CampaignSignatures.recognizeDifficulty(blue));
        assertEquals("easy", CampaignSignatures.recognizeDifficulty(red));
        assertEquals(null, CampaignSignatures.recognizeDifficulty(new CampaignSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                return rgb(0, 0, 0);
            }
        }));
    }

    private static void campaignBattleConfigParsesAllFieldsAndDefaults() {
        CampaignBattleConfig defaults = CampaignBattleConfig.parseText("");
        assertEquals("困难潜艇", defaults.battleType);
        assertEquals(3, defaults.times);
        assertEquals(2, defaults.formation);
        assertTrue(defaults.night, "night should default to true");
        assertEquals("moderate", defaults.repairMode);

        CampaignBattleConfig parsed = CampaignBattleConfig.parseText(
                "battle_type: 简单航母\n"
                        + "times: 2\n"
                        + "formation: 轮型\n"
                        + "night: false\n"
                        + "repair_mode: severe\n"
        );
        assertEquals("简单航母", parsed.battleType);
        assertEquals(2, parsed.times);
        assertEquals(3, parsed.formation);
        assertFalse(parsed.night, "night: false should disable night battle");
        assertEquals("severe", parsed.repairMode);

        CampaignBattleConfig noRepair = CampaignBattleConfig.parseText("repair_mode: none\n");
        assertEquals("none", noRepair.repairMode);
        assertEquals(-1, noRepair.repairThreshold());
        assertEquals(1, CampaignBattleConfig.parseText("repair_mode: moderate\n").repairThreshold());
        assertEquals(2, parsed.repairThreshold());

        try {
            CampaignBattleConfig.parseText("battle_type: 无效战役\n");
            throw new AssertionError("invalid battle_type should fail");
        } catch (IllegalArgumentException e) {
            assertContains(e.getMessage(), "battle_type");
        }
        try {
            CampaignBattleConfig.parseText("formation: 9\n");
            throw new AssertionError("out-of-range formation should fail");
        } catch (IllegalArgumentException e) {
            assertContains(e.getMessage(), "formation");
        }
        try {
            CampaignBattleConfig.parseText("repair_mode: 随便\n");
            throw new AssertionError("invalid repair_mode should fail");
        } catch (IllegalArgumentException e) {
            assertContains(e.getMessage(), "repair_mode");
        }
    }

    private static void campaignBattleConfigBuildsSingleNodePlanWithFormationAndNight() {
        try {
            CampaignBattleConfig config = CampaignBattleConfig.parseText(
                    "battle_type: 困难航母\nformation: 4\nnight: true\n"
            );
            CombatPlanPolicy policy = CombatPlanPolicy.parseText(config.planPath(), config.toPlanText());
            assertEquals(1, policy.fleetId, "campaign uses current fleet (plan defaults fleet_id=1)");
            assertEquals("formation_4", policy.decisionForPhase(NativeCombatPhase.FORMATION, 0).actionId);
            assertEquals("night_yes", policy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId);

            CampaignBattleConfig noNight = CampaignBattleConfig.parseText(
                    "battle_type: 困难航母\nformation: 1\nnight: false\n"
            );
            CombatPlanPolicy noNightPolicy = CombatPlanPolicy.parseText(noNight.planPath(), noNight.toPlanText());
            assertEquals("formation_1", noNightPolicy.decisionForPhase(NativeCombatPhase.FORMATION, 0).actionId);
            assertEquals("night_no", noNightPolicy.decisionForPhase(NativeCombatPhase.NIGHT_PROMPT, 0).actionId);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void campaignNavigatorTapTargetsMatchOriginal() {
        CampaignBattleNavigator.TapAction panel = CampaignBattleNavigator.battlePanelTap();
        assertEquals("campaign-battle-panel", panel.name);
        assertNear(0.5750f, panel.gameX, 0.0001f);
        assertNear(0.0458f, panel.gameY, 0.0001f);

        CampaignBattleNavigator.TapAction toggle = CampaignBattleNavigator.difficultyToggleTap();
        assertNear(0.800f, toggle.gameX, 0.0001f);
        assertNear(0.130f, toggle.gameY, 0.0001f);

        CampaignBattleNavigator.TapAction campaign = CampaignBattleNavigator.campaignTap(5);
        assertEquals("campaign-select-5", campaign.name);
        assertNear(0.85f, campaign.gameX, 0.0001f);
        assertNear(0.5f, campaign.gameY, 0.0001f);
    }

    private static void digitTemplateRecognizerMatchesSyntheticDigits() {
        int[][] templates = new int[10][DigitTemplateRecognizer.CELL_SIZE];
        for (int d = 0; d < 10; d++) {
            for (int i = 0; i < DigitTemplateRecognizer.CELL_SIZE; i++) {
                templates[d][i] = (d * 37 + i * 13) & 0xff;
            }
        }
        // 精确匹配
        assertEquals(5, DigitTemplateRecognizer.recognizeDigit(templates[5].clone(), templates, 0.9));
        // 带噪声(±6)仍正确
        int[] noisy = templates[7].clone();
        for (int i = 0; i < noisy.length; i++) {
            int delta = (i % 2 == 0) ? 6 : -6;
            noisy[i] = Math.max(0, Math.min(255, noisy[i] + delta));
        }
        assertEquals(7, DigitTemplateRecognizer.recognizeDigit(noisy, templates, 0.9));
        // 均匀灰 → 未知
        int[] flat = new int[DigitTemplateRecognizer.CELL_SIZE];
        java.util.Arrays.fill(flat, 128);
        assertEquals(-1, DigitTemplateRecognizer.recognizeDigit(flat, templates, 0.9));
        // 尺寸不符 → 未知
        assertEquals(-1, DigitTemplateRecognizer.recognizeDigit(new int[]{1, 2, 3}, templates, 0.9));
        // 多位拼接
        int[][] cells = new int[][]{templates[1].clone(), templates[2].clone(), templates[3].clone()};
        assertEquals("123", DigitTemplateRecognizer.recognizeNumber(cells, templates, 0.9));
        assertEquals(123, DigitTemplateRecognizer.parseNumber("123"));
        assertEquals(-1, DigitTemplateRecognizer.parseNumber("1?3"));
        // 相似度边界
        assertTrue(DigitTemplateRecognizer.similarity(templates[0], templates[0]) >= 0.999,
                "identical grids should score ~1.0");
    }

    private static void migratedRunnersPublishTypedTerminalOutcomes() {
        String normal = productionSource("MinimumNormalBattleRunner.java");
        assertSessionMigration(normal, "normal", "minimum-normal");
        assertContains(normal, "AutomationOutcome.SUCCESS");
        assertContains(normal, "AutomationOutcome.DOCK_FULL");
        assertContains(normal, "AutomationOutcome.BLOCKED");
        assertContains(normal, "AutomationOutcome.CANCELLED");
        assertContains(normal, "AutomationOutcome.FAILED");

        String expedition = productionSource("ExpeditionCollectionRunner.java");
        assertSessionMigration(expedition, "expedition", "auto-expedition");
        assertContains(expedition, "AutomationOutcome.NO_ACTION");
        assertContains(expedition, "AutomationOutcome.SUCCESS");
        assertContains(expedition, "AutomationOutcome.BLOCKED");
        assertContains(expedition, "AutomationOutcome.CANCELLED");
        assertContains(expedition, "AutomationOutcome.FAILED");

        String scheduler = productionSource("AutoExpeditionSchedulerRunner.java");
        assertSessionMigration(scheduler, "expedition", "auto-expedition");
        assertContains(scheduler, "AutomationOutcome.NO_ACTION");
        assertContains(scheduler, "AutomationOutcome.CANCELLED");
        assertContains(scheduler, "AutomationOutcome.FAILED");

        String campaign = productionSource("CampaignBattleRunner.java");
        assertSessionMigration(campaign, "campaign", "campaign-battle");
        assertContains(campaign, "AutomationOutcome.SUCCESS");
        assertContains(campaign, "AutomationOutcome.EXHAUSTED");
        assertContains(campaign, "AutomationOutcome.DOCK_FULL");
        assertContains(campaign, "AutomationOutcome.BLOCKED");
        assertContains(campaign, "AutomationOutcome.CANCELLED");
        assertContains(campaign, "AutomationOutcome.FAILED");

        String guarded = productionSource("GuardedCombatRunner.java");
        assertSessionMigration(guarded, "guarded", "guarded-combat");
        assertContains(guarded, "AutomationOutcome.SUCCESS");
        assertContains(guarded, "AutomationOutcome.EXHAUSTED");
        assertContains(guarded, "AutomationOutcome.BLOCKED");
        assertContains(guarded, "AutomationOutcome.CANCELLED");
        assertContains(guarded, "AutomationOutcome.FAILED");
        assertNotContains(guarded, "status.contains(");
        assertNotContains(guarded, "stepperStatus.contains(");
    }

    private static void migratedRunnersDelegateControlAndCheckpointActions() {
        for (String name : new String[]{
                "MinimumNormalBattleRunner.java",
                "ExpeditionCollectionRunner.java",
                "AutoExpeditionSchedulerRunner.java",
                "CampaignBattleRunner.java",
                "GuardedCombatRunner.java"
        }) {
            String source = productionSource(name);
            assertContains(source, "void pause()");
            assertContains(source, "session.pause()");
            assertContains(source, "void resume()");
            assertContains(source, "session.resume()");
            assertContains(source, "session.cancel()");
            assertContains(source, "session.checkpoint()");
        }

        String stepper = productionSource("GuardedCombatStepper.java");
        assertContains(stepper, "enum StepOutcome");
        assertContains(stepper, "control.checkpoint()");
        assertContains(stepper, "void pause()");
        assertContains(stepper, "void resume()");
        assertContains(stepper, "control.cancel()");
    }

    private static void schedulerKeepsPassTerminalsRunningUntilSchedulerStops() {
        String source = productionSource("AutoExpeditionSchedulerRunner.java");
        assertContains(source, "AutomationUpdate forwardedPassUpdate(AutomationUpdate update");
        assertContains(source, "AutomationUpdate.progress(");
        assertContains(source, "if (update.outcome.terminal)");
        assertNotContains(source, "session.finish(update.outcome");
    }

    private static void mainActivityUsesApplicationRuntimeWithoutPairwiseFlowMatrix() {
        String source = productionSource("MainActivity.java");
        assertContains(source, "((AutoWsgrApplication) getApplication()).automationRuntime()");
        assertNotContains(source, "new AutomationCoordinator(");
        assertContains(source, "runtime.start(flow)");
        assertContains(source, "正在运行：");
        assertContains(source, "暂停当前流程");
        assertContains(source, "继续当前流程");
        assertContains(source, "runtime.pauseActive()");
        assertContains(source, "runtime.resumeActive()");
        assertContains(source, "runtime.cancelActive()");
        assertNotContains(source, "campaignBattleRunner != null && campaignBattleRunner.isRunning()");
        assertNotContains(source, "minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()");
        assertNotContains(source, "autoExpeditionSchedulerRunner != null && autoExpeditionSchedulerRunner.isRunning()");
        assertNotContains(source, "guardedCombatRunner != null && guardedCombatRunner.isRunning()");
        assertNotContains(source, "guardedCombatStepper != null && guardedCombatStepper.isRunning()");
        assertNotContains(source, "liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()");
        assertContains(source, "getApplicationContext()");
        for (String id : new String[]{"normal", "expedition", "campaign", "guarded", "stepper", "waiter"}) {
            assertContains(source, "\"" + id + "\"");
        }
    }

    private static void mainActivityLifecycleReplaysWithoutDestroyCancellation() {
        String source = productionSource("MainActivity.java");
        assertContains(source, "protected void onStart()");
        assertContains(source, "runtime.addListener(automationListener, true)");
        assertContains(source, "protected void onStop()");
        assertContains(source, "runtime.removeListener(automationListener)");
        String onDestroy = methodSource(source, "protected void onDestroy()");
        assertNotContains(onDestroy, "runtime.cancelActive()");
        assertContains(source, "update.message");
        assertContains(source, "update.detail");
        assertContains(source, "update.logPath");
        assertContains(source, "update.screenshotPath");
    }

    private static void assertSessionMigration(String source, String flowId, String logPrefix) {
        assertContains(source, "private final AutomationSession session;");
        assertContains(source, "new AutomationSession(\"" + flowId + "\"");
        assertContains(source, "AndroidAutomationSessionSink.create(appContext, \"" + logPrefix + "\", runId)");
        assertContains(source, "AutomationCallback callback");
        assertNotContains(source, "interface Callback");
        assertNotContains(source, "static final class Update");
    }

    private static String productionSource(String fileName) {
        File workingDirectory = new File(System.getProperty("user.dir", "."));
        File sourceDirectory = new File(
                workingDirectory,
                "app/src/main/java/org/openwsgr/autowsgrmobile"
        );
        if (!sourceDirectory.isDirectory()) {
            sourceDirectory = new File(
                    workingDirectory,
                    "src/main/java/org/openwsgr/autowsgrmobile"
            );
        }
        File file = new File(sourceDirectory, fileName);
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read source contract " + file, e);
        }
    }

    private static String methodSource(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("missing method: " + signature);
        }
        int nextOverride = source.indexOf("@Override", start + signature.length());
        return nextOverride < 0 ? source.substring(start) : source.substring(start, nextOverride);
    }

    private static MinimumBattlePageSignatures.Sampler sampler(final float[][] samples) {
        return new MinimumBattlePageSignatures.Sampler() {
            @Override
            public int sample(float normalizedX, float normalizedY) {
                for (float[] item : samples) {
                    if (Math.abs(item[0] - normalizedX) < 0.0001f
                            && Math.abs(item[1] - normalizedY) < 0.0001f) {
                        return rgb((int) item[2], (int) item[3], (int) item[4]);
                    }
                }
                return rgb(0, 0, 0);
            }
        };
    }

    private static int rgb(int r, int g, int b) {
        return (0xff << 24) | (r << 16) | (g << 8) | b;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ", expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertContains(String text, String expected) {
        if (text == null || !text.contains(expected)) {
            throw new AssertionError("missing: " + expected + "\n" + text);
        }
    }

    private static void assertNotContains(String text, String unexpected) {
        if (text != null && text.contains(unexpected)) {
            throw new AssertionError("unexpected: " + unexpected + "\n" + text);
        }
    }

    private static void assertNear(float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("expected near=" + expected + ", actual=" + actual);
        }
    }
}
