# AutoWSGR Mobile

Android APK migration prototype for AutoWSGR. This repository is an independent
Android port workspace and does not replace the upstream OpenWSGR/AutoWSGR
project.

## Build

Run from this directory on Windows with Android SDK installed:

```powershell
.\build-apk.ps1
```

The script builds and signs a debug APK under `build/dist/`.

## Current Scope

The current port contains:

- Android control panel, MediaProjection capture, and AccessibilityService tap bridge.
- Bundled AutoWSGR YAML/image/bin assets loaded from Android assets.
- Native pixel/template recognition primitives and guarded combat phase handling.
- Config-driven normal sortie flow using original AutoWSGR plan assets.
- Automatic expedition collection with app-scoped periodic scheduling.
- Campaign battle automation with configurable battle type, times, formation,
  night choice, and repair mode.
- Run logs, failure screenshots, stop controls, and host contract tests.

Known boundaries:

- This is still an Android migration prototype, not a full upstream parity port.
- Map-title OCR is not implemented yet.
- Enemy composition, enemy formation, and decisive-battle text recognition remain
  prototype or design-stage features.
- The project intentionally avoids Gradle/AAR/native OCR dependencies for now.
