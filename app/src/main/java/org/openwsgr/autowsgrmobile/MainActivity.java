package org.openwsgr.autowsgrmobile;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MainActivity extends Activity {
    private static final int REQUEST_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private TextView captureStatus;
    private TextView accessibilityStatus;
    private TextView assetStatus;
    private TextView frameStatus;
    private TextView pixelStatus;
    private TextView runnerStatus;
    private TextView selfTestStatus;
    private TextView selfTestDetail;
    private TextView visionTestStatus;
    private TextView visionTestDetail;
    private TextView templateCatalogStatus;
    private TextView templateCatalogDetail;
    private TextView templateRecognitionStatus;
    private TextView templateRecognitionDetail;
    private TextView combatPhaseStatus;
    private TextView combatPhaseDetail;
    private TextView combatFlowStatus;
    private TextView combatFlowDetail;
    private TextView combatPlanPolicyStatus;
    private TextView combatPlanPolicyDetail;
    private TextView routeNodeStatus;
    private TextView routeNodeDetail;
    private TextView shipDamageStatus;
    private TextView shipDamageDetail;
    private TextView enemyCompositionStatus;
    private TextView enemyCompositionDetail;
    private TextView enemyFormationStatus;
    private TextView enemyFormationDetail;
    private TextView liveSpotDiagnosticsStatus;
    private TextView liveSpotDiagnosticsDetail;
    private TextView liveCombatFlowStatus;
    private TextView liveCombatFlowDetail;
    private TextView liveCombatWaitStatus;
    private TextView liveCombatWaitDetail;
    private TextView guardedStepperStatus;
    private TextView guardedStepperDetail;
    private TextView guardedRunnerStatus;
    private TextView guardedRunnerDetail;
    private String lastLiveSpotDiagnosticsStatus = "";
    private String lastLiveSpotDiagnosticsDetail = "";
    private TextView minimumRunStatus;
    private TextView minimumRunDetail;
    private TextView minimumRunLogPath;
    private TextView minimumRunScreenshotPath;
    private Spinner sortieChapterSpinner;
    private Spinner sortieMapSpinner;
    private TextView sortieTargetDetail;
    private NormalMapCatalog normalMapCatalog;
    private ConfigProfileRepository configProfileRepository;
    private ConfigProfileUiController configProfileUiController;
    private UnifiedAutomationConfig activeAutomationConfig;
    private String activeConfigProfileId = ConfigProfileRepository.BUILTIN_ID;
    private AutomationRuntime runtime;
    private final AutomationRuntime.Listener automationListener = new AutomationRuntime.Listener() {
        @Override public void onAutomationUpdate(AutomationUpdate update) {
            renderAutomationUpdate(update);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runtime = ((AutoWsgrApplication) getApplication()).automationRuntime();
        File externalDirectory = getExternalFilesDir(null);
        File baseDirectory = externalDirectory == null ? getFilesDir() : externalDirectory;
        File configsDirectory = new File(baseDirectory, "configs");
        configProfileRepository = new ConfigProfileRepository(
                configsDirectory,
                DefaultUnifiedConfig.text(),
                new FileActiveProfileStore(new File(configsDirectory, "active-profile.txt")),
                new CurrentConfigProfileValidator()
        );
        Exception importError = null;
        try {
            LegacyConfigProfileImporter.importIfNeeded(
                    configProfileRepository,
                    new File(baseDirectory, "config")
            );
        } catch (IOException error) {
            importError = error;
        }
        configProfileUiController = new ConfigProfileUiController(
                this,
                configProfileRepository,
                new ConfigProfileUiController.Listener() {
                    @Override public void onDefaultProfileChanged(
                            ConfigProfile profile,
                            UnifiedAutomationConfig config
                    ) {
                        activeAutomationConfig = config;
                        activeConfigProfileId = profile.id;
                    }
                }
        );
        setContentView(buildUi());
        configProfileUiController.loadActive();
        if (importError != null) {
            configProfileUiController.showError(importError);
        }
        configProfileUiController.restoreInstanceState(savedInstanceState);
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        configProfileUiController.saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        runtime.addListener(automationListener, true);
    }

    @Override
    protected void onStop() {
        runtime.removeListener(automationListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenCaptureService.class);
                service.setAction(ScreenCaptureService.ACTION_START);
                service.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                service.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
                captureStatus.setText("屏幕捕获：已授权，服务已启动");
            } else {
                captureStatus.setText("屏幕捕获：未授权");
            }
        }
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF7F9F9);
        scrollView.addView(root);

        TextView title = text("AutoWSGR 移动端", 28, 0xFF1B2A2F, true);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = text(
                "安卓端控制面板。请先授权屏幕捕获并启用无障碍服务，再进行索敌诊断、校准记录复制和受保护作战执行。",
                15,
                0xFF455A64,
                false
        );
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        TextView configHeading = text("配置档案", 18, 0xFF263238, true);
        configHeading.setPadding(0, dp(8), 0, dp(6));
        root.addView(configHeading);
        root.addView(configProfileUiController.buildView());

        captureStatus = statusText();
        accessibilityStatus = statusText();
        assetStatus = statusText();
        frameStatus = statusText();
        pixelStatus = statusText();
        runnerStatus = statusText();
        selfTestStatus = statusText();
        selfTestDetail = statusText();
        visionTestStatus = statusText();
        visionTestDetail = statusText();
        templateCatalogStatus = statusText();
        templateCatalogDetail = statusText();
        templateRecognitionStatus = statusText();
        templateRecognitionDetail = statusText();
        combatPhaseStatus = statusText();
        combatPhaseDetail = statusText();
        combatFlowStatus = statusText();
        combatFlowDetail = statusText();
        combatPlanPolicyStatus = statusText();
        combatPlanPolicyDetail = statusText();
        routeNodeStatus = statusText();
        routeNodeDetail = statusText();
        shipDamageStatus = statusText();
        shipDamageDetail = statusText();
        enemyCompositionStatus = statusText();
        enemyCompositionDetail = statusText();
        enemyFormationStatus = statusText();
        enemyFormationDetail = statusText();
        liveSpotDiagnosticsStatus = statusText();
        liveSpotDiagnosticsDetail = statusText();
        liveCombatFlowStatus = statusText();
        liveCombatFlowDetail = statusText();
        liveCombatWaitStatus = statusText();
        liveCombatWaitDetail = statusText();
        guardedStepperStatus = statusText();
        guardedStepperDetail = statusText();
        guardedRunnerStatus = statusText();
        guardedRunnerDetail = statusText();
        minimumRunStatus = statusText();
        minimumRunDetail = statusText();
        minimumRunLogPath = statusText();
        minimumRunScreenshotPath = statusText();
        root.addView(captureStatus);
        root.addView(accessibilityStatus);
        root.addView(frameStatus);
        root.addView(runnerStatus);
        root.addView(combatPlanPolicyStatus);
        root.addView(liveSpotDiagnosticsStatus);
        root.addView(guardedRunnerStatus);
        root.addView(minimumRunStatus);
        addNormalSortieTargetControls(root);

        root.addView(button("授权屏幕捕获", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestScreenCapture();
            }
        }));
        root.addView(button("打开无障碍设置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAccessibilitySettings();
            }
        }));
        root.addView(button("停止屏幕捕获", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopCaptureService();
            }
        }));
        root.addView(button("刷新状态", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshStatus();
            }
        }));
        root.addView(button("运行索敌画面诊断", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runLiveSpotDiagnostics();
            }
        }));
        root.addView(button("复制索敌校准记录", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyLiveSpotDiagnosticsRecord();
            }
        }));
        root.addView(button("分析剪贴板校准记录", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                analyzeClipboardCalibrationRecord();
            }
        }));
        root.addView(button("运行作战策略自检", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runCombatPlanPolicyProbe();
            }
        }));
        root.addView(button("运行受保护作战一步", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runGuardedRunnerOneStep();
            }
        }));
        root.addView(button("运行最小普通战", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runMinimumNormalBattle();
            }
        }));
        root.addView(button("自动收取远征", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runAutoExpeditionScheduler();
            }
        }));
        root.addView(button("战役", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runCampaignBattle();
            }
        }));
        root.addView(button("停止当前流程", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelCurrentFlow();
            }
        }));
        root.addView(button("暂停当前流程", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!runtime.pauseActive()) {
                    minimumRunStatus.setText("当前流程不支持暂停或没有运行中的流程");
                }
            }
        }));
        root.addView(button("继续当前流程", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!runtime.resumeActive()) {
                    minimumRunStatus.setText("当前流程不支持继续或没有运行中的流程");
                }
            }
        }));

        root.addView(liveSpotDiagnosticsDetail);
        root.addView(combatPlanPolicyDetail);
        root.addView(guardedRunnerDetail);
        root.addView(minimumRunDetail);
        root.addView(minimumRunLogPath);
        root.addView(minimumRunScreenshotPath);
        return scrollView;
    }

    private TextView statusText() {
        TextView view = text("", 14, 0xFF263238, false);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private void addSection(LinearLayout root, String heading, Iterable<String> items) {
        TextView headingView = text(heading, 18, 0xFF263238, true);
        headingView.setPadding(0, dp(18), 0, dp(6));
        root.addView(headingView);
        for (String item : items) {
            TextView row = text("- " + item, 14, 0xFF37474F, false);
            row.setPadding(0, dp(2), 0, dp(2));
            root.addView(row);
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void addNormalSortieTargetControls(LinearLayout root) {
        normalMapCatalog = loadNormalMapCatalog();
        TextView heading = text("普通图出征", 16, 0xFF263238, true);
        heading.setPadding(0, dp(12), 0, dp(4));
        root.addView(heading);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        sortieChapterSpinner = new Spinner(this);
        sortieMapSpinner = new Spinner(this);
        LinearLayout.LayoutParams chapterParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        chapterParams.setMargins(0, 0, dp(8), 0);
        row.addView(sortieChapterSpinner, chapterParams);
        row.addView(sortieMapSpinner, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        root.addView(row);

        sortieTargetDetail = statusText();
        root.addView(sortieTargetDetail);

        String[] chapters = normalMapCatalog.chapterLabels();
        if (chapters.length == 0) {
            chapters = new String[]{"1"};
        }
        sortieChapterSpinner.setAdapter(spinnerAdapter(chapters));
        sortieChapterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bindMapSpinnerForSelectedChapter();
                updateSortieTargetDetail();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateSortieTargetDetail();
            }
        });
        bindMapSpinnerForSelectedChapter();
        sortieMapSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSortieTargetDetail();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateSortieTargetDetail();
            }
        });
        updateSortieTargetDetail();
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void bindMapSpinnerForSelectedChapter() {
        if (sortieMapSpinner == null) {
            return;
        }
        int chapter = selectedChapter();
        String[] maps = normalMapCatalog.mapLabelsForChapter(chapter);
        if (maps.length == 0) {
            maps = new String[]{"1"};
        }
        sortieMapSpinner.setAdapter(spinnerAdapter(maps));
    }

    private void updateSortieTargetDetail() {
        if (sortieTargetDetail == null) {
            return;
        }
        NormalSortieTarget target = selectedNormalSortieTarget();
        sortieTargetDetail.setText("普通图：" + target.label()
                + "，地图：" + target.mapAssetPath());
    }

    private NormalSortieTarget selectedNormalSortieTarget() {
        int chapter = selectedChapter();
        int map = selectedMap();
        if (!normalMapCatalog.contains(chapter, map)) {
            return normalMapCatalog.defaultTarget();
        }
        return NormalSortieTarget.of(chapter, map);
    }

    private int selectedChapter() {
        return parseSpinnerInt(sortieChapterSpinner, normalMapCatalog.defaultTarget().chapter);
    }

    private int selectedMap() {
        return parseSpinnerInt(sortieMapSpinner, normalMapCatalog.defaultTarget().map);
    }

    private int parseSpinnerInt(Spinner spinner, int fallback) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(spinner.getSelectedItem()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private NormalMapCatalog loadNormalMapCatalog() {
        try {
            NormalMapCatalog catalog = NormalMapCatalog.fromAssetIndexText(readAssetText("asset-index.txt"));
            if (catalog.chapterCount() > 0) {
                return catalog;
            }
        } catch (IOException ignored) {
        }
        return NormalMapCatalog.fromAssetPaths(Arrays.asList("autowsgr/data/map/normal/1-1.yaml"));
    }

    private String readAssetText(String path) throws IOException {
        InputStream stream = getAssets().open(path);
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        } finally {
            stream.close();
        }
    }

    private void refreshStatus() {
        ScreenFrameStore.Snapshot snapshot = ScreenFrameStore.snapshot();
        captureStatus.setText(snapshot.hasFrame()
                ? "屏幕捕获：已授权，正在接收画面"
                : "屏幕捕获：等待授权");
        accessibilityStatus.setText("无障碍触控："
                + (isAccessibilityEnabled() ? "已启用" : "未启用"));
        assetStatus.setText(AssetInventory.load(this).toStatusLine());
        frameStatus.setText("画面帧：" + snapshot.toStatusLine());
        pixelStatus.setText(AndroidControllerBridge.centerPixelStatus());
        runnerStatus.setText("运行状态：核心诊断、策略自检和受保护一步执行已就绪；高级调试入口已隐藏");
        if (TextUtils.isEmpty(selfTestStatus.getText())) {
            selfTestStatus.setText("Runtime self-test: not run");
        }
        if (TextUtils.isEmpty(visionTestStatus.getText())) {
            visionTestStatus.setText("Vision self-test: not run");
        }
        if (TextUtils.isEmpty(templateCatalogStatus.getText())) {
            templateCatalogStatus.setText("Template catalog: not run");
        }
        if (TextUtils.isEmpty(templateRecognitionStatus.getText())) {
            templateRecognitionStatus.setText("Template recognition self-test: not run");
        }
        if (TextUtils.isEmpty(combatPhaseStatus.getText())) {
            combatPhaseStatus.setText("Combat phase self-test: not run");
        }
        if (TextUtils.isEmpty(combatFlowStatus.getText())) {
            combatFlowStatus.setText("Combat flow self-test: not run");
        }
        if (TextUtils.isEmpty(combatPlanPolicyStatus.getText())) {
            combatPlanPolicyStatus.setText("作战策略：未运行");
        }
        if (TextUtils.isEmpty(routeNodeStatus.getText())) {
            routeNodeStatus.setText("Route node probe: not run");
        }
        if (TextUtils.isEmpty(shipDamageStatus.getText())) {
            shipDamageStatus.setText("Ship damage probe: not run");
        }
        if (TextUtils.isEmpty(enemyCompositionStatus.getText())) {
            enemyCompositionStatus.setText("Enemy composition probe: not run");
        }
        if (TextUtils.isEmpty(enemyFormationStatus.getText())) {
            enemyFormationStatus.setText("Enemy formation probe: not run");
        }
        if (TextUtils.isEmpty(liveSpotDiagnosticsStatus.getText())) {
            liveSpotDiagnosticsStatus.setText("索敌诊断：未运行");
        }
        if (TextUtils.isEmpty(liveCombatFlowStatus.getText())) {
            liveCombatFlowStatus.setText("Live combat flow probe: not run");
        }
        if (TextUtils.isEmpty(liveCombatWaitStatus.getText())) {
            liveCombatWaitStatus.setText("Live combat wait: not run");
        }
        if (TextUtils.isEmpty(guardedStepperStatus.getText())) {
            guardedStepperStatus.setText("Guarded stepper: not run");
        }
        if (TextUtils.isEmpty(guardedRunnerStatus.getText())) {
            guardedRunnerStatus.setText("受保护作战：未运行");
        }
        if (TextUtils.isEmpty(minimumRunStatus.getText())) {
            minimumRunStatus.setText("最小普通战：未运行");
        }
        if (TextUtils.isEmpty(minimumRunLogPath.getText())) {
            minimumRunLogPath.setText("运行日志：未生成");
        }
        if (TextUtils.isEmpty(minimumRunScreenshotPath.getText())) {
            minimumRunScreenshotPath.setText("失败截图：未生成");
        }
    }

    private void runRuntimeSelfTest() {
        RuntimeSelfTest.Result result = RuntimeSelfTest.run(this);
        selfTestStatus.setText(result.toStatusLine());
        selfTestDetail.setText(result.detail);
    }

    private void runVisionSelfTest() {
        VisionSelfTest.Result result = VisionSelfTest.run(this);
        visionTestStatus.setText(result.toStatusLine());
        visionTestDetail.setText(result.detail);
    }

    private void runTemplateCatalogProbe() {
        TemplateCatalogProbe.Result result = TemplateCatalogProbe.run(this);
        templateCatalogStatus.setText(result.status);
        templateCatalogDetail.setText(result.detail);
    }

    private void runTemplateRecognitionSelfTest() {
        TemplateRecognitionSelfTest.Result result = TemplateRecognitionSelfTest.run(this);
        templateRecognitionStatus.setText(result.toStatusLine());
        templateRecognitionDetail.setText(result.detail);
    }

    private void runCombatPhaseSelfTest() {
        CombatPhaseSelfTest.Result result = CombatPhaseSelfTest.run(this);
        combatPhaseStatus.setText(result.toStatusLine());
        combatPhaseDetail.setText(result.detail);
    }

    private void runCombatFlowSelfTest() {
        CombatFlowSelfTest.Result result = CombatFlowSelfTest.run(this);
        combatFlowStatus.setText(result.toStatusLine());
        combatFlowDetail.setText(result.detail);
    }

    private void runCombatPlanPolicyProbe() {
        CombatPlanPolicy.ProbeResult result = CombatPlanPolicy.probe(this);
        combatPlanPolicyStatus.setText(result.ok ? "作战策略：通过" : "作战策略：失败");
        combatPlanPolicyDetail.setText(result.detail);
    }

    private void runRouteNodeProbe() {
        RouteNodeProbe.Result result = RouteNodeProbe.run(this);
        routeNodeStatus.setText(result.toStatusLine());
        routeNodeDetail.setText(result.detail);
    }

    private void runShipDamageProbe() {
        ShipDamageRecognizer.ProbeResult result = ShipDamageRecognizer.probe();
        shipDamageStatus.setText(result.toStatusLine());
        shipDamageDetail.setText(result.detail);
    }

    private void runEnemyCompositionProbe() {
        EnemyCompositionRecognizer.ProbeResult result = EnemyCompositionRecognizer.probe(this);
        enemyCompositionStatus.setText(result.toStatusLine());
        enemyCompositionDetail.setText(result.detail);
    }

    private void runEnemyFormationProbe() {
        EnemyFormationRecognizer.ProbeResult result = EnemyFormationRecognizer.probe(this);
        enemyFormationStatus.setText(result.toStatusLine());
        enemyFormationDetail.setText(result.detail);
    }

    private void runLiveSpotDiagnostics() {
        LiveSpotDiagnostics.Result result = LiveSpotDiagnostics.run();
        setLiveSpotDiagnosticsResult(result);
    }

    private void runLiveSpotDiagnosticsSelfTest() {
        LiveSpotDiagnostics.Result result = LiveSpotDiagnostics.probe();
        setLiveSpotDiagnosticsResult(result);
    }

    private void setLiveSpotDiagnosticsResult(LiveSpotDiagnostics.Result result) {
        lastLiveSpotDiagnosticsStatus = result.status;
        lastLiveSpotDiagnosticsDetail = result.detail;
        liveSpotDiagnosticsStatus.setText(zhLiveSpotStatus(result.status));
        liveSpotDiagnosticsDetail.setText(result.detail);
    }

    private void copyLiveSpotDiagnosticsRecord() {
        if (TextUtils.isEmpty(lastLiveSpotDiagnosticsDetail)) {
            liveSpotDiagnosticsStatus.setText("索敌校准记录：未生成");
            liveSpotDiagnosticsDetail.setText("clipboardRecord=unavailable, reason=no diagnostics detail");
            return;
        }
        String record = LiveSpotDiagnostics.calibrationRecord(
                lastLiveSpotDiagnosticsStatus,
                lastLiveSpotDiagnosticsDetail
        );
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            liveSpotDiagnosticsStatus.setText("索敌校准记录：不可用");
            liveSpotDiagnosticsDetail.setText("clipboardRecord=unavailable, reason=clipboard service missing");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoWSGR live spot diagnostics", record));
        liveSpotDiagnosticsStatus.setText("索敌校准记录：已复制");
        liveSpotDiagnosticsDetail.setText("clipboardRecord=v1, copied=true, chars="
                + record.length() + "\n" + record);
    }

    private void analyzeClipboardCalibrationRecord() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            liveSpotDiagnosticsStatus.setText("校准记录分析：不可用");
            liveSpotDiagnosticsDetail.setText("calibrationImport=unavailable, reason=clipboard service missing");
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            liveSpotDiagnosticsStatus.setText("校准记录分析：不可用");
            liveSpotDiagnosticsDetail.setText("calibrationImport=unavailable, reason=clipboard empty");
            return;
        }
        CharSequence clipboardText = clip.getItemAt(0).coerceToText(this);
        CalibrationRecordImportAnalyzer.Result result =
                CalibrationRecordImportAnalyzer.analyze(clipboardText);
        if (result.recordCount == 0) {
            liveSpotDiagnosticsStatus.setText("校准记录分析：不可用");
        } else if (result.ready) {
            liveSpotDiagnosticsStatus.setText("校准记录分析：可预览候选补丁");
        } else {
            liveSpotDiagnosticsStatus.setText("校准记录分析：需要更多真实样本");
        }
        liveSpotDiagnosticsDetail.setText(result.detail);
    }

    private void runLiveCombatFlowProbe() {
        LiveCombatFlowProbe.Result result = LiveCombatFlowProbe.run(this);
        liveCombatFlowStatus.setText(result.toStatusLine());
        liveCombatFlowDetail.setText(result.detail);
    }

    private void startSpotFightWait() {
        String runId = diagnosticRunId();
        startLiveCombatWait(LiveCombatFlowWaiter.mapSpotFight(
                getApplicationContext(), liveCombatWaitCallback(runtime, runId)
        ));
    }

    private void startMapStartWait() {
        String runId = diagnosticRunId();
        startLiveCombatWait(LiveCombatFlowWaiter.mapStart(
                getApplicationContext(), liveCombatWaitCallback(runtime, runId)
        ));
    }

    private void startResultWait() {
        String runId = diagnosticRunId();
        startLiveCombatWait(LiveCombatFlowWaiter.mapResult(
                getApplicationContext(), liveCombatWaitCallback(runtime, runId)
        ));
    }

    private void startProceedNoWait() {
        String runId = diagnosticRunId();
        startLiveCombatWait(LiveCombatFlowWaiter.mapProceedNo(
                getApplicationContext(), liveCombatWaitCallback(runtime, runId)
        ));
    }

    private void startLiveCombatWait(final LiveCombatFlowWaiter waiter) {
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "waiter", "作战等待", false,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return waiter.start(); }
                },
                null,
                null,
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { waiter.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return waiter.isRunning(); }
                }
        );
        startAutomationFlow(flow, liveCombatWaitStatus);
    }

    private static LiveCombatFlowWaiter.Callback liveCombatWaitCallback(
            final AutomationRuntime target,
            final String runId
    ) {
        return new LiveCombatFlowWaiter.Callback() {
            @Override public void onUpdate(LiveCombatFlowWaiter.Update update) {
                String message = update.toStatusLine();
                if (update.running) {
                    target.onUpdate(AutomationUpdate.progress(
                            "waiter", runId, message, update.detail, "", ""
                    ));
                    return;
                }
                AutomationOutcome outcome;
                if (update.matched) {
                    outcome = AutomationOutcome.SUCCESS;
                } else if (update.cancelled) {
                    outcome = AutomationOutcome.CANCELLED;
                } else if (update.timedOut) {
                    outcome = AutomationOutcome.BLOCKED;
                } else if (update.failed) {
                    outcome = AutomationOutcome.FAILED;
                } else {
                    outcome = AutomationOutcome.BLOCKED;
                }
                target.onUpdate(AutomationUpdate.terminal(
                        "waiter", runId, outcome, message, update.detail, "", ""
                ));
            }
        };
    }

    private void runGuardedProceedNoDryRun() {
        startGuardedStepper(GuardedCombatStepper.proceedNoDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedProceedNoExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.proceedNoExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotFightDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotFightDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotFightExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotFightExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotRetreatDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotRetreatDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotRetreatExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotRetreatExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotDetourDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotDetourDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedSpotDetourExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotDetourExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedFightConditionAimDryRun() {
        startGuardedStepper(GuardedCombatStepper.fightConditionAimDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedFightConditionAimExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.fightConditionAimExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedFormationDryRun() {
        startGuardedStepper(GuardedCombatStepper.formationSingleColumnDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedFormationExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.formationSingleColumnExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedMissileSkipDryRun() {
        startGuardedStepper(GuardedCombatStepper.missileSkipDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedMissileSkipExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.missileSkipExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedNightNoDryRun() {
        startGuardedStepper(GuardedCombatStepper.nightNoDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedNightNoExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.nightNoExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedResultDryRun() {
        startGuardedStepper(GuardedCombatStepper.resultContinueDryRun(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void runGuardedResultExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.resultContinueExecuteGuarded(
                getApplicationContext(), guardedStepperCallback(runtime, diagnosticRunId())
        ));
    }

    private void startGuardedStepper(final GuardedCombatStepper stepper) {
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "stepper", "单步作战", true,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return stepper.start(); }
                },
                new RunnerAutomationFlow.Pause() {
                    @Override public void pause() { stepper.pause(); }
                },
                new RunnerAutomationFlow.Resume() {
                    @Override public void resume() { stepper.resume(); }
                },
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { stepper.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return stepper.isRunning(); }
                }
        );
        startAutomationFlow(flow, guardedStepperStatus);
    }

    private static GuardedCombatStepper.Callback guardedStepperCallback(
            final AutomationRuntime target,
            final String runId
    ) {
        return new GuardedCombatStepper.Callback() {
            @Override public void onUpdate(GuardedCombatStepper.Update update) {
                if (update.running()) {
                    target.onUpdate(AutomationUpdate.progress(
                            "stepper", runId, update.status, update.detail, "", ""
                    ));
                    return;
                }
                AutomationOutcome outcome;
                switch (update.outcome) {
                    case MATCHED:
                        outcome = AutomationOutcome.SUCCESS;
                        break;
                    case CANCELLED:
                        outcome = AutomationOutcome.CANCELLED;
                        break;
                    case FAILED:
                        outcome = AutomationOutcome.FAILED;
                        break;
                    case BLOCKED:
                    case TIMED_OUT:
                    case RUNNING:
                    default:
                        outcome = AutomationOutcome.BLOCKED;
                        break;
                }
                target.onUpdate(AutomationUpdate.terminal(
                        "stepper", runId, outcome, update.status, update.detail, "", ""
                ));
            }
        };
    }

    private void runGuardedRunnerOneStep() {
        startGuardedRunner(true);
    }

    private void runGuardedRunnerFiveStepLoop() {
        startGuardedRunner(false);
    }

    private void startGuardedRunner(boolean oneStep) {
        final GuardedCombatRunner runner = oneStep
                ? GuardedCombatRunner.oneStepExecuteGuarded(getApplicationContext(), runtime)
                : GuardedCombatRunner.boundedLoopExecuteGuarded(getApplicationContext(), runtime);
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "guarded", "受保护作战", true,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return runner.start(); }
                },
                new RunnerAutomationFlow.Pause() {
                    @Override public void pause() { runner.pause(); }
                },
                new RunnerAutomationFlow.Resume() {
                    @Override public void resume() { runner.resume(); }
                },
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { runner.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return runner.isRunning(); }
                }
        );
        startAutomationFlow(flow, guardedRunnerStatus);
    }

    private void runMinimumNormalBattle() {
        UnifiedAutomationConfig activeConfig = activeConfigOrBlockStart();
        if (activeConfig == null) {
            return;
        }
        final MinimumNormalBattleRunner runner = new MinimumNormalBattleRunner(
                getApplicationContext(), activeConfig.normalSortie, runtime
        );
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "normal", "最小普通战", true,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return runner.start(); }
                },
                new RunnerAutomationFlow.Pause() {
                    @Override public void pause() { runner.pause(); }
                },
                new RunnerAutomationFlow.Resume() {
                    @Override public void resume() { runner.resume(); }
                },
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { runner.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return runner.isRunning(); }
                }
        );
        minimumRunStatus.setText("最小普通战：启动中");
        minimumRunDetail.setText("默认配置：" + activeConfigProfileId);
        minimumRunLogPath.setText("运行日志：准备生成");
        minimumRunScreenshotPath.setText("失败截图：未生成");
        startAutomationFlow(flow, minimumRunStatus);
    }

    private void runAutoExpeditionScheduler() {
        UnifiedAutomationConfig activeConfig = activeConfigOrBlockStart();
        if (activeConfig == null) {
            return;
        }
        final AutoExpeditionSchedulerRunner runner = new AutoExpeditionSchedulerRunner(
                getApplicationContext(), activeConfig.autoExpedition, runtime
        );
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "expedition", "自动远征", true,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return runner.start(); }
                },
                new RunnerAutomationFlow.Pause() {
                    @Override public void pause() { runner.pause(); }
                },
                new RunnerAutomationFlow.Resume() {
                    @Override public void resume() { runner.resume(); }
                },
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { runner.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return runner.isRunning(); }
                }
        );
        minimumRunStatus.setText("自动远征：启动中");
        minimumRunDetail.setText("默认配置：" + activeConfigProfileId
                + "，远征间隔：" + activeConfig.autoExpedition.intervalSeconds + " 秒");
        minimumRunLogPath.setText("运行日志：准备生成");
        minimumRunScreenshotPath.setText("失败截图：未生成");
        startAutomationFlow(flow, minimumRunStatus);
    }

    private void cancelCurrentFlow() {
        if (runtime.cancelActive()) {
            minimumRunStatus.setText("已请求停止当前流程");
        } else {
            minimumRunStatus.setText("没有运行中的流程");
        }
    }

    private void runCampaignBattle() {
        UnifiedAutomationConfig activeConfig = activeConfigOrBlockStart();
        if (activeConfig == null) {
            return;
        }
        final CampaignBattleRunner runner = new CampaignBattleRunner(
                getApplicationContext(), activeConfig.campaign, runtime
        );
        RunnerAutomationFlow flow = new RunnerAutomationFlow(
                "campaign", "战役", true,
                new RunnerAutomationFlow.Start() {
                    @Override public boolean start() { return runner.start(); }
                },
                new RunnerAutomationFlow.Pause() {
                    @Override public void pause() { runner.pause(); }
                },
                new RunnerAutomationFlow.Resume() {
                    @Override public void resume() { runner.resume(); }
                },
                new RunnerAutomationFlow.Cancel() {
                    @Override public void cancel() { runner.cancel(); }
                },
                new RunnerAutomationFlow.Running() {
                    @Override public boolean isRunning() { return runner.isRunning(); }
                }
        );
        minimumRunStatus.setText("战役：启动中");
        minimumRunDetail.setText("默认配置：" + activeConfigProfileId
                + "，" + activeConfig.campaign.summary());
        minimumRunLogPath.setText("运行日志：准备生成");
        minimumRunScreenshotPath.setText("失败截图：未生成");
        startAutomationFlow(flow, minimumRunStatus);
    }

    private UnifiedAutomationConfig activeConfigOrBlockStart() {
        if (activeAutomationConfig != null) {
            return activeAutomationConfig;
        }
        minimumRunStatus.setText("默认配置不可用");
        String parserError = configProfileUiController == null
                ? "配置档案尚未加载"
                : configProfileUiController.lastError();
        minimumRunDetail.setText(TextUtils.isEmpty(parserError) ? "请检查配置档案" : parserError);
        return null;
    }

    private void startAutomationFlow(AutomationFlow flow, TextView flowStatus) {
        AutomationCoordinator.StartResult result = runtime.start(flow);
        if (result.started) {
            return;
        }
        String activeLabel = TextUtils.isEmpty(result.activeLabel) ? flow.label() : result.activeLabel;
        String rejection = "正在运行：" + activeLabel;
        minimumRunStatus.setText(rejection);
        if (flowStatus != null) {
            flowStatus.setText(rejection);
        }
    }

    private void renderAutomationUpdate(AutomationUpdate update) {
        if (update == null || minimumRunStatus == null) {
            return;
        }
        minimumRunStatus.setText(update.message);
        minimumRunDetail.setText(update.detail);
        minimumRunLogPath.setText("运行日志："
                + (TextUtils.isEmpty(update.logPath) ? "未生成" : update.logPath));
        minimumRunScreenshotPath.setText("失败截图："
                + (TextUtils.isEmpty(update.screenshotPath) ? "未生成" : update.screenshotPath));
        runnerStatus.setText("当前流程：" + update.flowId + "，结果：" + update.outcome.name());
        if ("guarded".equals(update.flowId)) {
            guardedRunnerStatus.setText(update.message);
            guardedRunnerDetail.setText(update.detail);
        } else if ("stepper".equals(update.flowId)) {
            guardedStepperStatus.setText(update.message);
            guardedStepperDetail.setText(update.detail);
        } else if ("waiter".equals(update.flowId)) {
            liveCombatWaitStatus.setText(update.message);
            liveCombatWaitDetail.setText(update.detail);
        }
    }

    private static String diagnosticRunId() {
        return String.valueOf(System.currentTimeMillis());
    }
    private String zhLiveSpotStatus(String status) {
        if (status == null) {
            return "索敌诊断：未知";
        }
        if (status.contains("no frame")) {
            return "索敌诊断：未发现画面";
        }
        if (status.contains("ready")) {
            return "索敌诊断：已就绪";
        }
        if (status.contains("sampled")) {
            return "索敌诊断：已采样";
        }
        if (status.contains("self-test: passed")) {
            return "索敌诊断自检：通过";
        }
        if (status.contains("self-test: failed")) {
            return "索敌诊断自检：失败";
        }
        return "索敌诊断：" + status;
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void stopCaptureService() {
        Intent service = new Intent(this, ScreenCaptureService.class);
        service.setAction(ScreenCaptureService.ACTION_STOP);
        startService(service);
        captureStatus.setText("屏幕捕获：已请求停止");
    }

    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        ComponentName expected = new ComponentName(this, AutoWsgrAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName current = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(current)) {
                return true;
            }
        }
        return false;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
