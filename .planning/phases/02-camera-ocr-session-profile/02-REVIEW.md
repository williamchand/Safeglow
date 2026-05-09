---
phase: 02-camera-ocr-session-profile
reviewed: 2026-05-09T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - app/src/main/kotlin/com/safeglow/edge/camera/CameraScreen.kt
  - app/src/main/kotlin/com/safeglow/edge/camera/CameraViewModel.kt
  - app/src/main/kotlin/com/safeglow/edge/ocr/OcrRepository.kt
  - app/src/main/kotlin/com/safeglow/edge/ocr/MLKitOcrProcessor.kt
  - app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt
  - app/src/main/kotlin/com/safeglow/edge/normalization/INCISynonymMap.kt
  - app/src/main/kotlin/com/safeglow/edge/normalization/LevenshteinDistance.kt
  - app/src/main/kotlin/com/safeglow/edge/session/SessionProfile.kt
  - app/src/main/kotlin/com/safeglow/edge/session/SessionViewModel.kt
  - app/src/main/kotlin/com/safeglow/edge/session/ProfileScreen.kt
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - app/src/main/AndroidManifest.xml
  - tools/build_seed_db.sh
findings:
  critical: 2
  warning: 5
  info: 4
  total: 11
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-05-09T00:00:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Reviewed the Camera/OCR pipeline, INCI normalization stack, Session Profile UI, and supporting build configuration for Phase 2. The overall architecture is sound: the CameraX tap-to-capture flow, ML Kit bundled-model integration, four-stage INCI normalizer, and ephemeral SessionProfile StateFlow all follow correct patterns. No hardcoded secrets or injection vulnerabilities were found.

Two critical issues were identified: a shell script bug that will silently report a wrong row count at every run (wrong table name in the verification SELECT), and a resource leak path in `OcrRepository` where `ImageProxy` is never closed when the ML Kit task is cancelled mid-flight. Five warnings cover logic gaps in the normalization pipeline (double Levenshtein compute, Fuzzy match against wrong base, FTS query that likely returns no results, a no-op synonym entry) and an error-state swallow in `bindCamera`. Four informational items note a disabled release minification, a dependency inconsistency, dead import, and a magic number.

---

## Critical Issues

### CR-01: Shell script queries wrong table name — verification always fails or crashes

**File:** `tools/build_seed_db.sh:208`

**Issue:** The verification `SELECT` at line 208 queries `ingredient` (singular) but the table created at line 48 is named `ingredients` (plural). On every run `sqlite3` returns an error (`no such table: ingredient`), the shell exits because `set -euo pipefail` is active, and the `DATA-01 PASSED` gate is never reachable. Even if the error were suppressed, `ROW_COUNT` would be empty, and the arithmetic comparison `[ "$ROW_COUNT" -eq 80 ]` would produce a bash error under `set -u`.

**Fix:**
```bash
# Line 208 — change:
ROW_COUNT=$(sqlite3 "$OUT" "SELECT COUNT(*) FROM ingredient;")
# To:
ROW_COUNT=$(sqlite3 "$OUT" "SELECT COUNT(*) FROM ingredients;")
```

---

### CR-02: ImageProxy resource leak when coroutine is cancelled during ML Kit processing

**File:** `app/src/main/kotlin/com/safeglow/edge/ocr/OcrRepository.kt:39-73`

**Issue:** `addOnCompleteListener` is registered only on the `recognizer.process(inputImage)` task (line 61). If the `suspendCancellableCoroutine` is cancelled *after* `onCaptureSuccess` fires but *before* (or while) `recognizer.process()` executes — e.g., because `viewModelScope` is cleared when the user navigates away — the ML Kit task may be abandoned and `addOnCompleteListener` may never fire (ML Kit tasks are not guaranteed to call listeners if the client is garbage-collected with no other references). The `image` (`ImageProxy`) from CameraX is therefore never closed, which exhausts the CameraX buffer ring and hangs subsequent captures (exactly the T-2-02 pitfall the comment aims to prevent).

A `invokeOnCancellation` handler on the continuation is the correct fix:

**Fix:**
```kotlin
suspend fun extractRawTokens(imageCapture: ImageCapture): List<String> =
    suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val mediaImage = image.image
                    if (mediaImage == null) {
                        image.close()
                        continuation.resume(emptyList())
                        return
                    }
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        image.imageInfo.rotationDegrees
                    )
                    val task = recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            continuation.resume(visionText.toRawTokens())
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                        .addOnCompleteListener {
                            image.close()
                        }

                    // NEW: ensure ImageProxy is closed if coroutine is cancelled
                    // before the ML Kit task completes.
                    continuation.invokeOnCancellation {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
```

Note: calling `image.close()` twice is safe — `ImageProxy` is idempotent on close.

---

## Warnings

### WR-01: Levenshtein distance computed twice for the best match — wasted work and minor inconsistency risk

**File:** `app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt:82-84`

**Issue:** Stage 4 calls `LevenshteinDistance.compute(canonical, it)` in `minByOrNull` to select `best`, then calls `LevenshteinDistance.compute(canonical, best)` a second time on line 83 to check the threshold. For an 80-name list this is a minor inefficiency, but it also introduces a logical inconsistency: if two names tie on distance during `minByOrNull`, the second call recomputes the winner — which is fine — but there is no guarantee the result is stable under future refactors. Caching the distance eliminates both concerns.

**Fix:**
```kotlin
if (canonical.length >= MIN_FUZZY_LENGTH && allKbNames.isNotEmpty()) {
    val (best, bestDist) = allKbNames
        .map { name -> name to LevenshteinDistance.compute(canonical, name) }
        .minByOrNull { (_, dist) -> dist }
        ?: return@normalizeOne "UNRESOLVED"   // allKbNames is non-empty, so won't hit
    if (bestDist <= FUZZY_THRESHOLD) return best
}
```

---

### WR-02: Fuzzy match in Stage 4 runs against the post-synonym canonical form, but the KB stores pre-synonym INCI names — mismatch can produce false negatives

**File:** `app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt:81-85`

**Issue:** `normalizeOne` computes `canonical = synonymMap.resolve(cleaned)` (stage 2). If the synonym map resolves `GLYCERIN` → `GLYCEROL`, the fuzzy match at stage 4 searches `allKbNames` for something within edit distance 2 of `"GLYCEROL"`. That is correct when `GLYCEROL` is in the KB. However, when the synonym maps to a multi-word INCI name (e.g., `OCTYL METHOXYCINNAMATE` → `ETHYLHEXYL METHOXYCINNAMATE`), the canonical value after stage 2 is 26 characters long. The `MIN_FUZZY_LENGTH` guard passes and the Levenshtein distance to `ETHYLHEXYL METHOXYCINNAMATE` (also 26 chars) could easily exceed 2 due to OCR noise on a 26-char string, so the ingredient falls through to `UNRESOLVED` despite a synonym hit. This means stage 2 can *introduce* fuzzy-match false negatives for long synonyms.

More critically: stage 3a already checks `ingredientDao.findExact(canonical)`. If the synonym resolved correctly, stage 3a should match — stage 4 is a fallback for non-synonym noisy tokens. The bug surfaces only when the synonym fires but the canonical name does not exactly match a DB entry (possible if the DB uses a slightly different string). The fix is to also attempt fuzzy matching of the original `cleaned` string (before synonym resolution) when stages 3a/3b/4 all fail on `canonical`.

**Fix:**
```kotlin
// After the stage-4 block, before returning "UNRESOLVED":
// Retry fuzzy on the pre-synonym cleaned form if canonical differs
if (canonical != cleaned && cleaned.length >= MIN_FUZZY_LENGTH && allKbNames.isNotEmpty()) {
    val best = allKbNames.minByOrNull { LevenshteinDistance.compute(cleaned, it) }
    if (best != null && LevenshteinDistance.compute(cleaned, best) <= FUZZY_THRESHOLD) {
        return best
    }
}
return "UNRESOLVED"
```

---

### WR-03: FTS query likely returns zero results for all tokens due to missing FTS query syntax

**File:** `app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt:76-77`

**Issue:** Stage 3b calls `ingredientDao.ftsSearch(canonical)`. Room FTS4 `MATCH` queries require FTS syntax — a bare string like `METHYLPARABEN` will match only if the FTS index contains exactly that token. However, if the intended behavior is prefix matching (e.g., `METHYL*`), the query string must include the `*` wildcard. Without seeing `IngredientDao.ftsSearch()` source it is not possible to confirm whether the DAO appends `*`, but if it passes `canonical` directly to `MATCH`, Stage 3b is functionally identical to Stage 3a (exact match), making it a dead code path for its stated purpose of handling "partial / stemmed matches."

**Fix:** Verify `IngredientDao.ftsSearch` appends a wildcard, or update the call site:
```kotlin
// In IngredientDao (not in scope, but the fix belongs there):
@Query("SELECT * FROM ingredients WHERE ingredients_fts MATCH :query || '*'")
suspend fun ftsSearch(query: String): List<IngredientEntity>
```
If the DAO already appends `*`, add a comment in `INCINormalizer` to document this assumption so future maintainers don't remove it.

---

### WR-04: No-op synonym entry — RETINYL PALMITATE maps to itself

**File:** `app/src/main/kotlin/com/safeglow/edge/normalization/INCISynonymMap.kt:41`

**Issue:** The entry `"RETINYL PALMITATE" to "RETINYL PALMITATE"` (line 41) maps the key to itself. After stage-1 normalization, if OCR produces `RETINYL PALMITATE`, `resolve()` returns the same string regardless of whether this map entry exists. The entry is harmless but creates a misleading impression that a synonym translation occurs and wastes a map lookup. Separately, the DB seed record uses `RETINYL_PALMITATE` (with underscore) as the `inciName` while the synonym map produces `RETINYL PALMITATE` (with space) — this mismatch means Stage 3a exact match will fail for this ingredient.

**Fix:**
```kotlin
// Remove the no-op entry:
// "RETINYL PALMITATE" to "RETINYL PALMITATE",   // DELETE — no-op

// Additionally, reconcile the DB inciName: either change the seed INSERT to use
// 'RETINYL PALMITATE' (space) or add an underscore-to-space normalization in
// stage 1. The INCI standard uses spaces, not underscores.
```

Also note: `SALICYLIC_ACID` and `RETINYL_PALMITATE` in the seed DB use underscores, which will prevent exact-match lookup from `INCINormalizer` stage 3a since stage 1 strips underscores (the regex `[^A-Z0-9 \-]` removes `_`). Verify all `inciName` values in the DB use the INCI-standard space separator.

---

### WR-05: `bindCamera` errors are silently swallowed — camera bind failure produces no user feedback

**File:** `app/src/main/kotlin/com/safeglow/edge/camera/CameraViewModel.kt:58-78`

**Issue:** `viewModelScope.launch` in `bindCamera` has no `try/catch`. If `awaitCameraProvider()` throws (e.g., `CameraInfoUnavailableException` on a device with no back camera, or if `future.get()` throws `ExecutionException`) or if `cameraProvider.bindToLifecycle()` throws `IllegalArgumentException`, the exception propagates to the coroutine's uncaught exception handler and is silently dropped. The `_uiState` remains `Idle`, the `imageCapture` reference stays `null`, and pressing "Scan Label" produces `CameraUiState.Error("Camera not ready")` — a confusing message for a bind failure.

**Fix:**
```kotlin
fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
    viewModelScope.launch {
        try {
            val cameraProvider = awaitCameraProvider()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = capture
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
            )
        } catch (e: Exception) {
            _uiState.value = CameraUiState.Error("Camera init failed: ${e.message}")
        }
    }
}
```

---

## Info

### IN-01: Release build has minification disabled — APK ships debug-accessible code

**File:** `app/build.gradle.kts:24-26`

**Issue:** `isMinifyEnabled = false` in the `release` build type means the release APK is not shrunk or obfuscated. For a health app that embeds a knowledge base and LiteRT models, this is a minor concern (no secrets in code), but it increases APK size and leaves internal class names fully readable. This should be enabled before production distribution.

**Fix:**
```kotlin
release {
    isMinifyEnabled = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```
Add appropriate keep rules for Room entities, Hilt, and ML Kit in `proguard-rules.pro`.

---

### IN-02: `guava` full artifact declared alongside `concurrent-futures-ktx` — redundant heavy dependency

**File:** `app/build.gradle.kts:101`, `gradle/libs.versions.toml:40`

**Issue:** The comment on line 100-101 says Guava is needed for the `ListenableFuture` type used by `ProcessCameraProvider.getInstance()`. However, `concurrent-futures-ktx` already transitively pulls in `com.google.guava:listenablefuture` (the lightweight artifact). Adding the full `com.google.guava:guava:33.3.1-android` (~2.5 MB) on top is redundant. Moreover, `CameraViewModel.awaitCameraProvider()` uses `suspendCancellableCoroutine` + `addListener` directly and does not use the `await()` extension from `concurrent-futures-ktx`, making that dependency also potentially unused.

**Fix:** Remove `implementation(libs.guava)` and verify the project still compiles. If `ListenableFuture` is needed as a type reference, `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` is the conventional lightweight substitute.

---

### IN-03: Unused import `kotlin.coroutines.resumeWithException` in `OcrRepository.kt`

**File:** `app/src/main/kotlin/com/safeglow/edge/ocr/OcrRepository.kt:13`

**Issue:** Line 13 imports `kotlin.coroutines.resumeWithException` explicitly, but line 15 also imports `kotlin.coroutines.resume`. Both are in the `kotlin.coroutines` package; `resumeWithException` is an extension on `Continuation` and is already available via the star-import-equivalent when using `suspendCancellableCoroutine`. This is a duplicate import that IDEs will flag as redundant.

**Fix:** Remove line 13 (`import kotlin.coroutines.resumeWithException`). The function is still accessible via `continuation.resumeWithException(e)` because it is an extension in the same package as `resume`.

---

### IN-04: Magic number `42` used as Room master table row ID in seed script

**File:** `tools/build_seed_db.sh:71`

**Issue:** `INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$IDENTITY_HASH')` uses the literal `42` for the Room master table row ID. Room's internal code hardcodes this value as `42` and the script is correct, but it should have a comment explaining where `42` comes from so future maintainers do not change it by accident.

**Fix:**
```bash
# Room always uses id=42 in room_master_table — do not change this value.
# See androidx.room.RoomOpenHelper source for confirmation.
INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$IDENTITY_HASH');
```

---

_Reviewed: 2026-05-09T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
