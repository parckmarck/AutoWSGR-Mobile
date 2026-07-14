# AutoWSGR Mobile

AutoWSGR 的独立 Android APK 移植工程。本仓库不会替换上游
`OpenWSGR/AutoWSGR`，业务语义以原项目为依据。

## 构建与验证

环境要求：Windows、JDK 17+、Android SDK 34，以及 Build Tools 35.0.0。
Gradle Wrapper 会使用仓库固定的 Gradle 8.13 和 AGP 8.13.2。

在仓库根目录执行完整验证：

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleDebug
```

生成经过签名校验、带时间戳的兼容调试包：

```powershell
.\build-apk.ps1
```

- Gradle 原始产物：`app/build/outputs/apk/debug/app-debug.apk`
- 兼容脚本产物：`build/dist/AutoWSGR-Mobile-debug-<timestamp>.apk`

## 当前范围

Phase 1 基础层已经包含：

- 中文 Android 控制面板、MediaProjection 截图和 AccessibilityService 触控桥。
- UTF-8 YAML 多配置档案；内置配置只读；支持另存为、设为默认和重启恢复。
- 应用级统一自动化运行时、单活动流程互斥、暂停/继续/停止和结构化终态。
- 统一运行日志、失败截图和保守阻断策略。
- 批量帧复制、ARGB 帧缓存和进程内 ZIP 资产索引缓存。
- 基于原 AutoWSGR 资产的普通图、远征、战役和受保护战斗迁移骨架。

当前仍不是 95% 功能对等版本。端侧 OCR、14 页面注册表、BFS 导航、完整普通战、
活动战、演习、决战和其他日常任务将在后续阶段继续迁移。

Phase 1 的可复现实验、APK 哈希、模拟器验收和当前功能矩阵见
[Phase 1 基础验收证据](docs/parity/2026-07-10-phase-1-foundation-evidence.md)。
