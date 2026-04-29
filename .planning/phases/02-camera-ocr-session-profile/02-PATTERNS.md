# Phase 2: Camera + OCR + Session Profile — Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 16 (11 Kotlin source + 2 test groups + 1 shell script + 1 DB asset + 1 manifest)
**Analogs found:** 14 / 16 (2 files have no analog — first ViewModel and first Compose screen in codebase)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `camera/CameraScreen.kt` | component (Compose screen) | request-response | `MainActivity.kt` | role-partial (only Compose screen in project) |
| `camera/CameraViewModel.kt` | provider (ViewModel) | request-response | `LiteRTInferenceService.kt` | data-flow-match (suspend + coroutine dispatcher) |
| `ocr/OcrRepository.kt` | service | request-response | `LiteRTInferenceService.kt` | role-match (background-thread I/O service) |
| `ocr/MLKitOcrProcessor.kt` | utility | transform | `LiteRTInferenceService.kt` | data-flow-match (wraps third-party SDK call) |
| `normalization/INCINormalizer.kt` | service | transform | `LiteRTInferenceService.kt` + `IngredientDao.kt` | partial (suspend + DAO injection) |
| `normalization/INCISynonymMap.kt` | utility | transform | none — pure Kotlin object | no-analog |
| `normalization/LevenshteinDistance.kt` | utility | transform | none — pure Kotlin function | no-analog |
| `session/SessionProfile.kt` | model | — | `IngredientRecord.kt` | role-match (data class) |
| `session/SessionEnums.kt` | model | — | `IngredientRecord.kt` (safetyTag string constants) | partial |
| `session/SessionViewModel.kt` | provider (ViewModel) | event-driven | `LiteRTInferenceService.kt` | data-flow-match (StateFlow pattern) |
| `session/ProfileScreen.kt` | component (Compose screen) | event-driven | `MainActivity.kt` | role-partial (only Compose screen in project) |
| `di/CameraModule.kt` | config (Hilt module) | — | `DatabaseModule.kt` + `InferenceModule.kt` | exact (same @Module + @InstallIn + @Provides shape) |
| `tools/build_seed_db.sh` (extend) | config (build script) | batch | `tools/build_seed_db.sh` | exact (extend existing file) |
| `app/src/main/assets/knowledge_base.db` (rebuild) | config (DB asset) | batch | existing asset | exact (same schema, more rows) |
| Test: `INCINormalizerTest.kt`, `LevenshteinDistanceTest.kt`, `KnowledgeBaseCompletenessTest.kt` | test (unit) | — | `RoomSeedTest.kt` | role-match (JUnit4 + runBlocking) |
| Test: `OcrPipelineTest.kt`, `SessionClearTest.kt` | test (instrumented) | — | `RoomSeedTest.kt` + `HiltAndroidTest` pattern | exact |

---

## Pattern Assignments

### `camera/CameraScreen.kt` (component, request-response)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/MainActivity.kt`

The project has one Compose entry point. CameraScreen follows the same `@AndroidEntryPoint` + `setContent { MaterialTheme { Surface { ... } } }` shell but lives as a standalone `@Composable` function injected via `hiltViewModel()`.

**Imports pattern** (MainActivity.kt lines 1–13):
```kotlin
package com.safeglow.edge.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
// + CameraX, Accompanist imports
```

**Compose screen shell pattern** (MainActivity.kt lines 19–35):
```kotlin
// CameraScreen is a @Composable function (not an Activity).
// MainActivity hosts it via NavHost (Phase 2 introduces navigation-compose).
// Copy the Surface + MaterialTheme wrapper when composing the permission gate state.
MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CameraScreen()  // or NavHost routes to this
    }
}
```

**No auth/guard pattern exists** in Phase 1 (no permissions declared). CameraScreen is the FIRST screen in the project to use runtime permissions — copy the Accompanist pattern from RESEARCH.md Pattern 1 directly (no codebase analog exists for permission gate).

**Core Compose pattern — AndroidView wrapper** (copy structure from MainActivity.kt, adapt):
```kotlin
// The only AndroidView in the project is new in Phase 2.
// Pattern: use remember{} for stateful Android views; LaunchedEffect(Unit) for
// one-time side effects that require suspend/coroutine context.
// Source: MainActivity.kt lines 22-30 shows the setContent/MaterialTheme/Surface shell.
// The AndroidView + PreviewView is new — use RESEARCH.md Pattern 1 verbatim.
```

**Error handling:** No try/catch in MainActivity.kt — it is a scaffold. CameraScreen error states are managed via `ScanUiState` sealed class in the ViewModel (state-driven, not exception-driven in the Compose layer).

---

### `camera/CameraViewModel.kt` (provider, request-response)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt`

Best match for the threading + suspend pattern. Both hold a single resource (Engine / ImageCapture), initialize it once, and dispatch background work on a dedicated executor.

**Imports pattern** (LiteRTInferenceService.kt lines 1–17):
```kotlin
package com.safeglow.edge.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
```

**@HiltViewModel + @Inject constructor pattern** — the project currently uses `@Singleton` + `@Inject constructor` in services (LiteRTInferenceService.kt line 38). CameraViewModel follows the same `@Inject constructor` convention but with `@HiltViewModel` instead of `@Singleton`. No existing `@HiltViewModel` in the project — use RESEARCH.md Pattern 4 (SessionViewModel) as the definitive shape:
```kotlin
// LiteRTInferenceService.kt line 38 — copy the @Inject constructor shape:
@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
)

// CameraViewModel adapts to @HiltViewModel:
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val ocrRepository: OcrRepository,
    private val inciNormalizer: INCINormalizer
) : ViewModel()
```

**Dedicated executor pattern** (LiteRTInferenceService.kt lines 41–42):
```kotlin
// Copy this pattern — CameraX takePicture() requires an Executor, not a coroutine dispatcher.
// Use the same newSingleThreadExecutor approach as inference.
private val inferenceDispatcher =
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
```

**StateFlow state holder pattern** (LiteRTInferenceService.kt lines 44–45 show @Volatile engine; SessionViewModel from RESEARCH.md Pattern 4 shows the StateFlow shape):
```kotlin
// Use MutableStateFlow for UI state — same pattern as SessionViewModel in RESEARCH.md:
private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
```

**Suspend + withContext pattern** (LiteRTInferenceService.kt lines 47–71):
```kotlin
// Copy the suspend fun + withContext(dispatcher) shape exactly:
suspend fun initialize(): Unit = withContext(inferenceDispatcher) {
    if (engine != null) return@withContext
    // ... initialization logic
}
// CameraViewModel.captureAndProcess() follows same pattern:
fun captureAndProcess() {
    viewModelScope.launch {
        _uiState.value = ScanUiState.Capturing
        // ... suspend calls to OcrRepository, INCINormalizer
    }
}
```

**Error handling pattern** (LiteRTInferenceService.kt lines 53–70):
```kotlin
// GPU fallback try/catch is the codebase's canonical error handling shape:
engine = try {
    Engine(EngineConfig(..., backend = Backend.GPU())).also { it.initialize() }
} catch (e: Exception) {
    Log.w(TAG, "GPU init failed (${e.message}), falling back to CPU")
    Engine(EngineConfig(..., backend = Backend.CPU())).also { it.initialize() }
}
// CameraViewModel adapts:
try {
    val tokens = ocrRepository.extractRawTokens(imageCapture)
    // ...
} catch (e: Exception) {
    Log.e(TAG, "OCR capture failed: ${e.message}")
    _uiState.value = ScanUiState.Error(e.message ?: "Capture failed")
}
```

**Companion object TAG pattern** (LiteRTInferenceService.kt lines 93–96):
```kotlin
companion object {
    private const val TAG = "CameraViewModel"
}
```

---

### `ocr/OcrRepository.kt` (service, request-response)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt`

OcrRepository wraps a third-party SDK (ML Kit) behind a suspend interface — exactly as LiteRTInferenceService wraps the LiteRT Engine.

**Class declaration + @Inject constructor pattern** (LiteRTInferenceService.kt line 38):
```kotlin
// LiteRTInferenceService uses @Singleton + @Inject + @ApplicationContext.
// OcrRepository is NOT @Singleton (one TextRecognizer client is enough but
// there's no state to protect across calls) — use @Inject constructor only:
class OcrRepository @Inject constructor() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()
    // suspend fun extractRawTokens(imageCapture: ImageCapture): List<String>
}
```

**suspendCancellableCoroutine bridge pattern** — no existing analog in Phase 1 (all Phase 1 suspend work uses `withContext`). Use RESEARCH.md Pattern 2 verbatim. The key protocol elements to copy:
- `suspendCancellableCoroutine { continuation -> ... }`
- `continuation.resume(value)` in success listener
- `continuation.resumeWithException(e)` in failure listener
- `image.close()` in `addOnCompleteListener` (not in success/failure listeners)

**Flow pattern for comparison** (LiteRTInferenceService.kt lines 73–85 — channelFlow):
```kotlin
// LiteRTInferenceService uses channelFlow + awaitClose for streaming.
// OcrRepository uses suspendCancellableCoroutine for one-shot async bridge.
// Both are coroutine-to-callback bridges — different variants for different cardinality.
fun infer(prompt: String): Flow<String> = channelFlow {
    withContext(inferenceDispatcher) { ... }
    awaitClose { }
}
```

---

### `ocr/MLKitOcrProcessor.kt` (utility, transform)

**Analog:** Inline in `OcrRepository.kt` per RESEARCH.md Pattern 2. If extracted as a separate class, copy the `LiteRTInferenceService` single-responsibility shape — one class, one SDK client, no state beyond the client reference.

**Core pattern** (RESEARCH.md Pattern 2, no codebase analog — use research pattern directly):
```kotlin
// InputImage.fromMediaImage requires the mediaImage to still be open.
// Close only in addOnCompleteListener.
val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
recognizer.process(inputImage)
    .addOnSuccessListener { visionText -> continuation.resume(visionText.toRawTokens()) }
    .addOnFailureListener { e -> continuation.resumeWithException(e) }
    .addOnCompleteListener { image.close() }  // MUST be last; fires regardless of success/failure
```

---

### `normalization/INCINormalizer.kt` (service, transform)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt` (DAO suspend pattern) + `LiteRTInferenceService.kt` (@Inject constructor shape)

**@Inject constructor + DAO injection pattern** (IngredientDao.kt lines 7–25 + DatabaseModule.kt lines 32–33):
```kotlin
// IngredientDao is injected by Hilt via DatabaseModule.provideIngredientDao().
// INCINormalizer receives it via @Inject constructor — no module needed:
class INCINormalizer @Inject constructor(
    private val synonymMap: INCISynonymMap,
    private val ingredientDao: IngredientDao
) {
    // suspend fun normalize(rawTokens: List<String>): List<String>
}
```

**suspend DAO call pattern** (IngredientDao.kt lines 11–25):
```kotlin
// All DAO methods are suspend — copy the call pattern:
@Query("SELECT * FROM ingredients WHERE inciName = :name LIMIT 1")
suspend fun findExact(name: String): IngredientRecord?

@Query("SELECT i.* FROM ingredients i JOIN ingredients_fts fts ON i.rowid = fts.rowid WHERE ingredients_fts MATCH :query LIMIT 10")
suspend fun ftsSearch(query: String): List<IngredientRecord>

@Query("SELECT * FROM ingredients")
suspend fun getAll(): List<IngredientRecord>
```

**Critical performance pattern** — load `getAll()` ONCE before the normalization loop (RESEARCH.md Pitfall 5):
```kotlin
// Do NOT call ingredientDao.getAll() inside normalizeOne().
// Load once before the loop:
suspend fun normalize(rawTokens: List<String>): List<String> {
    val allNames = ingredientDao.getAll().map { it.inciName }  // cache once
    return rawTokens.map { token -> normalizeOne(token, allNames) }
}
```

---

### `normalization/INCISynonymMap.kt` (utility, transform)

**No codebase analog.** First pure-Kotlin `object` with a hardcoded `Map` in the project.

**Nearest structural reference:** `LiteRTInferenceService.companion object` (lines 93–96) shows the `companion object` / `object` declaration convention:
```kotlin
// LiteRTInferenceService.kt companion object pattern:
companion object {
    private const val TAG = "LiteRTInferenceService"
    private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
}
// INCISynonymMap is a top-level object (not companion):
object INCISynonymMap {
    private val synonyms: Map<String, String> = mapOf(...)
    fun resolve(cleaned: String): String = synonyms[cleaned] ?: cleaned
}
```

Use RESEARCH.md Pattern 5 verbatim for the synonym entries.

---

### `normalization/LevenshteinDistance.kt` (utility, transform)

**No codebase analog.** First pure-Kotlin top-level function file in the project.

**File convention:** Follow the same `package com.safeglow.edge.normalization` + single-responsibility shape. The function is a top-level `fun levenshtein(a: String, b: String): Int` — no class wrapper. Use RESEARCH.md Code Examples (lines 588–606) verbatim. The two-row `System.arraycopy` optimization is the verified implementation.

---

### `session/SessionProfile.kt` (model, —)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientRecord.kt`

**data class pattern** (IngredientRecord.kt lines 21–36):
```kotlin
// IngredientRecord uses @Entity annotation — SessionProfile does NOT (no Room persistence).
// Copy the data class shape, omit all Room annotations:
@Entity(tableName = "ingredients")
data class IngredientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inciName: String,
    // ...
)

// SessionProfile adapts:
data class SessionProfile(
    val pregnancyStatus: PregnancyStatus = PregnancyStatus.NOT_SET,
    val country: Country = Country.NOT_SET,
    val skinConcern: SkinConcern = SkinConcern.NOT_SET
)
// All fields have defaults — enables copy() updates in SessionViewModel.
```

---

### `session/SessionEnums.kt` (model, —)

**Analog:** `IngredientRecord.kt` (safetyTag field as string constant) — partial match only. No existing Kotlin `enum class` in the project.

**Pattern:** Three enum classes in one file. Each enum has a `NOT_SET` sentinel as its first value. File follows the same package convention:
```kotlin
package com.safeglow.edge.session

enum class PregnancyStatus { NOT_SET, PREGNANT, NOT_PREGNANT }
enum class Country { NOT_SET, EU, US, CN, JP }
enum class SkinConcern { NOT_SET, NORMAL, SENSITIVE, DRY, OILY }
```

---

### `session/SessionViewModel.kt` (provider, event-driven)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt`

LiteRTInferenceService is the closest analog for the StateFlow pattern (lines 44–45 show `@Volatile private var engine`) and the `@Inject constructor` convention. The ViewModel adapts this to `MutableStateFlow` + `_profile.update { it.copy(...) }`.

**@HiltViewModel + @Inject constructor + StateFlow pattern** (RESEARCH.md Pattern 4 — verified against Hilt docs; LiteRTInferenceService.kt line 38–42 is the @Inject shape to copy):
```kotlin
// LiteRTInferenceService.kt @Inject constructor + @Singleton:
@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var engine: Engine? = null
    // ...
}

// SessionViewModel adapts (NO @ApplicationContext needed — no Android context required):
@HiltViewModel
class SessionViewModel @Inject constructor() : ViewModel() {
    private val _profile = MutableStateFlow(SessionProfile())
    val profile: StateFlow<SessionProfile> = _profile.asStateFlow()

    fun setPregnancyStatus(status: PregnancyStatus) {
        _profile.update { it.copy(pregnancyStatus = status) }
    }
    // onCleared() not overridden — ViewModel lifecycle handles cleanup
}
```

**Critical constraint — NO SavedStateHandle, NO SharedPreferences, NO Room** (PROF-03). `LiteRTInferenceService` demonstrates the correct pattern: all state is in-memory instance variables, cleared when the object is GC'd.

---

### `session/ProfileScreen.kt` (component, event-driven)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/MainActivity.kt`

Only Compose screen in Phase 1. ProfileScreen is a `@Composable` function (not an Activity). Copy the `MaterialTheme` + `Surface` shell when wrapping the screen, and use `hiltViewModel()` injection.

**Imports pattern** (MainActivity.kt lines 1–13 — adapt for Compose screen):
```kotlin
package com.safeglow.edge.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
```

**collectAsState pattern** — no existing analog in Phase 1 (MainActivity has no ViewModel). Use the standard Compose + StateFlow pattern:
```kotlin
// Collect SessionViewModel.profile StateFlow in Compose:
val profile by viewModel.profile.collectAsState()
// Then use profile.pregnancyStatus, profile.country, profile.skinConcern
// to drive ExposedDropdownMenuBox selected value display.
```

**UI component choice:** `ExposedDropdownMenuBox` from `material3` (NOT `DropdownMenu` — see UI-SPEC.md line 195). No existing analog in project for this component.

---

### `di/CameraModule.kt` (config, —)

**Analog:** `app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt` AND `app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt`

Exact match — same `@Module + @InstallIn(SingletonComponent::class) + object + @Provides` shape.

**Full module pattern** (DatabaseModule.kt lines 14–33 — copy exactly):
```kotlin
// DatabaseModule.kt — canonical Hilt module shape in this project:
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "knowledge_base.db")
            .createFromAsset("knowledge_base.db")
            .build()

    @Provides
    fun provideIngredientDao(db: AppDatabase): IngredientDao = db.ingredientDao()
}

// CameraModule adapts (if providing ImageCapture use case):
@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideImageCapture(): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
}
```

**InferenceModule.kt lines 22–31 shows the @Provides + @Singleton + @ApplicationContext shape for context-dependent providers:**
```kotlin
@Provides
@Singleton
fun provideLiteRTInferenceService(
    @ApplicationContext context: Context
): LiteRTInferenceService = LiteRTInferenceService(context)
```

**Note on optionality:** RESEARCH.md marks CameraModule as optional. If `CameraViewModel` holds the `ImageCapture` reference directly (constructed inside `LaunchedEffect`), no Hilt module is needed. If a module is added, it MUST follow the DatabaseModule/InferenceModule shape exactly.

---

### `tools/build_seed_db.sh` (config, batch — EXTEND existing)

**Analog:** `tools/build_seed_db.sh` (exact — extend the existing file)

**Existing script structure** (build_seed_db.sh lines 1–81 — do not alter the schema or room_master_table sections):
- Lines 1–30: Header, `set -euo pipefail`, `identityHash` extraction — DO NOT MODIFY
- Lines 33–58: `CREATE TABLE` statements — DO NOT MODIFY (schema is locked)
- Lines 60–71: 10 existing `INSERT INTO ingredients` rows — DO NOT MODIFY (preserve Phase 1 data)
- Lines 73–80: FTS backfill + validation — keep at end, after all INSERTs

**Extension pattern — add 70 INSERT statements between line 71 and line 73:**
```sql
-- Phase 2 additions: 70 new rows after the Phase 1 seed block.
-- Follow the exact column order from the existing INSERTs:
INSERT INTO ingredients (inciName, commonName, safetyTag, healthMechanism, affectedPopulation, doseThreshold, euStatus, usStatus, cnStatus, jpStatus, citationIds, confidence, dataValidAsOf) VALUES
    ('ETHYLPARABEN', 'Ethylparaben', 'CAUTION', '...', '...', '...', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1514_13"]', 0.88, '2024-01-15'),
    -- ... remaining 69 rows
```

**Validation command** (build_seed_db.sh line 80 — update expected count):
```bash
# After extension, the final count assertion should be 80, not 10:
sqlite3 "$OUT" "SELECT count(*) FROM ingredients;"
# Confirm output is 80 before committing the DB.
```

---

### Test files — Unit tests: `INCINormalizerTest.kt`, `LevenshteinDistanceTest.kt`, `KnowledgeBaseCompletenessTest.kt`

**Analog:** `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt`

Unit tests live in `app/src/test/` (not `androidTest/`). No existing unit test files in the project (`Glob("app/src/test/**/*.kt")` returned empty). Use the instrumented test shape from RoomSeedTest.kt but strip Hilt + `@HiltAndroidTest` — pure JUnit4.

**RoomSeedTest.kt structure to copy and simplify** (lines 1–49):
```kotlin
// RoomSeedTest.kt — instrumented test shape:
@HiltAndroidTest
class RoomSeedTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var dao: IngredientDao

    @Before fun setUp() { hiltRule.inject() }

    @Test
    fun tenSeedRecordsReadable() = runBlocking {
        val records = dao.getAll()
        assertTrue("Expected at least 10 seed records", records.size >= 10)
    }
}

// Unit test adapts (no Hilt, no @Inject, no runBlocking for pure Kotlin):
class LevenshteinDistanceTest {

    @Test
    fun identicalStringsReturnZero() {
        assertEquals(0, levenshtein("METHYLPARABEN", "METHYLPARABEN"))
    }

    @Test
    fun singleSubstitutionReturnsOne() {
        assertEquals(1, levenshtein("METHYLPARABEN", "METHYLPARAEEN"))
    }

    @Test
    fun thresholdGuardRejectsShortTokens() {
        // Tokens shorter than 6 chars should not be fuzzy-matched
        assertTrue(levenshtein("CI", "CIN") > 2)
    }
}
```

**KnowledgeBaseCompletenessTest.kt** — opens the asset DB via JDBC (no Room needed for a count check):
```kotlin
// No Hilt, no Android dependencies — pure JVM unit test using JDBC:
// Requires sqlite-jdbc on testImplementation classpath.
class KnowledgeBaseCompletenessTest {
    @Test
    fun knowledgeBaseContainsEightyRows() {
        // Use DriverManager.getConnection("jdbc:sqlite:app/src/main/assets/knowledge_base.db")
        // or copy the asset to a temp file and query via sqlite4java.
        // Assert count(*) == 80.
    }
}
```

**INCINormalizerTest.kt** — tests normalize() with a real or stubbed IngredientDao:
```kotlin
// Copy runBlocking pattern from RoomSeedTest.kt line 35:
@Test
fun methylparabenSynonymResolvesToCanonical() = runBlocking {
    val result = normalizer.normalize(listOf("METHYL PARABEN"))
    assertEquals(listOf("METHYLPARABEN"), result)
}
```

---

### Test files — Instrumented: `OcrPipelineTest.kt`, `SessionClearTest.kt`

**Analog:** `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt` + `HiltAndroidTest.kt` + `InferenceServiceTest.kt`

**Full instrumented test pattern** (copy from RoomSeedTest.kt lines 22–49 + InferenceServiceTest.kt lines 26–52):
```kotlin
// RoomSeedTest.kt + InferenceServiceTest.kt — canonical instrumented test shape:
@HiltAndroidTest
class SessionClearTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionViewModel: SessionViewModel  // injected via Hilt

    @Before fun setUp() { hiltRule.inject() }

    @Test
    fun profileDefaultsToNotSetOnCreation() = runBlocking {
        // SessionViewModel starts with all NOT_SET values — PROF-03 baseline
        assertEquals(PregnancyStatus.NOT_SET, sessionViewModel.profile.value.pregnancyStatus)
        assertEquals(Country.NOT_SET, sessionViewModel.profile.value.country)
        assertEquals(SkinConcern.NOT_SET, sessionViewModel.profile.value.skinConcern)
    }

    @Test
    fun profileUpdatesThenClearsOnCleared() = runBlocking {
        sessionViewModel.setPregnancyStatus(PregnancyStatus.PREGNANT)
        assertEquals(PregnancyStatus.PREGNANT, sessionViewModel.profile.value.pregnancyStatus)
        // Simulate onCleared() — call the ViewModel's onCleared via reflection or
        // by recreating the ViewModel without SavedStateHandle restoration
    }
}
```

**HiltTestRunner** (HiltTestRunner.kt lines 12–20 — already exists, no changes needed):
```kotlin
// This runner is already registered in app/build.gradle.kts line 19.
// All new instrumented tests inherit it automatically via testInstrumentationRunner.
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

**@After tearDown pattern** (InferenceServiceTest.kt lines 34–36):
```kotlin
// InferenceServiceTest uses @After for cleanup:
@After
fun tearDown() { inferenceService.close() }
// OcrPipelineTest should close ImageCapture / recognizer in @After.
```

---

## Shared Patterns

### Hilt @Inject constructor
**Source:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt` lines 38–40
**Apply to:** `OcrRepository`, `INCINormalizer`, `CameraViewModel`, `SessionViewModel`
```kotlin
// Every injectable class in the project uses this exact convention — no variation:
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
)
// Classes without Android context dependency omit @ApplicationContext:
class OcrRepository @Inject constructor()
class INCINormalizer @Inject constructor(private val dao: IngredientDao)
```

### Hilt Module declaration
**Source:** `app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt` lines 14–33
**Apply to:** `di/CameraModule.kt` (if created)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = ...

    @Provides  // Note: NO @Singleton on DAO — Room DAO is thread-safe by design
    fun provideIngredientDao(db: AppDatabase): IngredientDao = db.ingredientDao()
}
```

### Dedicated Executor (for thread-affinity I/O)
**Source:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt` lines 41–42
**Apply to:** `OcrRepository` (camera executor for `takePicture`)
```kotlin
private val inferenceDispatcher =
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
// OcrRepository copies this for the camera executor:
private val executor = Executors.newSingleThreadExecutor()
// Note: OcrRepository passes executor to takePicture() directly (not as dispatcher).
```

### Room suspend DAO calls
**Source:** `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt` lines 8–25
**Apply to:** `INCINormalizer` (calls `findExact`, `ftsSearch`, `getAll`)
```kotlin
// All three DAO methods are suspend — call them from a suspend context:
suspend fun findExact(name: String): IngredientRecord?
suspend fun ftsSearch(query: String): List<IngredientRecord>
suspend fun getAll(): List<IngredientRecord>
// INCINormalizer.normalize() must itself be suspend to call these.
```

### Instrumented test structure (@HiltAndroidTest)
**Source:** `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt` lines 22–49
**Apply to:** `OcrPipelineTest`, `SessionClearTest`
```kotlin
@HiltAndroidTest
class RoomSeedTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var dao: IngredientDao
    @Before fun setUp() { hiltRule.inject() }
    @Test fun tenSeedRecordsReadable() = runBlocking { ... }
}
```

### Companion object TAG
**Source:** `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt` lines 93–96
**Apply to:** `CameraViewModel`, `OcrRepository`
```kotlin
companion object {
    private const val TAG = "LiteRTInferenceService"
}
// Each new class defines its own TAG string.
```

### AndroidManifest permission declaration
**Source:** `app/src/main/AndroidManifest.xml` lines 1–29
**Apply to:** `AndroidManifest.xml` (FIRST Phase 2 change — before any Kotlin files)
```xml
<!-- Current manifest has ZERO uses-permission entries (lines 4-7 explain why). -->
<!-- Phase 2 MUST add exactly ONE new permission before the <application> tag: -->
<uses-permission android:name="android.permission.CAMERA" />
<!-- INTERNET permission must remain absent — verified by LiteRTNoNetworkTest. -->
```

### libs.versions.toml additions
**Source:** `gradle/libs.versions.toml` lines 1–40
**Apply to:** `gradle/libs.versions.toml` (add Phase 2 entries in the same toml sections)
```toml
[versions]
# Add after existing versions:
camerax = "1.6.0"
mlkit-text-recognition = "16.0.1"
accompanist = "0.37.3"

[libraries]
# Add after existing libraries — follow module = "group:artifact" + version.ref convention:
camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camera-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
```

---

## No Analog Found

Files with no close match in the codebase — planner must use RESEARCH.md patterns directly:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `normalization/INCISynonymMap.kt` | utility | transform | First pure Kotlin `object` with hardcoded `Map` in project; no existing pattern for domain-specific synonym dictionaries |
| `normalization/LevenshteinDistance.kt` | utility | transform | First top-level pure-Kotlin function file in project; no algorithm utility files exist |
| `session/SessionEnums.kt` | model | — | No Kotlin `enum class` exists in Phase 1; all enum-like values are strings (e.g., safetyTag: String) |
| Unit test files (`app/src/test/`) | test | — | `app/src/test/` directory contains zero files; all Phase 1 tests are instrumented (`app/src/androidTest/`); pure JVM unit test pattern must be derived from RoomSeedTest.kt structure with Hilt removed |

---

## Metadata

**Analog search scope:** `app/src/main/kotlin/com/safeglow/edge/` (all 9 source files), `app/src/androidTest/java/com/safeglow/edge/` (5 test files), `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `tools/build_seed_db.sh`
**Files scanned:** 19
**Pattern extraction date:** 2026-04-28
