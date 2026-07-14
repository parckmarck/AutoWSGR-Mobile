# Android 功能对等迁移 Phase 1 基础验收证据

- **验收日期**：2026-07-14（Asia/Shanghai）
- **上游语义锚点**：`OpenWSGR/AutoWSGR@97f8b16`
- **分支基线**：`500c835`
- **已验证实现 HEAD**：`216a1aafdaa846f198334d9ead33366d5458cd42`
- **分支**：`codex/android-parity-phase-1-foundation`
- **范围**：标准构建、持久化 YAML 配置档案、中文编辑界面、统一运行契约与互斥、
  结构化终态、帧/资产性能和并发修复。

本文只验收六阶段设计中的 Phase 1，不声明已达到最终 95% 功能对等。

## 1. 工具链

| 项目 | 实测值 |
|---|---|
| 操作系统 | Windows 11 10.0 amd64 |
| JDK | Oracle Java 21.0.2 LTS |
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| compile / target / min SDK | 34 / 34 / 26 |
| Android Build Tools | 35.0.0 |
| Android SDK command-line tools | 20.0 |
| Java source / target | 8 / 8 |

版本定义位于 [`build.gradle`](../../build.gradle)、
[`app/build.gradle`](../../app/build.gradle) 和
[`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties)。Wrapper 已启用
`validateDistributionUrl=true`。

## 2. 干净构建、测试与 Lint

### 2.1 标准命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleDebug
.\build-apk.ps1
```

本机 `gradlew.bat --stop` 退出码为 `0`。随后两个通过批处理入口启动的构建在 Gradle
配置之前被主机文件权限拦截：

```text
.gradle\8.13\fileHashes\fileHashes.lock (拒绝访问。)
```

这是当前 Codex/Windows 主机对批处理子进程的目录锁限制，不是 Java 编译、测试、Lint
或 Android 打包失败。为保留同一个 Wrapper JAR、同一组 Gradle 参数和同一项目语义，
本次验收使用 Wrapper 批处理最终调用的入口进行回退：

```powershell
java '-Dorg.gradle.appname=gradlew' `
  '-classpath' 'gradle\wrapper\gradle-wrapper.jar' `
  'org.gradle.wrapper.GradleWrapperMain' `
  --no-daemon --no-problems-report `
  clean testDebugUnitTest lintDebug assembleDebug
```

结果：

```text
BUILD SUCCESSFUL in 9s
49 actionable tasks: 22 executed, 27 from cache
```

| 检查 | 结果 |
|---|---:|
| JUnit 测试套件 | 15 |
| JUnit 测试 | 99 |
| failures / errors / skipped | 0 / 0 / 0 |
| Android Lint error | 0 |
| Android Lint warning | 40 |
| `assembleDebug` | 成功 |

`build-apk.ps1` 仅因同一主机锁在调用 `gradlew.bat` 时终止。验收随后按脚本的剩余步骤
把上述成功构建的 `app-debug.apk` 复制到 `build/dist`，并独立运行 `aapt`、`apksigner`
和 SHA-256 校验。标准脚本在不受该主机 ACL 限制的 Windows 环境中仍是推荐入口。

## 3. APK 产物

- **仓库相对路径**：
  `build/dist/AutoWSGR-Mobile-debug-1784017301868.apk`
- **本次机器绝对路径**：
  `C:\Users\14612\Documents\Codex\2026-06-16\apk-openwsgr-autowsgr-https-github-com\work\AutoWSGR-Mobile\.worktrees\android-parity-phase-1-foundation\build\dist\AutoWSGR-Mobile-debug-1784017301868.apk`
- **大小**：1,236,260 bytes
- **SHA-256**：
  `AB3D8C4B659788869F4656C56C493AAB9D8F2FDC19298C3116B8A573DCAEE46D`

`aapt dump badging`：

| 字段 | 值 |
|---|---|
| package | `org.openwsgr.autowsgrmobile` |
| versionCode / versionName | `77` / `0.77.0` |
| compile / target / min SDK | `34` / `34` / `26` |
| application label | `AutoWSGR Mobile` |

`apksigner verify --verbose --print-certs`：

| 项目 | 结果 |
|---|---|
| 验证 | `Verifies` |
| v1 / v2 / v3 / v3.1 / v4 | false / **true** / false / false / false |
| 签名者 | `C=US, O=Android, CN=Android Debug` |
| 证书 SHA-256 | `6f0cd4351ffea3842612e209594fc1971c267a07b426e88ae1c248c5ad350dc3` |

该产物是调试签名 APK，不是商店发布签名。

## 4. 模拟器验收

### 4.1 设备

| 项目 | 值 |
|---|---|
| 模拟器 | MuMu 模拟器 12，5.24.5.3354，实例 0 |
| ADB | `127.0.0.1:16384` |
| Android | 12 / API 32 |
| 型号 / ABI | V2218A / x86_64 |
| 物理画布 | 720×1280，density 180 |
| 游戏包 | `com.huanmeng.zhanjian2`（已安装） |

设备原有 APK 使用不同签名，因此 `adb install -r` 正确拒绝覆盖。卸载旧包之前已把可访问的
外部应用数据备份到
`C:\Users\14612\AppData\Local\Temp\AutoWSGR-device-backup-1784018095922`
（84 个文件，14,050,369 bytes），随后卸载旧包并安装本次 APK。冷启动耗时 448 ms。

### 4.2 配置档案与中文前端

1. 配置下拉框可加载只读的“内置安全配置”；本设备同时检测到旧版数据并生成
   “从旧版配置迁移”用户档案。
2. 修改内置档案后点击“保存”，界面显示：
   `保存方式：需要另存为` 和 `错误：内置配置只读，请使用另存为`。
3. 另存为 `阶段一验收` 后，创建：
   `/storage/emulated/0/Android/data/org.openwsgr.autowsgrmobile/files/configs/阶段一验收.yaml`。
4. `active-profile.txt` 内容为 `阶段一验收.yaml`；界面显示
   `配置：阶段一验收；默认：是；状态：未修改`。
5. force-stop/relaunch 后仍自动加载 `阶段一验收`，证明默认档案跨进程重启保持。

对应实现和自动化测试：

- [`ConfigProfileRepository`](../../app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileRepository.java)
  / [`ConfigProfileRepositoryTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileRepositoryTest.java)
- [`ConfigProfileUiController`](../../app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileUiController.java)
  / [`ConfigProfileUiStateTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileUiStateTest.java)
- [`CurrentConfigProfileValidatorTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/CurrentConfigProfileValidatorTest.java)

### 4.3 截图和无障碍

- 安全设置中启用组件：
  `org.openwsgr.autowsgrmobile/.AutoWsgrAccessibilityService`。
- `dumpsys accessibility`/服务状态确认服务已绑定，界面显示
  `无障碍触控：已启用`。
- 接受系统 MediaProjection 对话框后，`dumpsys media_projection` 显示
  `org.openwsgr.autowsgrmobile: TYPE_SCREEN_CAPTURE`。
- `ScreenCaptureService` 是活动的前台服务；界面实测：
  `屏幕捕获：已授权，正在接收画面`，
  `Screen frame: 720x1280 RGBA, 3686400 bytes, frames=1996, age=87ms`。

### 4.4 流程互斥、停止、日志与失败截图

1. 启动“运行受保护作战一步”并暂停，使统一运行时保持活动流程 `guarded`。
2. 点击第二个会改变游戏状态的“战役”流程。
3. 界面保持 `当前流程：guarded，结果：RUNNING`，并明确显示
   `正在运行：受保护作战`；没有生成 campaign 运行日志。
4. 点击“停止当前流程”，界面和日志都得到
   `当前流程：guarded，结果：CANCELLED` / `Guarded runner: cancelled`。

另外一次自动远征实测启动了已安装游戏；在当前画面无法可靠识别时以
`initial-state-timeout` 保守阻断，没有猜测点击。运行时保存了：

- 日志：`/data/user/0/org.openwsgr.autowsgrmobile/files/runs/auto-expedition-1784020018333.log`
- 失败截图：`/data/user/0/org.openwsgr.autowsgrmobile/files/failures/auto-expedition-1784020018333.png`

本轮设备日志筛选 `FATAL EXCEPTION`、本包进程崩溃和 ANR，匹配数为 0。

对应契约测试：

- [`AutomationCoordinatorTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/AutomationCoordinatorTest.java)
- [`AutomationRuntimeTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/AutomationRuntimeTest.java)
- [`AutomationSessionTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/AutomationSessionTest.java)

## 5. 历史代码审查项复核

| 发现 | Phase 1 状态 | 实现证据 | 测试证据 |
|---|---|---|---|
| #4 帧缓冲逐字节复制 | **已修复** | [`FrameBufferCopier`](../../app/src/main/java/org/openwsgr/autowsgrmobile/FrameBufferCopier.java)、[`ScreenFrameStore`](../../app/src/main/java/org/openwsgr/autowsgrmobile/ScreenFrameStore.java) | [`FrameBufferCopierTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/FrameBufferCopierTest.java) |
| #5 ZIP 每次线性扫描 | **已修复** | [`ZipAssetIndex`](../../app/src/main/java/org/openwsgr/autowsgrmobile/ZipAssetIndex.java)、[`AutoWsgrAssetStore`](../../app/src/main/java/org/openwsgr/autowsgrmobile/AutoWsgrAssetStore.java) | [`ZipAssetIndexTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/ZipAssetIndexTest.java) |
| #8 waiter/running 竞争 | **已修复** | [`GuardedCombatStepper.WaiterLifecycle`](../../app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatStepper.java) | [`GuardedStepperLifecycleTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepperLifecycleTest.java)、[`Task5RunnerSafetyTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/Task5RunnerSafetyTest.java) |
| #9 状态字符串驱动控制流 | **已修复** | [`AutomationOutcome`](../../app/src/main/java/org/openwsgr/autowsgrmobile/AutomationOutcome.java)、[`GuardedCombatRunner`](../../app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatRunner.java) | [`GuardedStepOutcomeTest`](../../app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepOutcomeTest.java)、[`MinimumRunContractsTest`](../../tests/MinimumRunContractsTest.java) |

## 6. Phase 1 功能矩阵复算

评分继续采用
[`2026-07-10 Android 95% 功能对等设计`](../superpowers/specs/2026-07-10-android-95-percent-parity-design.md)
的 100 分矩阵。只有本阶段新增且具备代码、测试或设备证据的能力才加分。

| 功能域 | 权重 | Phase 1 分数 | 本阶段依据 |
|---|---:|---:|---|
| 截图、触控、启停、日志与失败证据 | 6 | **6.0** | 统一会话/互斥/终态测试及第 4 节设备证据 |
| OCR、模板、页面识别与通用导航 | 12 | **5.0** | 本阶段未新增端侧 OCR、14 页面注册或 BFS |
| 战斗状态机、计划、规则、损伤与路线语义 | 16 | **10.5** | typed outcome、取消/动作/证据/waiter 竞争测试；未扩展完整语义 |
| 普通战 | 9 | **5.0** | 保持基线，尚未完成完整准备/重复/计数/船坞处理 |
| 活动战 | 7 | **1.0** | 保持基线 |
| 战役 | 5 | **3.5** | 保持基线；Phase 1 只接入统一运行时 |
| 演习 | 5 | **0.5** | 保持基线 |
| 决战 | 13 | **0.5** | 保持基线 |
| 远征 | 3 | **2.5** | 保持基线；设备验证保守阻断、日志和失败证据 |
| 其他日常操作 | 14 | **0.0** | 尚未迁移 |
| 配置、任务队列与中文前端 | 10 | **5.5** | 多档案、只读内置、另存为、默认持久化、中文编辑；任务队列仍缺 |
| **合计** | **100** | **40.0** | 基线约 36.5；Phase 1 增加基础运行与配置证据 |

**当前证据分数为 40.0/100，不是 95%。** 未完成业务族的分数没有因类、按钮或资产数量
而增加。

## 7. 后续阶段边界

Phase 2 必须继续完成：

- 端侧 `OcrEngine` 和带可靠度/诊断信息的 `Recognition<T>`；
- 原项目 14 个主要页面的统一注册、排他识别和截图契约；
- 每次只执行一条边、可重新规划的 BFS 页面导航；
- 地图标题/章节识别和目标页面验证，不以固定坐标连点代替导航。

之后仍需：完整战斗核心与普通战；活动战/战役/演习；日常操作、任务队列和完整中文
前端；决战、全量矩阵审计以及六个业务族的代表性模拟器验收。只有第六阶段通过最终门槛
后才能声明达到 95%。
