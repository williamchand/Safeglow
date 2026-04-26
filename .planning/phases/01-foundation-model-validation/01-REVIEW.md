---
phase: 01-foundation-model-validation
reviewed: 2026-04-26T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt
  - app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt
  - app/src/androidTest/java/com/safeglow/edge/HiltTestRunner.kt
  - app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt
  - app/src/androidTest/java/com/safeglow/edge/LiteRTNoNetworkTest.kt
  - app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt
  - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/AppDatabase.kt
  - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientFts.kt
  - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientRecord.kt
  - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt
  - app/src/main/kotlin/com/safeglow/edge/data/knowledge/schema/KB_SCHEMA.md
  - app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt
  - app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt
  - app/src/main/kotlin/com/safeglow/edge/MainActivity.kt
  - app/src/main/kotlin/com/safeglow/edge/MainApplication.kt
  - build.gradle.kts
  - docs/MODEL_DEPLOY.md
  - gradle.properties
  - gradle/libs.versions.toml
  - settings.gradle.kts
  - tools/build_seed_db.sh
findings:
  critical: 2
  warning: 5
  info: 2
  total: 9
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-04-26T00:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

This is the Phase 1 foundation scaffold for SafeGlow Edge: an on-device Gemma 4 E2B inference service, a Room knowledge-base backed by a prebuilt SQLite asset, and a Hilt dependency graph. The architecture is sound and the privacy posture (no network permissions, filesDir-only model path) is correctly implemented.

Two critical issues were found. The most impactful is a path mismatch in the model deployment documentation that will cause `InferenceServiceTest` to fail with `FileNotFoundException` on every device — the adb push command targets external storage while `context.filesDir` resolves to internal storage. The second critical issue is a double-initialization race condition in `LiteRTInferenceService` that allows two engines to be created and one to be leaked.

Five warnings cover a CPU fallback that can itself crash silently, a non-thread-safe `close()`, a redundant Hilt binding that risks a future duplicate-binding compile error, a fragile FTS rowid join, and a hardcoded Gradle JVM path that will break CI.

---

## Critical Issues

### CR-01: Model path in MODEL_DEPLOY.md points to external storage; code reads internal storage

**File:** `docs/MODEL_DEPLOY.md:21`
**Issue:** The `adb push` command pushes the model to `/sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm` (external storage). The inference service resolves the path as `context.filesDir.resolve(...)`, which maps to `/data/data/com.safeglow.edge/files/` (internal storage). These are different directories. The verification step in step 5 even shows the correct internal path (`/data/data/...`), confirming the step 4 push destination is wrong. Every run of `InferenceServiceTest` on a freshly set-up device will hit `FileNotFoundException`.

**Fix:** Change the `adb push` destination in step 4:
```
# Wrong (external storage):
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm

# Correct (internal storage, requires run-as):
adb push gemma-4-E2B-it.litertlm /data/local/tmp/gemma-4-E2B-it.litertlm
adb shell run-as com.safeglow.edge cp /data/local/tmp/gemma-4-E2B-it.litertlm files/gemma-4-E2B-it.litertlm
```
Or use the external-storage path but update the service to use `context.getExternalFilesDir(null)` instead of `context.filesDir`. The two-step copy approach is recommended because it works on both rooted and non-rooted devices without requiring `WRITE_EXTERNAL_STORAGE`.

---

### CR-02: Double-initialization race condition in LiteRTInferenceService.initialize()

**File:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt:47-71`
**Issue:** The null-check on line 48 (`if (engine != null) return@withContext`) and the assignment on line 53 (`engine = try { ... }`) are both executed inside `withContext(inferenceDispatcher)`, which is a single-thread dispatcher. This means sequential calls are safe. However, `@Volatile` alone does not make the check-then-act atomic. If two coroutines call `initialize()` concurrently before the dispatcher processes the first one, both will be queued and will execute serially on the single thread — the second will see `engine != null` and return early. The dispatcher does enforce ordering here.

**However**, the deeper race is in `close()` at lines 87-91: `close()` runs on the caller's thread (not `inferenceDispatcher`), sets `engine = null`, then closes `inferenceDispatcher`. If `infer()` is executing concurrently on `inferenceDispatcher` when `close()` is called, `close()` will close the dispatcher mid-execution, causing `ClosedSendChannelException` or `CancellationException` to leak into the flow collector. More critically, `inferenceDispatcher.close()` on a running dispatcher can cause the in-flight coroutine to be interrupted without cleanup.

**Fix:** Route `close()` through the inference dispatcher as well, or synchronize it with a mutex:
```kotlin
fun close() {
    // Dispatch close onto the same single-threaded executor to avoid
    // racing with an in-flight infer() call.
    val executor = (inferenceDispatcher as? ExecutorCoroutineDispatcher)?.executor
    executor?.submit {
        engine?.close()
        engine = null
    }?.get() // block until close completes
    inferenceDispatcher.close()
}
```
Alternatively, use a `Mutex` to guard `engine` reads and writes across `initialize()`, `infer()`, and `close()`.

---

## Warnings

### WR-01: CPU fallback in initialize() has no error handling — model-file-absent error is lost

**File:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt:61-70`
**Issue:** The `catch (e: Exception)` block on line 61 catches GPU-init failures and falls back to CPU. The CPU `Engine(...).also { it.initialize() }` call on lines 62-69 has no exception handler. If the model file is absent or corrupt, the CPU init will throw (e.g., `FileNotFoundException`) but the exception will propagate out of the `catch` block as an unhandled exception from `initialize()`. The log message at line 62 (`"GPU init failed ... falling back to CPU"`) will have already been emitted, misleading the developer into thinking the error is GPU-related. `GpuFallbackTest` will also pass on a GPU device (GPU succeeds) but hide this latent failure path.

**Fix:** Wrap the CPU fallback in its own try/catch with a distinct log tag, or re-throw with a clear message:
```kotlin
} catch (e: Exception) {
    Log.w(TAG, "GPU init failed (${e.message}), falling back to CPU")
    try {
        Engine(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        ).also { it.initialize() }
    } catch (cpuEx: Exception) {
        throw IllegalStateException(
            "CPU fallback also failed — model file missing or corrupt at $modelPath",
            cpuEx
        )
    }
}
```

---

### WR-02: InferenceModule provides LiteRTInferenceService via @Provides while the class also has @Inject constructor — latent duplicate binding

**File:** `app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt:28-31`
**Issue:** `LiteRTInferenceService` is annotated with `@Singleton` and has `@Inject constructor(...)` (making it directly injectable), AND `InferenceModule.provideLiteRTInferenceService()` provides it again via `@Provides`. Hilt currently resolves this without error because a `@Provides` method takes precedence over `@Inject constructor` when both exist in the same component. However, this is a confusing dual-registration that makes it unclear which binding is authoritative. If someone adds `@Singleton` to the class-level `@Inject constructor` directly (which it already implicitly has via the class annotation), Hilt may emit a duplicate-binding compile error in a future version or after a Hilt upgrade.

**Fix:** Remove `@Inject constructor` from `LiteRTInferenceService` and keep the `@Provides` method (preferred since it matches the module's purpose), or remove `InferenceModule` entirely and rely solely on the `@Inject constructor` + `@Singleton` annotation on the class. Do not use both simultaneously.

---

### WR-03: FTS rowid join is fragile under delete operations

**File:** `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt:15-20`
**Issue:** The FTS query joins `ingredients_fts` to `ingredients` on `i.rowid = fts.rowid`. For a Room FTS4 content table, the FTS shadow table stores the content row's `rowid` as `docid`. Joins on `rowid` are correct for append-only datasets, but if any ingredient row is deleted and re-inserted (e.g., during a DB migration or a future seed-update script that does DELETE + INSERT), the `id` (AUTOINCREMENT) will not match the original `rowid`. The FTS shadow table's `docid` will then point to a stale or nonexistent row, silently returning wrong results or no results.

**Fix:** Use the standard Room FTS content-table query pattern which joins on `rowid` explicitly via the content table's `rowid` column (safe for a read-only/append-only seed database, which this is in Phase 1). Add a code comment documenting that this join is only safe because the seed database is append-only and never deletes rows:
```kotlin
// NOTE: rowid join is correct only for append-only datasets.
// If rows are ever deleted and re-inserted, FTS docid and rowid diverge.
@Query(
    "SELECT i.* FROM ingredients i " +
    "JOIN ingredients_fts fts ON i.rowid = fts.rowid " +
    "WHERE ingredients_fts MATCH :query LIMIT 10"
)
suspend fun ftsSearch(query: String): List<IngredientRecord>
```

---

### WR-04: Seed data uses underscores in INCI names for two records — violates canonical INCI format

**File:** `tools/build_seed_db.sh:65-66`
**Issue:** The seed insert at line 65 uses `'RETINYL_PALMITATE'` and line 66 uses `'SALICYLIC_ACID'` as `inciName` values. Canonical INCI names use spaces, not underscores: the correct names are `RETINYL PALMITATE` and `SALICYLIC ACID`. Every other record in the seed insert uses space-free single-word names (which happen to be the same with or without spaces). Callers of `findExact("RETINYL PALMITATE")` — the expected normalized form from an ingredient scanner — will return `null`, failing silently. The FTS index will also index `RETINYL_PALMITATE` as a single token, so partial-word queries like `RETINYL` will not match.

**Fix:**
```sql
-- Line 65: change RETINYL_PALMITATE to 'RETINYL PALMITATE'
-- Line 66: change SALICYLIC_ACID to 'SALICYLIC ACID'
('RETINYL PALMITATE', 'Retinyl palmitate', ...),
('SALICYLIC ACID', 'Beta hydroxy acid', ...),
```
Also update `RoomSeedTest.methylparabenSeedRecordPresentWithExpectedTag` — a companion test for `findExact("SALICYLIC ACID")` should be added to catch this class of issue on future seed edits.

---

### WR-05: gradle.properties hardcodes a macOS-only JDK path — CI on Linux will fail

**File:** `gradle.properties:5`
**Issue:** `org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home` is a macOS path. On any Linux CI agent (GitHub Actions, etc.), this path does not exist and Gradle will fail to start with `Could not find or use the Java installation specified`. The path is not in `.gitignore`, so it will be committed.

**Fix:** Remove the `org.gradle.java.home` line from `gradle.properties` and let each developer/CI environment set it via their `$JAVA_HOME` or via a local `gradle.properties` (which should be in `.gitignore`). Alternatively, add a `local.properties`-style override mechanism and add `local.properties` to `.gitignore`.

---

## Info

### IN-01: Release build has minification disabled — ProGuard rules file is empty

**File:** `app/build.gradle.kts:23-26`
**Issue:** `isMinifyEnabled = false` in the release build type means the empty `proguard-rules.pro` has no effect. LiteRT uses reflection internally, so minification requires careful keep-rules — disabling it is a reasonable Phase 1 choice. However, the build config still references `proguard-rules.pro`, implying minification may be enabled later. When that happens, the empty rules file will likely cause LiteRT and Room DAO classes to be stripped, crashing at runtime.

**Fix:** Add a comment to `proguard-rules.pro` documenting which keep-rules will be required when `isMinifyEnabled` is eventually set to `true`:
```proguard
# LiteRT / LiteRT-LM: keep all classes in the litert package
# -keep class com.google.ai.edge.** { *; }

# Room: keep generated DAO implementation classes
# -keep class * extends androidx.room.RoomDatabase { *; }

# Hilt: keep component entry points
# -keep @dagger.hilt.android.HiltAndroidApp class * { *; }
```

---

### IN-02: ksp version in libs.versions.toml does not include the Kotlin version suffix

**File:** `gradle/libs.versions.toml:9`
**Issue:** The KSP version is declared as `ksp = "2.3.0"` but the canonical KSP version format is `{kotlin-version}-{ksp-release}`, e.g., `2.3.0-1.0.29`. Using a bare `2.3.0` may resolve to an unexpected artifact version or fail to resolve if Maven Central does not have that exact string. The root `build.gradle.kts` forces `kotlin-metadata-jvm` to `2.3.21` (a non-released version at time of writing), suggesting version resolution is already being manually managed.

**Fix:** Verify the exact artifact coordinate for the KSP plugin. For Kotlin 2.3.0, the correct KSP version is `2.3.0-1.0.29` (or the latest patch for that Kotlin version):
```toml
ksp = "2.3.0-1.0.29"
```
Check https://github.com/google/ksp/releases for the correct suffix.

---

_Reviewed: 2026-04-26T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
