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
    private LiveCombatFlowWaiter liveCombatFlowWaiter;
    private boolean liveCombatWaitCancelRequested;
    private GuardedCombatStepper guardedCombatStepper;
    private GuardedCombatRunner guardedCombatRunner;
    private TextView minimumRunStatus;
    private TextView minimumRunDetail;
    private TextView minimumRunLogPath;
    private TextView minimumRunScreenshotPath;
    private Spinner sortieChapterSpinner;
    private Spinner sortieMapSpinner;
    private TextView sortieTargetDetail;
    private TextView sortieConfigStatus;
    private NormalMapCatalog normalMapCatalog;
    private MinimumNormalBattleRunner minimumNormalBattleRunner;
    private AutoExpeditionSchedulerRunner autoExpeditionSchedulerRunner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        cancelAutoExpeditionSchedulerSilently();
        cancelMinimumNormalRunnerSilently();
        cancelGuardedRunner();
        cancelLiveCombatWait();
        cancelGuardedStepper();
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
        root.addView(button("写入默认出征配置", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeDefaultSortieConfig();
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
        root.addView(button("停止当前流程", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelCurrentFlow();
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
        sortieConfigStatus = statusText();
        root.addView(sortieConfigStatus);

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
        refreshSortieConfigStatus();
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
        refreshSortieConfigStatus();
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
        startLiveCombatWait(LiveCombatFlowWaiter.mapSpotFight(this, liveCombatWaitCallback()));
    }

    private void startMapStartWait() {
        startLiveCombatWait(LiveCombatFlowWaiter.mapStart(this, liveCombatWaitCallback()));
    }

    private void startResultWait() {
        startLiveCombatWait(LiveCombatFlowWaiter.mapResult(this, liveCombatWaitCallback()));
    }

    private void startProceedNoWait() {
        startLiveCombatWait(LiveCombatFlowWaiter.mapProceedNo(this, liveCombatWaitCallback()));
    }

    private void startLiveCombatWait(LiveCombatFlowWaiter waiter) {
        if (isAutoExpeditionRunning()) {
            liveCombatWaitStatus.setText("等待任务：自动远征正在运行");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            liveCombatWaitStatus.setText("Live combat wait: already running");
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            liveCombatWaitStatus.setText("Live combat wait: blocked by active guarded runner");
            return;
        }
        liveCombatFlowWaiter = waiter;
        liveCombatWaitCancelRequested = false;
        liveCombatWaitStatus.setText("Live combat wait: starting");
        if (!liveCombatFlowWaiter.start()) {
            liveCombatWaitStatus.setText("Live combat wait: already running");
        }
    }

    private LiveCombatFlowWaiter.Callback liveCombatWaitCallback() {
        return new LiveCombatFlowWaiter.Callback() {
            @Override
            public void onUpdate(LiveCombatFlowWaiter.Update update) {
                if (liveCombatWaitCancelRequested && update.running) {
                    return;
                }
                if (liveCombatWaitCancelRequested && update.timedOut) {
                    liveCombatWaitStatus.setText("Live combat wait: cancelled after " + update.elapsedMs + "ms");
                    liveCombatWaitDetail.setText(update.detail + "\nuser cancellation requested");
                    liveCombatWaitCancelRequested = false;
                    liveCombatFlowWaiter = null;
                    return;
                }
                liveCombatWaitStatus.setText(update.toStatusLine());
                liveCombatWaitDetail.setText(update.detail);
                if (!update.running) {
                    liveCombatWaitCancelRequested = false;
                    liveCombatFlowWaiter = null;
                }
            }
        };
    }

    private void cancelLiveCombatWait() {
        if (liveCombatFlowWaiter == null) {
            return;
        }
        liveCombatWaitCancelRequested = true;
        liveCombatWaitStatus.setText("Live combat wait: cancelled by user");
        liveCombatWaitDetail.setText("cancel requested");
        liveCombatFlowWaiter.cancel();
    }

    private void runGuardedProceedNoDryRun() {
        startGuardedStepper(GuardedCombatStepper.proceedNoDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedProceedNoExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.proceedNoExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedSpotFightDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotFightDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedSpotFightExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotFightExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedSpotRetreatDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotRetreatDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedSpotRetreatExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotRetreatExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedSpotDetourDryRun() {
        startGuardedStepper(GuardedCombatStepper.spotDetourDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedSpotDetourExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.spotDetourExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedFightConditionAimDryRun() {
        startGuardedStepper(GuardedCombatStepper.fightConditionAimDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedFightConditionAimExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.fightConditionAimExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedFormationDryRun() {
        startGuardedStepper(GuardedCombatStepper.formationSingleColumnDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedFormationExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.formationSingleColumnExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedMissileSkipDryRun() {
        startGuardedStepper(GuardedCombatStepper.missileSkipDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedMissileSkipExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.missileSkipExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedNightNoDryRun() {
        startGuardedStepper(GuardedCombatStepper.nightNoDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedNightNoExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.nightNoExecuteGuarded(this, guardedStepperCallback()));
    }

    private void runGuardedResultDryRun() {
        startGuardedStepper(GuardedCombatStepper.resultContinueDryRun(this, guardedStepperCallback()));
    }

    private void runGuardedResultExecuteGuarded() {
        startGuardedStepper(GuardedCombatStepper.resultContinueExecuteGuarded(this, guardedStepperCallback()));
    }

    private void startGuardedStepper(GuardedCombatStepper stepper) {
        if (isAutoExpeditionRunning()) {
            guardedStepperStatus.setText("单步作战：自动远征正在运行");
            return;
        }
        if (minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()) {
            guardedStepperStatus.setText("Guarded stepper: blocked by active minimum normal battle");
            return;
        }
        if (guardedCombatStepper != null && guardedCombatStepper.isRunning()) {
            guardedStepperStatus.setText("Guarded stepper: already running");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            guardedStepperStatus.setText("Guarded stepper: blocked by active live wait");
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            guardedStepperStatus.setText("Guarded stepper: blocked by active guarded runner");
            return;
        }
        guardedCombatStepper = stepper;
        if (!guardedCombatStepper.start()) {
            guardedStepperStatus.setText("Guarded stepper: already running");
        }
    }

    private GuardedCombatStepper.Callback guardedStepperCallback() {
        return new GuardedCombatStepper.Callback() {
            @Override
            public void onUpdate(GuardedCombatStepper.Update update) {
                guardedStepperStatus.setText(update.status);
                guardedStepperDetail.setText(update.detail);
                if (!update.running) {
                    guardedCombatStepper = null;
                }
            }
        };
    }

    private void cancelGuardedStepper() {
        if (guardedCombatStepper == null) {
            return;
        }
        guardedStepperStatus.setText("Guarded stepper: cancel requested");
        guardedStepperDetail.setText("cancel requested");
        guardedCombatStepper.cancel();
    }

    private void runGuardedRunnerOneStep() {
        startGuardedRunner(GuardedCombatRunner.oneStepExecuteGuarded(this, guardedRunnerCallback()));
    }

    private void runGuardedRunnerFiveStepLoop() {
        startGuardedRunner(GuardedCombatRunner.boundedLoopExecuteGuarded(this, guardedRunnerCallback()));
    }

    private void startGuardedRunner(GuardedCombatRunner runner) {
        if (isAutoExpeditionRunning()) {
            guardedRunnerStatus.setText("受保护作战：自动远征正在运行");
            return;
        }
        if (minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()) {
            guardedRunnerStatus.setText("受保护作战：被最小普通战占用");
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            guardedRunnerStatus.setText("受保护作战：正在运行");
            return;
        }
        if (guardedCombatStepper != null && guardedCombatStepper.isRunning()) {
            guardedRunnerStatus.setText("受保护作战：被单步任务占用");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            guardedRunnerStatus.setText("受保护作战：被等待任务占用");
            return;
        }
        guardedCombatRunner = runner;
        if (!guardedCombatRunner.start()) {
            guardedRunnerStatus.setText("受保护作战：正在运行");
        }
    }

    private GuardedCombatRunner.Callback guardedRunnerCallback() {
        return new GuardedCombatRunner.Callback() {
            @Override
            public void onUpdate(GuardedCombatRunner.Update update) {
                guardedRunnerStatus.setText(zhGuardedRunnerStatus(update.status));
                guardedRunnerDetail.setText(update.detail);
                if (!update.running) {
                    guardedCombatRunner = null;
                }
            }
        };
    }

    private void cancelGuardedRunner() {
        if (guardedCombatRunner == null) {
            return;
        }
        guardedRunnerStatus.setText("受保护作战：已请求停止");
        guardedRunnerDetail.setText("cancel requested");
        guardedCombatRunner.cancel();
    }

    private void refreshSortieConfigStatus() {
        if (sortieConfigStatus == null) {
            return;
        }
        sortieConfigStatus.setText(NormalSortieConfigStore.statusLine(this));
    }

    private void writeDefaultSortieConfig() {
        try {
            NormalSortieConfigStore.LoadResult result = NormalSortieConfigStore.writeDefault(this);
            sortieConfigStatus.setText("出征配置：" + result.detail);
        } catch (IOException e) {
            sortieConfigStatus.setText("出征配置：写入失败，" + e.getMessage());
        }
    }

    private void runMinimumNormalBattle() {
        if (minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()) {
            minimumRunStatus.setText("最小普通战：正在运行");
            return;
        }
        if (isAutoExpeditionRunning()) {
            minimumRunStatus.setText("最小普通战：自动远征正在运行");
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            minimumRunStatus.setText("最小普通战：受保护作战正在运行");
            return;
        }
        if (guardedCombatStepper != null && guardedCombatStepper.isRunning()) {
            minimumRunStatus.setText("最小普通战：单步作战正在运行");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            minimumRunStatus.setText("最小普通战：等待任务正在运行");
            return;
        }
        NormalSortieConfigStore.LoadResult configResult;
        try {
            configResult = NormalSortieConfigStore.loadOrCreateDefault(this);
        } catch (IOException e) {
            minimumRunStatus.setText("最小普通战：配置不可用");
            minimumRunDetail.setText(e.getMessage());
            refreshSortieConfigStatus();
            return;
        }
        sortieConfigStatus.setText("出征配置：" + configResult.detail);
        minimumNormalBattleRunner = new MinimumNormalBattleRunner(this, configResult.config, minimumRunnerCallback());
        minimumRunStatus.setText("最小普通战：启动中");
        minimumRunDetail.setText("出征配置：" + configResult.file.getAbsolutePath());
        minimumRunLogPath.setText("运行日志：准备生成");
        minimumRunScreenshotPath.setText("失败截图：未生成");
        if (!minimumNormalBattleRunner.start()) {
            minimumRunStatus.setText("最小普通战：正在运行");
        }
    }

    private void runAutoExpeditionScheduler() {
        if (isAutoExpeditionRunning()) {
            minimumRunStatus.setText("自动远征：正在运行");
            return;
        }
        if (minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()) {
            minimumRunStatus.setText("自动远征：最小普通战正在运行");
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            minimumRunStatus.setText("自动远征：受保护作战正在运行");
            return;
        }
        if (guardedCombatStepper != null && guardedCombatStepper.isRunning()) {
            minimumRunStatus.setText("自动远征：单步作战正在运行");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            minimumRunStatus.setText("自动远征：等待任务正在运行");
            return;
        }
        AutoExpeditionConfigStore.LoadResult configResult;
        try {
            configResult = AutoExpeditionConfigStore.loadOrCreateDefault(this);
        } catch (IOException e) {
            minimumRunStatus.setText("自动远征：配置不可用");
            minimumRunDetail.setText(e.getMessage());
            return;
        }
        autoExpeditionSchedulerRunner = new AutoExpeditionSchedulerRunner(
                this,
                configResult.config,
                autoExpeditionCallback()
        );
        minimumRunStatus.setText("自动远征：启动中");
        minimumRunDetail.setText("远征配置：" + configResult.detail);
        minimumRunLogPath.setText("运行日志：准备生成");
        minimumRunScreenshotPath.setText("失败截图：未生成");
        if (!autoExpeditionSchedulerRunner.start()) {
            if (configResult.config.enabled()) {
                minimumRunStatus.setText("自动远征：正在运行");
            } else {
                autoExpeditionSchedulerRunner = null;
            }
        }
    }

    private void cancelCurrentFlow() {
        if (isAutoExpeditionRunning()) {
            cancelAutoExpeditionScheduler();
            return;
        }
        if (minimumNormalBattleRunner != null && minimumNormalBattleRunner.isRunning()) {
            cancelMinimumNormalBattle();
            return;
        }
        if (guardedCombatRunner != null && guardedCombatRunner.isRunning()) {
            cancelGuardedRunner();
            minimumRunStatus.setText("最小普通战：已请求停止受保护作战");
            return;
        }
        if (liveCombatFlowWaiter != null && liveCombatFlowWaiter.isRunning()) {
            cancelLiveCombatWait();
            minimumRunStatus.setText("最小普通战：已请求停止等待任务");
            return;
        }
        if (guardedCombatStepper != null && guardedCombatStepper.isRunning()) {
            cancelGuardedStepper();
            minimumRunStatus.setText("最小普通战：已请求停止单步任务");
            return;
        }
        minimumRunStatus.setText("最小普通战：没有运行中的流程");
    }

    private void cancelAutoExpeditionScheduler() {
        if (autoExpeditionSchedulerRunner == null) {
            minimumRunStatus.setText("自动远征：没有运行中的流程");
            return;
        }
        minimumRunStatus.setText("自动远征：请求停止");
        autoExpeditionSchedulerRunner.cancel();
    }

    private void cancelAutoExpeditionSchedulerSilently() {
        if (autoExpeditionSchedulerRunner != null) {
            autoExpeditionSchedulerRunner.cancel();
            autoExpeditionSchedulerRunner = null;
        }
    }

    private void cancelMinimumNormalBattle() {
        if (minimumNormalBattleRunner == null) {
            minimumRunStatus.setText("最小普通战：没有运行中的流程");
            return;
        }
        minimumRunStatus.setText("最小普通战：请求停止");
        minimumNormalBattleRunner.cancel();
    }

    private void cancelMinimumNormalRunnerSilently() {
        if (minimumNormalBattleRunner != null) {
            minimumNormalBattleRunner.cancel();
            minimumNormalBattleRunner = null;
        }
    }

    private boolean isAutoExpeditionRunning() {
        return AutoExpeditionSchedulerRunner.shouldBlockNewFlowForAutoExpedition(
                autoExpeditionSchedulerRunner != null && autoExpeditionSchedulerRunner.isRunning()
        );
    }

    private MinimumNormalBattleRunner.Callback minimumRunnerCallback() {
        return new MinimumNormalBattleRunner.Callback() {
            @Override
            public void onUpdate(MinimumNormalBattleRunner.Update update) {
                minimumRunStatus.setText(update.status);
                minimumRunDetail.setText(update.detail);
                minimumRunLogPath.setText("运行日志：" + (TextUtils.isEmpty(update.logPath) ? "未生成" : update.logPath));
                minimumRunScreenshotPath.setText("失败截图：" + (TextUtils.isEmpty(update.screenshotPath) ? "未生成" : update.screenshotPath));
                if (!update.running) {
                    minimumNormalBattleRunner = null;
                }
            }
        };
    }

    private AutoExpeditionSchedulerRunner.Callback autoExpeditionCallback() {
        return new AutoExpeditionSchedulerRunner.Callback() {
            @Override
            public void onUpdate(ExpeditionCollectionRunner.Update update) {
                minimumRunStatus.setText(update.status);
                minimumRunDetail.setText(update.detail);
                minimumRunLogPath.setText("运行日志：" + (TextUtils.isEmpty(update.logPath) ? "未生成" : update.logPath));
                minimumRunScreenshotPath.setText("失败截图：" + (TextUtils.isEmpty(update.screenshotPath) ? "未生成" : update.screenshotPath));
                if (!update.running) {
                    autoExpeditionSchedulerRunner = null;
                }
            }
        };
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

    private String zhGuardedRunnerStatus(String status) {
        if (status == null) {
            return "受保护作战：未知";
        }
        if (status.contains("no guarded phase")) {
            return "受保护作战：当前没有可执行阶段";
        }
        if (status.contains("finished")) {
            return "受保护作战：已结束";
        }
        if (status.contains("running")) {
            return "受保护作战：正在运行";
        }
        if (status.contains("cancel")) {
            return "受保护作战：已停止";
        }
        return "受保护作战：" + status;
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
