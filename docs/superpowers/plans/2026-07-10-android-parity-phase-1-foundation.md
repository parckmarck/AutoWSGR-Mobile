# Android Parity Phase 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prototype build and duplicated flow/config scaffolding with a standard, tested Android foundation that supports persistent user-created YAML profiles, one structured automation contract, one flow coordinator, and efficient frame/asset handling without changing upstream AutoWSGR semantics.

**Architecture:** Keep the existing Java application and its MediaProjection/accessibility implementation. Introduce Gradle as the single build authority, isolate pure-Java profile and automation state from Android adapters, then migrate the three existing user-facing runners through typed updates and a centralized coordinator. Extract byte-buffer and ZIP work into testable pure-Java helpers before optimizing Android call sites.

**Tech Stack:** Java 8 source compatibility, Android API 26+, Android Gradle Plugin 8.13.2, Gradle 8.13, JUnit 4.13.2, PowerShell compatibility wrapper, Android MediaProjection and AccessibilityService.

## Global Constraints

- Source semantics are anchored to upstream `OpenWSGR/AutoWSGR` commit `97f8b16`.
- Do not modify `work/AutoWSGR`; all implementation work belongs to `work/AutoWSGR-Mobile`.
- Preserve application id and namespace `org.openwsgr.autowsgrmobile`.
- Preserve `minSdk 26`, `targetSdk 34`, version code `77`, and version name `0.77.0` in this phase.
- Use AGP `8.13.2`, Gradle `8.13`, and Build Tools `35.0.0`; official compatibility reference: `https://developer.android.com/build/releases/agp-8-13-0-release-notes`.
- Keep Java source/target compatibility at 8; do not rewrite existing Java to Kotlin.
- All new behavior follows TDD: add a failing test, observe the expected failure, add the minimum implementation, and rerun the focused and full suites.
- Built-in profiles are read-only. User edits are saved as UTF-8 `.yaml` files under the app external `configs/` directory.
- A profile only becomes default after validation and atomic file persistence succeed.
- Automation control flow uses enums/fields, never localized or English status-string parsing.
- Uncertain recognition and invalid configuration remain conservative blockers; this phase must not weaken existing safety gates.
- UI copy added in this phase is Simplified Chinese; diagnostics may retain machine-readable English details.

---

## File Structure

### Build and test authority

- Create `settings.gradle`: plugin/dependency repositories and `:app` inclusion.
- Create `build.gradle`: AGP plugin version.
- Create `gradle.properties`: deterministic Gradle settings.
- Create `app/build.gradle`: Android configuration and unit-test source sets.
- Create `app/proguard-rules.pro`: explicit empty rules file for future release builds.
- Create `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, and `gradlew.bat`: Gradle 8.13 wrapper output.
- Modify `build-apk.ps1`: compatibility entry that invokes Gradle and verifies the resulting APK.
- Modify `.gitignore`: Gradle and module build artifacts.
- Modify `app/src/main/AndroidManifest.xml`: remove the deprecated manifest `package` attribute; namespace moves to Gradle.
- Create `app/src/test/java/org/openwsgr/autowsgrmobile/LegacyContractSuiteTest.java`: JUnit bridge for the two existing host-test mains.

### Profiles

- Create `ConfigProfile.java`: immutable profile metadata and YAML.
- Create `ConfigProfileValidator.java`: validation contract.
- Create `ActiveProfileStore.java`: persisted active-profile id contract.
- Create `FileActiveProfileStore.java`: atomic active id file.
- Create `AtomicTextFile.java`: same-directory temporary write and atomic replacement.
- Create `ConfigProfileRepository.java`: list/load/create/save-as/save/rename/delete/set-default behavior.
- Create `DefaultUnifiedConfig.java`: read-only safe built-in YAML.
- Create `YamlSectionExtractor.java`: indentation-aware extraction of top-level mappings without dropping unknown sections.
- Create `UnifiedAutomationConfig.java`: typed normal/expedition/campaign views of the selected profile.
- Create `LegacyConfigProfileImporter.java`: one-time migration of the three existing flat config files into a user profile.
- Create `CurrentConfigProfileValidator.java`: phase-1 validation for recognized normal/expedition/campaign sections and baseline YAML shape.
- Create `ConfigProfileUiState.java`: selected profile and dirty-state policy.
- Create `ConfigProfileUiController.java`: programmatic Android profile editor surface.
- Modify `MainActivity.java`: install and refresh the profile editor, and load the default profile at startup.

### Automation contract and coordination

- Create `AutomationOutcome.java`: typed progress/terminal outcomes.
- Create `AutomationUpdate.java`: common callback payload.
- Create `AutomationCallback.java`: common update callback.
- Create `AutomationControl.java`: synchronized pause/resume/cancel checkpoints.
- Create `AutomationSession.java`: idempotent progress/log/evidence/finalization owner.
- Create `AndroidAutomationSessionSink.java`: app-files log and failure-evidence adapter.
- Create `AutomationFlow.java`: start/cancel/running/id contract.
- Create `AutomationCoordinator.java`: one-active-flow reservation, cancellation, and release.
- Create `RunnerAutomationFlow.java`: adapter around existing runners.
- Create `CallbackDispatcher.java`: thread-dispatch contract used by the runtime.
- Create `AndroidMainThreadDispatcher.java`: main-looper listener delivery.
- Create `AutomationRuntime.java`: application-scoped coordinator, latest update, and UI listeners.
- Create `AutoWsgrApplication.java`: owner of the application-scoped runtime.
- Modify `app/src/main/AndroidManifest.xml`: register `AutoWsgrApplication`.
- Modify `MinimumNormalBattleRunner.java`, `ExpeditionCollectionRunner.java`, `AutoExpeditionSchedulerRunner.java`, and `CampaignBattleRunner.java`: publish `AutomationUpdate`.
- Modify `MainActivity.java`: replace pairwise flow checks with `AutomationCoordinator`.
- Modify `GuardedCombatStepper.java` and `GuardedCombatRunner.java`: typed step outcome instead of status text parsing.

### Performance helpers

- Create `FrameBufferCopier.java`: row-wise RGBA plane copy.
- Modify `ScreenFrameStore.java`: use row-wise copy and cache ARGB pixels for bitmap reconstruction.
- Create `ZipAssetIndex.java`: one-pass ZIP index with defensive byte-array reads.
- Modify `AutoWsgrAssetStore.java`: lazily create and reuse one process-local index.
- Add focused JUnit tests under `app/src/test/java/org/openwsgr/autowsgrmobile/`.

---

### Task 1: Make Gradle the Single Build and Test Authority

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `app/build.gradle`
- Create: `app/proguard-rules.pro`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/src/test/java/org/openwsgr/autowsgrmobile/LegacyContractSuiteTest.java`
- Modify: `app/src/main/AndroidManifest.xml:1-3`
- Modify: `build-apk.ps1`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `gradlew.bat testDebugUnitTest`, `gradlew.bat lintDebug`, and `gradlew.bat assembleDebug`.
- Preserves: package id, min/target SDK, version and current assets/resources.

- [ ] **Step 1: Verify the build wrapper is absent (RED)**

Run:

```powershell
if (Test-Path .\gradlew.bat) { throw 'gradlew.bat unexpectedly exists' }
& .\gradlew.bat --version
```

Expected: the first check passes and PowerShell reports that `gradlew.bat` is not recognized or not found.

- [ ] **Step 2: Add the Gradle project files**

Create `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = 'AutoWSGR-Mobile'
include ':app'
```

Create root `build.gradle`:

```groovy
plugins {
    id 'com.android.application' version '8.13.2' apply false
}
```

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.caching=true
android.useAndroidX=false
```

Create `app/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'org.openwsgr.autowsgrmobile'
    compileSdk 34
    buildToolsVersion '35.0.0'

    defaultConfig {
        applicationId 'org.openwsgr.autowsgrmobile'
        minSdk 26
        targetSdk 34
        versionCode 77
        versionName '0.77.0'
        testInstrumentationRunner 'android.app.Instrumentation'
    }

    buildTypes {
        debug {
            minifyEnabled false
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    sourceSets {
        test.java.srcDirs += ["${rootDir}/tests"]
    }

    testOptions {
        unitTests.returnDefaultValues = true
    }
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

Create an empty `app/proguard-rules.pro` containing one explanatory comment:

```proguard
# Release shrinking is disabled in phase 1; keep this file as the stable Gradle contract.
```

Remove `package="org.openwsgr.autowsgrmobile"` from the `<manifest>` tag because Gradle now owns `namespace` and `applicationId`.

- [ ] **Step 3: Generate and pin the Gradle 8.13 wrapper**

Run:

```powershell
$Gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" `
  -Recurse -File -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
if (-not $Gradle) { throw 'cached Gradle 8.13 executable not found' }
& $Gradle wrapper --gradle-version 8.13 --distribution-type bin
```

Expected: wrapper files are created and `gradle-wrapper.properties` contains:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```

- [ ] **Step 4: Add the legacy JUnit bridge**

Create `LegacyContractSuiteTest.java`:

```java
package org.openwsgr.autowsgrmobile;

import org.junit.Test;

public final class LegacyContractSuiteTest {
    @Test
    public void minimumRunContractsRemainGreen() {
        MinimumRunContractsTest.main(new String[0]);
    }

    @Test
    public void calibrationImportContractsRemainGreen() {
        CalibrationRecordImportAnalyzerTest.main(new String[0]);
    }
}
```

- [ ] **Step 5: Run the Gradle tests and build (GREEN)**

Run:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`, both bridge tests pass, lint has no fatal errors, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Replace the compatibility build script**

Change `build-apk.ps1` to:

```powershell
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Gradlew = Join-Path $Root 'gradlew.bat'
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$Dist = Join-Path $Root 'build\dist'

& $Gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

$SourceApk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $SourceApk)) { throw "Gradle APK not found: $SourceApk" }
New-Item -ItemType Directory -Force $Dist | Out-Null
$Apk = Join-Path $Dist "AutoWSGR-Mobile-debug-$RunId.apk"
Copy-Item -LiteralPath $SourceApk -Destination $Apk -Force

$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$Apksigner = Join-Path $Sdk 'build-tools\35.0.0\apksigner.bat'
& $Apksigner verify --verbose $Apk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $LASTEXITCODE" }
Write-Host "APK: $Apk"
```

Append to `.gitignore`:

```gitignore
.gradle/
app/build/
```

- [ ] **Step 7: Verify the compatibility entry**

Run:

```powershell
.\build-apk.ps1
```

Expected: unit tests/lint/build succeed, signature verification reports v2 and/or v3 true, and a timestamped APK is written under `build/dist/`.

- [ ] **Step 8: Commit Task 1**

```powershell
git add .gitignore settings.gradle build.gradle gradle.properties gradlew gradlew.bat gradle app/build.gradle app/proguard-rules.pro app/src/main/AndroidManifest.xml app/src/test/java/org/openwsgr/autowsgrmobile/LegacyContractSuiteTest.java build-apk.ps1
git commit -m "build: standardize Android Gradle workflow"
```

---

### Task 2: Add Atomic Multi-Profile YAML Storage

**Files:**
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfile.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileValidator.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ActiveProfileStore.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/FileActiveProfileStore.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AtomicTextFile.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileRepository.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/DefaultUnifiedConfig.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileRepositoryTest.java`

**Interfaces:**
- Produces: `ConfigProfileRepository.loadActive()`, `list()`, `saveAs()`, `save()`, `rename()`, `delete()`, and `setDefault()`.
- Produces: read-only built-in id `builtin-safe`.
- Persists: `configs/active-profile.txt` and user YAML files in the same `configs/` directory.

- [ ] **Step 1: Write the failing repository tests**

Create `ConfigProfileRepositoryTest.java` with these tests and shared setup using JUnit `TemporaryFolder`:

```java
package org.openwsgr.autowsgrmobile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public final class ConfigProfileRepositoryTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ConfigProfileRepository repository(File directory) {
        return new ConfigProfileRepository(
                directory,
                DefaultUnifiedConfig.text(),
                new FileActiveProfileStore(new File(directory, "active-profile.txt")),
                new ConfigProfileValidator() {
                    @Override public void validate(String source, String yaml) throws IOException {
                        if (yaml == null || !yaml.contains("automation:")) {
                            throw new IOException(source + ": missing automation root");
                        }
                    }
                }
        );
    }

    @Test public void missingPointerLoadsReadOnlyBuiltin() throws Exception {
        ConfigProfileRepository.LoadResult result = repository(temporaryFolder.newFolder("configs")).loadActive();
        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.profile.builtIn);
        assertTrue(result.fallbackUsed);
    }

    @Test public void saveAsCreatesSeparateUtf8YamlAndCanBecomeDefault() throws Exception {
        File dir = temporaryFolder.newFolder("profiles");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("7-4刷图", "automation:\n  tasks: []\n", true, false);
        assertEquals("7-4刷图.yaml", saved.id);
        assertFalse(saved.builtIn);
        assertEquals(saved.id, repository(dir).loadActive().profile.id);
        assertEquals("automation:\n  tasks: []\n", repository(dir).loadActive().profile.yaml);
    }

    @Test public void pathTraversalAndImplicitOverwriteAreRejected() throws Exception {
        ConfigProfileRepository repo = repository(temporaryFolder.newFolder("safe"));
        try {
            repo.saveAs("../escape", "automation:\n", false, false);
            fail("path traversal must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid profile name"));
        }
        repo.saveAs("日常", "automation:\n", false, false);
        try {
            repo.saveAs("日常", "automation:\n  changed: true\n", false, false);
            fail("implicit overwrite must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already exists"));
        }
    }

    @Test public void builtinCannotBeOverwrittenRenamedOrDeleted() throws Exception {
        ConfigProfileRepository repo = repository(temporaryFolder.newFolder("readonly"));
        assertThrows(IOException.class, () -> repo.save("builtin-safe", "automation:\n"));
        assertThrows(IOException.class, () -> repo.rename("builtin-safe", "new"));
        assertThrows(IOException.class, () -> repo.delete("builtin-safe"));
    }

    @Test public void deletedOrInvalidActiveProfileFallsBackWithoutOverwritingIt() throws Exception {
        File dir = temporaryFolder.newFolder("fallback");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("broken", "automation:\n", true, false);
        assertTrue(saved.file.delete());
        ConfigProfileRepository.LoadResult result = repository(dir).loadActive();
        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.fallbackUsed);
        assertTrue(result.warning.contains("missing"));
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests org.openwsgr.autowsgrmobile.ConfigProfileRepositoryTest
```

Expected: compilation fails because the profile classes do not exist.

- [ ] **Step 3: Implement the immutable profile and contracts**

Create `ConfigProfile.java`:

```java
package org.openwsgr.autowsgrmobile;

import java.io.File;

final class ConfigProfile {
    final String id;
    final String displayName;
    final File file;
    final boolean builtIn;
    final String yaml;

    ConfigProfile(String id, String displayName, File file, boolean builtIn, String yaml) {
        this.id = id;
        this.displayName = displayName;
        this.file = file;
        this.builtIn = builtIn;
        this.yaml = yaml;
    }
}
```

Create the contracts:

```java
package org.openwsgr.autowsgrmobile;

import java.io.IOException;

interface ConfigProfileValidator {
    void validate(String source, String yaml) throws IOException;
}
```

```java
package org.openwsgr.autowsgrmobile;

import java.io.IOException;

interface ActiveProfileStore {
    String load() throws IOException;
    void save(String profileId) throws IOException;
}
```

- [ ] **Step 4: Implement atomic UTF-8 writes and active pointer storage**

`AtomicTextFile.write(File target, String text)` must:

1. Create the parent directory or throw.
2. Write UTF-8 to `<target>.tmp-<UUID>` in the same directory.
3. Flush and close the stream.
4. Try `Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)`.
5. On `AtomicMoveNotSupportedException`, retry with `REPLACE_EXISTING`.
6. Delete the temporary file in `finally` when it still exists.

`FileActiveProfileStore` must return an empty string when the pointer does not exist and use `AtomicTextFile.write()` for updates.

- [ ] **Step 5: Implement the repository**

Use these exact public package interfaces:

```java
final class ConfigProfileRepository {
    static final String BUILTIN_ID = "builtin-safe";

    ConfigProfileRepository(
            File directory,
            String builtInYaml,
            ActiveProfileStore activeStore,
            ConfigProfileValidator validator
    );

    synchronized java.util.List<ConfigProfile> list() throws IOException;
    synchronized LoadResult loadActive() throws IOException;
    synchronized ConfigProfile load(String id) throws IOException;
    synchronized ConfigProfile saveAs(
            String displayName,
            String yaml,
            boolean makeDefault,
            boolean overwrite
    ) throws IOException;
    synchronized ConfigProfile save(String id, String yaml) throws IOException;
    synchronized ConfigProfile rename(String id, String newDisplayName) throws IOException;
    synchronized void delete(String id) throws IOException;
    synchronized ConfigProfile setDefault(String id) throws IOException;
    synchronized boolean hasExplicitDefault() throws IOException;

    static final class LoadResult {
        final ConfigProfile profile;
        final boolean fallbackUsed;
        final String warning;
    }
}
```

Required rules:

- User ids are exactly `<displayName>.yaml`.
- Reject empty names, `.`/`..`, path separators, control characters and `<>:"|?*`.
- Resolve canonical paths and require the parent to equal the canonical config directory.
- Validate text before writing.
- `overwrite=false` rejects an existing file.
- Saving/renaming/deleting the built-in throws `IOException`.
- Deleting or renaming the active profile updates the pointer only after the file operation succeeds.
- Invalid/missing active files return the built-in with `fallbackUsed=true`; do not mutate the invalid user file.

Create `DefaultUnifiedConfig.text()` with this exact safe YAML:

```yaml
version: 1
profile_name: 内置安全配置
automation:
  tasks: []
normal_sortie:
  plan_path: autowsgr/data/plan/normal_fight/1-1.yaml
auto_expedition:
  interval_seconds: 900
campaign:
  battle_type: 困难潜艇
  times: 3
  formation: 2
  night: true
  repair_mode: moderate
```

- [ ] **Step 6: Run focused and full tests (GREEN)**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests org.openwsgr.autowsgrmobile.ConfigProfileRepositoryTest
.\gradlew.bat --no-daemon testDebugUnitTest
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 2**

```powershell
git add app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfile.java app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileValidator.java app/src/main/java/org/openwsgr/autowsgrmobile/ActiveProfileStore.java app/src/main/java/org/openwsgr/autowsgrmobile/FileActiveProfileStore.java app/src/main/java/org/openwsgr/autowsgrmobile/AtomicTextFile.java app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileRepository.java app/src/main/java/org/openwsgr/autowsgrmobile/DefaultUnifiedConfig.java app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileRepositoryTest.java
git commit -m "feat: add persistent YAML configuration profiles"
```

---

### Task 3: Expose Profile Editing and Default Selection in the Frontend

**Files:**
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/CurrentConfigProfileValidator.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/YamlSectionExtractor.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/UnifiedAutomationConfig.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/LegacyConfigProfileImporter.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileUiState.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileUiController.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/MainActivity.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileUiStateTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/CurrentConfigProfileValidatorTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/UnifiedAutomationConfigTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/LegacyConfigProfileImporterTest.java`

**Interfaces:**
- Consumes: `ConfigProfileRepository` from Task 2.
- Produces: startup loading of the default profile, runtime use of that profile by the three existing business flows, and front-end actions for new, save, save-as, and set-default.
- Defers to Phase 5: structured form fields, Storage Access Framework import/export, and final six-tab visual redesign.

- [ ] **Step 1: Write failing UI-state and validator tests**

Test these behaviors:

```java
@Test public void editingMarksStateDirtyButSelectionResetsIt();
@Test public void builtinRequiresSaveAsInsteadOfSave();
@Test public void successfulSaveAsSelectsNewProfileAndClearsDirty();
@Test public void emptyYamlAndTabsAreRejected();
@Test public void recognizedLegacySectionsUseExistingParsers();
@Test public void unknownTopLevelSectionsArePreservedAndAccepted();
@Test public void unifiedProfileMapsNestedSectionsToExistingRuntimeConfigs();
@Test public void missingOptionalSectionsUseDocumentedSafeDefaults();
@Test public void legacyFlatFilesImportOnceAndBecomeDefault();
@Test public void existingExplicitDefaultPreventsLegacyReimport();
```

The validator must reject an empty document, tab indentation, a non-key top-level line, and invalid recognized sections. It must not reject unknown well-formed top-level sections because later phases preserve upstream fields not yet surfaced as forms.

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*ConfigProfileUiStateTest' --tests '*CurrentConfigProfileValidatorTest' --tests '*UnifiedAutomationConfigTest' --tests '*LegacyConfigProfileImporterTest'
```

Expected: compilation failure for missing classes.

- [ ] **Step 3: Implement the pure UI state**

Use this contract:

```java
final class ConfigProfileUiState {
    private String selectedId = ConfigProfileRepository.BUILTIN_ID;
    private boolean builtIn = true;
    private boolean dirty;

    void select(ConfigProfile profile);
    void edited();
    void saved(ConfigProfile profile);
    String selectedId();
    boolean dirty();
    boolean canSaveInPlace();
    boolean requiresSaveAs();
}
```

`canSaveInPlace()` is true only for a dirty non-built-in profile. `requiresSaveAs()` is true for a dirty built-in profile.

- [ ] **Step 4: Implement section extraction and typed runtime configuration**

Use these contracts:

```java
final class YamlSectionExtractor {
    static java.util.Map<String, String> topLevelMappings(String source, String yaml)
            throws java.io.IOException;
}

final class UnifiedAutomationConfig {
    final NormalSortieRunConfig normalSortie;
    final AutoExpeditionConfig autoExpedition;
    final CampaignBattleConfig campaign;

    static UnifiedAutomationConfig parse(String source, String yaml) throws java.io.IOException;
}
```

`YamlSectionExtractor` normalizes CRLF, rejects tabs, and requires every non-comment indentation-zero line to contain a non-empty key followed by `:`. It accepts both top-level scalars such as `version: 1` and nested mappings such as `normal_sortie:`. For a nested mapping it returns the body dedented by two spaces; for a scalar it returns the trimmed scalar value. Duplicate top-level keys are rejected with source and line number. It never serializes the full document, so unknown sections remain unchanged in the profile text.

`UnifiedAutomationConfig.parse()` uses the extracted `normal_sortie`, `auto_expedition`, and `campaign` bodies. A missing section uses the existing safe defaults:

- `NormalSortieConfigStore.defaultConfigText(NormalSortieTarget.of(1, 1))`
- `AutoExpeditionConfig.defaultConfigText()`
- `CampaignBattleConfig.defaultConfigText()`

Present sections are parsed by the existing production parsers. Parser exceptions are wrapped in `IOException` with source and section name.

- [ ] **Step 5: Implement the phase-1 compatibility validator and legacy import**

`CurrentConfigProfileValidator.validate(source, yaml)` must:

- normalize CRLF to LF;
- reject empty content and tabs;
- require each non-comment top-level line to contain a key and `:`;
- preserve unknown sections without mutation;
- delegate recognized section parsing to `UnifiedAutomationConfig.parse(source, yaml)`;
- wrap parser failures as `IOException` containing the profile source and section name.

Implement:

```java
final class LegacyConfigProfileImporter {
    static ConfigProfile importIfNeeded(
            ConfigProfileRepository repository,
            java.io.File legacyConfigDirectory
    ) throws java.io.IOException;
}
```

If `repository.hasExplicitDefault()` is true, return `null` without reading legacy files. Otherwise read any existing `normal-sortie.yaml`, `auto-expedition.yaml`, and `campaign-battle.yaml`, indent their non-comment content under the matching unified section, use safe defaults for absent files, save as `从旧版配置迁移.yaml`, and make it default. If that profile already exists and validates, set it default instead of overwriting it. This import runs once before the first `loadActive()`.

- [ ] **Step 6: Implement the programmatic profile editor**

`ConfigProfileUiController` owns:

```java
final class ConfigProfileUiController {
    interface Listener {
        void onDefaultProfileChanged(ConfigProfile profile, UnifiedAutomationConfig config);
    }

    ConfigProfileUiController(
            Activity activity,
            ConfigProfileRepository repository,
            Listener listener
    );
    View buildView();
    void loadActive();
    void refreshProfiles(String selectId);
    String selectedProfileId();
}
```

The view contains these Simplified Chinese controls:

- Spinner showing profile display names and `（内置只读）` suffix for the built-in.
- Multiline monospace `EditText` containing YAML.
- Status `TextView` showing selected name, default state, dirty state and file path.
- Buttons: `新建空白配置`, `保存`, `另存为`, `设为默认`.

Button behavior:

- `新建空白配置`: place `DefaultUnifiedConfig.text()` in the editor, keep the built-in selected, mark dirty, and require save-as.
- `保存`: reject built-in; validate and save the selected user profile.
- `另存为`: show an `AlertDialog` name input, call `saveAs(name, editorText, true, false)`, select the new profile, and clear dirty state.
- `设为默认`: call `setDefault(selectedId)` only after the selected YAML validates.
- Any exception is rendered in the status text; it never clears editor contents.
- After `另存为` or `设为默认`, parse `UnifiedAutomationConfig` and notify the listener so subsequent runs immediately use the new default.

- [ ] **Step 7: Integrate startup default loading into MainActivity and current runners**

Add fields:

```java
private ConfigProfileRepository configProfileRepository;
private ConfigProfileUiController configProfileUiController;
private UnifiedAutomationConfig activeAutomationConfig;
```

In `onCreate`, initialize the repository from `getExternalFilesDir(null)` with fallback to `getFilesDir()`, directory `configs`, `FileActiveProfileStore`, `DefaultUnifiedConfig.text()`, and `CurrentConfigProfileValidator`. Run `LegacyConfigProfileImporter.importIfNeeded()` against the old `config/` directory. Add `configProfileUiController.buildView()` to the main scroll content under a `配置档案` heading, then call `loadActive()` after `setContentView`.

The listener sets `activeAutomationConfig`. Replace the three start-method calls to their legacy stores with:

- `activeAutomationConfig.normalSortie` for `MinimumNormalBattleRunner`;
- `activeAutomationConfig.autoExpedition` for `AutoExpeditionSchedulerRunner`;
- `activeAutomationConfig.campaign` for `CampaignBattleRunner`.

If `activeAutomationConfig` is unavailable, block start with `默认配置不可用` and retain the parser error. Existing flat stores remain only as one-time migration inputs.

- [ ] **Step 8: Run tests, lint, and assemble (GREEN)**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Expected: all tasks succeed and the APK contains the new profile editor.

- [ ] **Step 9: Commit Task 3**

```powershell
git add app/src/main/java/org/openwsgr/autowsgrmobile/CurrentConfigProfileValidator.java app/src/main/java/org/openwsgr/autowsgrmobile/YamlSectionExtractor.java app/src/main/java/org/openwsgr/autowsgrmobile/UnifiedAutomationConfig.java app/src/main/java/org/openwsgr/autowsgrmobile/LegacyConfigProfileImporter.java app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileUiState.java app/src/main/java/org/openwsgr/autowsgrmobile/ConfigProfileUiController.java app/src/main/java/org/openwsgr/autowsgrmobile/MainActivity.java app/src/test/java/org/openwsgr/autowsgrmobile/ConfigProfileUiStateTest.java app/src/test/java/org/openwsgr/autowsgrmobile/CurrentConfigProfileValidatorTest.java app/src/test/java/org/openwsgr/autowsgrmobile/UnifiedAutomationConfigTest.java app/src/test/java/org/openwsgr/autowsgrmobile/LegacyConfigProfileImporterTest.java
git commit -m "feat: add editable default configuration profiles"
```

---

### Task 4: Introduce Typed Automation Updates and One Flow Coordinator

**Files:**
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationOutcome.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationUpdate.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationCallback.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationControl.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationSession.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AndroidAutomationSessionSink.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationFlow.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationCoordinator.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/RunnerAutomationFlow.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/CallbackDispatcher.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AndroidMainThreadDispatcher.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutomationRuntime.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/AutoWsgrApplication.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/AutomationCoordinatorTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/AutomationUpdateTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/AutomationSessionTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/AutomationRuntimeTest.java`

**Interfaces:**
- Produces: a single active-flow gate for all current and future user-facing flows.
- Produces: typed terminal outcomes that remain independent of localized status text.
- Produces: pause/resume/cancel checkpoints and application-scoped execution that does not depend on one `MainActivity` instance.

- [ ] **Step 1: Write failing typed-update tests**

Cover:

```java
@Test public void progressIsRunningAndNotTerminal();
@Test public void everyNonRunningOutcomeIsTerminal();
@Test public void localizedMessageDoesNotChangeOutcome();
@Test public void coordinatorRejectsSecondFlowAndNamesActiveFlow();
@Test public void failedStartReleasesReservation();
@Test public void terminalCallbackReleasesOnlyMatchingFlow();
@Test public void cancelTargetsTheActiveFlowExactlyOnce();
@Test public void pausedControlBlocksCheckpointUntilResume();
@Test public void cancelUnblocksPausedCheckpointAndReturnsFalse();
@Test public void sessionFinalizationLogsAndSavesEvidenceOnlyOnce();
@Test public void runtimeReplaysLatestUpdateToNewActivityListener();
@Test public void removingActivityListenerDoesNotCancelActiveFlow();
@Test public void runtimeDeliversListenerUpdatesThroughInjectedDispatcher();
```

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*AutomationCoordinatorTest' --tests '*AutomationUpdateTest' --tests '*AutomationSessionTest' --tests '*AutomationRuntimeTest'
```

Expected: missing-symbol compilation failures.

- [ ] **Step 3: Implement the common contracts**

Create:

```java
enum AutomationOutcome {
    RUNNING(false),
    SUCCESS(true),
    NO_ACTION(true),
    EXHAUSTED(true),
    DOCK_FULL(true),
    BLOCKED(true),
    CANCELLED(true),
    FAILED(true);

    final boolean terminal;
    AutomationOutcome(boolean terminal) { this.terminal = terminal; }
}
```

```java
final class AutomationUpdate {
    final String flowId;
    final String runId;
    final AutomationOutcome outcome;
    final String message;
    final String detail;
    final String logPath;
    final String screenshotPath;

    private AutomationUpdate(String flowId, String runId, AutomationOutcome outcome,
                             String message, String detail, String logPath, String screenshotPath) {
        this.flowId = flowId == null ? "" : flowId;
        this.runId = runId == null ? "" : runId;
        this.outcome = outcome;
        this.message = message == null ? "" : message;
        this.detail = detail == null ? "" : detail;
        this.logPath = logPath == null ? "" : logPath;
        this.screenshotPath = screenshotPath == null ? "" : screenshotPath;
    }

    static AutomationUpdate progress(String flowId, String runId, String message, String detail,
                                     String logPath, String screenshotPath) {
        return new AutomationUpdate(flowId, runId, AutomationOutcome.RUNNING,
                message, detail, logPath, screenshotPath);
    }

    static AutomationUpdate terminal(String flowId, String runId, AutomationOutcome outcome,
                                     String message, String detail, String logPath,
                                     String screenshotPath) {
        if (outcome == null || !outcome.terminal) {
            throw new IllegalArgumentException("terminal outcome required");
        }
        return new AutomationUpdate(flowId, runId, outcome, message, detail, logPath, screenshotPath);
    }

    boolean running() { return !outcome.terminal; }
}
```

```java
interface AutomationCallback {
    void onUpdate(AutomationUpdate update);
}

interface AutomationFlow {
    String id();
    String label();
    boolean start();
    boolean supportsPause();
    void pause();
    void resume();
    void cancel();
    boolean isRunning();
}
```

- [ ] **Step 4: Implement pause/cancel control and the idempotent session**

Use:

```java
final class AutomationControl {
    synchronized void pause();
    synchronized void resume();
    synchronized void cancel();
    synchronized boolean cancelled();
    synchronized boolean paused();
    synchronized boolean checkpoint() throws InterruptedException;
}
```

`checkpoint()` waits while paused, returns true when execution may continue, and returns false after cancellation. `cancel()` clears pause and `notifyAll()` so no worker remains stuck.

Use this session contract:

```java
final class AutomationSession {
    interface Sink {
        void append(String line) throws java.io.IOException;
        String logPath();
        String saveFailure(String runId);
    }

    AutomationSession(
            String flowId,
            String runId,
            AutomationControl control,
            Sink sink,
            AutomationCallback callback
    );

    void progress(String message, String detail);
    synchronized boolean finish(
            AutomationOutcome outcome,
            String message,
            String detail,
            boolean saveFailure
    );
    void pause();
    void resume();
    void cancel();
    boolean checkpoint();
    boolean cancelled();
}
```

`finish()` accepts terminal outcomes only, appends exactly one terminal log, optionally saves exactly one failure screenshot, publishes exactly one terminal update, and ignores later finish calls. `AndroidAutomationSessionSink` writes through `MinimumRunLogStore` and `FailureEvidenceStore`; its factory uses the application files directory and a sanitized flow prefix.

- [ ] **Step 5: Implement the coordinator**

Use:

```java
final class AutomationCoordinator {
    synchronized StartResult start(AutomationFlow flow);
    synchronized boolean finish(String flowId);
    boolean cancelActive();
    boolean pauseActive();
    boolean resumeActive();
    synchronized boolean hasActiveFlow();
    synchronized String activeId();
    synchronized String activeLabel();

    static final class StartResult {
        final boolean started;
        final String activeId;
        final String activeLabel;
        final String reason;
    }
}
```

Rules:

- Reject null flow and duplicate starts.
- Reserve the flow before calling `flow.start()` to prevent reentrant starts.
- If `start()` returns false or throws, clear the same reservation and return/rethrow.
- `finish(id)` only clears when id equals the active flow id.
- `cancelActive()` calls the captured active flow outside the synchronized block, then returns true; it does not clear until the terminal callback arrives.
- `pauseActive()` and `resumeActive()` capture the active flow under lock and invoke it outside the synchronized block.
- `pauseActive()`/`resumeActive()` return false without invoking delegates when `supportsPause()` is false; user-facing normal, expedition-scheduler, and campaign flows must support pause, while diagnostic-only wait/step flows may explicitly report unsupported.

`RunnerAutomationFlow` stores id, label and five delegates (`Start`, `Pause`, `Resume`, `Cancel`, `Running`) plus the `supportsPause` flag so existing runners can be adapted without inheritance.

Use these delegate signatures and constructor:

```java
final class RunnerAutomationFlow implements AutomationFlow {
    interface Start { boolean start(); }
    interface Pause { void pause(); }
    interface Resume { void resume(); }
    interface Cancel { void cancel(); }
    interface Running { boolean isRunning(); }

    RunnerAutomationFlow(
            String id,
            String label,
            boolean supportsPause,
            Start start,
            Pause pause,
            Resume resume,
            Cancel cancel,
            Running running
    );
}
```

- [ ] **Step 6: Implement the application-scoped runtime**

Use:

```java
final class AutomationRuntime implements AutomationCallback {
    interface Listener { void onAutomationUpdate(AutomationUpdate update); }

    AutomationRuntime(CallbackDispatcher dispatcher);
    AutomationCoordinator.StartResult start(AutomationFlow flow);
    boolean pauseActive();
    boolean resumeActive();
    boolean cancelActive();
    void addListener(Listener listener, boolean replayLatest);
    void removeListener(Listener listener);
    AutomationUpdate latestUpdate();
    @Override public void onUpdate(AutomationUpdate update);
}

public final class AutoWsgrApplication extends android.app.Application {
    public AutomationRuntime automationRuntime();
}
```

Create:

```java
interface CallbackDispatcher {
    void dispatch(Runnable action);
}

final class AndroidMainThreadDispatcher implements CallbackDispatcher {
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    @Override public void dispatch(Runnable action) { handler.post(action); }
}
```

`AutomationRuntime` owns one coordinator and a thread-safe listener list. A terminal update releases `update.flowId` before listener delivery. Every listener callback goes through the injected dispatcher, so worker threads never update Android views directly. Unit tests inject an immediate dispatcher; `AutoWsgrApplication` uses `AndroidMainThreadDispatcher`. Listener removal never cancels a flow. The manifest declares `android:name=".AutoWsgrApplication"`.

- [ ] **Step 7: Run focused and full tests (GREEN)**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*AutomationCoordinatorTest' --tests '*AutomationUpdateTest' --tests '*AutomationSessionTest' --tests '*AutomationRuntimeTest'
.\gradlew.bat --no-daemon testDebugUnitTest
```

- [ ] **Step 8: Commit Task 4**

```powershell
git add app/src/main/java/org/openwsgr/autowsgrmobile/AutomationOutcome.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationUpdate.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationCallback.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationControl.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationSession.java app/src/main/java/org/openwsgr/autowsgrmobile/AndroidAutomationSessionSink.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationFlow.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationCoordinator.java app/src/main/java/org/openwsgr/autowsgrmobile/RunnerAutomationFlow.java app/src/main/java/org/openwsgr/autowsgrmobile/CallbackDispatcher.java app/src/main/java/org/openwsgr/autowsgrmobile/AndroidMainThreadDispatcher.java app/src/main/java/org/openwsgr/autowsgrmobile/AutomationRuntime.java app/src/main/java/org/openwsgr/autowsgrmobile/AutoWsgrApplication.java app/src/main/AndroidManifest.xml app/src/test/java/org/openwsgr/autowsgrmobile/AutomationCoordinatorTest.java app/src/test/java/org/openwsgr/autowsgrmobile/AutomationUpdateTest.java app/src/test/java/org/openwsgr/autowsgrmobile/AutomationSessionTest.java app/src/test/java/org/openwsgr/autowsgrmobile/AutomationRuntimeTest.java
git commit -m "refactor: centralize automation flow coordination"
```

---

### Task 5: Migrate Existing Runners and Guarded Steps to Typed Outcomes

**Files:**
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/MinimumNormalBattleRunner.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/ExpeditionCollectionRunner.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/AutoExpeditionSchedulerRunner.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/CampaignBattleRunner.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatStepper.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatRunner.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/MainActivity.java`
- Modify: `tests/MinimumRunContractsTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepOutcomeTest.java`

**Interfaces:**
- Consumes: `AutomationUpdate`, `AutomationCallback`, and `AutomationCoordinator`.
- Removes: runner-specific duplicate `Update`/`Callback` classes and `GuardedCombatRunner` status-string control flow.
- Removes: runner-specific duplicate run-id/log/evidence/finalization guards by routing them through `AutomationSession`.

- [ ] **Step 1: Add failing migration contracts**

Add tests that assert:

- normal success emits `SUCCESS`, cancellation emits `CANCELLED`, and blocked preflight emits `BLOCKED`;
- campaign no-times emits `EXHAUSTED`;
- expedition no-ready notification emits `NO_ACTION`, successful collection emits `SUCCESS`;
- scheduler progress remains `RUNNING` until cancellation/failure;
- `GuardedCombatStepper.Update` with outcome `MATCHED` completes even when its message contains no `wait matched` text;
- a message containing `wait matched` with outcome `BLOCKED` does not complete;
- MainActivity source no longer contains pairwise checks such as `campaignBattleRunner != null && campaignBattleRunner.isRunning()` in each start method.
- pausing a runner at an action boundary prevents further taps until resume; cancelling a paused runner unblocks and terminates it.
- destroying/recreating `MainActivity` does not cancel the application-scoped active flow, and the recreated activity receives the latest update.

- [ ] **Step 2: Run tests and confirm RED**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*GuardedStepOutcomeTest' --tests '*LegacyContractSuiteTest'
```

Expected: missing typed step fields and old source-contract assertions fail.

- [ ] **Step 3: Replace runner callbacks**

For each current runner:

- Constructor callback type becomes `AutomationCallback`.
- Each runner owns one `AutomationSession` created with a stable flow id and its existing log prefix.
- `progress(...)` delegates to `AutomationSession.progress(...)`, which posts the common update.
- `finishSuccess(...)` posts `AutomationOutcome.SUCCESS`.
- no-work/no-notification posts `NO_ACTION`.
- campaign times exhausted posts `EXHAUSTED`.
- dock-full terminal paths post `DOCK_FULL`.
- cancellation posts `CANCELLED`.
- guarded/preflight/recognition refusal posts `BLOCKED`.
- uncaught exception posts `FAILED` and preserves the failure screenshot.
- Remove the nested duplicate `Callback` and `Update` classes.
- Replace local run-id/log-file/screenshot-path/finish-idempotency fields with the session.
- Call `session.checkpoint()` before every discrete gesture and at every bounded loop iteration. A false checkpoint follows the existing cancellation path without dispatching another gesture.
- Nested internal runners (guarded combat batches and one-shot expedition passes) report to their parent monitor callback, not directly to `AutomationRuntime`; only the top-level flow session publishes the terminal update whose `flowId` owns the coordinator reservation.

`AutoExpeditionSchedulerRunner` forwards a pass update as `RUNNING` while the scheduler remains active, preserving current behavior without rewriting its outcome.

- [ ] **Step 4: Add typed guarded-step outcomes**

Inside `GuardedCombatStepper` add:

```java
enum StepOutcome {
    RUNNING,
    MATCHED,
    BLOCKED,
    TIMED_OUT,
    CANCELLED,
    FAILED
}
```

`Update` becomes:

```java
static final class Update {
    final StepOutcome outcome;
    final String status;
    final String detail;

    private Update(StepOutcome outcome, String status, String detail) {
        this.outcome = outcome;
        this.status = status;
        this.detail = detail;
    }

    static Update running(String status, String detail) {
        return new Update(StepOutcome.RUNNING, status, detail);
    }

    static Update terminal(StepOutcome outcome, String status, String detail) {
        if (outcome == StepOutcome.RUNNING) throw new IllegalArgumentException("terminal step required");
        return new Update(outcome, status, detail);
    }

    boolean running() { return outcome == StepOutcome.RUNNING; }
}
```

Map `LiveCombatFlowWaiter.Update` fields directly to `MATCHED`, `CANCELLED`, `TIMED_OUT`, or `FAILED`. Guard failures before gesture use `BLOCKED`.

Replace `GuardedCombatRunner.stepCompleted()` and `finalStatus()` with enum switches; no `.contains()` call may decide outcome.

- [ ] **Step 5: Route MainActivity through AutomationCoordinator**

Add one field:

```java
private final AutomationCoordinator automationCoordinator = new AutomationCoordinator();
```

Each start method:

1. Loads and validates its configuration.
2. Creates its runner.
3. Wraps it in `RunnerAutomationFlow` with a stable id (`normal`, `expedition`, `campaign`, `guarded`, `stepper`, or `waiter`).
4. Calls `automationCoordinator.start(flow)`.
5. On rejection, shows `正在运行：<activeLabel>`.

Replace direct Activity callbacks with the application `AutomationRuntime`: top-level runners publish to the runtime, the runtime releases the matching coordinator reservation on a terminal update, and `MainActivity` registers a listener in `onStart`, unregisters in `onStop`, and renders `AutomationUpdate.message/detail/logPath/screenshotPath`. `cancelCurrentFlow()` calls only `runtime.cancelActive()`. Do not cancel the runtime in `onDestroy`.

Construct all top-level runners with `getApplicationContext()` rather than the Activity so an active flow cannot retain a destroyed `MainActivity`.

Add user-facing buttons `暂停当前流程` and `继续当前流程` wired to `runtime.pauseActive()` and `runtime.resumeActive()`. The existing stop button calls `runtime.cancelActive()`.

- [ ] **Step 6: Run focused/full tests and build (GREEN)**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*GuardedStepOutcomeTest' --tests '*LegacyContractSuiteTest'
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Expected: no test failure, no fatal lint issue, and no outcome-driving `status.contains(...)` in `GuardedCombatRunner.java`.

- [ ] **Step 7: Commit Task 5**

```powershell
git add app/src/main/java/org/openwsgr/autowsgrmobile/MinimumNormalBattleRunner.java app/src/main/java/org/openwsgr/autowsgrmobile/ExpeditionCollectionRunner.java app/src/main/java/org/openwsgr/autowsgrmobile/AutoExpeditionSchedulerRunner.java app/src/main/java/org/openwsgr/autowsgrmobile/CampaignBattleRunner.java app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatStepper.java app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatRunner.java app/src/main/java/org/openwsgr/autowsgrmobile/MainActivity.java tests/MinimumRunContractsTest.java app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepOutcomeTest.java
git commit -m "refactor: publish typed automation outcomes"
```

---

### Task 6: Fix Frame Copy, ZIP Scan, and Stepper Visibility Hotspots

**Files:**
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/FrameBufferCopier.java`
- Create: `app/src/main/java/org/openwsgr/autowsgrmobile/ZipAssetIndex.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/ScreenFrameStore.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/AutoWsgrAssetStore.java`
- Modify: `app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatStepper.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/FrameBufferCopierTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/ZipAssetIndexTest.java`
- Test: `app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepperLifecycleTest.java`

**Interfaces:**
- Produces: one row-wise frame copy per incoming image and one in-memory ZIP index per process.
- Preserves: exact current RGBA-to-ARGB color semantics and defensive asset bytes.

- [ ] **Step 1: Write failing helper tests**

`FrameBufferCopierTest` covers:

- tightly packed 2x2 RGBA;
- padded row stride;
- pixel stride greater than four;
- source buffer position remains unchanged;
- invalid capacity/stride throws `IllegalArgumentException`.

`ZipAssetIndexTest` creates an in-memory ZIP and covers:

- one-pass indexed read;
- normalized paths with and without `autowsgr/` prefix;
- missing path throws `IOException`;
- returned byte arrays are defensive copies;
- deterministic `listFirst(maxItems)` order.

`GuardedStepperLifecycleTest` covers cancellation racing with waiter publication through a package-private lifecycle helper or synchronized accessor; the waiter must receive cancellation exactly once.

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*FrameBufferCopierTest' --tests '*ZipAssetIndexTest' --tests '*GuardedStepperLifecycleTest'
```

- [ ] **Step 3: Implement row-wise copying**

Use this API:

```java
final class FrameBufferCopier {
    static byte[] copyRgba(
            java.nio.ByteBuffer source,
            int width,
            int height,
            int pixelStride,
            int rowStride
    );
}
```

For `pixelStride == 4`, copy each visible row with one `ByteBuffer.get(byte[], offset, width * 4)` using a duplicate buffer. For other valid pixel strides, copy only RGBA bytes for each pixel. Validate positive dimensions, `pixelStride >= 4`, `rowStride >= width * pixelStride`, and required buffer capacity.

Modify `ScreenFrameStore.update()` to call the helper. Convert the tight RGBA array into an ARGB `int[]` once during update and store that array under the existing lock. `latestBitmap()` clones the `int[]` and calls `Bitmap.createBitmap(pixels, width, height, ARGB_8888)`; it no longer reconverts every byte on every consumer request.

- [ ] **Step 4: Implement one-pass ZIP indexing**

Use:

```java
final class ZipAssetIndex {
    static ZipAssetIndex load(java.io.InputStream input) throws java.io.IOException;
    byte[] read(String normalizedPath) throws java.io.IOException;
    java.util.List<String> listFirst(int maxItems);
}
```

Store entries in `LinkedHashMap<String, byte[]>`. Return cloned arrays. `AutoWsgrAssetStore` uses a static lock and lazily loads one `ZipAssetIndex` from `autowsgr-data.zip`; `read`, `readText`, and `listFirst` use the index. `readBitmap` decodes a fresh bitmap from cloned bytes so callers may recycle it safely.

- [ ] **Step 5: Make GuardedCombatStepper lifecycle visibility consistent**

- Guard `running` and `waiter` publication/clear under the same synchronized methods.
- In `startWaiter`, publish the waiter before starting it while holding the lock.
- If cancellation already occurred, cancel the newly published waiter after leaving the lock.
- In `handleWaitUpdate`, clear running/waiter through one synchronized terminal method.
- Do not rely only on `volatile`; preserve atomic relationships between `cancelled`, `running`, and `waiter`.

- [ ] **Step 6: Run focused, full, lint, and build verification (GREEN)**

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest --tests '*FrameBufferCopierTest' --tests '*ZipAssetIndexTest' --tests '*GuardedStepperLifecycleTest'
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

- [ ] **Step 7: Commit Task 6**

```powershell
git add app/src/main/java/org/openwsgr/autowsgrmobile/FrameBufferCopier.java app/src/main/java/org/openwsgr/autowsgrmobile/ZipAssetIndex.java app/src/main/java/org/openwsgr/autowsgrmobile/ScreenFrameStore.java app/src/main/java/org/openwsgr/autowsgrmobile/AutoWsgrAssetStore.java app/src/main/java/org/openwsgr/autowsgrmobile/GuardedCombatStepper.java app/src/test/java/org/openwsgr/autowsgrmobile/FrameBufferCopierTest.java app/src/test/java/org/openwsgr/autowsgrmobile/ZipAssetIndexTest.java app/src/test/java/org/openwsgr/autowsgrmobile/GuardedStepperLifecycleTest.java
git commit -m "perf: cache assets and batch frame copies"
```

---

### Task 7: Verify and Document Phase 1

**Files:**
- Create: `docs/parity/2026-07-10-phase-1-foundation-evidence.md`
- Modify: `README.md`
- Modify: `CODE_REVIEW.md`

**Interfaces:**
- Produces: reproducible commands, exact artifact hash, known limitations, and updated review status.

- [ ] **Step 1: Run the complete clean verification**

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleDebug
.\build-apk.ps1
git diff --check
git status --short
```

Expected:

- Gradle clean verification reports `BUILD SUCCESSFUL`.
- All legacy and new JUnit tests pass with zero failures.
- Lint has zero fatal errors.
- Compatibility build emits a signed timestamped APK.
- `git diff --check` has no output.
- `git status --short` contains only the intended documentation edits before commit.

- [ ] **Step 2: Verify APK metadata and signature**

```powershell
$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$Apk = Get-ChildItem 'build\dist\AutoWSGR-Mobile-debug-*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
& "$Sdk\build-tools\35.0.0\aapt.exe" dump badging $Apk.FullName
& "$Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $Apk.FullName
Get-FileHash $Apk.FullName -Algorithm SHA256
```

Expected: package is `org.openwsgr.autowsgrmobile`, version is `77/0.77.0`, min/target SDK are `26/34`, signature verification succeeds, and a SHA-256 is printed.

- [ ] **Step 3: Perform an available-device smoke test**

```powershell
$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $Adb devices -l
```

If no device is listed, actively locate and start an installed emulator AVD:

```powershell
$Emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
$Avds = @(& $Emulator -list-avds | Where-Object { $_.Trim().Length -gt 0 })
if ($Avds.Count -gt 0) {
    $Avd = $Avds[0]
    Start-Process -FilePath $Emulator -ArgumentList @('-avd', $Avd, '-no-snapshot-save') -WindowStyle Hidden
    & $Adb wait-for-device
    do {
        Start-Sleep -Seconds 2
        $Booted = (& $Adb shell getprop sys.boot_completed 2>$null).Trim()
    } while ($Booted -ne '1')
}
& $Adb devices -l
```

Prefer the AVD referenced by prior project evidence when multiple AVDs exist; record the chosen name. Do not launch a second emulator when an ADB device is already online.

If a device is now listed, install with `adb install -r`, launch `org.openwsgr.autowsgrmobile/.MainActivity`, and verify:

- default built-in profile loads;
- editing built-in forces save-as;
- saving as a Chinese name creates a separate YAML;
- setting it default survives force-stop/relaunch;
- starting one flow blocks a second flow and the stop button targets the active flow;
- capture/accessibility statuses remain available.

If no AVD exists or no device becomes online, record `emulator=unavailable` and the exact AVD/ADB output. Do not claim device verification.

- [ ] **Step 4: Write the evidence document**

Record:

- base/head commits;
- Gradle/AGP/JDK/SDK versions;
- test, lint and build commands with exit codes;
- APK path, package metadata, signature schemes and SHA-256;
- profile smoke results or explicit device unavailability;
- unresolved work reserved for Phase 2 (OCR/page registry/navigation) and later phases;
- revised feature-matrix score with evidence links, without claiming 95%.

Update `README.md` build instructions to use `gradlew.bat`/`build-apk.ps1`. Mark old review findings #4, #5, #8 and #9 as fixed only when the corresponding tests and source checks above pass.

- [ ] **Step 5: Commit Task 7**

```powershell
git add README.md CODE_REVIEW.md docs/parity/2026-07-10-phase-1-foundation-evidence.md
git commit -m "docs: record phase one Android foundation evidence"
```

- [ ] **Step 6: Final branch verification**

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintDebug assembleDebug
git status --short --branch
git log --oneline --decorate -8
```

Expected: clean verification succeeds and the branch contains the seven intentional task commits with no uncommitted source changes.

---

## Self-Review Checklist

- The plan changes only `AutoWSGR-Mobile`; upstream is read-only.
- Build versions match the official AGP 8.13 compatibility table and installed SDK/tooling.
- Every production behavior task begins with a failing test and an observed RED command.
- Built-in profile immutability, save-as, default persistence, invalid fallback and atomic write rules are explicit.
- Unknown upstream YAML fields are preserved instead of dropped.
- Typed outcomes remove status-string control decisions.
- Coordinator integration removes pairwise business-flow checks before adding more runners.
- Frame and ZIP optimizations are extracted into pure-Java helpers with deterministic tests.
- Device absence is reported as unavailable rather than converted into a false pass.
- Phase 1 does not claim the final 95% target; it creates the foundation for the remaining five migration plans.
