---
phase: 02-camera-ocr-session-profile
plan: "01"
subsystem: camera-ocr-normalization-session
tags: [camerax, ml-kit, ocr, inci-normalization, session, hilt, compose]
dependency_graph:
  requires:
    - 01-foundation-model-validation (Room IngredientDao, Hilt DI, AppDatabase asset)
  provides:
    - camera/CameraScreen.kt (Compose Camera UI with PreviewView + Scan button)
    - camera/CameraViewModel.kt (ImageCapture binding + captureAndProcess)
    - ocr/OcrRepository.kt (suspend extractRawTokens via ML Kit)
    - ocr/MLKitOcrProcessor.kt (token extraction + NON_INCI_PATTERN pre-filter)
    - normalization/INCINormalizer.kt (4-stage normalize pipeline)
    - normalization/INCISynonymMap.kt (21 seed synonym entries)
    - normalization/LevenshteinDistance.kt (pure Kotlin two-row Levenshtein)
    - session/SessionProfile.kt + enums (PregnancyStatus, Country, SkinConcern)
    - session/SessionViewModel.kt (@HiltViewModel ephemeral StateFlow)
    - session/ProfileScreen.kt (3 ExposedDropdownMenuBox selectors)
    - tools/build_seed_db.sh (70-row DATA-01 scaffold with sqlite3 verification)
  affects:
    - Phase 3 RAG pipeline (consumes normalizedTokens: List<String> + SessionProfile)
    - Phase 4 inference (SessionViewModel.profile context filter)
tech_stack:
  added:
    - androidx.camera:camera-{core,camera2,lifecycle,view}:1.6.0
    - com.google.mlkit:text-recognition:16.0.1 (bundled/offline)
    - com.google.accompanist:accompanist-permissions:0.37.3
    - androidx.hilt:hilt-navigation-compose:1.2.0
    - androidx.lifecycle:lifecycle-runtime-compose:2.8.7
    - androidx.concurrent:concurrent-futures-ktx:1.2.0
    - com.google.guava:guava:33.3.1-android
    - com.android.application AGP: 8.7.3 -> 8.9.1
    - compileSdk: 35 -> 36 (CameraX 1.6.0 requirement)
  patterns:
    - CameraX ImageCapture tap-to-capture (not ImageAnalysis)
    - ML Kit TextRecognition.getClient(DEFAULT_OPTIONS) bundled offline
    - suspendCancellableCoroutine + addListener (avoids ListenableFuture classpath issue)
    - INCINormalizer 4-stage pipeline: uppercase -> synonym -> findExact -> ftsSearch -> Levenshtein
    - @HiltViewModel + plain MutableStateFlow (no SavedStateHandle) for ephemeral session
key_files:
  created:
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
  modified:
    - gradle/libs.versions.toml (camerax/mlkit/accompanist/guava + supporting deps)
    - app/build.gradle.kts (AGP 8.9.1, compileSdk 36, new implementation() entries)
    - app/src/main/AndroidManifest.xml (CAMERA permission; INTERNET intentionally absent)
    - tools/build_seed_db.sh (70 TODO placeholder INSERTs + sqlite3 row-count verification)
decisions:
  - "suspendCancellableCoroutine+addListener instead of concurrent-futures-ktx.await() — ListenableFuture type not accessible via guava 9999.0-empty-to-avoid-conflict-with-guava placeholder; adding guava 33.3.1-android resolves it"
  - "AGP 8.7.3->8.9.1 + compileSdk 35->36 required by CameraX 1.6.0 (RESEARCH.md assumed 35 was sufficient; actual manifest requirement is 36)"
  - "bindCamera() in ViewModel rather than composable — keeps ListenableFuture handling internal, cleaner composable API"
  - "INCISynonymMap as Kotlin object (not injected class) — pure data map; no async operations; directly referenced in INCINormalizer"
  - "DATA-01 synonym TODO comments in INCISynonymMap are intentional — operator-driven data authoring, not code stubs"
metrics:
  duration_minutes: 60
  completed_date: "2026-05-09"
  tasks_completed: 3
  tasks_total: 3
  files_created: 10
  files_modified: 4
---

# Phase 02 Plan 01: Camera + OCR + Session Profile Summary

**One-liner:** CameraX 1.6.0 ImageCapture + ML Kit text-recognition:16.0.1 (offline/bundled) + 4-stage INCI normalization (uppercase→synonym→Room FTS→Levenshtein≤2) + ephemeral @HiltViewModel session profile (pregnancy/country/skin concern).

---

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Add CameraX + ML Kit + Accompanist deps | `66d8efc` | gradle/libs.versions.toml, app/build.gradle.kts, AndroidManifest.xml |
| 2 | CameraScreen, CameraViewModel, OcrRepository, MLKitOcrProcessor | `9b7263f` | camera/*.kt, ocr/*.kt |
| 3 | INCINormalizer, INCISynonymMap, LevenshteinDistance, SessionViewModel, ProfileScreen, build_seed_db.sh | `9ac03ae` | normalization/*.kt, session/*.kt, tools/build_seed_db.sh |

---

## Compile Output

```
./gradlew :app:compileDebugKotlin -q --warning-mode=summary
Exit: 0
```

All three tasks compile cleanly. No runtime device verification performed (physical device required for CameraX + OCR pipeline — Phase 02 UAT scope per plan success criteria).

---

## Added Dependencies and Pinned Versions

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.camera:camera-{core,camera2,lifecycle,view}` | `1.6.0` | CameraX ImageCapture + PreviewView |
| `com.google.mlkit:text-recognition` | `16.0.1` | ML Kit v2 Latin OCR, bundled (offline) |
| `com.google.accompanist:accompanist-permissions` | `0.37.3` | Compose CAMERA permission state machine |
| `androidx.hilt:hilt-navigation-compose` | `1.2.0` | hiltViewModel() in Compose screens |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.8.7` | collectAsStateWithLifecycle() |
| `androidx.concurrent:concurrent-futures-ktx` | `1.2.0` | ListenableFuture KTX extensions |
| `com.google.guava:guava` | `33.3.1-android` | ListenableFuture type (ProcessCameraProvider) |
| AGP | `8.7.3 → 8.9.1` | CameraX 1.6.0 minimum AGP requirement |
| compileSdk | `35 → 36` | CameraX 1.6.0 minimum compileSdk requirement |

---

## Source Files Confirmation

**Camera / OCR (SCAN-01):**
- `CameraScreen.kt` — AndroidView+PreviewView, Accompanist permission gate, Scan Label button (enabled/disabled via CameraUiState.Loading)
- `CameraViewModel.kt` — bindCamera() via suspendCancellableCoroutine+addListener; captureAndProcess() dispatches to OcrRepository; CameraUiState sealed interface
- `OcrRepository.kt` — ImageCapture.takePicture + ML Kit process(); image.close() in addOnCompleteListener (T-2-02 mitigation)
- `MLKitOcrProcessor.kt` — Text.toRawTokens() with comma/semicolon/newline split; NON_INCI_PATTERN pre-filter (Pitfall 4 mitigation)

**Normalization (SCAN-02, SCAN-03):**
- `INCINormalizer.kt` — 4-stage pipeline; allKbNames pre-loaded once (Pitfall 5 fix); MAX_TOKEN_LENGTH=1000 OOM guard (ASVS V5)
- `INCISynonymMap.kt` — 21 seed synonym entries (parabens, retinoids, sunscreens, fragrances, preservatives, acids, humectants)
- `LevenshteinDistance.kt` — pure Kotlin, two-row space optimization; compute() + isWithinThreshold() helpers

**Session Profile (PROF-01, PROF-03):**
- `SessionProfile.kt` — data class + PregnancyStatus / Country / SkinConcern enums
- `SessionViewModel.kt` — @HiltViewModel, MutableStateFlow (no SavedStateHandle, no Room, no SharedPreferences — PROF-03 compliant)
- `ProfileScreen.kt` — 3 ExposedDropdownMenuBox selectors with legal disclaimer ("Educational only — not medical advice")

---

## build_seed_db.sh Verification

The script now contains:
- 10 authored Phase 1 seed records (unchanged)
- 70 TODO placeholder comments for Phase 2 DATA-01 expansion (parabens ×6, retinoids ×4, sunscreen filters ×15, fragrances/allergens ×20, preservatives ×10, solvents/humectants ×10, surfactants ×5)
- sqlite3 verification step: `SELECT COUNT(*) FROM ingredient;` — outputs current count and prints `DATA-01 PASSED` when count reaches 80

**Operator action required (DATA-01):** Replace each TODO placeholder with a real INSERT sourced from SCCS/CIR/ACOG/FDA, then run `./tools/build_seed_db.sh`. The verification step will confirm 80 rows.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AGP 8.7.3 → 8.9.1, compileSdk 35 → 36**
- **Found during:** Task 1 compile verification
- **Issue:** CameraX 1.6.0 declares `minAgp = 8.9.1` and `compileSdk = 36` in its manifest. Build failed with 12 dependency compatibility errors.
- **Fix:** Updated `agp = "8.9.1"` in libs.versions.toml; set `compileSdk = 36` in app/build.gradle.kts. Android 36 SDK was already installed (`android-36.1`). Gradle 8.11.1 is compatible with AGP 8.9.1.
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts
- **Commits:** included in `66d8efc`, `9b7263f`

**2. [Rule 3 - Blocking] Added guava 33.3.1-android for ListenableFuture type**
- **Found during:** Task 2 compile verification
- **Issue:** `ProcessCameraProvider.getInstance()` returns `ListenableFuture<ProcessCameraProvider>`. The `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` placeholder on the compile classpath is an empty jar — the `ListenableFuture` class and `addListener()` method were inaccessible.
- **Fix:** Added `com.google.guava:guava:33.3.1-android` as an explicit implementation dependency to put the real `ListenableFuture` class on the compile classpath.
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts
- **Commit:** `9b7263f`

**3. [Rule 3 - Blocking] Added hilt-navigation-compose 1.2.0 and lifecycle-runtime-compose 2.8.7**
- **Found during:** Task 2 compile verification
- **Issue:** `hiltViewModel()` in composables requires `androidx.hilt:hilt-navigation-compose`; `collectAsStateWithLifecycle()` requires `androidx.lifecycle:lifecycle-runtime-compose`. Neither was present.
- **Fix:** Added both as implementation dependencies.
- **Files modified:** gradle/libs.versions.toml, app/build.gradle.kts
- **Commit:** `9b7263f`

**4. [Rule 3 - Blocking] Moved ProcessCameraProvider binding from composable to ViewModel**
- **Found during:** Task 2 compile verification (third attempt)
- **Issue:** `concurrent-futures-ktx` `.await()` on `ListenableFuture` required the `ListenableFuture` type to be resolvable at the call site in the composable. Even after adding guava, the generic type inference failed when the future was stored in a local variable inside `LaunchedEffect`.
- **Fix:** Moved `awaitCameraProvider()` into `CameraViewModel` using `suspendCancellableCoroutine + addListener`. The composable calls `viewModel.bindCamera(lifecycleOwner, previewView)` — no `ListenableFuture` type exposure in the composable.
- **Files modified:** camera/CameraScreen.kt, camera/CameraViewModel.kt
- **Commit:** `9b7263f`

**5. [Rule 2 - Missing critical] Added CAMERA permission to AndroidManifest.xml**
- **Found during:** Task 1 (RESEARCH.md Pitfall 2 review)
- **Issue:** Phase 1 intentionally had zero permissions. RESEARCH.md explicitly documents that `rememberPermissionState` silently fails if CAMERA is not declared in the manifest.
- **Fix:** Added `<uses-permission android:name="android.permission.CAMERA" />`. INTERNET permission remains absent (T-2-01 maintained).
- **Files modified:** app/src/main/AndroidManifest.xml
- **Commit:** `66d8efc`

---

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| 21 seed synonyms, ~40 TODO comments | `normalization/INCISynonymMap.kt:62` | DATA-01 data-authoring task — operator fills synonym map as KB grows to 80 ingredients; does not block SCAN-03 compile gate |
| 70 TODO INSERT placeholders | `tools/build_seed_db.sh` | DATA-01 operator data-authoring; the sqlite3 verification step prints count and pass/fail when operator runs the script |

These stubs do not prevent the plan's goal (compile-verified OCR + normalization + session pipeline). The plan explicitly states "Data authoring (filling actual 70 INSERTs) is a human-driven step but scaffolded in the script."

---

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: camera_input | camera/CameraScreen.kt | New CAMERA permission and camera capture surface. Mitigated: permission gate via Accompanist; ImageCapture.OnImageCapturedCallback (in-memory only, never MediaStore/disk); ML Kit bundled model (no INTERNET) |
| threat_flag: manual_text_input | normalization/INCINormalizer.kt | Manual ingredient text entry path (SCAN-02). Mitigated: stage-1 regex strips all non-INCI chars; MAX_TOKEN_LENGTH=1000 OOM guard; Room parameterized queries prevent injection |

---

## Self-Check

**Created files exist:**
- `app/src/main/kotlin/com/safeglow/edge/camera/CameraScreen.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/camera/CameraViewModel.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/ocr/OcrRepository.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/ocr/MLKitOcrProcessor.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/normalization/INCISynonymMap.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/normalization/LevenshteinDistance.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/session/SessionProfile.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/session/SessionViewModel.kt` — FOUND
- `app/src/main/kotlin/com/safeglow/edge/session/ProfileScreen.kt` — FOUND

**Commits exist:**
- `66d8efc` — FOUND (chore: deps)
- `9b7263f` — FOUND (feat: camera/OCR)
- `9ac03ae` — FOUND (feat: normalization/session/build_seed_db)

**Compile:** `./gradlew :app:compileDebugKotlin` exits 0 — CONFIRMED

## Self-Check: PASSED
