# AutoWSGR Android 移植版代码审查报告

- **审查对象**：`work/autowsgr-android`
- **审查日期**：2026-06-19
- **审查范围**：全部 39 个 Java 源文件（约 10,600 行）、`AndroidManifest.xml`、`res/` 资源、`build-apk.ps1`、`assets/`（`autowsgr-data.zip` + 资产索引）
- **排除**：`build/`（编译产物 `intermediates*`、`dist/` 下 60+ 个历史 APK、UI dump）

---

## 总体评价

这是一个**质量相当高的早期移植壳**。代码自我定位清晰（MainActivity 副标题：「migration shell … before the Python automation engine can be ported」），架构分层干净，防御性强：

- 后台线程 + 主线程 `Handler` 回调
- Bitmap 普遍 `try/finally recycle()`
- 安全决策一律「不确定即保守停止」
- 用递归下降解析器替代 Python `eval`
- 空值处理完备

主要问题集中在**实景识别链路的可用性**（分辨率 / 方向 / 坐标空间）与**性能**。这两类多数已在 `AutomationEngine.nativeRewriteQueue()` 中被作者标注为「待校准」，但其实际后果值得明确点出。下面按严重度排列。

---

## 一、功能正确性（影响最大）

### 1. 模板分辨率/缩放不匹配 —— 模板匹配链路在真机上基本无法命中
- `NativeTemplateKey.java:8-34` 所有模板是 `*_540p.png`（基准 960×540）；`ScreenCaptureService.java:98-104` 用 `getRealMetrics` 按**物理分辨率**（如 2400×1080）抓帧；`ScreenFrameStore.latestBitmap()` 返回全分辨率位图，**全程无降采样到 540p**。
- `TemplateMatcher.findBest`（`TemplateMatcher.java:13-41`）是 1:1 像素滑窗，无尺度不变性。540p 模板贴到 2.5× 大小的实景帧上，同一图标尺寸差 2.5 倍 → 相似度恒低 → 永远达不到阈值 `0.999`（见 `GuardedCombatRunner.java:14` 等）。
- **后果**：所有 `templateKey != null` 的相位（PROCEED / SPOT_ENEMY / RESULT / NIGHT 等，见 `NativeCombatPhase.java`）在非 540p 设备上识别恒失败；只有归一化坐标的像素签名相位（FORMATION / EVENT_MAP）是分辨率无关、可用的。
- **建议**：模板匹配前把帧缩放到模板基准高度（或反之按帧尺寸缩放模板），或引入尺度金字塔 / OpenCV。这是让 runner 在真机跑起来的前提。

### 2. 采集 VirtualDisplay 方向固定，不跟随旋转
- `MainActivity` 锁 `portrait`（`AndroidManifest.xml:17`），`ScreenCaptureService.startProjection` 在服务启动那一刻按当时（竖屏）的 `getRealMetrics` 固定创建 VirtualDisplay 尺寸（`ScreenCaptureService.java:101-128`）。WSGR 实际是横屏游戏——用户切到横屏游戏后，镜像内容会被旋转/信箱化塞进竖屏 surface。
- **后果**：喂给识别器的帧是「竖屏外框里旋转 90° 的横屏画面」，连归一化像素签名都会指向错误位置。
- **建议**：监听旋转 / 用 `DisplayListener` 重建 VirtualDisplay，或按帧实际宽高比判定方向再做坐标变换。

### 3. 抓帧坐标空间 ≠ 手势坐标空间
- 抓帧用 `getRealMetrics`（含系统栏的物理像素），而 `AutoWsgrAccessibilityService.toPixels`（`AutoWsgrAccessibilityService.java:69-74`）用 `getResources().getDisplayMetrics()`（通常是**应用可用区**，可能不含导航栏高度）。
- 识别器基于全分辨率帧算出的归一化坐标，经手势侧用较小的高度反算像素，会产生约「导航栏高度」的系统性偏移，靠近屏幕底部的点击（如 `RESULT_CONTINUE` y=0.954）更易点偏。
- **建议**：手势侧也用 `getRealMetrics`，与抓帧坐标空间统一。

---

## 二、性能

### 4. `ScreenFrameStore.update` 逐像素 `ByteBuffer.get`
- `ScreenFrameStore.java:32-43`：每帧 `width×height×4` 次单字节 `buffer.get(int)`（1080p 约 1000 万次/帧）。虽在采集工作线程、不致 ANR，但严重限制帧率、空耗 CPU/电量。
- **建议**：当 `pixelStride==4` 时按行 `buffer.get(tight, dstRow, rowBytes)` 批量拷贝；`rowStride==width*4` 时可整块拷贝。

### 5. `AutoWsgrAssetStore` 每次读取都重开并线性扫描整个 ZIP，无缓存
- `AutoWsgrAssetStore.java:28-47`：单次 `read()` 是 O(N)（374 个条目），且无任何缓存。
- 放大效应：`NativeTemplateKey.loadTemplates` 对每个路径各扫一次（`NativeTemplateKey.java:46-53`）；`NativeCombatRecognizer.identifyCurrent` 对每个候选相位都重新 `loadTemplates`（`NativeCombatRecognizer.java:47`）；runner 每步都做。
- **建议**：进程内缓存解压后的字节/位图（或启动时把 ZIP 解到内部存储一次），模板按 key 缓存复用。

### 6. `TemplateCatalogProbe` 在 UI 线程解码 67 张 PNG
- `MainActivity.runTemplateCatalogProbe` → `TemplateCatalogProbe.run(this)` 同步在主线程执行（`TemplateCatalogProbe.java:54-130`），对 67 个 PNG 各做一次「全 ZIP 重扫 + 解码」。位图已在 `finally` 正确 recycle，但**整批解码同步占用 UI 线程**有卡顿/ANR 风险。
- 对照：waiter/stepper/runner 都正确开后台线程并经 `Handler` 回主线程（`LiveCombatFlowWaiter.java:267-274` 等），唯独一次性 probe/self-test 全在主线程跑。`LiveCombatFlowProbe` 已特意只取像素候选来规避（`LiveCombatFlowProbe.java:26`），说明作者有意识——但 catalog probe 是漏网的重活。
- **建议**：把 catalog probe（及其它可能较重的 probe）移到后台线程。

### 7. `TemplateMatcher` / `RouteNodeTracker` 用 `getPixel` 单点取像
- `TemplateMatcher.similarityAt` 用 `Bitmap.getPixel` 逐采样点（`TemplateMatcher.java:54-55`）；`RouteNodeTracker.detectShipPosition` 扫描网格也用 `getPixel`（`RouteNodeTracker.java:191`）。`getPixel` 跨 native 开销大。相比之下 `EnemyCompositionRecognizer`/`EnemyFormationRecognizer` 已用 `getPixels` 批量取（`EnemyCompositionRecognizer.java:318`）——值得统一到批量取像。

---

## 三、线程与并发

### 8. `GuardedCombatStepper` 的 `running` / `waiter` 字段访问未一致同步
- `running`（`GuardedCombatStepper.java:191`）：`start/cancel/isRunning/finish` 都 `synchronized`，但 `startWaiter()` 第 602 行与 `handleWaitUpdate()` 第 641 行**未持锁**写 `running=false`；前者可能在工作线程执行，存在与 `isRunning()` 的可见性/竞争窗口。
- `waiter` 字段在 `startWaiter`（工作线程，未同步）写、`cancel`（同步）读，取消恰好与赋值并发时可能漏掉对 waiter 的取消。
- 影响低（窗口窄），但**建议**：`running` 改 `volatile` 或所有访问入锁；`waiter` 赋值也纳入同步块。
- 注：`GuardedCombatRunner` 的同名字段处理得更规范（全部在 `synchronized` 内，`GuardedCombatRunner.java:115-146,228-235,350-354`），可作模板。

---

## 四、健壮性与可维护性

### 9. 用人类可读状态串驱动控制流（脆弱耦合）
- `GuardedCombatRunner.stepCompleted` 用 `update.status.contains("wait matched")`、`finalStatus` 用 `.contains("cancelled"/"blocked"/"timed out")`（`GuardedCombatRunner.java:544-562`）判流程走向。一旦有人改了状态文案，runner 逻辑就静默错乱。
- **建议**：用枚举/布尔结果位（`Update` 里加 `matched/cancelled/timedOut/blocked` 字段，像 `LiveCombatFlowWaiter.Update` 那样）替代字符串匹配。

### 10. 手写 YAML 子集解析器的脆弱性
- `CombatPlanPolicy.parse`（`CombatPlanPolicy.java:539-668`）与 `MapNodeData.parse` 依赖固定缩进（node 名 `indent==2`、参数 `indent>=4`）。缩进不符的合法 YAML 会被静默丢弃，不报错只产出错误决策。bulk-parse probe 能发现整体解析失败（`CombatPlanPolicy.java:96-109`），但发现不了「部分字段被静默忽略」。可接受（资产受控），但属潜在隐患。

### 11. `EnemyRuleEngine` 不支持链式比较与减法
- `parseComparison` 只接受单个 `sum OP sum`（`EnemyRuleEngine.java:384-415`），`parseSum` 只支持 `+`（`EnemyRuleEngine.java:417-434`）。Python 风格的链式比较 `1 <= CV <= 2` 会在 `parse()` 末尾抛 "unexpected token" → IOException → **整个计划文件解析失败**。
- 解析器本身实现干净、无 `eval`，安全性好；这是兼容性缺口。若原版计划中存在链式比较/减法表达式需补语法（bulk-parse probe 会暴露）。

### 12. 实景识别器目前是占位实现（已知，但后果需明确）
- `EnemyCompositionRecognizer` 的 6 个 `FIGHT_PROTOTYPES`（`EnemyCompositionRecognizer.java:48-53`）**hash64、stdev、edge 全相同，仅 meanLuma 相差 24**，`CLASSIFIER_DISTANCE_LIMIT=14`。即分类实际上只是「均值亮度分桶」，无法区分真实舰种；且存在小概率「6 槽恰好落桶 → 自信地误判」喂错规则引擎的路径。
- `EnemyFormationRecognizer.SignatureFormationBackend` 只能识别「轮型阵」一种（`EnemyFormationRecognizer.java:366-381`）。
- `ShipDamageRecognizer` 单像素采样 + 阈值 80（`ShipDamageRecognizer.java:109-130`）易受缩放/抗锯齿影响，理论上有「大破被判为正常 → 误前进」的不安全失败模式。
- 三者均有「不可靠即拒绝/保守」的兜底（reliability gate），整体偏安全；但作者已在 `nativeRewriteQueue` 列入「用真机截图校准/替换」。**仅作确认与风险提示**。

---

## 五、安全 / 工程 / 杂项（低）

- `AndroidManifest.xml:12` `usesCleartextTraffic="true"`：当前 APK 无任何网络代码（原版的 FastAPI/WebSocket 未移植），该标志冗余，建议移除。
- 组件导出正确：`ScreenCaptureService` `exported=false`；无障碍服务按框架要求 `exported=true`+`BIND_ACCESSIBILITY_SERVICE`。`canRetrieveWindowContent=false`（隐私友好），但 `accessibility_service_config.xml:3` 订阅了 window 事件而 `onAccessibilityEvent` 为空，可去掉订阅。
- 缺少运行时暂停/用户中止/前台执行保护（`nativeRewriteQueue` 已列）——对一个会自动点击的工具，这是上线前必须项。
- `build-apk.ps1`：仅 debug 签名（口令硬编码 `android`，调试可接受）；每次构建产物按时间戳落 `build/dist/`，**从不清理**（已堆积 60+ APK），建议构建前清旧产物或输出到稳定文件名。

---

## 优点（值得保留的做法）

- 后台 worker + `Handler` 主线程回调的统一模式（waiter/stepper/runner），Bitmap 全程 `try/finally recycle`。
- 安全门保守：`ShipDamagePolicy.evaluate` 对 unknown/unreliable 一律不前进（`ShipDamagePolicy.java:48-87`）；识别器 reliability gate「部分识别即判不可靠」。
- `EnemyRuleEngine` 以递归下降解析器替代 `eval`，干净且无注入面。
- `NativeCombatTransitions` 状态图清晰、不可变；runner 有 `maxSteps` 上界、路线偏离守卫，无死循环。

---

## 优先级建议

1. **先解决识别可用性**（#1 模板缩放、#2 方向、#3 坐标空间）——否则 runner 在真机无法工作。
2. **性能三连**：#5 资产缓存、#4 帧拷贝、#6 probe 移出 UI 线程。
3. **#9 去掉字符串驱动控制流**、**#8 并发字段同步**——低成本、防未来回归。
4. 上线前补 **运行时暂停/用户同意**（安全）与 **#10/#11 解析器健壮性**。

---

## 附：发现一览表

| # | 严重度 | 类别 | 位置 | 摘要 |
|---|--------|------|------|------|
| 1 | 高 | 功能 | `NativeTemplateKey.java:8-34` / `ScreenCaptureService.java:98-104` / `TemplateMatcher.java:13-41` | 540p 模板 vs 全分辨率帧，无尺度不变性 → 模板匹配恒失败 |
| 2 | 高 | 功能 | `ScreenCaptureService.java:101-128` | VirtualDisplay 方向固定，不跟随横竖屏旋转 |
| 3 | 中 | 功能 | `AutoWsgrAccessibilityService.java:69-74` | 抓帧(getRealMetrics) 与手势(getDisplayMetrics) 坐标空间不一致 |
| 4 | 中 | 性能 | `ScreenFrameStore.java:32-43` | 每帧千万次逐像素 `ByteBuffer.get` |
| 5 | 中 | 性能 | `AutoWsgrAssetStore.java:28-47` | 每次读取重扫整个 ZIP，无缓存 |
| 6 | 中 | 性能 | `TemplateCatalogProbe.java:54-130` | UI 线程同步解码 67 张 PNG，卡顿/ANR 风险 |
| 7 | 低 | 性能 | `TemplateMatcher.java:54-55` / `RouteNodeTracker.java:191` | `getPixel` 单点取像，应改批量 `getPixels` |
| 8 | 低 | 并发 | `GuardedCombatStepper.java:191,602,641` | `running`/`waiter` 字段访问未一致同步 |
| 9 | 中 | 可维护性 | `GuardedCombatRunner.java:544-562` | 用状态字符串 `.contains()` 驱动控制流 |
| 10 | 低 | 健壮性 | `CombatPlanPolicy.java:539-668` | 手写 YAML 解析器对缩进敏感，静默丢字段 |
| 11 | 低 | 兼容性 | `EnemyRuleEngine.java:384-434` | 不支持链式比较 / 减法，遇到则整文件解析失败 |
| 12 | 低 | 功能 | `EnemyCompositionRecognizer.java:48-53` 等 | 识别器为占位实现（已知，待真机校准） |
