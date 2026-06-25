package org.openwsgr.autowsgrmobile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AutomationEngine {
    private AutomationEngine() {
    }

    static List<String> migratedCapabilities() {
        return Collections.unmodifiableList(Arrays.asList(
                "Android APK shell and launch surface",
                "MediaProjection based screen capture permission flow",
                "In-memory RGBA screen frame store for the future vision layer",
                "AccessibilityService based normalized tap/swipe bridge",
                "Foreground capture service lifecycle",
                "Bundled AutoWSGR YAML/image/bin data assets",
                "ZIP asset store that reads original AutoWSGR paths",
                "Runtime self-test for YAML loading, PNG decoding, and frame pixel sampling",
                "Native vision primitives: color distance, pixel signatures, and template matching",
                "Native template catalog probe for bundled PNG resources and combat TemplateKey coverage",
                "Native TemplateKey mapping and findAny template recognition self-test",
                "Native combat phase recognizer skeleton for template-backed battle states",
                "Pixel-signature combat phases for formation and event map page",
                "Native combat transition graph and flow recognizer self-test",
                "Live MediaProjection frame probe for a safe pixel-only combat flow lane",
                "YAML combat plan policy parser and probe for bundled normal/event fight plans",
                "Safe enemy rule parser/evaluator for YAML enemy_rules without runtime eval",
                "APK-local enemy ship-type prototype backend, calibrated fight/exercise slot sampler, enemy formation signature OCR hook backend, and policy probe hooks for future OCR/native recognizers",
                "Live spot diagnostics probe, sampled-frame self-test, copyable calibration records, v0.41 calibration tuning loop, and v0.42 in-memory calibration sample-set advice",
                "Fight-condition guarded action specs wired from YAML fight_condition into Android normalized taps",
                "Missile animation skip guarded action spec with original double-tap behavior",
                "Rule-triggered spot retreat/detour guarded action specs, including BYPASS template tap support",
                "Rule-selected formation carry from spot rules into the next guarded formation step",
                "Map node data parser and route-node probe for bundled normal/event map YAML",
                "Live route-node tracker that can sync accepted yellow-ship nodes into the guarded runner route cursor",
                "Selected-route deviation guard that stops the runner when a reliable route node is outside the YAML plan",
                "Plan-aware guarded runner route cursor for YAML-driven proceed, night, and formation action selection",
                "Proceed-stop damage policy evaluator with conservative unknown-damage handling",
                "Native ship damage recognizer for preparation and result-page blood bar probes",
                "Cancellable background live combat waits for spot, start, result, and proceed branches",
                "Guarded combat stepper dry-run and execute entries for proceed, spot, formation, night, and result phases",
                "Bounded guarded combat runner that scans the current phase, selects the matching safe action, and loops after matched waits",
                "Minimum runnable normal 1-1 flow with game launch, guarded state recognition, stop control, run logs, failure screenshots, and night-prompt choice handling",
                "Simplified Chinese main screen with user-facing capture, diagnostics, policy, and guarded runner controls",
                "AutoWSGR feature inventory and Android port boundary"
        ));
    }

    static List<String> originalFeatures() {
        return Collections.unmodifiableList(Arrays.asList(
                "Normal fight runner with YAML combat plans",
                "Campaign runner, exercise runner, event fight runner",
                "Decisive battle controller and fleet decision logic",
                "Expedition collection, repair, build, cook, destroy, reward collection",
                "Pixel signatures, OpenCV template matching, OCR based recognition",
                "Task scheduler plus FastAPI and WebSocket server"
        ));
    }

    static List<String> nativeRewriteQueue() {
        return Collections.unmodifiableList(Arrays.asList(
                "Replace adb/scrcpy controller with in-app MediaProjection frames",
                "Replace adb input commands with AccessibilityService gestures",
                "Load YAML combat plans and image templates from Android assets",
                "Expand native vision stack and optionally replace hot paths with OpenCV for Android",
                "Map AutoWSGR TemplateKey and page signatures onto native template catalog probes",
                "Use native findAny results to drive first Android combat phase recognizers",
                "Wire native combat flow recognizer to live MediaProjection frames and user actions",
                "Calibrate live ship damage recognizer against real game screenshots and feed accepted result-page snapshots into proceed_stop",
                "Calibrate live yellow-ship route node tracking against real movement screenshots and tune deviation guard thresholds",
                "Calibrate fight-condition guarded taps against real sortie screenshots before broad unattended use",
                "Calibrate missile animation skip timing against real missile-support screenshots",
                "Collect multiple exported real spot_enemy_success calibration records across sessions before changing the APK-local ship-type prototype classifier thresholds",
                "Collect multiple exported real formation ROI calibration records across sessions before replacing the signature OCR hook backend with ML Kit, Paddle Lite, or another real OCR backend",
                "Calibrate rule-selected formation carry against real spot-to-formation combat screenshots",
                "Replace OCR with ML Kit, Paddle Lite, or another embeddable OCR engine",
                "Port combat/ui/ops state machines from Python to Kotlin or embed a supported Python runtime",
                "Move YAML/image resources into Android assets with versioned update support",
                "Add user consent, pause controls, and foreground execution safeguards"
        ));
    }
}
