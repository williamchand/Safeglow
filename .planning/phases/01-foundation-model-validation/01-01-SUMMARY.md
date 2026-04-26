---
phase: 01-foundation-model-validation
plan: 01
subsystem: build-infrastructure
tags: [android, gradle, hilt, compose, litert, room, privacy, scaffolding]
dependency_graph:
  requires: []
  provides:
    - gradle-version-catalog
    - hilt-application-skeleton
    - android-manifest-privacy-lock
    - instrumented-test-infrastructure
  affects:
    - 01-02 (Room database module depends on version catalog and Hilt skeleton)
    - 01-03 (Inference service depends on Hilt skeleton and test runner)
tech_stack:
  added:
    - "litertlm-android:0.10.2 — LiteRT-LM runtime for Gemma 4 E2B on-device inference"
    - "litert:1.0.1 — LiteRT core runtime (for Phase 3 embedding)"
    - "litert-gpu:1.0.1 — LiteRT GPU delegate (for Phase 3 embedding)"
    - "room:2.7.1 — Pre-populated SQLite knowledge base"
    - "hilt:2.57.2 — Dependency injection (upgraded from planned 2.54)"
    - "kotlin:2.3.0 — Kotlin compiler (upgraded from planned 2.1.0)"
    - "agp:8.7.3 — Android Gradle Plugin"
    - "ksp:2.3.0 — Kotlin Symbol Processing"
    - "coroutines:1.9.0 — Kotlin coroutines"
    - "compose-bom:2026.03.00 — Jetpack Compose BOM"
  patterns:
    - "@HiltAndroidApp on Application class triggers compile-time DI graph generation"
    - "@AndroidEntryPoint on Activity enables field injection"
    - "KSP processors (not kapt) for Room and Hilt compilation"
    - "HiltTestRunner substitutes HiltTestApplication for instrumented tests"
key_files:
  created:
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - gradle/wrapper/gradle-wrapper.jar
    - gradlew
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
    - app/proguard-rules.pro
    - gradle.properties
    - app/src/main/AndroidManifest.xml
    - app/src/main/kotlin/com/safeglow/edge/MainApplication.kt
    - app/src/main/kotlin/com/safeglow/edge/MainActivity.kt
    - docs/MODEL_DEPLOY.md
    - app/src/androidTest/java/com/safeglow/edge/HiltTestRunner.kt
    - app/src/androidTest/java/com/safeglow/edge/LiteRTNoNetworkTest.kt
    - app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt
    - app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt
    - app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt
    - app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt
  modified: []
decisions:
  - "Upgraded Hilt from 2.54 to 2.57.2: dagger-spi 2.57+ no longer bundles shaded kotlin-metadata, allowing Gradle resolution strategy to supply kotlin-metadata-jvm 2.3.21 which handles Kotlin 2.3.0 metadata emitted by litertlm-android:0.10.2"
  - "Upgraded Kotlin from 2.1.0 to 2.3.0: litertlm-android:0.10.2 was compiled with Kotlin 2.3.0 — using 2.1.0 caused metadata version mismatch during KSP processing"
  - "Fixed litert-gpu from 2.3.0 to 1.0.1: version 2.3.0 does not exist on Maven Central; 1.0.1 matches litert runtime and is published"
  - "Added gradle.properties with android.useAndroidX=true: required for AGP to accept AndroidX dependencies"
  - "Switched to compileOptions JavaVersion.VERSION_21: Kotlin 2.3.0 targets JVM 21 by default; must match compileOptions to avoid JVM-target inconsistency error"
  - "Added kotlin-metadata-jvm:2.3.21 force in root build.gradle.kts: ensures classpath-resolved version is new enough for Hilt 2.57.2"
metrics:
  duration_minutes: 28
  completed_date: "2026-04-26"
  tasks_completed: 3
  tasks_total: 3
  files_created: 19
  files_modified: 0
---

# Phase 1 Plan 1: Project Scaffold, Version Catalog, and Privacy-Locked Build Configuration Summary

Greenfield Android project scaffolded with pinned Gradle version catalog, Hilt 2.57.2 DI skeleton, privacy-locked AndroidManifest (zero permissions), and 6 instrumented test stub files — APK builds at 77 MB under the 150 MB SC-1 gate.

## What Was Built

### Task 1: Version Catalog and Gradle Build Configuration

Created the complete Gradle build infrastructure from scratch:

- `gradle/libs.versions.toml` — 10 pinned library versions, 19 library aliases, 5 plugin aliases
- `settings.gradle.kts` — project name "SafeGlowEdge", google/mavenCentral repos, `:app` module
- `build.gradle.kts` — root plugins with `apply false`, kotlin-metadata-jvm force resolution
- `app/build.gradle.kts` — KSP processors (no kapt), Room schema export, HiltTestRunner, minSdk 26 / compileSdk 35
- `gradle.properties` — `android.useAndroidX=true`, `android.nonTransitiveRClass=true`
- Gradle wrapper (gradlew + gradle-wrapper.jar + gradle-wrapper.properties for Gradle 8.11.1)

Verification: `./gradlew help` exits 0.

### Task 2: Hilt Application Skeleton, AndroidManifest, and Operator Deploy Doc

- `app/src/main/AndroidManifest.xml` — zero `<uses-permission>` entries (PRIV-01/PRIV-03); declares MainApplication and MainActivity
- `app/src/main/kotlin/com/safeglow/edge/MainApplication.kt` — `@HiltAndroidApp class MainApplication : Application()`
- `app/src/main/kotlin/com/safeglow/edge/MainActivity.kt` — `@AndroidEntryPoint` with placeholder Compose Surface
- `docs/MODEL_DEPLOY.md` — adb push runbook for Gemma 4 E2B 2.58 GB model

Verification: `./gradlew assembleDebug` exits 0. APK size: **81,069,762 bytes (77.3 MB)** — SC-1 gate passes.

### Task 3: Instrumented Test Infrastructure

Six Kotlin files in `app/src/androidTest/java/com/safeglow/edge/`:

| File | Status | Covers |
|------|--------|--------|
| `HiltTestRunner.kt` | Full — substitutes `HiltTestApplication` | Test infrastructure |
| `LiteRTNoNetworkTest.kt` | Full — asserts `PERMISSION_DENIED` for INTERNET and ACCESS_NETWORK_STATE | PRIV-01, PRIV-03, T-1-01 |
| `InferenceServiceTest.kt` | @Ignore stub | SC-2 (Plan 03) |
| `GpuFallbackTest.kt` | @Ignore stub | SC-3 (Plan 03) |
| `HiltRotationTest.kt` | @Ignore stub | SC-4 (Plan 03) |
| `RoomSeedTest.kt` | @Ignore stub | SC-5 (Plan 02) |

Verification: `./gradlew compileDebugAndroidTestKotlin` exits 0.

## Pinned Versions (libs.versions.toml)

```toml
litertlm = "0.10.2"    # verified published 2026-04-17; required for Gemma 4 E2B
litert = "1.0.1"
litert-gpu = "1.0.1"   # deviation: plan specified 2.3.0 which does not exist
room = "2.7.1"
hilt = "2.57.2"         # deviation: plan specified 2.54; see deviations
kotlin = "2.3.0"        # deviation: plan specified 2.1.0; see deviations
agp = "8.7.3"
ksp = "2.3.0"           # deviation: plan specified 2.1.0-1.0.29; upgraded to match Kotlin 2.3.0
coroutines = "1.9.0"
compose-bom = "2026.03.00"
```

## APK Debug Size

`app/build/outputs/apk/debug/app-debug.apk`: **81,069,762 bytes (77.3 MB)**

SC-1 gate: < 157,286,400 bytes (150 MB). **PASS.**

## Privacy Verification

- `AndroidManifest.xml` zero `<uses-permission>` elements (verified with XML comment stripping): **PASS**
- `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -E 'firebase|amplitude|mixpanel|datadog|google-auth'` → empty: **PASS (PRIV-02 / T-1-02)**

## Hilt Compilation Gate

`./gradlew assembleDebug` exits 0. Hilt generates `Hilt_MainApplication.java` at compile time — confirms no `[Dagger/DependencyCycle]` errors and KSP processor resolves correctly. **PASS.**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] litert-gpu version 2.3.0 does not exist on Maven Central**
- **Found during:** Task 1 — `./gradlew assembleDebug` failed resolving `litert-gpu:2.3.0`
- **Issue:** Plan specified `litert-gpu = "2.3.0"` but Maven Central only has versions up to 1.4.2; 2.3.0 has never been published
- **Fix:** Updated to `litert-gpu = "1.0.1"` to match `litert:1.0.1`
- **Files modified:** `gradle/libs.versions.toml`
- **Commit:** 62b3131

**2. [Rule 1 - Bug] Kotlin 2.1.0 incompatible with litertlm-android:0.10.2 (Kotlin 2.3.0 metadata)**
- **Found during:** Task 2 — KSP processing of litertlm-android:0.10.2 failed because Kotlin 2.1.0 produces metadata `2.1.0` but the library emits `2.3.0` metadata
- **Issue:** `litertlm-android:0.10.2` was compiled with Kotlin 2.3.0. The Kotlin metadata version mismatch caused a compilation error during KSP processing
- **Fix:** Upgraded `kotlin = "2.1.0"` → `"2.3.0"` and `ksp = "2.1.0-1.0.29"` → `"2.3.0"`. Also changed `compileOptions` from `VERSION_17` to `VERSION_21` (Kotlin 2.3.0 defaults to JVM 21)
- **Files modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts`
- **Commit:** 62b3131

**3. [Rule 1 - Bug] Hilt 2.54 dagger-spi bundles shaded kotlin-metadata with max version 2.2.0**
- **Found during:** Task 2 — after Kotlin upgrade, Hilt's annotation processor still rejected Kotlin 2.3.0 metadata: `"Provided Metadata instance has version 2.3.0, while maximum supported version is 2.2.0"`
- **Issue:** dagger-spi 2.54 bundles `kotlin-metadata-jvm` as shaded classes under `dagger/spi/internal/shaded/androidx/room/jarjarred/kotlin/metadata/`. The shaded copy supports max 2.2.0. Gradle resolution strategies cannot override shaded classes
- **Investigation:** Tested dagger-spi across versions; dagger-spi 2.57 removed the kotlin/metadata bundling (0 bundled classes vs 990 in 2.54)
- **Fix:** Upgraded `hilt = "2.54"` → `"2.57.2"`. Added `resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.21")` in root `build.gradle.kts` as defense-in-depth
- **Files modified:** `gradle/libs.versions.toml`, `build.gradle.kts`
- **Commit:** 62b3131

**4. [Rule 3 - Blocking] Missing gradle.properties with android.useAndroidX=true**
- **Found during:** Task 2 — `checkDebugAarMetadata` failed: "Configuration contains AndroidX dependencies, but the android.useAndroidX property is not enabled"
- **Issue:** Greenfield project had no `gradle.properties`; AGP requires `android.useAndroidX=true` to accept AndroidX dependencies
- **Fix:** Created `gradle.properties` with `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, and JVM args
- **Files modified:** `gradle.properties` (created)
- **Commit:** 62b3131

**5. [Rule 3 - Blocking] kotlinOptions { jvmTarget } removed in Kotlin 2.3.0**
- **Found during:** Task 2 — build script compilation failed: `Using 'jvmTarget: String' is an error. Please migrate to the compilerOptions DSL`
- **Issue:** Kotlin 2.3.0 made `kotlinOptions { jvmTarget }` an error. Must use either `kotlin { jvmToolchain() }` or rely on `compileOptions` matching the Kotlin default JVM target
- **Fix:** Removed `kotlinOptions` block; updated `compileOptions` to `VERSION_21` to match Kotlin 2.3.0's default JVM 21 target
- **Files modified:** `app/build.gradle.kts`
- **Commit:** 62b3131

## Known Stubs

The following test files are intentional stubs — downstream plans must implement the test bodies:

| File | Plan | Stub type |
|------|------|-----------|
| `InferenceServiceTest.kt` | Plan 03 | @Ignore'd test method with TODO comment |
| `GpuFallbackTest.kt` | Plan 03 | @Ignore'd test method with TODO comment |
| `HiltRotationTest.kt` | Plan 03 | @Ignore'd test method with TODO comment |
| `RoomSeedTest.kt` | Plan 02 | @Ignore'd test method with TODO comment |

These stubs are intentional per the plan — they establish the test infrastructure shape for downstream plans. `LiteRTNoNetworkTest.kt` is fully implemented (no @Ignore).

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes beyond what is in the plan's threat model.

- T-1-01 mitigated: `LiteRTNoNetworkTest` asserts `PERMISSION_DENIED` for INTERNET
- T-1-02 mitigated: `releaseRuntimeClasspath` dependency audit found no analytics/auth SDKs
- T-1-03 mitigated: No user-facing model path surface in Phase 1 (path hardcoded in Plan 03)
- T-1-04 mitigated: KSP Hilt compilation succeeded with no `[Dagger/DependencyCycle]` errors

## Self-Check: PASSED

All 15 source files exist. All 3 task commits (ee2b3e8, 62b3131, a49481a) verified in git log.

| Check | Result |
|-------|--------|
| gradle/libs.versions.toml | FOUND |
| settings.gradle.kts | FOUND |
| build.gradle.kts | FOUND |
| app/build.gradle.kts | FOUND |
| gradle.properties | FOUND |
| app/src/main/AndroidManifest.xml | FOUND |
| MainApplication.kt | FOUND |
| MainActivity.kt | FOUND |
| docs/MODEL_DEPLOY.md | FOUND |
| HiltTestRunner.kt | FOUND |
| LiteRTNoNetworkTest.kt | FOUND |
| InferenceServiceTest.kt | FOUND |
| GpuFallbackTest.kt | FOUND |
| HiltRotationTest.kt | FOUND |
| RoomSeedTest.kt | FOUND |
| commit ee2b3e8 | FOUND |
| commit 62b3131 | FOUND |
| commit a49481a | FOUND |
