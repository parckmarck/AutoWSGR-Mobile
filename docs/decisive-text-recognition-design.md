# 决战文字识别（方案 C）设计草案

> 用「模板匹配」替代通用 OCR，识别决战所需的**有限词表舰名**与**数字**。纯 Java + assets 模板，复用现有
> `vision/TemplateMatcher`，兼容裸 javac 构建。本文件是接口与资产草案，非最终实现。

## 1. 设计原则（与现架构一致）

- **纯算法与设备采样解耦**：识别核心在 `int[]` 灰度网格上运算（仿现有 `*Signatures.Sampler` 模式），可离机单测；
  Bitmap → 网格的采样在设备侧单独完成。这样既能跑契约测试，又不依赖 `Bitmap.getPixel`（离机会抛 not-mocked）。
- **不确定即 UNKNOWN**：低于阈值一律返回未知，交由上层保守处理（与决战「OCR 无结果重试/放弃」一致）。
- **零新依赖**：相似度复用 `vision/TemplateMatcher.similarityAt` 同款「均值色距→[0,1]」打分思路。

## 2. 组件

### 2.1 `DigitTemplateRecognizer`（数字：费用/资源/阶段号）
```
final class DigitTemplateRecognizer {
    // 单个数字单元：归一化为 CELL_W×CELL_H 的灰度网格(0..255)。
    static int recognizeDigit(int[] cell, int[][] templates, double minScore); // 返回 0-9，未知 -1
    static String recognizeNumber(int[][] cells, int[][] templates, double minScore); // 逐单元拼接，未知用 '?'
    static double similarity(int[] a, int[] b); // 1 - 均值绝对差/255
}
```
- `templates[d]` = 数字 d 的参考网格（来自 `assets/decisive/digits/<d>.png` 预处理）。
- 设备侧：把 `COST_AREA`/`RESOURCE_AREA` 裁剪→按字符切分→每字符缩放到 `CELL_W×CELL_H`→灰度→`recognizeNumber`。

### 2.2 `ShipNameTemplateRecognizer`（舰名：有限词表）
```
final class ShipNameTemplateRecognizer {
    // nameRegion: 舰名区域归一化为固定 NAME_W×NAME_H 灰度网格。
    // candidates: 候选舰名(来自配置 level1+level2+flagship_priority)；templatesByName: 名→参考网格。
    static Match recognize(int[] nameRegion, List<String> candidates,
                           Map<String,int[]> templatesByName, double minScore);
    // Match{ name, score, matched }；matched=false 表示无候选达阈值 → UNKNOWN
}
```
- 只在候选集合内比对（决战选船只关心「这张卡是不是我要的某条船」），鲁棒且快。
- 区域坐标来自原版 `ui/decisive/overlay.py`：`FLEET_CARD_X_POSITIONS`（每张卡 x）、`SHIP_NAME_Y_RANGE`（名字 y 带）。

## 3. 资产组织
```
app/src/main/assets/  (或并入 autowsgr-data.zip)
  decisive/
    digits/0.png .. 9.png                 # 数字模板
    ship_names/<舰名>.png                 # 候选舰名模板（按需采集，文件名=舰名）
```
- 采集流程（后续真机一次性）：进入决战「战备舰队获取」弹窗，截图→按 `FLEET_CARD_X_POSITIONS`×`SHIP_NAME_Y_RANGE` 裁剪每张卡的舰名区→灰度+缩放到固定尺寸→存为 `ship_names/<舰名>.png`。数字模板从费用/资源区采集 0-9 各一次。
- 仅需覆盖**用户配置里出现过的舰名**（+ 决战常见舰池），不是全游戏舰船。

## 4. 与决战状态机的衔接点
- **仅 `CHOOSE_FLEET` 阶段**用到本识别：`ShipNameTemplateRecognizer` 读每张卡舰名 → 套用原版 `choose_ships` 决策（按 `level1`/`level2`/`flagship_priority`）→ 点击购买/技能。
- 其余阶段（`ENTER_MAP`/`WAITING_FOR_MAP`/`ADVANCE_CHOICE`/`PREPARE_COMBAT`/`IN_COMBAT`/`NODE_RESULT`/`STAGE_CLEAR`/`CONFIRM_EXIT`）走**像素签名**（移植 `overlay.py` 的 `PixelSignature` 到安卓 `vision/PixelSignature`），**不需要本识别**。
- 费用/资源/阶段号用 `DigitTemplateRecognizer`（可选；阶段号也可用像素签名分桶）。

## 5. 风险与边界
- 依赖「字体/分辨率稳定」——与安卓港现有所有识别前提一致；归一化到 960×540 游戏帧（见 [[frame-space-invariant]]）后采集与识别同坐标系。
- 词表外的舰名 → UNKNOWN（保守跳过/不购买），不会误选。
- 舰名模板采集是后续真机工作；本轮仅给出可离机验证的**数字识别 PoC**（`DigitTemplateRecognizer` + 契约测试）证明路线可行。

## 6. 后续（评估通过后另起）
1. 真机采集数字与候选舰名模板。
2. 移植 `overlay.py` 像素签名 → 安卓 `DecisiveOverlaySignatures`。
3. 实现 `ShipNameTemplateRecognizer` + 决战 `choose_ships` 选船逻辑。
4. 决战状态机（`DecisiveController` 对应物）+ 地图/节点推进 + 真机坐标校准。
