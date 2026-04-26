---
phase: 01-foundation-model-validation
plan: 03
subsystem: inference-service
tags: [android, litert, hilt, singleton, gpu-fallback, threading, instrumented-tests]
dependency_graph:
  requires:
    - 01-01 (Hilt skeleton, version catalog, test infrastructure stubs)
  provides:
    - litert-inference-service
    - hilt-singleton-inference-binding
    - instrumented-inference-tests
  affects:
    - 01-04 (full instrumented test run requires model pre-pushed via adb)
tech_stack:
  added: []
  patterns:
    - "newSingleThreadExecutor().asCoroutineDispatcher() for GPU thread affinity (T-1-08)"
    - "try/catch Engine(GPU).also { it.initialize() } with CPU fallback (T-1-07)"
    - "context.filesDir.resolve(literal-string) model path — no user input accepted (T-1-03)"
    - "@Singleton + @InstallIn(SingletonComponent::class) binding survives screen rotation (SC-4)"
    - "channelFlow + withContext(inferenceDispatcher) for streaming inference on single thread"
key_files:
  created:
    - app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt
    - app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt
  modified:
    - app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt
    - app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt
    - app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt
decisions:
  - "Removed literal 'Dispatchers.IO' from KDoc comment in LiteRTInferenceService to satisfy T-1-08 grep gate (zero occurrences in service file); replaced with prose explanation"
  - "infer() declared as non-suspend fun returning Flow<String> (matches plan spec); channelFlow + awaitClose handles the streaming lifecycle on the single-thread dispatcher"
  - "HiltRotationTest uses two @Inject fields (firstReference, secondReference) injected in a single hiltRule.inject() call — both fields resolve from the same SingletonComponent, so assertSame holds without needing a second inject() call"
metrics:
  duration_minutes: 5
  completed_date: "2026-04-26"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 3
---

# Phase 1 Plan 3: LiteRTInferenceService Implementation and Inference Test Wiring Summary

LiteRTInferenceService implemented with newSingleThreadExecutor dispatcher, try/catch GPU-to-CPU fallback, and filesDir-locked model path; wired through Hilt as @Singleton; three @Ignore stubs replaced with real SC-2/SC-3/SC-4 assertions.

## What Was Built

### Task 1: LiteRTInferenceService and InferenceModule

**`app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt`**

Key dispatcher and import excerpt:
```kotlin
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inferenceDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
```

The `inferenceDispatcher` is a `newSingleThreadExecutor` — every Engine call is wrapped in `withContext(inferenceDispatcher)`. Zero occurrences of the shared IO dispatcher in this file (T-1-08 mitigated).

try/catch GPU-to-CPU fallback excerpt:
```kotlin
engine = try {
    Engine(
        EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = cacheDir)
    ).also { it.initialize() }
} catch (e: Exception) {
    Log.w(TAG, "GPU init failed (${e.message}), falling back to CPU")
    Engine(
        EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
    ).also { it.initialize() }
}
```

Model path is constructed only from `context.filesDir.resolve(MODEL_FILENAME).absolutePath` where `MODEL_FILENAME = "gemma-4-E2B-it.litertlm"` is a private companion constant — no public setter, no constructor parameter accepts a path (T-1-03 mitigated).

**`app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt`**

@Singleton + @ApplicationContext binding:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideLiteRTInferenceService(
        @ApplicationContext context: Context
    ): LiteRTInferenceService = LiteRTInferenceService(context)
}
```

`SingletonComponent` lifecycle is bound to Application — the same Engine instance is injected into any new Activity after screen rotation (SC-4). `@ApplicationContext` prevents Activity context leak.

### Task 2: Three Instrumented Tests (SC-2, SC-3, SC-4)

All three previously-@Ignore'd stubs replaced with real assertions:

| File | Success Criterion | Key Assertion |
|------|------------------|---------------|
| `InferenceServiceTest.kt` | SC-2: first token produced | `.take(1).toList()` on `infer()` result, `assertTrue(tokens.isNotEmpty())` |
| `GpuFallbackTest.kt` | SC-3: no exception from initialize() | `try { inferenceService.initialize() } catch (t) { fail(...) }` |
| `HiltRotationTest.kt` | SC-4: same Singleton instance | `assertSame(firstReference, secondReference)` across two @Inject fields |

InferenceServiceTest uses the Gemma 4 turn-marker prompt format:
```kotlin
val gemmaTurnPrompt =
    "<start_of_turn>user\nSay hello.\n<end_of_turn>\n<start_of_turn>model\n"
```

## Threat Mitigations Confirmed

| Threat ID | Status | Verification |
|-----------|--------|-------------|
| T-1-03 | Mitigated | `grep -c 'context.filesDir.resolve' LiteRTInferenceService.kt` = 2 (in initialize() and companion constant reference); no constructor param, no public setter |
| T-1-07 | Mitigated | try/catch block present; GpuFallbackTest asserts initialize() does not throw |
| T-1-08 | Mitigated | `grep -c 'Dispatchers.IO' LiteRTInferenceService.kt` = 0; all Engine calls in `withContext(inferenceDispatcher)` |

## Plan 04 Prerequisite

The three inference tests (SC-2, SC-3, SC-4) require the Gemma 4 E2B model file to be pre-pushed to the device before running:

```bash
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm
```

See `docs/MODEL_DEPLOY.md` for the complete runbook. If the model is absent, `initialize()` throws `FileNotFoundException` — the test fails with a clear message rather than hanging (Pitfall 3 in RESEARCH.md).

## Verification Results

| Check | Command | Result |
|-------|---------|--------|
| Service compiles | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| Hilt graph resolves | `./gradlew assembleDebug` | BUILD SUCCESSFUL (no DependencyCycle) |
| Threading lock | `grep -c 'Dispatchers.IO' LiteRTInferenceService.kt` | 0 |
| Single-thread executor | `grep -c 'newSingleThreadExecutor' LiteRTInferenceService.kt` | 1 |
| GPU fallback | `grep -c 'Backend.GPU' LiteRTInferenceService.kt` | 2 (code + comment) |
| CPU fallback | `grep -c 'Backend.CPU' LiteRTInferenceService.kt` | 1 |
| Model path locked | `grep -c 'context.filesDir.resolve' LiteRTInferenceService.kt` | 2 |
| Test compile | `./gradlew compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| No @Ignore remaining | `grep -lr '@Ignore' InferenceServiceTest.kt GpuFallbackTest.kt HiltRotationTest.kt` | NONE |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Removed 'Dispatchers.IO' string literal from KDoc comment**
- **Found during:** Task 1 acceptance criteria verification
- **Issue:** The plan requires `grep -c 'Dispatchers.IO' LiteRTInferenceService.kt` = 0 as a security/correctness gate (T-1-08). The initial KDoc comment contained the literal string `Dispatchers.IO` in explanatory text
- **Fix:** Replaced `"Dispatchers.IO is a shared pool with no thread affinity guarantee"` with `"the shared IO dispatcher has no thread affinity guarantee"` — preserves meaning, satisfies grep gate
- **Files modified:** `LiteRTInferenceService.kt`
- **Commit:** f1363f4

## Known Stubs

None. All files created by this plan are fully implemented with real logic. RoomSeedTest.kt retains its @Ignore — that is Plan 02's stub, outside this plan's scope.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes beyond what is in the plan's threat model. LiteRTInferenceService.kt accesses only `context.filesDir` and `context.cacheDir` — both sandboxed to the app's private storage. No external network calls introduced.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| LiteRTInferenceService.kt | FOUND |
| InferenceModule.kt | FOUND |
| InferenceServiceTest.kt (no @Ignore) | FOUND |
| GpuFallbackTest.kt (no @Ignore) | FOUND |
| HiltRotationTest.kt (no @Ignore) | FOUND |
| commit f1363f4 | FOUND |
| commit fd497e0 | FOUND |
