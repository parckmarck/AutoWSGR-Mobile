# 决战 OCR 接入可行性评估

- **评估对象**：`work/autowsgr-android`（安卓移植版）接入决战所需的「文字识别」能力
- **评估日期**：2026-06-27
- **结论**：**不引入通用 OCR 引擎；采用领域专用「模板匹配」识别（方案 C）**

---

## 1. 为什么需要评估

给安卓港加「决战」时，原版决战的核心——**按配置选船**——依赖 `autowsgr/ui/decisive/fleet_ocr.py` 用 **EasyOCR** 识别「战备舰队获取」弹窗中的**舰船名**，再由 `ops/decisive/logic.py::choose_ships` 按配置 `level1`/`level2`/`flagship_priority`（均为舰名列表）决策购买。安卓港**没有任何 OCR 引擎**，故无法忠实实现「按配置完整决战」。

## 2. 关键事实

### 2.1 决战中真正需要「读文字」的只有一处
- **舰名**：`ui/decisive/overlay.py` 的 `SHIP_NAME_X_RANGES` / `SHIP_NAME_Y_RANGE` 区域 → OCR → 与配置舰名匹配。
- **数字**：费用/资源（`COST_AREA` / `RESOURCE_AREA`）、阶段号（`battle_page.detect_stage`）。

### 2.2 其余识别都已是「像素/模板」，无需 OCR
决战三类弹窗 `FLEET_ACQUISITION` / `CONFIRM_EXIT` / `ADVANCE_CHOICE` 在 `overlay.py` 中均由 **`PixelSignature`（像素规则）** 识别；节点/舰标/阶段流转走像素与模板。安卓港 `app/.../vision/` 下**已有** `PixelSignature`、`PixelRule`、`CompositePixelSignature`、`TemplateMatcher`、`VisionColor`——可直接移植这些识别。

### 2.3 舰名是「有限已知词表」，不是任意文本
候选舰只来自用户配置（`level1`+`level2`+`flagship_priority`）与决战固定舰池，集合**有限且已知**。这意味着「识别舰名」=「在有限模板集合中找最像的一个」，而非通用 OCR。

### 2.4 构建系统限制（硬约束）
`work/autowsgr-android/build-apk.ps1` 的流程是：
```
aapt2 compile/link(资源+assets) → javac → jar → d8(dex) → aapt add classes.dex → zipalign → apksigner
```
- **无 gradle / 无依赖下载 / 不解析 AAR**。
- **不打包原生库** `lib/<abi>/*.so`（脚本无任何 jniLibs 处理）。
- 引入任何带 AAR/原生库/模型的 OCR，都需要**改造构建**（手工合并 AAR 的 `classes.jar`、把 `.so` 放进 `lib/<abi>/`、把模型放进 assets、合并资源），工作量与维护成本高，且破坏当前「无依赖、纯源码」的工程形态。

## 3. 方案对比

| 方案 | 接入方式 | 兼容裸 javac 构建 | 体积/成本 | 适配「有限词表选船」 |
|---|---|---|---|---|
| **A. ML Kit Text Recognition** | Google AAR + 中文 v2 模型（+可能 Play Services / `.so`） | ✗ 须改造构建打包 AAR/`.so`/assets | 大；维护重 | 通用 OCR，杀鸡用牛刀 |
| **B. Tesseract（tesseract4android / tess-two）** | JNI `.so` + `chi_sim.traineddata` | ✗ 须打包原生库 + 训练数据，做 ABI 适配 | 大；JNI 复杂 | 通用 OCR，过度 |
| **C. 领域专用模板识别（推荐）** | 复用现有 `TemplateMatcher`/`PixelSignature`：舰名区域对「有限词表舰名图模板」匹配 + 0-9 数字模板读费用/阶段 | ✓ **零新依赖**，纯 assets 模板 + 现有 vision 基建 | 小；可离机单测 | 与需求天然契合 |

## 4. 结论与理由

**采用方案 C**：用「**舰名区域模板匹配 + 数字模板**」充当决战所需的文字识别。

1. 决战识别 ~95% 本就是像素/模板，安卓港基建齐全；唯一缺口（舰名）是有限词表，正好用模板匹配解决。
2. 完全兼容现有裸 javac 构建：只新增 assets 模板 + 纯 Java 识别类，**不改构建、不引原生库**。
3. 成本最低，且**可离机做单元测试**（匹配是纯算法）；舰名/数字模板采集是后续真机一次性工作。
4. ML Kit/Tesseract 体积大、维护重、需重构构建，且会破坏当前「无第三方依赖」的工程形态——属过度工程。

**取舍提醒**：方案 C 的代价是需要为决战候选舰准备「舰名图模板」（真机截图采集，一次性），且仅在「字体/分辨率稳定」前提下可靠；但这与安卓港现有所有识别（像素签名/模板）的前提一致，不增加新风险面。

详细接口与资产组织见 `decisive-text-recognition-design.md`。本仓库已附**数字识别 PoC**（`DigitTemplateRecognizer` + 契约测试）证明该路线在裸 javac 下可跑通。
