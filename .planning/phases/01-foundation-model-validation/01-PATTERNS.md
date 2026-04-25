# Phase 1: Foundation + Model Validation — Pattern Map

**Mapped:** 2026-04-25
**Files analyzed:** 15 (new files — all greenfield)
**Analogs found:** 0 / 15

---

## Greenfield Notice

This is a fully greenfield Android/Kotlin project. No source files exist anywhere in the repository (`find . -name "*.kt"` returns zero results). Every file listed below will be created from scratch. There are no codebase analogs to extract — the planner MUST use the verified code excerpts from RESEARCH.md as the authoritative pattern source for all files.

This PATTERNS.md documents:
1. The canonical pattern for each file (sourced from RESEARCH.md verified references)
2. The data flow and role classification the planner needs to assign tasks correctly
3. The cross-cutting shared patterns that apply to multiple files

---

## File Classification

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `app/build.gradle.kts` | config | — | None | no-analog |
| `gradle/libs.versions.toml` | config | — | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/MainApplication.kt` | application | — | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/MainActivity.kt` | activity | request-response | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt` | provider | — | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt` | provider | — | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt` | service | streaming | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/AppDatabase.kt` | model | CRUD | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt` | model | CRUD | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientRecord.kt` | model | CRUD | None | no-analog |
| `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientFts.kt` | model | CRUD | None | no-analog |
| `app/src/main/assets/knowledge_base.db` | config | file-I/O | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/HiltTestRunner.kt` | test | — | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/InferenceServiceTest.kt` | test | streaming | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt` | test | CRUD | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/GpuFallbackTest.kt` | test | streaming | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/HiltRotationTest.kt` | test | — | None | no-analog |
| `app/src/androidTest/java/com/safeglow/edge/LiteRTNoNetworkTest.kt` | test | request-response | None | no-analog |

---

## Pattern Assignments

All patterns below are sourced from RESEARCH.md (01-RESEARCH.md), which cites official Android/Google documentation and verified GitHub issues. Line numbers reference the RESEARCH.md file.

---

### `gradle/libs.versions.toml` (config)

**Source:** RESEARCH.md lines 82–105 (Standard Stack — Installation section)

**Full version catalog pattern:**
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

**Critical note:** Pin `litertlm-android` to exactly `0.10.2` — do NOT use `latest.release`. Verified published April 17, 2026 on libraries.io.

---

### `app/build.gradle.kts` (config)

**Source:** RESEARCH.md lines 548–595 (Code Examples — LiteRT-LM Repository Setup and Room Export Schema)

**Build config pattern:**
```kotlin
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26   // required for FTS4 on SQLite 3.9+
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.litertlm.android)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)      // NOT kapt — kapt is deprecated
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)      // NOT kapt
    implementation(libs.coroutines.android)
}

// Room schema export — required to build seed .db file in SQLite Browser
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

**Critical note:** Use `ksp(...)` for Room and Hilt compilers, never `kapt(...)`. KSP is required for Room 2.7+ and Hilt 2.54 with Kotlin 2.x.

---

### `app/src/main/kotlin/com/safeglow/edge/MainApplication.kt` (application)

**Source:** RESEARCH.md lines 314–317 (Pattern 3 — Hilt @HiltAndroidApp)

**Pattern:**
```kotlin
@HiltAndroidApp
class MainApplication : Application()
```

**Role:** This single annotation triggers Hilt's compile-time component generation. No other logic belongs here for Phase 1. The `SingletonComponent` lifecycle is tied to `Application.onCreate()` — Hilt resolves all `@Singleton` bindings (including `LiteRTInferenceService` and `AppDatabase`) when the application starts.

---

### `app/src/main/kotlin/com/safeglow/edge/MainActivity.kt` (activity, request-response)

**Source:** RESEARCH.md lines 318–320 (Pattern 3 — @AndroidEntryPoint)

**Pattern:**
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Phase 1: thin shell only — no feature logic
    // Compose setContent {} with a placeholder scaffold
}
```

**Role:** `@AndroidEntryPoint` enables Hilt field injection in the Activity. Phase 1 scope is a placeholder scaffold — no navigation, no ViewModel wiring yet.

---

### `app/src/main/kotlin/com/safeglow/edge/di/InferenceModule.kt` (provider)

**Source:** RESEARCH.md lines 187–234 (Pattern 1 — LiteRTInferenceService). The module itself is described in lines 36–38 (Architectural Responsibility Map) and lines 159–160 (Recommended Project Structure).

**Pattern:**
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

**Role:** Binds `LiteRTInferenceService` as a `@Singleton` in the Hilt graph. `SingletonComponent` survives screen rotation — the same Engine instance is injected into any new ViewModel after rotation (Phase 1 success criterion 4). Always inject `@ApplicationContext`, never `Activity` context — see Shared Patterns (Anti-Patterns).

---

### `app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt` (provider)

**Source:** RESEARCH.md lines 291–305 (Pattern 2 — DatabaseModule.kt)

**Full pattern:**
```kotlin
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

**Critical note:** `provideDatabase` is `@Singleton` — Room's `createFromAsset()` copies the asset to internal storage only once; `@Singleton` enforces this. `provideIngredientDao` does NOT need `@Singleton` — Room generates a thread-safe DAO backed by the singleton database.

---

### `app/src/main/kotlin/com/safeglow/edge/data/inference/LiteRTInferenceService.kt` (service, streaming)

**Source:** RESEARCH.md lines 187–234 (Pattern 1 — LiteRTInferenceService — Single-Thread Executor with try/catch GPU Fallback)

This is the highest-risk file in Phase 1. Copy the pattern exactly — do not improvise thread management or GPU fallback logic.

**Full pattern:**
```kotlin
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
        // try/catch is the only reliable fallback — no pre-check API exists.
        // Source: GitHub issue #1860 on google-ai-edge/LiteRT-LM
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

**Threading rule:** Every Engine call (`initialize`, `infer`, `close`) MUST run on `inferenceDispatcher`. Never use `Dispatchers.IO` — it is a shared pool with no thread affinity guarantee. GPU delegate state is bound to the thread that called `initialize()`.

**Model path rule:** Always use `context.filesDir.resolve("gemma-4-E2B-it.litertlm").absolutePath`. Never accept a model path from user input (path traversal).

**GPU silent failure rule:** `Backend.GPU()` can construct and `engine.initialize()` can return without exception on Tensor G3 (Pixel 8 Pro), but inference crashes with "Can not find OpenCL library". The `try/catch` around `initialize()` is the only correct mitigation.

---

### `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientRecord.kt` (model, CRUD)

**Source:** RESEARCH.md lines 248–264 (Pattern 2 — Regular entity) and lines 341–368 (Pattern 4 — Knowledge Base JSON Schema)

**Entity pattern:**
```kotlin
@Entity(tableName = "ingredients")
data class IngredientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inciName: String,           // canonical INCI uppercase
    val commonName: String,
    val safetyTag: String,          // "EXPLAIN" | "CAUTION" | "SOLVE" | "DANGER"
    val healthMechanism: String,
    val affectedPopulation: String,
    val doseThreshold: String,
    val euStatus: String,           // "ALLOWED" | "RESTRICTED" | "PROHIBITED"
    val usStatus: String,
    val cnStatus: String,
    val jpStatus: String,
    val citationIds: String,        // JSON array of citation_id strings
    val confidence: Float,
    val dataValidAsOf: String       // ISO date
)
```

**Schema lock note:** This entity drives Room's exported schema JSON (`schemas/.../1.json`), which is the reference for building `knowledge_base.db` in SQLite Browser. The schema MUST be committed before any `.db` file is created — changing entity columns after the `.db` is built requires rebuilding the asset. `embedding_vector` is intentionally absent — pre-computed embeddings live in a separate `embedding_index.json` (Phase 3 scope).

---

### `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientFts.kt` (model, CRUD)

**Source:** RESEARCH.md lines 266–272 (Pattern 2 — FTS4 shadow table)

**FTS pattern:**
```kotlin
@Fts4(contentEntity = IngredientRecord::class)
@Entity(tableName = "ingredients_fts")
data class IngredientFts(
    val inciName: String
)
```

**Critical note:** Room has `@Fts4` but NO `@Fts5` annotation. Do not attempt `@Fts5` — it does not exist in Room's annotation processor. `@Fts4` is sufficient for exact INCI matching and is available on all devices at `minSdk 26`.

---

### `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt` (model, CRUD)

**Source:** RESEARCH.md lines 273–283 (Pattern 2 — DAO)

**DAO pattern:**
```kotlin
@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE inciName = :name LIMIT 1")
    suspend fun findExact(name: String): IngredientRecord?

    @Query("SELECT i.* FROM ingredients i JOIN ingredients_fts fts ON i.rowid = fts.rowid WHERE ingredients_fts MATCH :query LIMIT 10")
    suspend fun ftsSearch(query: String): List<IngredientRecord>

    @Query("SELECT * FROM ingredients")
    suspend fun getAll(): List<IngredientRecord>
}
```

**Phase 1 scope:** Only `getAll()` is strictly required for the 10-seed-record validation test. `findExact` and `ftsSearch` are defined here to lock the schema and DAO interface before Phase 2 builds on top of them.

---

### `app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/AppDatabase.kt` (model, CRUD)

**Source:** RESEARCH.md lines 285–289 (Pattern 2 — Database)

**Database pattern:**
```kotlin
@Database(
    entities = [IngredientRecord::class, IngredientFts::class],
    version = 1,
    exportSchema = true   // required — schema export drives .db file creation
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ingredientDao(): IngredientDao
}
```

**Schema export note:** `exportSchema = true` combined with the `ksp { arg("room.schemaLocation", ...) }` block in `build.gradle.kts` writes `schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json` after the first compile. This JSON file is the authoritative reference for building `knowledge_base.db` using SQLite Browser.

---

### `app/src/main/assets/knowledge_base.db` (config, file-I/O)

**Source:** RESEARCH.md lines 308–308, 419–421 (Pattern 2 — Schema validation caveat; Pitfall 4 — Room Schema Mismatch)

**Not a code file — build instructions:**

1. After first compile with `exportSchema = true`, read `schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json`
2. Create a new SQLite database in SQLite Browser using EXACTLY the column names from the exported schema (camelCase, as Room generates them — e.g., `inciName` not `inci_name`)
3. Insert 10 seed ingredient records covering Phase 1 test ingredients
4. Export as `knowledge_base.db` and place in `app/src/main/assets/knowledge_base.db`

**Schema mismatch risk:** If the `.db` column names do not match the Room-generated schema, `Room.databaseBuilder().createFromAsset()` throws `IllegalStateException: Pre-packaged database has an invalid schema` on first launch.

---

### Test Files (instrumented, `app/src/androidTest/`)

**Source:** RESEARCH.md lines 463–499 (Validation Architecture — Wave 0 Gaps and Phase Requirements → Test Map)

All test files require a physical Android device (`./gradlew connectedDebugAndroidTest`). The emulator is not supported for LiteRT GPU delegate validation.

#### `HiltTestRunner.kt`

**Pattern (standard Hilt instrumented test runner):**
```kotlin
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

Register in `build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "com.safeglow.edge.HiltTestRunner"
    }
}
```

#### `InferenceServiceTest.kt` (covers SC-2)

**Test goal:** `LiteRTInferenceService.initialize()` completes and `infer()` emits at least one token without crashing.

```kotlin
@HiltAndroidTest
class InferenceServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var inferenceService: LiteRTInferenceService

    @Before fun setUp() { hiltRule.inject() }

    @Test fun firstTokenProducedWithoutCrash() = runTest {
        inferenceService.initialize()
        val tokens = mutableListOf<String>()
        inferenceService.infer("<start_of_turn>user\nSay hello.\n<end_of_turn>\n<start_of_turn>model\n")
            .take(1)
            .collect { tokens.add(it) }
        assertTrue("Expected at least one token", tokens.isNotEmpty())
    }
}
```

#### `GpuFallbackTest.kt` (covers SC-3)

**Test goal:** `initialize()` does not throw any exception regardless of GPU availability.

```kotlin
@HiltAndroidTest
class GpuFallbackTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var inferenceService: LiteRTInferenceService
    @Before fun setUp() { hiltRule.inject() }

    @Test fun initializeDoesNotThrowOnAnyDevice() = runTest {
        // If GPU path fails, CPU fallback must succeed without propagating exception
        assertDoesNotThrow { inferenceService.initialize() }
    }
}
```

#### `HiltRotationTest.kt` (covers SC-4)

**Test goal:** `LiteRTInferenceService` is `@Singleton` — same instance injected after simulated re-inject (proxy for rotation).

```kotlin
@HiltAndroidTest
class HiltRotationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var service1: LiteRTInferenceService

    @Test fun singletonSurvivesReInjection() {
        hiltRule.inject()
        val first = service1
        hiltRule.inject()
        assertSame("@Singleton must return same instance", first, service1)
    }
}
```

#### `RoomSeedTest.kt` (covers SC-5)

**Test goal:** Room opens via `createFromAsset()` and all 10 seed records are readable.

```kotlin
@HiltAndroidTest
class RoomSeedTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var dao: IngredientDao
    @Before fun setUp() { hiltRule.inject() }

    @Test fun tenSeedRecordsReadable() = runTest {
        val records = dao.getAll()
        assertTrue("Expected 10 seed records, got ${records.size}", records.size >= 10)
    }
}
```

#### `LiteRTNoNetworkTest.kt` (covers PRIV-01)

**Test goal:** Verify no `INTERNET` permission in manifest (static check proxy).

```kotlin
@RunWith(AndroidJUnit4::class)
class LiteRTNoNetworkTest {
    @Test fun internetPermissionAbsentFromManifest() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = ctx.packageManager
        val result = pm.checkPermission(
            android.Manifest.permission.INTERNET,
            ctx.packageName
        )
        assertEquals(
            "INTERNET permission must NOT be declared",
            PackageManager.PERMISSION_DENIED,
            result
        )
    }
}
```

---

## Shared Patterns

### Threading: newSingleThreadExecutor for all Engine calls
**Source:** RESEARCH.md lines 193–194, 374–375 (Pitfall 2 — Dispatchers.IO Thread Affinity Violation)
**Apply to:** `LiteRTInferenceService.kt` — every method that touches `Engine`

```kotlin
private val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
// All Engine calls: withContext(inferenceDispatcher) { ... }
```

Never use `Dispatchers.IO`. The GPU delegate is bound to the thread that called `initialize()`. `Dispatchers.IO` is a shared pool with no thread affinity guarantee — causes `TfLiteGpuDelegate Invoke: GpuDelegate must run on the same thread where it was initialized`.

---

### Hilt @ApplicationContext — No Activity Context in Singletons
**Source:** RESEARCH.md lines 377–378 (Anti-Patterns — Activity context in Hilt Singleton)
**Apply to:** `InferenceModule.kt`, `DatabaseModule.kt`, `LiteRTInferenceService.kt`

```kotlin
// CORRECT
@Provides @Singleton
fun provideService(@ApplicationContext context: Context): LiteRTInferenceService

// WRONG — causes memory leak and model re-init on rotation
@Provides @Singleton
fun provideService(activity: Activity): LiteRTInferenceService
```

---

### KSP over kapt for all annotation processors
**Source:** RESEARCH.md lines 379, 603–604 (Anti-Patterns and State of the Art)
**Apply to:** `app/build.gradle.kts` — Room and Hilt compiler declarations

```kotlin
ksp(libs.room.compiler)    // not kapt(libs.room.compiler)
ksp(libs.hilt.compiler)    // not kapt(libs.hilt.compiler)
```

---

### GPU try/catch — the only correct fallback
**Source:** RESEARCH.md lines 185, 202–216, 401–405 (Pattern 1 and Pitfall 1)
**Apply to:** `LiteRTInferenceService.initialize()`

```kotlin
engine = try {
    Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = cacheDir))
        .also { it.initialize() }
} catch (e: Exception) {
    Log.w("LiteRT", "GPU init failed (${e.message}), falling back to CPU")
    Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir))
        .also { it.initialize() }
}
```

`CompatibilityList.isDelegateSupportedOnThisDevice()` is for the old TFLite Interpreter API — it does not cover the LiteRT-LM Engine API. `try/catch` is the only correct mechanism.

---

### Model path — hardcoded filesDir resolution
**Source:** RESEARCH.md lines 197, 413–415 (Pattern 1 and Pitfall 3)
**Apply to:** `LiteRTInferenceService.initialize()`

```kotlin
val modelPath = context.filesDir.resolve("gemma-4-E2B-it.litertlm").absolutePath
```

Never accept `modelPath` from user input. The model is NOT in `src/main/assets/` — 2.58 GB APK install would fail. Must be `adb push`-ed to `filesDir` before first launch.

---

### Gemma 4 turn format for test prompt
**Source:** RESEARCH.md lines 571–581 (Code Examples — Gemma Turn Format)
**Apply to:** `InferenceServiceTest.kt` — test prompt construction

```
<start_of_turn>user
{prompt}
<end_of_turn>
<start_of_turn>model
```

Sending raw text without turn markers produces degraded or incoherent output. Low risk for Phase 1 (just need any token output), but establish the pattern here so Phase 4 does not need to retrofit.

---

### Room @Fts4 — not @Fts5
**Source:** RESEARCH.md lines 239–241, 379 (Pattern 2 note and Anti-Patterns)
**Apply to:** `IngredientFts.kt`

Room has `@Fts4` annotation only — `@Fts5` does not exist in the Room annotation processor. Use `@Fts4(contentEntity = IngredientRecord::class)`.

---

## No Analog Found

All files have no analog — this is a greenfield project. The planner must use RESEARCH.md patterns as the primary reference for all files.

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| All 18 files listed in File Classification | various | various | Greenfield — zero existing Kotlin source files in repository |

---

## Metadata

**Analog search scope:** Entire repository (`find . -name "*.kt"`) — returned zero results
**Files scanned:** 0 source files (greenfield)
**Pattern extraction date:** 2026-04-25
**Pattern source:** RESEARCH.md (01-RESEARCH.md) — all patterns sourced from verified official documentation and GitHub issue tracker references cited therein
**Package name:** `com.safeglow.edge` (confirmed in RESEARCH.md architecture diagram and adb push commands)
**Min SDK:** 26 (required for SQLite 3.9+ / FTS4 availability)
**Compile SDK:** 35
