# Phase 2: Camera + OCR + Session Profile — Research

**Researched:** 2026-04-28
**Domain:** Android (Kotlin) + CameraX 1.6.0 + ML Kit Text Recognition v2 + INCI Normalization + Room DB expansion + session ViewModel
**Confidence:** HIGH — all critical claims verified via official docs, Maven Central, or live registry checks

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCAN-01 | User can capture a cosmetic product label photo and receive an extracted list of INCI ingredients via on-device OCR | CameraX 1.6.0 ImageCapture or ImageAnalysis bound to ProcessCameraProvider; ML Kit text-recognition:16.0.1 bundled variant; OCR pipeline converts Text.TextBlock hierarchy to token list |
| SCAN-02 | User can manually enter an ingredient list when OCR fails | Standard Compose TextField with multiline input; same INCINormalizer pipeline post-input; no extra library required |
| SCAN-03 | INCI names are normalized (uppercase, synonym resolution, Levenshtein fuzzy matching) before knowledge base lookup | Handwritten Kotlin: uppercase() is trivial; synonyms HashMap in code; Levenshtein implemented in ~20 lines pure Kotlin (no dep); threshold = edit-distance ≤ 2 for strings with length ≥ 6 |
| PROF-01 | User can set session context (pregnancy status, country, skin concern type) without creating an account | @HiltViewModel scoped SessionViewModel with StateFlow<SessionProfile>; UI is a Compose DropdownMenu; no persistence — cleared when app process exits |
| PROF-03 | Session context is cleared when the app closes — zero persistent personal data retained | @HiltViewModel bound to Activity ViewModelStore; NOT SavedStateHandle; NOT SharedPreferences; not written to Room; cleared on process death |
| DATA-01 | Knowledge base covers 80 priority INCI ingredients at launch with health metadata and authoritative citations | Expand existing 10-row knowledge_base.db asset to 80 rows using extended tools/build_seed_db.sh; schema is locked (Phase 1); must add CAS-sourced records for parabens, retinoids, sunscreen filters, fragrances, preservatives |
</phase_requirements>

---

## Summary

Phase 2 delivers three parallel tracks against the Phase 1 infrastructure: (1) a CameraX capture pipeline that hands a still photo to ML Kit for OCR, (2) an INCI normalization layer that converts raw OCR tokens to canonical uppercase names with synonym resolution and Levenshtein fuzzy matching, and (3) a session profile ViewModel that exposes ephemeral pregnancy/country/skin-concern state with zero persistence.

The CameraX approach for this use case is **ImageCapture over ImageAnalysis**. The success criterion requires an extracted token list within 3 seconds — a single-shot tap-to-capture of a still image gives ML Kit the highest-quality input (no motion blur, full resolution) and avoids the real-time frame-dropping complexity of ImageAnalysis. The user will tap once; the single captured photo is processed synchronously in a coroutine on a background thread.

The INCI normalization pipeline is intentionally hand-rolled for three reasons: there are no Android INCI-specific libraries, the synonym vocabulary is finite and domain-specific (70–200 common synonyms for 80 ingredients), and a pure-Kotlin Levenshtein function is 20 lines. Do not import a fuzzy-matching library for this task. The normalization order is: (1) uppercase + strip non-alpha, (2) synonym map lookup, (3) exact Room FTS match, (4) Levenshtein distance ≤ 2 fallback. This order is critical — applying Levenshtein before exact match adds unnecessary computation.

The 80-ingredient knowledge base expansion (DATA-01) is a data-authoring task, not a code task. The schema is already locked by Phase 1. The task is to author 70 additional ingredient records (10 exist) as SQL INSERT statements in an extended tools/build_seed_db.sh, rebuild the asset DB, and commit. The 80 priority categories are: parabens (6), retinoids (4), sunscreen filters (15), fragrances/fragrance allergens (20), preservatives (10), solvents/humectants (10), surfactants/emulsifiers (10), actives (5).

**Primary recommendation:** Use CameraX 1.6.0 stable with `ImageCapture.takePicture(executor, OnImageCapturedCallback)`, convert the `ImageProxy` to `InputImage.fromMediaImage()`, and call `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(inputImage)`. Keep normalization entirely in-process with no new dependencies.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Camera permission request | UI (CameraScreen composable) | — | Permission must be triggered from user-facing context; Accompanist `rememberPermissionState` handles Compose lifecycle |
| CameraX lifecycle binding | UI (CameraScreen composable via LaunchedEffect) | ScanViewModel owns use-case references | ProcessCameraProvider.bindToLifecycle requires LifecycleOwner — Activity context flows through Compose LocalLifecycleOwner |
| ImageCapture (still photo capture) | UI trigger → ScanViewModel | ScanViewModel dispatches to OcrRepository | Tap event originates in composable; suspend fun in ViewModel handles capture and passes ImageProxy to OCR layer |
| OCR text extraction | Data layer (OcrRepository / MLKitOcrProcessor) | — | ML Kit call is I/O-bound background work; must close ImageProxy after Task completes |
| INCI tokenization + normalization | Domain layer (INCINormalizer) | — | Pure business logic; no Android API dependencies; unit-testable in isolation |
| Synonym resolution dictionary | Domain layer (INCISynonymMap) | — | Hardcoded HashMap<String, String>; keyed by common name / alias, value is canonical INCI uppercase |
| Levenshtein fuzzy matching | Domain layer (INCINormalizer, inline) | — | 20-line pure Kotlin; no dependency; called only when exact + synonym lookup fails |
| Session context state | Data layer (SessionRepository / SessionViewModel) | @HiltViewModel | StateFlow<SessionProfile> held in ViewModel; NOT Room, NOT SharedPreferences |
| 80-ingredient DB authoring | Build asset (tools/build_seed_db.sh) | Data: assets/knowledge_base.db | SQL data task — no Kotlin code changes; schema already locked |
| Camera preview UI | UI (PreviewView wrapped in AndroidView) | — | camera-compose artifact is alpha — use stable AndroidView + PreviewView pattern |

---

## Standard Stack

### Core (Phase 2 additions)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.camera:camera-core` | **1.6.0** | CameraX use-case architecture | Latest stable (2026-03-25); minSdk 23 compatible with project minSdk 26 [VERIFIED: developer.android.com/jetpack/androidx/releases/camera] |
| `androidx.camera:camera-camera2` | **1.6.0** | Camera2 implementation backend | Required companion to camera-core [VERIFIED: official CameraX docs] |
| `androidx.camera:camera-lifecycle` | **1.6.0** | ProcessCameraProvider lifecycle binding | Handles bind/unbind with LifecycleOwner [VERIFIED: official CameraX docs] |
| `androidx.camera:camera-view` | **1.6.0** | PreviewView for camera preview surface | Stable; camera-compose is alpha — use AndroidView + PreviewView [VERIFIED: Android blog Dec 2024] |
| `com.google.mlkit:text-recognition` | **16.0.1** | ML Kit Text Recognition v2 Latin-script OCR (bundled) | Latest published version; bundled variant runs fully offline — ~4 MB APK increase per architecture; required for PRIV-01/PRIV-03 [VERIFIED: developers.google.com/ml-kit/vision/text-recognition/v2/android, April 22 2026 update] |
| `com.google.accompanist:accompanist-permissions` | **0.37.3** | Compose-idiomatic camera permission request | Latest published version on libraries.io; @ExperimentalPermissionsApi annotation required [VERIFIED: libraries.io/maven/com.google.accompanist:accompanist-permissions] |

### Not Added (already present from Phase 1)

| Library | Version | Used For |
|---------|---------|---------|
| `androidx.room:room-runtime` | 2.7.1 | Knowledge base; IngredientDao.findExact + ftsSearch used in normalization |
| `com.google.dagger:hilt-android` | 2.57.2 | SessionViewModel injection |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Suspend camera + OCR pipeline |
| `androidx.compose.material3` | BOM 2026.03.00 | Session profile Compose dropdowns |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `camera-view:1.6.0` (AndroidView + PreviewView) | `camera-compose:1.5.0-alpha` | camera-compose is alpha — API not stable; AndroidView + PreviewView is production-proven and sufficient for Phase 2 |
| Inline Levenshtein (pure Kotlin) | `fuzzywuzzy-kotlin` or `kt-fuzzy` | External library adds APK weight, adds transitive deps; 80-ingredient vocabulary makes a 20-line implementation fully sufficient and more maintainable |
| `com.google.mlkit:text-recognition:16.0.1` (bundled) | `com.google.android.gms:play-services-mlkit-text-recognition` (unbundled) | Unbundled requires network for model download on first install; violates PRIV-01/PRIV-03 |

**Installation (libs.versions.toml additions):**
```toml
[versions]
camerax = "1.6.0"
mlkit-text-recognition = "16.0.1"
accompanist = "0.37.3"

[libraries]
camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camera-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
```

**app/build.gradle.kts additions:**
```kotlin
dependencies {
    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit Text Recognition v2 (bundled — offline, no network)
    implementation(libs.mlkit.text.recognition)

    // Compose camera permission helper
    implementation(libs.accompanist.permissions)
}
```

**Version verification:**
- `camera-*:1.6.0` — confirmed stable release 2026-03-25 [VERIFIED: developer.android.com/jetpack/androidx/releases/camera]
- `text-recognition:16.0.1` — confirmed latest on mvnrepository.com; docs updated 2026-04-22 [VERIFIED: developers.google.com/ml-kit]
- `accompanist-permissions:0.37.3` — confirmed latest on libraries.io as of 2026 [VERIFIED: libraries.io]

---

## Architecture Patterns

### System Architecture Diagram

```
User taps "Capture" button
        │
        ▼
CameraScreen.kt (Composable)
    └─► captureButton.onClick
           │
           ▼ suspend call via ViewModelScope
    ScanViewModel.captureAndProcess()
           │
           ├─► ImageCapture.takePicture(executor, OnImageCapturedCallback)
           │           │
           │           ▼ (camera I/O thread)
           │   ImageProxy (still photo, full res)
           │           │
           │           ▼
           ├─► OcrRepository.extractText(ImageProxy)
           │       └─► InputImage.fromMediaImage(mediaImage, rotationDegrees)
           │       └─► TextRecognition.getClient(DEFAULT_OPTIONS).process(image)
           │               │
           │               ▼ Task<Text> resolved on background thread
           │       rawTokens: List<String>
           │       (one token per TextLine.text, stripped + split on comma/newline)
           │               │
           │               ▼ (close ImageProxy here — critical)
           │
           ▼
    INCINormalizer.normalize(rawTokens): List<String>
           │
           ├─ Step 1: uppercase + strip non-alpha/hyphen/space → "METHYLPARABEN"
           ├─ Step 2: INCISynonymMap.lookup(token) → canonical INCI or same
           ├─ Step 3: IngredientDao.findExact(normalized) → hit or null
           └─ Step 4: if null → levenshteinFuzzy(normalized, allInciNames, threshold=2)
                           → best match or "UNRESOLVED"
           │
           ▼
    normalizedTokens: List<String>
           │
           ▼
    ScanUiState(tokens = normalizedTokens) → Compose UI (Phase 3+ consumes for RAG)

----

Session Profile (independent path):
    ProfileScreen.kt (Composable)
           │
           ▼
    SessionViewModel (HiltViewModel, no persistence)
           └─► StateFlow<SessionProfile>
                   ├─ pregnancyStatus: PregnancyStatus  (enum: NOT_SET, PREGNANT, NOT_PREGNANT)
                   ├─ country: Country  (enum: NOT_SET, EU, US, CN, JP)
                   └─ skinConcern: SkinConcern  (enum: NOT_SET, NORMAL, SENSITIVE, DRY, OILY)
           (cleared on app process exit — not SavedStateHandle, not SharedPreferences, not Room)
```

### Recommended Project Structure (Phase 2 additions)

```
app/src/main/kotlin/com/safeglow/edge/
├── camera/
│   ├── CameraScreen.kt              # Compose: PreviewView + capture button + permission gate
│   └── CameraViewModel.kt           # ProcessCameraProvider binding, ImageCapture use case holder
│
├── ocr/
│   ├── OcrRepository.kt             # suspend fun: ImageProxy → List<String> raw tokens
│   └── MLKitOcrProcessor.kt         # InputImage.fromMediaImage + TextRecognition.process
│
├── normalization/
│   ├── INCINormalizer.kt            # orchestrates uppercase → synonym → exact → fuzzy
│   ├── INCISynonymMap.kt            # val SYNONYMS: Map<String, String> = mapOf(...)
│   └── LevenshteinDistance.kt       # pure Kotlin, no deps, ~20 lines
│
├── session/
│   ├── SessionProfile.kt            # data class (pregnancyStatus, country, skinConcern)
│   ├── SessionEnums.kt              # PregnancyStatus, Country, SkinConcern enums
│   ├── SessionViewModel.kt          # @HiltViewModel, StateFlow<SessionProfile>
│   └── ProfileScreen.kt             # Compose: three DropdownMenus
│
└── di/
    └── CameraModule.kt              # @Provides ImageCapture use case (optional for testability)

app/src/main/assets/
└── knowledge_base.db                # REBUILD: 10 → 80 rows via extended build_seed_db.sh

tools/
└── build_seed_db.sh                 # EXTEND: add 70 INSERT statements for full 80-ingredient set
```

### Pattern 1: CameraX — ProcessCameraProvider + ImageCapture Binding in Compose

**What:** Bind CameraX use cases to Compose lifecycle using `LocalLifecycleOwner` and `LaunchedEffect`. `AndroidView` wraps `PreviewView` for the camera surface (camera-compose is alpha — do not use).

**When to use:** Phase 2 camera preview + tap-to-capture.

```kotlin
// Source: developer.android.com/media/camera/camerax + Jolanda Verhoef Medium 2024 [CITED]
@Composable
fun CameraScreen(viewModel: CameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission check via Accompanist
    val permissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (!permissionState.status.isGranted) {
        // Show rationale / request button
        Button(onClick = { permissionState.launchPermissionRequest() }) {
            Text("Grant Camera Permission")
        }
        return
    }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        viewModel.setImageCapture(imageCapture)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = { viewModel.captureAndProcess() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
        ) {
            Text("Scan Label")
        }
    }
}
```

### Pattern 2: ImageCapture + ML Kit Text Recognition

**What:** Take a single still photo via `OnImageCapturedCallback`, pass to ML Kit `InputImage.fromMediaImage`, close the `ImageProxy` in `addOnCompleteListener`.

**Critical:** MUST close `ImageProxy` in the `addOnCompleteListener` regardless of success/failure. If not closed, the camera buffer is never released and subsequent captures fail silently.

```kotlin
// Source: developers.google.com/ml-kit/vision/text-recognition/v2/android [CITED]
// Source: developer.android.com/media/camera/camerax/take-photo [CITED]
class OcrRepository @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()

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
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                continuation.resume(visionText.toRawTokens())
                            }
                            .addOnFailureListener { e ->
                                continuation.resumeWithException(e)
                            }
                            .addOnCompleteListener {
                                image.close()  // CRITICAL: always close ImageProxy
                            }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }

    // Extract one token per TextLine; comma/semicolon splitting handles "A, B, C" ingredient lists
    private fun Text.toRawTokens(): List<String> =
        textBlocks
            .flatMap { block -> block.lines }
            .flatMap { line ->
                line.text
                    .split(",", ";", "\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
}
```

### Pattern 3: INCI Normalization Pipeline

**What:** Three-stage normalization: (1) uppercase + strip, (2) synonym map, (3) Levenshtein fuzzy. Stage 3 only runs when exact + synonym lookup misses.

**Performance note:** Levenshtein at threshold 2 over 80 INCI names is instantaneous (<1 ms). No pre-computation needed at this knowledge base size.

```kotlin
// Source: Adapted from standard dynamic programming [ASSUMED for Levenshtein implementation]
// Levenshtein O(m*n) — for 80-name KB, this is negligible at query time

class INCINormalizer @Inject constructor(
    private val synonymMap: INCISynonymMap,
    private val ingredientDao: IngredientDao
) {
    suspend fun normalize(rawTokens: List<String>): List<String> =
        rawTokens.map { token -> normalizeOne(token) }

    private suspend fun normalizeOne(raw: String): String {
        // Stage 1: uppercase + strip non-INCI characters
        val cleaned = raw.uppercase()
            .replace(Regex("[^A-Z0-9 \\-]"), "")
            .trim()
        if (cleaned.isBlank()) return "UNRESOLVED"

        // Stage 2: synonym map lookup
        val canonical = synonymMap.resolve(cleaned)

        // Stage 3a: exact Room match
        if (ingredientDao.findExact(canonical) != null) return canonical

        // Stage 3b: FTS search (handles partial matches)
        val ftsResults = ingredientDao.ftsSearch(canonical)
        if (ftsResults.isNotEmpty()) return ftsResults.first().inciName

        // Stage 3c: Levenshtein fuzzy — only if canonical is long enough to avoid false positives
        if (canonical.length >= 6) {
            val allNames = ingredientDao.getAll().map { it.inciName }
            val best = allNames.minByOrNull { levenshtein(canonical, it) }
            if (best != null && levenshtein(canonical, best) <= 2) return best
        }

        return "UNRESOLVED"
    }
}

// Pure Kotlin, O(min(m,n)) space via two-row optimization
fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in a.indices) {
        curr[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
        }
        prev.indices.forEach { prev[it] = curr[it] }
    }
    return prev[b.length]
}
```

### Pattern 4: Session Profile ViewModel — Ephemeral State Only

**What:** `@HiltViewModel` with `MutableStateFlow<SessionProfile>`. No persistence. Cleared on `onCleared()` (which fires on process death).

**Critical PROF-03 requirement:** Do NOT use `SavedStateHandle`, `SharedPreferences`, or `Room` for session data. The entire point is that closing the app clears session context.

```kotlin
// Source: Adapted from developer.android.com/topic/libraries/architecture/viewmodel [CITED]
data class SessionProfile(
    val pregnancyStatus: PregnancyStatus = PregnancyStatus.NOT_SET,
    val country: Country = Country.NOT_SET,
    val skinConcern: SkinConcern = SkinConcern.NOT_SET
)

enum class PregnancyStatus { NOT_SET, PREGNANT, NOT_PREGNANT }
enum class Country { NOT_SET, EU, US, CN, JP }
enum class SkinConcern { NOT_SET, NORMAL, SENSITIVE, DRY, OILY }

@HiltViewModel
class SessionViewModel @Inject constructor() : ViewModel() {
    private val _profile = MutableStateFlow(SessionProfile())
    val profile: StateFlow<SessionProfile> = _profile.asStateFlow()

    fun setPregnancyStatus(status: PregnancyStatus) {
        _profile.update { it.copy(pregnancyStatus = status) }
    }
    fun setCountry(country: Country) {
        _profile.update { it.copy(country = country) }
    }
    fun setSkinConcern(concern: SkinConcern) {
        _profile.update { it.copy(skinConcern = concern) }
    }
    // onCleared() auto-fires on process death; no explicit clear needed
}
```

### Pattern 5: Synonym Map — Hardcoded for 80-Ingredient KB

**What:** `HashMap<String, String>` keyed by alias, value is canonical INCI uppercase. Covers most common label variants (IUPAC names, trade names, abbreviations) for the 80 priority ingredients.

**Data sourcing:** CIR Cosmetic Ingredient Dictionary and INCIdecoder.com cross-reference. Not a library — a curated Kotlin object.

```kotlin
// Source: [ASSUMED — data must be curated from CIR / INCIdecoder per ingredient]
object INCISynonymMap {
    private val synonyms: Map<String, String> = mapOf(
        // Parabens
        "METHYL PARABEN" to "METHYLPARABEN",
        "NIPAGIN" to "METHYLPARABEN",
        "METHYL 4-HYDROXYBENZOATE" to "METHYLPARABEN",
        "PROPYL PARABEN" to "PROPYLPARABEN",
        "NIPASOL" to "PROPYLPARABEN",
        "ETHYL PARABEN" to "ETHYLPARABEN",
        "BUTYL PARABEN" to "BUTYLPARABEN",
        // Retinoids
        "VITAMIN A" to "RETINOL",
        "RETINOIC ACID" to "TRETINOIN",
        "RETINALDEHYDE" to "RETINAL",
        // Sunscreens
        "BENZOPHENONE-3" to "OXYBENZONE",
        "OCTYL METHOXYCINNAMATE" to "ETHYLHEXYL METHOXYCINNAMATE",
        "OCTOCRYLENE" to "OCTOCRYLENE",  // same — but alias for clarity
        "PARSOL 1789" to "BUTYL METHOXYDIBENZOYLMETHANE",
        "AVOBENZONE" to "BUTYL METHOXYDIBENZOYLMETHANE",
        // Fragrances
        "FRAGRANCE" to "PARFUM",
        "PERFUME" to "PARFUM",
        // Preservatives
        "2-PHENOXYETHANOL" to "PHENOXYETHANOL",
        // Add remaining ~60 synonym entries per KB ingredient
    )

    fun resolve(cleaned: String): String = synonyms[cleaned] ?: cleaned
}
```

### Anti-Patterns to Avoid

- **`ImageAnalysis` instead of `ImageCapture` for tap-to-capture:** `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` drops frames; for a still photo processed once, `ImageCapture` is correct — it delivers a full-resolution, non-dropped image to ML Kit.
- **Not closing `ImageProxy`:** If `image.close()` is not called in `addOnCompleteListener`, the camera buffer pool exhausts after the first capture, and subsequent calls fail silently. Use `addOnCompleteListener` (not just success/failure listeners) to guarantee close.
- **`camera-compose` artifact in production:** `androidx.camera:camera-compose` is alpha. Use `camera-view:1.6.0` with `AndroidView { PreviewView(...) }` instead.
- **Unbundled ML Kit variant (`play-services-mlkit-text-recognition`):** Requires Google Play Services and downloads the OCR model on first use. Violates PRIV-01 (no network during analysis) and fails in offline environments.
- **`SavedStateHandle` in `SessionViewModel`:** This persists state across process recreation (e.g., system-kill-and-restore). PROF-03 explicitly requires zero persistence — use plain `MutableStateFlow` in the ViewModel.
- **Levenshtein without minimum-length guard:** Applying Levenshtein to 2-letter tokens (e.g., "CI") will produce false matches. Guard with `length >= 6` before calling fuzzy matching.
- **`ANDROID.PERMISSION.CAMERA` absent from AndroidManifest:** The camera permission must be declared in the manifest even for runtime-requested permissions. Phase 1 intentionally locked out all permissions — Phase 2 must add `<uses-permission android:name="android.permission.CAMERA" />` as its first manifest change.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Camera lifecycle management | Manual Camera2 session, SurfaceTexture, TextureView | CameraX `ProcessCameraProvider.bindToLifecycle` | CameraX handles rotation, lifecycle teardown, Camera2 quirks across 1000s of devices; Camera2 direct has ~300 device-specific bugs documented in CameraX |
| OCR / text detection | Custom TFLite OCR model, OpenCV | ML Kit `text-recognition:16.0.1` | ML Kit Text Recognition v2 achieves high accuracy on printed product labels; bundled variant is 4 MB and fully offline; no training data needed |
| Permission UX flow | Manual `checkSelfPermission` + `shouldShowRationale` + `ActivityResultContracts` | Accompanist `rememberPermissionState` | Accompanist encapsulates permission state machine (not-requested / denied / permanently-denied / granted) in a single Compose-idiomatic API |
| FTS + fuzzy matching | Custom SQLite queries with LIKE and trigrams | Room FTS4 (`findExact` + `ftsSearch`) + inline Levenshtein | FTS4 already exists from Phase 1; Levenshtein is 20 lines of pure Kotlin; no library overhead |

**Key insight:** The three hardest parts of Phase 2 (camera lifecycle, OCR, permission state) each have a one-dependency solution with official Google support. Hand-rolling any of them risks device-specific bugs, offline capability gaps, or permission denial edge cases.

---

## Common Pitfalls

### Pitfall 1: ImageProxy Not Closed After OCR
**What goes wrong:** `TextRecognition.getClient().process(image)` returns a `Task<Text>` — if `image.close()` is called in `addOnSuccessListener` but not `addOnFailureListener`, and the OCR fails, the `ImageProxy` is never released. On the next capture call, `imageCapture.takePicture()` throws `ImageCaptureException.ERROR_CAPTURE_FAILED` or silently fails.
**Why it happens:** ML Kit's `Task` API has separate success and failure callbacks; developers forget the failure path.
**How to avoid:** Always use `addOnCompleteListener { image.close() }` — fires regardless of success or failure.
**Warning signs:** Second capture attempt fails immediately after a first OCR failure; `CameraX` Logcat shows "Unable to open next image" or "Max images acquired" errors.

### Pitfall 2: Camera Permission Not Declared in Manifest
**What goes wrong:** `rememberPermissionState(android.Manifest.permission.CAMERA).launchPermissionRequest()` silently does nothing — the system never shows the permission dialog. The permission state remains `PermissionStatus.Denied` with no rationale to show.
**Why it happens:** Android requires permissions to be declared in `AndroidManifest.xml` before they can be requested at runtime, even for permissions the user must grant. Phase 1 intentionally declared zero permissions.
**How to avoid:** Add `<uses-permission android:name="android.permission.CAMERA" />` to `AndroidManifest.xml` as the FIRST task in Phase 2.
**Warning signs:** `rememberPermissionState` shows denied but never transitions to the permission request dialog; `adb shell pm list permissions | grep CAMERA` shows the permission is not registered for the package.

### Pitfall 3: `ProcessCameraProvider.bindToLifecycle` Called on Wrong Thread
**What goes wrong:** `ProcessCameraProvider.getInstance(context)` returns a `ListenableFuture`. Calling `.get()` on the main thread blocks the UI. If called from a background coroutine without `Dispatchers.Main`, the bind can throw `IllegalStateException` because CameraX requires use case binding to happen on the main thread.
**Why it happens:** Incorrect dispatcher in `LaunchedEffect` or `viewModelScope.launch`.
**How to avoid:** Use `ProcessCameraProvider.getInstance(context).await()` (KotlinX extension) inside `LaunchedEffect(Unit)` which executes on the main thread by default, OR explicitly switch to `Dispatchers.Main` before calling `bindToLifecycle`.
**Warning signs:** `IllegalStateException: bindToLifecycle must be called on the main thread` in Logcat; camera preview is blank.

### Pitfall 4: OCR Token List Includes Non-Ingredient Text
**What goes wrong:** ML Kit recognizes ALL text on the product label — brand name, "INGREDIENTS:", weight ("Net wt. 50 ml"), regulatory text, lot numbers. The raw token list has 50+ entries; only 10–20 are INCI names.
**Why it happens:** ML Kit performs structural text recognition, not semantic filtering.
**How to avoid:** Apply heuristic pre-filter before normalization: (a) uppercase the token, (b) reject tokens with only digits or common non-INCI patterns (`Regex("[0-9]+(\\s?[mMlLgG%])")`, `"NET WT"`, `"EXP"`, `"LOT"`), (c) reject tokens shorter than 4 characters. The INCINormalizer returning "UNRESOLVED" also acts as a post-filter.
**Warning signs:** Normalized token list has 40+ entries for a 15-ingredient product; non-ingredient strings like "50ML" or "MADE IN EU" appear in the output.

### Pitfall 5: Room `getAll()` Called Inside Every Levenshtein Call
**What goes wrong:** `ingredientDao.getAll()` is a Room suspend query — calling it for every token in a 20-token list triggers 20 sequential DB round-trips for the fuzzy fallback path.
**Why it happens:** Naive implementation of the normalization loop re-fetches the full ingredient list per token.
**How to avoid:** Load all ingredient names once before the normalization loop: `val allNames = ingredientDao.getAll().map { it.inciName }`. Pass the cached list into each `normalizeOne` call. For 80 ingredients, the full list is ~2 KB in memory — always cache it for the duration of a scan session.
**Warning signs:** Normalization of a 20-token list takes >500 ms; multiple "getAll" queries visible in Room query log.

### Pitfall 6: CameraX minSdk Compatibility (1.5+ requires minSdk 23)
**What goes wrong:** CameraX 1.5.0 and 1.6.0 require `minSdk = 23`. The project's `minSdk = 26` is safe, but if the version is downgraded to 1.4.x during troubleshooting, the `camera-mlkit-vision` artifact (not used here) requires extra attention.
**Why it happens:** CameraX 1.5.0-rc01 bumped minSdk from 21 to 23 to use modern Camera2 APIs.
**How to avoid:** Use 1.6.0 (minSdk 23 compatible, compileSdk 35 required — project already on compileSdk 35). Do not pin to 1.4.x.
**Warning signs:** Manifest merger warning about minSdk mismatch; `Lint` flags `NewApi` on `ProcessCameraProvider` calls.

---

## Code Examples

### ML Kit Bundled Variant — Verify No Network Model Download

```kotlin
// Source: developers.google.com/ml-kit/vision/text-recognition/v2/android [CITED]
// The bundled variant (com.google.mlkit:text-recognition) ships the model inside the APK.
// The unbundled variant (com.google.android.gms:play-services-mlkit-text-recognition)
// downloads the model from Google Play Services — PROHIBITED by PRIV-01.
// Verify correct artifact: check libs.versions.toml uses "com.google.mlkit" group, NOT "com.google.android.gms".

// Recognizer creation — no configuration object needed for Latin (cosmetic labels):
val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
```

### CameraX Use Case Combination — Preview + ImageCapture Only

```kotlin
// Source: developer.android.com/media/camera/camerax/architecture [CITED]
// Binding only Preview + ImageCapture uses fewer camera resources than Preview + ImageAnalysis + ImageCapture.
// Phase 2 does NOT use ImageAnalysis (no continuous frame processing needed).
cameraProvider.bindToLifecycle(
    lifecycleOwner,
    CameraSelector.DEFAULT_BACK_CAMERA,
    preview,      // for viewfinder display
    imageCapture  // for tap-to-capture
    // No ImageAnalysis — not needed here
)
```

### AndroidManifest.xml — Phase 2 Required Change

```xml
<!-- REQUIRED: Add before <application> tag -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Phase 1 privacy comment is still valid — INTERNET permission remains absent -->
```

### Levenshtein Distance — Verified Pure Kotlin (two-row optimization)

```kotlin
// Source: Standard dynamic programming algorithm [VERIFIED: multiple Kotlin gists cross-referenced]
// O(m*n) time, O(min(m,n)) space via row-swap technique
fun levenshtein(a: String, b: String): Int {
    val m = a.length; val n = b.length
    if (m == 0) return n; if (n == 0) return m
    val prev = IntArray(n + 1) { it }
    val curr = IntArray(n + 1)
    for (i in 0 until m) {
        curr[0] = i + 1
        for (j in 0 until n) {
            curr[j + 1] = minOf(
                curr[j] + 1,
                prev[j + 1] + 1,
                prev[j] + if (a[i] == b[j]) 0 else 1
            )
        }
        System.arraycopy(curr, 0, prev, 0, n + 1)
    }
    return prev[n]
}
```

### Knowledge Base SQL — Phase 2 Expansion Template

```sql
-- tools/build_seed_db.sh pattern for each new ingredient record
-- 70 additional INSERT statements cover: parabens (×6), retinoids (×4),
-- sunscreen filters (×15), fragrances (×20), preservatives (×10),
-- solvents/humectants (×10), surfactants (×5)
INSERT INTO ingredients (
    inciName, commonName, safetyTag, healthMechanism, affectedPopulation,
    doseThreshold, euStatus, usStatus, cnStatus, jpStatus,
    citationIds, confidence, dataValidAsOf
) VALUES (
    'ETHYLPARABEN',
    'Ethylparaben',
    'CAUTION',
    'Estrogenic activity at high concentrations; lower potency than methylparaben',
    'Pregnant individuals; infants',
    'EU limit: 0.4% (single), 0.8% (mixed)',
    'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED',
    '["SCCS_1514_13","CIR_PARABEN_2020"]',
    0.88,
    '2024-01-15'
);
```

---

## Runtime State Inventory

Step 2.5 SKIPPED — Phase 2 is a feature addition phase, not a rename/refactor. No runtime state needs to be migrated. The only persistent artifact being modified is `knowledge_base.db` (adding 70 rows to an existing table — schema unchanged).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android Studio | Build, run | Yes | Installed | — |
| adb (Android Debug Bridge) | Device deployment | Yes | 1.0.41 | — |
| Physical Android device (minSdk 26+) | CameraX integration testing | UNKNOWN | — | None — camera requires physical hardware |
| sqlite3 (system binary) | tools/build_seed_db.sh rebuild | UNKNOWN | — | Can use Python sqlite3 module |
| Python3 | build_seed_db.sh identity_hash extraction | Unknown (used in Phase 1 successfully) | — | Fallback: grep in shell script |

**Missing dependencies with no fallback:**
- Physical Android device: CameraX preview and image capture require real hardware; emulators provide a software camera but accuracy benchmarking against real product labels requires a physical device.

**Missing dependencies with fallback:**
- `sqlite3` binary for DB rebuild: Phase 1 successfully ran `build_seed_db.sh` (tools dir confirmed). If missing on a CI machine, can substitute with Python `sqlite3` module directly.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Android Instrumented Tests (androidx.test / HiltAndroidTest) + JUnit 4 unit tests |
| Config file | `app/src/androidTest/` (instrumented) and `app/src/test/` (unit) |
| Quick run command | `./gradlew testDebugUnitTest` |
| Full suite command | `./gradlew connectedDebugAndroidTest` (physical device required) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCAN-01 | CameraX captures image and ML Kit extracts text tokens | Instrumented (physical device) | `./gradlew connectedDebugAndroidTest --tests "*.OcrPipelineTest"` | No — Wave 0 |
| SCAN-01 | OCR result arrives within 3 seconds of tap | Instrumented (manual timing) | Manual benchmark on 5 real labels | No — manual |
| SCAN-02 | Manual text input path produces same normalized list as OCR path | Unit | `./gradlew testDebugUnitTest --tests "*.INCINormalizerTest"` | No — Wave 0 |
| SCAN-03 | INCI normalization: uppercase, synonym, Levenshtein ≥80% token recovery | Unit | `./gradlew testDebugUnitTest --tests "*.INCINormalizerTest"` | No — Wave 0 |
| PROF-01 | Session profile UI shows three dropdown selectors | Manual / UI test | Manual on device | No — manual |
| PROF-03 | Session profile cleared on app close | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.SessionClearTest"` | No — Wave 0 |
| DATA-01 | knowledge_base.db contains exactly 80 ingredient records | Unit (DB asset check) | `./gradlew testDebugUnitTest --tests "*.KnowledgeBaseCompletenessTest"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew testDebugUnitTest` (unit tests only, <30s)
- **Per wave merge:** `./gradlew connectedDebugAndroidTest` (physical device)
- **Phase gate:** All unit tests green + manual OCR benchmark on 5 real labels ≥80% token recovery

### Wave 0 Gaps

- [ ] `app/src/test/java/com/safeglow/edge/normalization/INCINormalizerTest.kt` — covers SCAN-02, SCAN-03; pure JVM unit test
- [ ] `app/src/test/java/com/safeglow/edge/normalization/LevenshteinDistanceTest.kt` — covers Levenshtein correctness
- [ ] `app/src/test/java/com/safeglow/edge/KnowledgeBaseCompletenessTest.kt` — opens assets/knowledge_base.db via SQLite JDBC, asserts count(*) = 80; covers DATA-01
- [ ] `app/src/androidTest/java/com/safeglow/edge/OcrPipelineTest.kt` — covers SCAN-01; requires physical device
- [ ] `app/src/androidTest/java/com/safeglow/edge/SessionClearTest.kt` — covers PROF-03; verifies SessionViewModel.onCleared() resets to default

---

## Security Domain

Phase 2 introduces camera input and user-provided text. PRIV-01/PRIV-03 from Phase 1 must remain intact.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth by design (PRIV-02) |
| V3 Session Management | Partial | Session context in ViewModel — ephemeral only; PROF-03 prohibits persistence |
| V4 Access Control | No | Single-user local app |
| V5 Input Validation | Yes | Manual ingredient text input must be stripped of control characters before normalization; max length enforcement (1000 chars) to prevent OOM |
| V6 Cryptography | No | No secrets or encrypted data |
| V9 Communication | Yes — PRIV-01/PRIV-03 | ML Kit bundled variant (not play-services) must be confirmed; no new network permissions |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| ML Kit unbundled variant pulled via transitive dep | Information Disclosure | Verify `com.google.mlkit` (not `com.google.android.gms`) in `./gradlew :app:dependencies | grep mlkit` |
| Camera image persisted to external storage | Information Disclosure | `ImageCapture.OnImageCapturedCallback` (in-memory only) — never use `OutputFileOptions` / `MediaStore`; never write captured image to disk (PRIV-03) |
| Manual ingredient text injection (SQL/shell injection) | Tampering | INCINormalizer stage-1 strip regex removes all non-alpha characters before any SQL query; Room uses parameterized queries — never string concatenation |
| CAMERA permission added to manifest enables camera-related metadata leakage | Information Disclosure | No `ACCESS_FINE_LOCATION` or `READ_MEDIA_*` permissions; camera permission must remain the only new permission added |
| Session profile data written to SharedPreferences by accident | Information Disclosure | Verify no `SharedPreferences` calls in SessionViewModel via `grep -r 'SharedPreferences\|getSharedPreferences\|PreferenceManager' app/src/main/kotlin` = 0 |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `androidx.camera:camera-*:1.4.x` | `1.6.0` | 2026-03-25 stable release | Uses CameraPipe backend (same as Pixel camera); minSdk bumped to 23 (compatible with project minSdk 26) |
| `AndroidView { PreviewView }` (only option pre-1.5) | Same AndroidView approach OR alpha `camera-compose:1.5.x` | Dec 2024 blog (alpha) | `camera-compose` is alpha — not for production; AndroidView + PreviewView remains the stable standard |
| ML Kit Text Recognition v1 (Vision API) | ML Kit Text Recognition v2 (16.0.1) | 2022 | v2 uses a completely new model with higher accuracy; v1 is deprecated |
| `com.google.firebase:firebase-ml-vision` | `com.google.mlkit:text-recognition` | 2021 | Firebase ML Kit renamed to standalone ML Kit; Firebase dependency removed |

**Deprecated/outdated:**
- `firebase-ml-vision`: Deprecated. Use `com.google.mlkit:text-recognition`. [VERIFIED: firebase.google.com/docs/ml-kit/android/recognize-text]
- ML Kit Text Recognition v1: Deprecated; lower accuracy on curved or low-contrast label surfaces. [VERIFIED: developers.google.com/ml-kit/migration/android]
- `camera-compose` for production use: Alpha stability — API can change without notice. [VERIFIED: Android Developers Blog Dec 2024]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `accompanist-permissions:0.37.3` is compatible with Compose BOM 2026.03.00 | Standard Stack | Build failure; must check accompanist compatibility matrix and downgrade/upgrade |
| A2 | `camera-*:1.6.0` does not conflict with `compileSdk = 35` already set in the project | Standard Stack | Build warning or error if CameraX 1.6.0 required a compileSdk upgrade above 35 (unlikely — confirmed compileSdk 35 in release notes) |
| A3 | Levenshtein threshold of ≤ 2 achieves ≥ 80% token recovery on real cosmetic labels | Architecture Patterns / SCAN-03 | Threshold too strict → more UNRESOLVED tokens; threshold too loose → false matches; must be validated empirically against 5 real product labels in phase benchmarking |
| A4 | 80 priority INCI ingredients can be authored with authoritative citations (SCCS/CIR/ACOG/IARC) within the phase timeline | DATA-01 / Code Examples | Citation sourcing is the bottleneck; each record requires a real regulatory citation; budget ~15 minutes per novel ingredient record |
| A5 | The synonym map covering ~60 aliases (one per Phase 2 ingredient) is sufficient to achieve ≥ 80% token recovery | Architecture Patterns | Insufficient synonym coverage on certain common brand names or informal names; must be extended after empirical testing |

---

## Open Questions

1. **Should the CameraScreen live in `MainActivity` or be a separate destination in a Navigation graph?**
   - What we know: Phase 2 adds CameraScreen and ProfileScreen. Phase 5 adds ResultScreen. Phase 1 has a single-Activity placeholder.
   - What's unclear: Whether to introduce `androidx.navigation:navigation-compose` in Phase 2 or defer navigation to Phase 5.
   - Recommendation: Introduce navigation-compose in Phase 2 with two routes (CAMERA, PROFILE). This avoids a major refactor in Phase 5 and allows the OCR → normalized-tokens flow to be tested end-to-end via navigation. Navigation-compose is stable and low-risk.

2. **How should OCR results be passed between CameraScreen and the eventual ResultsScreen?**
   - What we know: Phase 2 ends at normalized token list. Phase 3 picks up that list for RAG. Phase 5 builds ResultsScreen.
   - What's unclear: Whether `normalizedTokens: List<String>` should be a Navigation argument or held in a shared `ScanViewModel` above the navigation graph.
   - Recommendation: Use a shared `ScanViewModel` (scoped to the Navigation back-stack entry or Activity) to hold scan state between phases. Navigation arguments are for primitive types and are limited by Intent bundle size.

3. **What is the empirical OCR accuracy on physical cosmetic labels?**
   - What we know: ML Kit Text Recognition v2 is highly accurate on printed label text. The 3-second success criterion is hardware-dependent.
   - What's unclear: Actual accuracy on small-print label text on curved surfaces (tube/bottle). Ingredients on cosmetic packaging are often in very small font.
   - Recommendation: Run OCR benchmark on 5 real product labels as the first instrumented test in the phase. If accuracy is below 80% before normalization, add an image pre-processing step (CLAHE contrast enhancement via `android.graphics.ColorMatrix` or `RenderScript`).

---

## Sources

### Primary (HIGH confidence)
- `developer.android.com/jetpack/androidx/releases/camera` — CameraX 1.6.0 stable release date (2026-03-25), minSdk 23, compileSdk 35 requirement, CameraPipe migration [CITED]
- `developers.google.com/ml-kit/vision/text-recognition/v2/android` — ML Kit v2 API: TextRecognition.getClient, InputImage.fromMediaImage, TextRecognizerOptions.DEFAULT_OPTIONS, ImageProxy close requirement, bundled (~4 MB) vs unbundled model sizes; docs updated 2026-04-22 [CITED]
- `developer.android.com/media/camera/camerax/take-photo` — ImageCapture.takePicture(executor, OnImageCapturedCallback), ImageProxy to Bitmap conversion, ProcessCameraProvider.bindToLifecycle [CITED]
- `developer.android.com/topic/libraries/architecture/viewmodel` — ViewModel lifecycle, cleared on Activity finish, NOT cleared on configuration change, PROF-03 rationale for plain StateFlow [CITED]
- Phase 1 RESEARCH.md / PATTERNS.md / VERIFICATION.md — existing Hilt modules, IngredientDao API, Room schema, actual pinned versions from libs.versions.toml [VERIFIED: codebase grep]

### Secondary (MEDIUM confidence)
- `android-developers.googleblog.com/2024/12/whats-new-in-camerax-140-and-jetpack-compose-support.html` — camera-compose alpha status; CameraXViewfinder not yet stable [MEDIUM — official blog but dec 2024]
- `android-developers.googleblog.com/2025/11/introducing-camerax-15-powerful-video.html` — CameraX 1.5 stable features; 1.6 release confirmed in subsequent release notes [MEDIUM]
- `atomicrobot.com/blog/mlkit-on-device-ocr-android/` — STRATEGY_KEEP_ONLY_LATEST for ImageAnalysis; confidence threshold filtering for off-angle images [MEDIUM — non-official but technically specific]
- `libraries.io/maven/com.google.accompanist:accompanist-permissions` — accompanist-permissions:0.37.3 latest version [MEDIUM]
- `mvnrepository.com/artifact/com.google.mlkit/text-recognition` — text-recognition:16.0.1 latest version [MEDIUM — registry page]

### Tertiary (LOW confidence)
- A1: accompanist-permissions:0.37.3 ↔ Compose BOM 2026.03.00 compatibility — not verified against official accompanist compatibility matrix
- A3: Levenshtein threshold ≤ 2 achieving ≥ 80% token recovery — empirical claim, not verified against actual cosmetic label data
- A4: DATA-01 citation authoring timeline — depends on user doing manual research per ingredient

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — CameraX 1.6.0 stable confirmed on official release notes; ML Kit 16.0.1 confirmed on mvnrepository and official docs; accompanist 0.37.3 confirmed on libraries.io
- Architecture: HIGH — CameraX + ML Kit integration pattern sourced from official docs; Levenshtein is standard algorithm; session ViewModel pattern from official ViewModel docs
- Pitfalls: HIGH for ImageProxy close and permission manifest (documented in official ML Kit guide and commonly reported); MEDIUM for Levenshtein threshold tuning (needs empirical validation)
- Data (DATA-01): MEDIUM — schema is locked, expansion is straightforward, but citation sourcing timeline is not verified

**Research date:** 2026-04-28
**Valid until:** 2026-05-02 (hackathon deadline; CameraX and ML Kit are stable releases, unlikely to change)
