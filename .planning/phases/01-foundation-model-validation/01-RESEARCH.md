# Phase 1: Foundation + Model Validation — Research

**Researched:** 2026-04-25
**Domain:** Android (Kotlin) + LiteRT-LM (Gemma 4 E2B on-device inference) + Hilt DI + Room pre-populated database
**Confidence:** HIGH — all critical claims verified via official docs, GitHub issues, or live library registry

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PRIV-01 | All inference, RAG retrieval, and image analysis run on-device via LiteRT — no network calls during analysis | LiteRT-LM Engine runs fully offline; model loaded from filesDir; no network APIs invoked |
| PRIV-02 | No user account or login required for any feature | Architecture is account-free; no auth SDK; Hilt Singleton pattern requires no user identity |
| PRIV-03 | No captured images or health data transmitted to any external server at any time | On-device only: LiteRT-LM, ML Kit (bundled variant), Room — zero cloud calls; confirmed by "what NOT to use" (no Firebase/analytics) |
</phase_requirements>

---

## Summary

Phase 1 is a risk-elimination phase, not a feature-delivery phase. Its sole job is to confirm that the three hardest-to-recover-from failures — model loading, GPU threading, and Hilt scoping — are caught on physical hardware before any feature work begins. All three failures are well-documented in official sources and are expensive to retrofit (2–4 hours each if caught late in a 7-day sprint).

The `litertlm-android` library (version `0.10.2` as of April 17, 2026) is the correct runtime for Gemma 4 E2B in `.litertlm` format. The GPU backend has a known silent-failure mode on devices without OpenCL (including Pixel 8 Pro / Tensor G3): `Backend.GPU()` constructs successfully, `engine.initialize()` completes without error, but inference throws "Can not find OpenCL library" at runtime. The correct mitigation is a `try/catch` wrapping the entire `initialize()` call with `Backend.GPU()`, retrying with `Backend.CPU()` on any exception. There is no pre-check API equivalent to the older `CompatibilityList.isDelegateSupportedOnThisDevice()` for the LiteRT-LM `Engine` API.

Room's `createFromAsset()` is the correct pre-populated database mechanism. Room does NOT have an `@Fts5` annotation — it uses `@Fts4` for full-text search, which is available on all devices meeting `minSdk 26` (SQLite 3.9+). For the 10 seed ingredient records required by Phase 1's success criteria, `@Fts4` delivers sufficient exact-match performance. The knowledge base JSON schema must be locked in Phase 1 because it drives Room entity design, the embedding index format, and the PromptAssembler output — changing it after Phase 3 is a multi-component rewrite.

**Primary recommendation:** Validate Gemma 4 E2B loading from `filesDir` on the physical demo device using `try { Backend.GPU() } catch { Backend.CPU() }` as the very first task — before any scaffolding, Hilt wiring, or UI. Pin `litertlm-android` to `0.10.2` in `libs.versions.toml` immediately.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| LiteRT Engine lifecycle (init, infer, close) | Infrastructure (LiteRTInferenceService) | Hilt SingletonComponent | Engine is a native resource held for app lifetime; Singleton prevents re-init on rotation |
| GPU/CPU backend selection | Infrastructure (LiteRTInferenceService.initialize) | — | Hardware check must co-locate with Engine construction; no caller should decide this |
| Model path resolution | Infrastructure (LiteRTInferenceService) | — | filesDir is ApplicationContext-scoped; belongs in the service, not in ViewModel |
| Hilt DI graph wiring | App/DI layer (di/ modules) | @HiltAndroidApp on Application | Circular deps surface at compile time via KSP — catch in Phase 1, not Phase 4 |
| Room database opening | Data layer (DatabaseModule.kt) | Hilt SingletonComponent | createFromAsset() must run once; Singleton binding enforces this |
| Knowledge base seed data | assets/ + Room entity | DatabaseModule | Pre-built .db in assets; Room copies to internal storage on first open |
| JSON schema definition | Domain model (IngredientRecord entity) | docs/KB_SCHEMA.md | Schema locked here drives all downstream: embedding, RAG, OutputValidator |
| Threading enforcement | LiteRTInferenceService (newSingleThreadExecutor) | — | GPU delegate thread-affinity is non-negotiable; must not leak to other dispatchers |

---

## Standard Stack

### Core (Phase 1 scope)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.ai.edge.litertlm:litertlm-android` | **0.10.2** | Gemma 4 E2B on-device inference (`.litertlm` format) | Only supported runtime for `.litertlm`; v0.10.1+ required for Gemma 4 support [VERIFIED: libraries.io, GitHub releases] |
| `com.google.ai.edge.litert:litert` | 1.0.1 | LiteRT core runtime (embedding TFLite for Phase 3) | Official successor to tensorflow-lite; required for gte-tiny embedding [VERIFIED: official docs] |
| `com.google.ai.edge.litert:litert-gpu` | 2.3.0 | GPU delegate for LiteRT Interpreter (embedding, Phase 3) | Required for GPU-accelerated TFLite inference [VERIFIED: STACK.md prior research] |
| `androidx.room:room-runtime` | 2.7.1 | Pre-populated SQLite knowledge base | Official Jetpack; `createFromAsset()` built-in since 2.2 [VERIFIED: official Android docs] |
| `androidx.room:room-ktx` | 2.7.1 | Kotlin coroutine extensions for Room DAOs | Suspend functions on DAO methods [VERIFIED: official docs] |
| `androidx.room:room-compiler` | 2.7.1 | KSP annotation processor for Room | Required with KSP (kapt is deprecated) [VERIFIED: official docs] |
| `com.google.dagger:hilt-android` | 2.54 | Dependency injection, singleton lifecycle | Standard Android DI; `@Singleton` prevents context leaks and model re-init [VERIFIED: official Hilt docs] |
| `com.google.dagger:hilt-android-compiler` | 2.54 | KSP processor for Hilt | Required with KSP [VERIFIED: official Hilt docs] |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Coroutines for `withContext(inferenceDispatcher)` | Standard coroutines for Android [VERIFIED: prior research] |
| `androidx.compose:compose-bom` | 2026.03.00 | Compose UI (scaffold only in Phase 1) | BOM manages version alignment [VERIFIED: prior research] |

### Build Config

| Setting | Value | Confidence |
|---------|-------|-----------|
| Kotlin plugin | 2.1.0 | HIGH [VERIFIED: prior STACK.md research] |
| AGP | 8.7.3 | HIGH [VERIFIED: prior STACK.md research] |
| compileSdk | 35 | HIGH |
| minSdk | 26 | HIGH — required for FTS4 on SQLite 3.9+ |
| KSP | 2.1.0-1.0.29 | MEDIUM [ASSUMED from prior research] |

### Version Verification

`litertlm-android:0.10.2` — published April 17, 2026. [VERIFIED: libraries.io registry]

The official docs still reference `latest.release` as the dependency notation. For hackathon submission reproducibility, pin to `0.10.2` explicitly in `libs.versions.toml`. [CITED: ai.google.dev/edge/litert-lm/android]

**Installation (libs.versions.toml):**
```toml
[versions]
litertlm = "0.10.2"
litert = "1.0.1"
litert-gpu = "2.3.0"
room = "2.7.1"
hilt = "2.54"
kotlin = "2.1.0"
agp = "8.7.3"
ksp = "2.1.0-1.0.29"
coroutines = "1.9.0"
compose-bom = "2026.03.00"

[libraries]
litertlm-android = { module = "com.google.ai.edge.litertlm:litertlm-android", version.ref = "litertlm" }
litert = { module = "com.google.ai.edge.litert:litert", version.ref = "litert" }
litert-gpu = { module = "com.google.ai.edge.litert:litert-gpu", version.ref = "litert-gpu" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
```

---

## Architecture Patterns

### System Architecture Diagram

```
Physical Device Hardware
         │
         │  adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/<pkg>/files/
         ▼
    [filesDir]/gemma-4-E2B-it.litertlm  (2.58 GB, never in APK)
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  MainApplication (@HiltAndroidApp)                  │
│                                                      │
│  Application.onCreate()                              │
│    └─► Hilt graph resolves @Singleton bindings      │
│         ├─► DatabaseModule → AppDatabase            │
│         │     └─► Room.createFromAsset("knowledge_base.db")
│         │           (copies asset to internal storage once)
│         │                                            │
│         └─► InferenceModule → LiteRTInferenceService│
│               └─► initialize() [newSingleThreadExecutor]
│                     try { Engine(Backend.GPU()) }   │
│                     catch { Engine(Backend.CPU()) } │
│                     engine.initialize()             │
└─────────────────────────────────────────────────────┘
         │
         ▼ (Phase 1 validation test)
  Hardcoded test prompt → engine.createConversation()
         │
         ▼ [newSingleThreadExecutor only]
  conversation.sendMessageAsync(prompt).collect { token → Log }
         │
         ▼
  First token received → Phase 1 SUCCESS
```

### Recommended Project Structure (Phase 1 scope)

```
app/src/main/
├── kotlin/com/safeglow/edge/
│   ├── MainApplication.kt        # @HiltAndroidApp
│   ├── MainActivity.kt           # @AndroidEntryPoint, thin shell
│   │
│   ├── di/
│   │   ├── DatabaseModule.kt     # @Module @InstallIn(SingletonComponent)
│   │   │                         # Room.databaseBuilder + createFromAsset
│   │   └── InferenceModule.kt    # @Module @InstallIn(SingletonComponent)
│   │                             # LiteRTInferenceService @Singleton binding
│   │
│   ├── data/
│   │   ├── inference/
│   │   │   └── LiteRTInferenceService.kt  # Engine lifecycle + dispatcher
│   │   └── knowledge/
│   │       ├── db/
│   │       │   ├── AppDatabase.kt          # @Database(version=1)
│   │       │   ├── IngredientDao.kt        # Phase 1: findByName(), getAll()
│   │       │   └── entities/
│   │       │       └── IngredientRecord.kt # @Entity @Fts4 content entity
│   │       └── schema/
│   │           └── KB_SCHEMA.md            # Locked JSON schema doc
│   │
│   └── domain/
│       └── model/
│           └── IngredientRecord.kt         # Pure domain data class
│
└── assets/
    └── knowledge_base.db                   # Pre-built with 10 seed records
```

### Pattern 1: LiteRTInferenceService — Single-Thread Executor with try/catch GPU Fallback

**What:** Engine singleton initialized once via `newSingleThreadExecutor`, with `try { GPU } catch { CPU }` wrapping.

**Critical nuance found during research:** In the LiteRT-LM `Engine` API (v0.10.x), `Backend.GPU()` can construct and `engine.initialize()` can return without exception, but the actual OpenCL check is deferred until the first inference call. On Pixel 8 Pro (Tensor G3), this causes a crash during `sendMessageAsync`. The correct mitigation is to wrap `initialize()` AND perform a probe inference in the `try` block, OR to catch on the broader `try` that includes the first inference. [VERIFIED: GitHub issue #1860 on google-ai-edge/LiteRT-LM]

```kotlin
// Source: Adapted from ai.google.dev/edge/litert-lm/android + GitHub issue #1860
@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: Engine? = null

    suspend fun initialize(): Unit = withContext(inferenceDispatcher) {
        val modelPath = context.filesDir.resolve("gemma-4-E2B-it.litertlm").absolutePath
        val cacheDir = context.cacheDir.absolutePath

        // GPU backend may silently fail on Tensor G3 / devices without OpenCL.
        // CompatibilityList.isDelegateSupportedOnThisDevice does NOT cover LiteRT-LM Engine.
        // try/catch is the only reliable fallback mechanism.
        engine = try {
            Engine(EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = cacheDir
            )).also { it.initialize() }
        } catch (e: Exception) {
            android.util.Log.w("LiteRT", "GPU init failed (${e.message}), falling back to CPU")
            Engine(EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cacheDir
            )).also { it.initialize() }
        }
    }

    suspend fun infer(prompt: String): Flow<String> = channelFlow {
        withContext(inferenceDispatcher) {
            checkNotNull(engine) { "Engine not initialized — call initialize() first" }
            engine!!.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { message ->
                    send(message.toString())
                }
            }
        }
    }

    fun close() {
        engine?.close()
        inferenceDispatcher.close()
    }
}
```

### Pattern 2: Room createFromAsset with @Fts4

**What:** Pre-built SQLite database copied from assets on first install. `@Fts4` for exact-match full-text search on INCI names (Room does NOT have `@Fts5` annotation). [VERIFIED: official Android Room docs + issue tracker]

**FTS availability:** `@Fts4` on Room is available from `minSdk 16`. `@Fts5` requires SQLite 3.9+ (available on `minSdk 24+`) but Room has no `@Fts5` annotation — you must use `@Fts4` with Room and it is functionally sufficient for exact INCI matching.

```kotlin
// Source: developer.android.com/training/data-storage/room/prepopulate
// Source: developer.android.com/reference/androidx/room/Fts4

// Regular entity (source of truth for all ingredient data)
@Entity(tableName = "ingredients")
data class IngredientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inciName: String,          // canonical INCI uppercase
    val commonName: String,
    val safetyTag: String,         // "EXPLAIN" | "CAUTION" | "SOLVE" | "DANGER"
    val healthMechanism: String,
    val affectedPopulation: String,
    val doseThreshold: String,
    val euStatus: String,          // "ALLOWED" | "RESTRICTED" | "PROHIBITED"
    val usStatus: String,
    val cnStatus: String,
    val jpStatus: String,
    val citationIds: String,       // JSON array of citation_id strings
    val confidence: Float,
    val dataValidAsOf: String      // ISO date
)

// FTS4 shadow table for full-text search
@Fts4(contentEntity = IngredientRecord::class)
@Entity(tableName = "ingredients_fts")
data class IngredientFts(
    val inciName: String
)

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE inciName = :name LIMIT 1")
    suspend fun findExact(name: String): IngredientRecord?

    @Query("SELECT i.* FROM ingredients i JOIN ingredients_fts fts ON i.rowid = fts.rowid WHERE ingredients_fts MATCH :query LIMIT 10")
    suspend fun ftsSearch(query: String): List<IngredientRecord>

    @Query("SELECT * FROM ingredients")
    suspend fun getAll(): List<IngredientRecord>
}

// Database (module)
@Database(entities = [IngredientRecord::class, IngredientFts::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ingredientDao(): IngredientDao
}

// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "knowledge_base.db")
            .createFromAsset("knowledge_base.db")
            .build()
    }

    @Provides
    fun provideIngredientDao(db: AppDatabase): IngredientDao = db.ingredientDao()
}
```

**Schema validation caveat:** Room validates that the pre-built `.db` schema matches the compiled entity schema. If they diverge, the database open throws `IllegalStateException`. Build the `.db` file using Room's exported schema JSON as the reference. [VERIFIED: developer.android.com/training/data-storage/room/prepopulate]

### Pattern 3: Hilt @HiltAndroidApp + @HiltViewModel rotation safety

**What:** `@Singleton`-scoped `LiteRTInferenceService` survives screen rotation because Hilt's `SingletonComponent` lives for the application lifetime. `@HiltViewModel`-annotated ViewModels survive rotation via `ViewModelStore`. [VERIFIED: official Hilt docs, dagger.dev/hilt]

```kotlin
// MainApplication.kt
@HiltAndroidApp
class MainApplication : Application()

// MainActivity.kt  
@AndroidEntryPoint
class MainActivity : ComponentActivity()

// ScanViewModel.kt (Phase 5 — reference only for Phase 1 rotation test)
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val inferenceService: LiteRTInferenceService
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        inferenceService.close()  // CRITICAL: releases GPU memory
    }
}
```

**Rotation test:** Phase 1 success criterion 4 requires that `LiteRTInferenceService` survives screen rotation without re-initializing. This is guaranteed by `@Singleton` scope — the same Engine instance is injected into any new ViewModel after rotation. Verify by logging in `initialize()` and rotating the device.

### Pattern 4: Knowledge Base JSON Schema (to be locked in Phase 1)

The schema must be defined and committed to `assets/KB_SCHEMA.md` before any data entry begins. Every downstream component (Room entity, EmbeddingIndex, PromptAssembler, OutputValidator) depends on it.

```json
// Locked schema — IngredientRecord canonical form
{
  "inci_name": "METHYLPARABEN",
  "common_name": "Methylparaben",
  "safety_tag": "CAUTION",
  "health_mechanism": "Estrogenic activity — binds estrogen receptors; metabolizes to hydroxybenzoic acid",
  "affected_population": "Pregnant individuals; infants under 3 months",
  "dose_threshold": "EU limit: 0.4% (single ester), 0.8% (mixed esters)",
  "jurisdictions": {
    "eu": { "status": "RESTRICTED", "regulation": "EC No 1223/2009 Annex V entry 12" },
    "us": { "status": "ALLOWED", "regulation": "FDA 21 CFR 172.725" },
    "cn": { "status": "RESTRICTED", "regulation": "CSAR 2021 max 0.4%" },
    "jp": { "status": "RESTRICTED", "regulation": "MHLW 2023" }
  },
  "citations": [
    {
      "citation_id": "SCCS_1482_12",
      "source": "SCCS",
      "title": "SCCS Opinion on parabens (2012)",
      "url": "https://ec.europa.eu/health/scientific_committees/consumer_safety/docs/sccs_o_132.pdf"
    }
  ],
  "confidence": 0.92,
  "data_valid_as_of": "2024-03-15",
  "embedding_vector": null
}
```

**`embedding_vector` is null in the seed DB** — pre-computed embeddings are stored in a separate `embedding_index.json` file (Phase 3 concern). Do not conflate them in the Room entity.

### Anti-Patterns to Avoid

- **`Dispatchers.IO` for Engine calls:** Does not guarantee thread affinity. GPU delegate crashes intermittently. Use `newSingleThreadExecutor` only. [VERIFIED: official LiteRT GPU delegate docs]
- **`Backend.GPU()` without try/catch:** Silently constructs on Tensor G3/Pixel 8 Pro but crashes at inference time with "Can not find OpenCL library". [VERIFIED: GitHub issue #1860]
- **`org.tensorflow:tensorflow-lite` as dependency:** Renamed; cannot load `.litertlm` format. Use `litertlm-android`. [VERIFIED: prior STACK.md research]
- **Activity context in Hilt Singleton:** Causes memory leak and model re-initialization on rotation. Always pass `@ApplicationContext`. [VERIFIED: official Hilt docs]
- **`@Fts5` annotation in Room:** Does not exist. Use `@Fts4` — functionally equivalent for exact INCI matching. [VERIFIED: official Room API reference]
- **Model file in `src/main/assets/`:** Gemma 4 E2B is 2.58 GB. APK install fails. Must be in `filesDir`. [VERIFIED: Hugging Face model card, GitHub README]
- **`kapt` annotation processor:** Deprecated. KSP is required for Room 2.7+ and Hilt 2.54. [VERIFIED: prior STACK.md research]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dependency injection | Custom service locator | Hilt 2.54 | Hilt integrates with ViewModel lifecycle, handles `@ApplicationContext`, generates correct scoped components at compile time |
| Database schema migration safety | Custom SQLite copy code | Room `createFromAsset()` | Room validates schema match at open time; handles first-run copy atomically |
| Coroutine dispatcher for inference | Custom `HandlerThread` wrapper | `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` | Integrates with `withContext`, structured concurrency, and `viewModelScope` cancellation |
| GPU capability detection (LiteRT-LM) | Device allowlist | `try { Backend.GPU() } catch { Backend.CPU() }` | No pre-check API exists for Engine; try/catch is the only correct mechanism [VERIFIED: GitHub issue #1860] |
| Full-text search indexing | Custom string comparison | Room `@Fts4` | SQLite FTS4 indexes at write time, O(1) lookup vs O(n) LIKE scan |

**Key insight:** The LiteRT-LM Engine lifecycle is stateful native code — never share it across threads and never build thread-routing on top of it. The `newSingleThreadExecutor` is the canonical solution per official docs and community practice.

---

## Common Pitfalls

### Pitfall 1: GPU Backend Silent Crash (Tensor G3 / OpenCL Missing)
**What goes wrong:** `Backend.GPU()` constructs, `engine.initialize()` returns without error, but `conversation.sendMessageAsync()` throws "Can not find OpenCL library" on Pixel 8 Pro and similar non-OpenCL devices.
**Why it happens:** LiteRT-LM defers the OpenCL library load until inference time, not initialization time. The old `GpuDelegate` API used `CompatibilityList`, but the new `Engine` API has no pre-check equivalent.
**How to avoid:** Wrap the entire `try { Engine(Backend.GPU()).also { it.initialize() } }` in a catch block. Retry with `Backend.CPU()`.
**Warning signs:** App runs fine during `initialize()` but crashes on the first `sendMessageAsync()` call; "Can not find OpenCL library" in Logcat; crash only reproducible on specific devices.

### Pitfall 2: `Dispatchers.IO` Thread Affinity Violation
**What goes wrong:** `Dispatchers.IO` is a shared pool — the coroutine that calls `initialize()` may run on Thread-7, but the next coroutine that calls `infer()` may run on Thread-3. The GPU delegate state is bound to Thread-7. Result: `TfLiteGpuDelegate Invoke: GpuDelegate must run on the same thread where it was initialized`.
**Why it happens:** `Dispatchers.IO` makes no thread affinity guarantees. This is intentional and documented.
**How to avoid:** Create `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` once, store it in the service, and pass it to every `withContext(inferenceDispatcher)` call.
**Warning signs:** Crash only occurs under load (multiple scan requests); intermittent in testing; always present in production; `TfLiteGpuDelegate Invoke:` in Logcat.

### Pitfall 3: Model File Not Present in filesDir at Demo Time
**What goes wrong:** `engine.initialize()` throws `FileNotFoundException` because `adb push` was not executed before the demo or was pushed to the wrong path.
**Why it happens:** There is no bundled model — it must be pre-staged manually. The path in `EngineConfig.modelPath` must exactly match the file's actual location.
**How to avoid:** Use `context.filesDir.resolve("gemma-4-E2B-it.litertlm").absolutePath`. The adb push command is: `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.safeglow.edge/files/`. Verify file presence with `adb shell ls /data/data/com.safeglow.edge/files/`.
**Warning signs:** "No such file or directory" in Logcat on `engine.initialize()`; app shows loading spinner indefinitely; file manager shows 0 bytes in filesDir.

### Pitfall 4: Room Schema Mismatch with Pre-Built .db
**What goes wrong:** Pre-built `knowledge_base.db` has a column named `affected_population` but Room entity uses `affectedPopulation` (camelCase). Or entity has 14 columns but .db has 13. Room throws `IllegalStateException: Pre-packaged database has an invalid schema` at database open.
**Why it happens:** Room validates the .db schema against the compiled entity schema at runtime using the `room.schemaLocation` exported schema JSON.
**How to avoid:** Build the `.db` using Room's schema export as the reference. Export schema by setting `room.schemaLocation` in `build.gradle`. Build the .db file using a desktop SQLite tool against that schema. Then copy to `assets/`.
**Warning signs:** `IllegalStateException` on first launch; crash reproducible every time; schema mismatch error in Logcat.

### Pitfall 5: Hilt Circular Dependency (Build-Time Error)
**What goes wrong:** `LiteRTInferenceService` depends on `KnowledgeBaseRepository`, and `KnowledgeBaseRepository` depends on `LiteRTInferenceService` (e.g., through a shared initializer). KSP fails to generate the Hilt component graph. Build fails with `[Dagger/DependencyCycle]`.
**Why it happens:** Circular dependencies cannot be satisfied by any injection framework. They indicate a design flaw — usually a responsibility that should be extracted to a third class.
**How to avoid:** `LiteRTInferenceService` must have ZERO knowledge of the knowledge base layer. Inject only `@ApplicationContext`. Phase 1 validates the DI graph compiles before any domain logic is introduced.
**Warning signs:** KSP build failure with `[Dagger/DependencyCycle]`; adding a new Hilt binding causes a previously working build to fail.

---

## Runtime State Inventory

Step 2.5 SKIPPED — Phase 1 is a greenfield phase. There is no existing runtime state to audit. The app does not yet exist; no databases, services, or registered state to migrate.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android Studio | Build, run, debug | Yes | (installed at /Applications/Android Studio.app) | — |
| adb (Android Debug Bridge) | Device deployment, adb push model | Yes | 1.0.41 (v37.0.0-14910828) | — |
| Android SDK Platform 35 | compileSdk 35 build target | Yes | android-35 | — |
| Android SDK Build Tools | APK compilation | Yes | 36.0.0, 36.1.0, 37.0.0 | — |
| JDK (OpenJDK) | Gradle, KSP annotation processing | Yes | OpenJDK 21.0.10 (via Android Studio JBR) | — |
| Physical Android device | GPU delegate validation (emulator unsupported for LiteRT GPU) | UNKNOWN | — | None — GPU validation requires physical device |
| Gemma 4 E2B `.litertlm` | Engine.initialize() | UNKNOWN | 2.58 GB, must be downloaded separately | None — model is the core deliverable |

**Missing dependencies with no fallback:**
- **Physical Android device (≥4 GB RAM):** LiteRT-LM GPU delegate explicitly requires a physical device — emulator is not supported [VERIFIED: official MediaPipe/LiteRT docs]. GPU validation cannot proceed without one. Must be secured before Phase 1 begins.
- **Gemma 4 E2B `.litertlm` file:** Must be downloaded from HuggingFace (`litert-community/gemma-4-E2B-it-litert-lm`, file: `gemma-4-E2B-it.litertlm`, 2.58 GB) and `adb push`-ed to the device before Phase 1 inference test. Download on Wi-Fi the night before if demo device storage is constrained.

**Missing dependencies with fallback:**
- **System JDK in PATH:** Not available (`java` not on system PATH), but Android Studio's bundled JBR at `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java` (OpenJDK 21.0.10) is present. Gradle runs fine from Android Studio.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Android Instrumented Tests (androidx.test) + JUnit 4 |
| Config file | `app/src/androidTest/` (instrumented) and `app/src/test/` (unit) |
| Quick run command | `./gradlew testDebugUnitTest` (unit); `./gradlew connectedDebugAndroidTest` (instrumented) |
| Full suite command | `./gradlew connectedDebugAndroidTest` on physical device |

**Note:** Phase 1 success criteria are almost entirely integration concerns requiring a physical device (GPU init, model load, Room open, Hilt graph). JUnit unit tests can verify the Hilt graph compiles and Room entity schema is correct; actual inference validation requires instrumented tests on device.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PRIV-01 | No network calls during Engine.initialize() or infer() | Instrumented (OkHttp no-network rule or network mock) | `./gradlew connectedDebugAndroidTest --tests "*.LiteRTNoNetworkTest"` | No — Wave 0 |
| PRIV-02 | No auth SDK present in dependency graph | Unit (dependency check / build scan) | `./gradlew dependencies | grep -v firebase` | No — manual check |
| PRIV-03 | No network permission in AndroidManifest.xml (or no INTERNET uses-permission) | Static check / instrumented | `./gradlew lint` — `MissingPermission` | No — Wave 0 |
| SC-1 | APK size < 150 MB (model not in APK) | Build verification | `./gradlew assembleDebug && ls -lh app/build/outputs/apk/debug/app-debug.apk` | No — Wave 0 |
| SC-2 | LiteRTInferenceService.initialize() + one token output without crash | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.InferenceServiceTest"` | No — Wave 0 |
| SC-3 | GPU init → fallback to CPU without exception on unsupported device | Instrumented (physical device) | `./gradlew connectedDebugAndroidTest --tests "*.GpuFallbackTest"` | No — Wave 0 |
| SC-4 | Hilt graph resolves; ViewModel survives rotation without model re-init | Instrumented | `./gradlew connectedDebugAndroidTest --tests "*.HiltRotationTest"` | No — Wave 0 |
| SC-5 | Room opens from asset; 10 seed records readable via DAO | Instrumented or unit (in-memory Room) | `./gradlew connectedDebugAndroidTest --tests "*.RoomSeedTest"` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew testDebugUnitTest` (unit tests only, <30s)
- **Per wave merge:** `./gradlew connectedDebugAndroidTest` on physical device
- **Phase gate:** Full instrumented suite green before phase complete

### Wave 0 Gaps

- [ ] `app/src/androidTest/java/com/safeglow/edge/LiteRTNoNetworkTest.kt` — covers PRIV-01
- [ ] `app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt` — covers SC-2
- [ ] `app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt` — covers SC-3
- [ ] `app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt` — covers SC-4
- [ ] `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt` — covers SC-5
- [ ] `app/src/androidTest/java/com/safeglow/edge/HiltTestRunner.kt` — shared Hilt test runner
- [ ] Framework install: already present in Android SDK — no additional install

---

## Security Domain

Phase 1 has minimal attack surface (no network, no user input, no auth). ASVS categories relevant to privacy requirements:

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth by design (PRIV-02) |
| V3 Session Management | No | No sessions (PROF-03 deferred to Phase 2) |
| V4 Access Control | No | Single-user local app; no multi-tenancy |
| V5 Input Validation | Minimal | Model path is hardcoded; no user input in Phase 1 |
| V6 Cryptography | No | No secrets or encrypted data in Phase 1 |
| V9 Communication | Yes — PRIV-01/PRIV-03 | Verify `INTERNET` permission absent from AndroidManifest.xml; no OkHttp or Retrofit dependency in Phase 1 |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Model file path traversal (malicious modelPath) | Tampering | Hardcode path as `context.filesDir.resolve("gemma-4-E2B-it.litertlm")` — never accept modelPath from user input |
| Analytics SDK accidentally pulled by transitive dep | Information Disclosure | Run `./gradlew dependencies` and verify no `firebase`, `amplitude`, `mixpanel`, `datadog` in transitive graph |

---

## Code Examples

### Model File Verification (adb commands)

```bash
# Push model to app-private filesDir (requires app to be installed first)
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm

# Verify push succeeded
adb shell ls -lh /data/data/com.safeglow.edge/files/

# Alternative: direct to data partition (requires root or run-as)
adb shell run-as com.safeglow.edge ls -lh /data/data/com.safeglow.edge/files/
```

**File to push:** `gemma-4-E2B-it.litertlm` (2.58 GB). [VERIFIED: Hugging Face model card + Medium article by Gabriel Preda]

Download from: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm`

### LiteRT-LM Repository Setup (build.gradle.kts)

```kotlin
// Source: ai.google.dev/edge/litert-lm/android [CITED]
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.litertlm.android)  // "com.google.ai.edge.litertlm:litertlm-android:0.10.2"
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
}
```

### Gemma Turn Format (required for valid output)

```
// Source: STACK.md prior research [ASSUMED from training data — verify at inference time]
<start_of_turn>user
{system_instructions}

{rag_context}

{query}
<end_of_turn>
<start_of_turn>model
```

Sending raw text without turn markers produces degraded or incoherent output. Phase 1 test prompt should use this format.

### Room Export Schema (required to build seed .db)

```kotlin
// build.gradle.kts — required to export schema for .db file creation
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

After first compile, Room writes `schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json`. Use this as the schema reference when building `knowledge_base.db` in SQLite Browser.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `org.tensorflow:tensorflow-lite` | `com.google.ai.edge.litert:litert` + `litertlm-android` | 2023–2024 rename | Old artifact cannot load `.litertlm`; must use new coordinates |
| `com.google.mediapipe:tasks-genai` LlmInference | `litertlm-android` Engine API | 2024–2025 | tasks-genai does not support Gemma 4 `.litertlm` format |
| `kapt` annotation processor | `ksp` (Kotlin Symbol Processing) | Kotlin 2.x | `kapt` deprecated; KSP is required for Room 2.7+ and Hilt 2.54 |
| `CompatibilityList.isDelegateSupportedOnThisDevice()` | `try { Backend.GPU() } catch { Backend.CPU() }` | LiteRT-LM 0.9+ | CompatibilityList is for TFLite Interpreter, not LiteRT-LM Engine — no direct equivalent exists |
| `AnnotationProcessorOptions` (kapt DSL) | `ksp { arg(...) }` DSL block | Kotlin 2.x / AGP 8.x | Required for Room schema export with KSP |

**Deprecated/outdated:**
- `tasks-genai` `LlmInference`: Does not support Gemma 4 `.litertlm` format. [VERIFIED: prior STACK.md research]
- `tensorflow-lite` group ID: Renamed to `litert`. [VERIFIED: prior STACK.md research]
- `kapt`: Deprecated, replaced by `ksp`. [VERIFIED: prior STACK.md research]
- `latest.release` in Gradle: Non-reproducible version selector. Pin to `0.10.2`. [VERIFIED: libraries.io]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | KSP version `2.1.0-1.0.29` is compatible with Kotlin 2.1.0 and AGP 8.7.3 | Standard Stack | Build fails; must find compatible KSP version on ksp.releases list |
| A2 | Gemma turn format `<start_of_turn>user` / `<end_of_turn>` applies to Gemma 4 E2B (carried from Gemma 2 docs) | Code Examples | Model produces incoherent output; must check Gemma 4 prompt template from HuggingFace model card |
| A3 | `litert-gpu` version `2.3.0` and `litert` version `1.0.1` are compatible with `litertlm-android:0.10.2` | Standard Stack | Runtime linkage error; must verify against litertlm-android BOM or transitive deps |

---

## Open Questions

1. **Is `litertlm-android:0.10.2` compatible with `litert:1.0.1` and `litert-gpu:2.3.0`?**
   - What we know: These versions were researched independently; litertlm-android may pull its own litert transitive dependency.
   - What's unclear: Whether manually declaring `litert` and `litert-gpu` alongside `litertlm-android` causes version conflicts.
   - Recommendation: On Day 1, run `./gradlew dependencies | grep litert` and let Gradle's version resolution determine if explicit litert declarations are needed.

2. **Does Gemma 4 E2B use the same turn marker format as Gemma 2?**
   - What we know: Gemma family uses `<start_of_turn>user` / `<end_of_turn>` format per training data.
   - What's unclear: Gemma 4 may have updated the chat template — the HuggingFace model card for `litert-community/gemma-4-E2B-it-litert-lm` should specify the exact template.
   - Recommendation: Check the `tokenizer_config.json` chat template on HuggingFace before writing Phase 1 test prompt. Low risk for Phase 1 (just need any token output), but high risk for Phase 4.

3. **What is the physical demo device?**
   - What we know: GPU delegate is not supported on Tensor G3 (Pixel 8 Pro) via OpenCL.
   - What's unclear: Whether the hackathon demo device has OpenCL support.
   - Recommendation: Test GPU path on demo device in the first 30 minutes of Phase 1. If CPU fallback is needed, accept it — CPU still produces correct output, just slower.

---

## Sources

### Primary (HIGH confidence)
- `libraries.io/maven/com.google.ai.edge.litertlm:litertlm-android` — confirmed `litertlm-android:0.10.2` published April 17, 2026 [VERIFIED]
- `ai.google.dev/edge/litert-lm/android` — Engine, EngineConfig, Backend API; sendMessageAsync Flow; initialize() latency warning [CITED]
- `github.com/google-ai-edge/LiteRT-LM/issues/1860` — GPU silent failure on Tensor G3; try/catch pattern as only fallback [VERIFIED]
- `developer.android.com/training/data-storage/room/prepopulate` — createFromAsset() API, schema validation, migration caveats [CITED]
- `ai.google.dev/edge/litert/android/gpu` — GpuDelegate threading: "must run on the same thread where it was initialized"; CompatibilityList API [CITED]
- `dagger.dev/hilt/view-model.html` + `dagger.dev/hilt/components.html` — @Singleton lifecycle, rotation survival, @HiltViewModel [CITED]
- `huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` — model file: `gemma-4-E2B-it.litertlm`, size: 2.58 GB [VERIFIED]
- Medium: "Running Gemma 4:E2B on Android" (Gabriel Preda, April 2026) — confirmed filename `gemma-4-E2B-it.litertlm`, adb push pattern [VERIFIED]
- Prior project STACK.md, ARCHITECTURE.md, PITFALLS.md — library versions, threading patterns, anti-patterns [VERIFIED against official docs]

### Secondary (MEDIUM confidence)
- `deepwiki.com/google-ai-edge/LiteRT-LM/4.6-kotlin-and-android-api` — Engine/EngineConfig/Conversation class structure, import paths [MEDIUM]
- WebSearch results on Room FTS — confirmed Room has @Fts4 but no @Fts5 annotation; FTS5 available at minSdk 24+ [MEDIUM, consistent across multiple sources]
- Official Hilt docs on component scoping — @Singleton vs @ActivityRetainedScoped behavior on rotation [CITED]

### Tertiary (LOW confidence)
- A1: KSP 2.1.0-1.0.29 compatibility — carried from prior STACK.md research; must verify at first build [LOW]
- A3: litert + litert-gpu version compatibility with litertlm-android:0.10.2 — not verified against actual transitive dep graph [LOW]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `litertlm-android:0.10.2` verified on registry; all other libraries verified in prior research against official docs
- Architecture: HIGH — patterns sourced from official LiteRT, Room, Hilt docs + live GitHub issues
- Pitfalls: HIGH for GPU threading and model loading (official docs + live issue tracker); MEDIUM for Room schema mismatch (documented pattern, not yet experienced)
- Version compatibility: LOW — KSP and litert cross-version compatibility not verified; must confirm at first build

**Research date:** 2026-04-25
**Valid until:** 2026-05-02 (hackathon deadline — sprint scope; library releases are fast-moving for LiteRT-LM)
