---
phase: 1
slug: foundation-model-validation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-25
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android Instrumented Tests (androidx.test + JUnit 4) + Unit Tests |
| **Config file** | `app/src/androidTest/` (instrumented) and `app/src/test/` (unit) |
| **Quick run command** | `./gradlew testDebugUnitTest` |
| **Full suite command** | `./gradlew connectedDebugAndroidTest` (physical device required) |
| **Estimated runtime** | ~90 seconds (unit); ~5 minutes (instrumented on device) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest`
- **After every plan wave:** Run `./gradlew connectedDebugAndroidTest` on physical device
- **Before `/gsd-verify-work`:** Full instrumented suite must be green
- **Max feedback latency:** 90 seconds (unit); 5 minutes (instrumented)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 1-SC1 | 01 | 1 | SC-1 | — | APK < 150 MB (model not bundled) | Build verify | `./gradlew assembleDebug && ls -lh app/build/outputs/apk/debug/app-debug.apk` | ❌ W0 | ⬜ pending |
| 1-SC2 | 01 | 2 | SC-2 | — | Engine.initialize() + token output without crash | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.InferenceServiceTest"` | ❌ W0 | ⬜ pending |
| 1-SC3 | 01 | 2 | SC-3 | — | GPU init falls back to CPU without exception | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.GpuFallbackTest"` | ❌ W0 | ⬜ pending |
| 1-SC4 | 01 | 3 | SC-4 | — | Hilt graph resolves; ViewModel survives rotation without re-init | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.HiltRotationTest"` | ❌ W0 | ⬜ pending |
| 1-SC5 | 01 | 3 | SC-5 | — | Room opens from asset; 10 seed records readable | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.RoomSeedTest"` | ❌ W0 | ⬜ pending |
| 1-PRIV01 | 01 | 2 | PRIV-01 | T-1-01 | No network calls during inference | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.LiteRTNoNetworkTest"` | ❌ W0 | ⬜ pending |
| 1-PRIV02 | 01 | 1 | PRIV-02 | — | No auth SDK in dependency graph | Unit (dep check) | `./gradlew dependencies \| grep -E "firebase\|auth\|amplitude\|mixpanel"` | ❌ W0 | ⬜ pending |
| 1-PRIV03 | 01 | 1 | PRIV-03 | T-1-02 | No INTERNET permission in AndroidManifest | Static / lint | `./gradlew lint && grep -c "INTERNET" app/src/main/AndroidManifest.xml` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt` — stubs for SC-2
- [ ] `app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt` — stubs for SC-3
- [ ] `app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt` — stubs for SC-4
- [ ] `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt` — stubs for SC-5
- [ ] `app/src/androidTest/java/com/safeglow/edge/LiteRTNoNetworkTest.kt` — stubs for PRIV-01
- [ ] `app/src/androidTest/java/com/safeglow/edge/HiltTestRunner.kt` — shared Hilt instrumented test runner
- [ ] Framework install: Android SDK + androidx.test already available — no additional install required

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Gemma 4 E2B model loads from filesDir on physical device | SC-2 | Requires 2.58 GB model file pre-staged via adb push; cannot be automated without model present | 1. `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.safeglow.edge/files/` 2. Install APK 3. Check Logcat for "GPU init failed" or "Engine initialized" 4. Verify first token appears in Logcat |
| GPU vs CPU path determined on demo device | SC-3 | Depends on physical device OpenCL support — cannot know until first run | Check Logcat for "GPU init failed, falling back to CPU" or "Engine(Backend.GPU) initialized" within first 30 minutes of Phase 1 |
| PRIV-02 no auth SDK | PRIV-02 | Dependency graph audit needs human review of output | Run `./gradlew dependencies` and visually confirm no `firebase-auth`, `google-auth`, account-related SDK in output |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s (unit) / 5m (instrumented)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
