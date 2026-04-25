# Architecture Research: SafeGlow Edge

**Domain:** Android on-device RAG + LiteRT LLM inference (cosmetic ingredient safety)
**Researched:** 2026-04-25
**Confidence:** HIGH — all major patterns verified via Context7 against official LiteRT, Android, ML Kit, Room, and sqlite-vec documentation.

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  ScanScreen  │  │ ResultScreen │  │ProfileScreen │              │
│  │  (Compose)   │  │  (Compose)   │  │  (Compose)   │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         └─────────────────┼─────────────────┘                       │
├─────────────────────────  │  ───────────────────────────────────────┤
│                   ViewModel Layer                                     │
│  ┌────────────────────────▼────────────────────────────────────┐    │
│  │                    ScanViewModel                             │    │
│  │  StateFlow<ScanUiState>   viewModelScope.launch(IO)          │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
├──────────────────────────── │ ──────────────────────────────────────┤
│                    Domain / Use-Case Layer                            │
│  ┌────────────┐  ┌──────────▼────────┐  ┌─────────────────────┐    │
│  │VisionUseCase│ │AnalysisUseCase    │  │ SessionContextStore │    │
│  │            │  │                   │  │  (DataStore Prefs)  │    │
│  └─────┬──────┘  └──────────┬────────┘  └─────────────────────┘    │
├────────│────────────────────│─────────────────────────────────────  ┤
│        │           Infrastructure Layer                               │
│  ┌─────▼──────┐  ┌──────────▼────────┐  ┌─────────────────────┐    │
│  │VisionPipeline│ │    RAGPipeline    │  │  LiteRTInference    │    │
│  │            │  │                   │  │      Service        │    │
│  └─────┬──────┘  └──────────┬────────┘  └──────────┬──────────┘    │
├────────│────────────────────│────────────────────── │ ──────────────┤
│        │              Data Layer                     │               │
│  ┌─────▼──────┐  ┌──────────▼────────┐  ┌──────────▼──────────┐    │
│  │  CameraX   │  │  KnowledgeBase    │  │   Gemma 4 E2B       │    │
│  │  ML Kit    │  │  Repository       │  │   (.litertlm)       │    │
│  │(OCR + Face)│  │  (Room + cosine)  │  │   (filesDir/)       │    │
│  └────────────┘  └───────────────────┘  └─────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Interface |
|-----------|----------------|-----------|
| ScanScreen | Camera preview, capture trigger, results rendering | Consumes `ScanUiState` StateFlow |
| ScanViewModel | Orchestrates scan pipeline, owns UI state, lifecycle-safe | `viewModelScope`, exposes `StateFlow<ScanUiState>` |
| VisionUseCase | Coordinates CameraX frame → OCR + face detection | Returns `VisionResult` sealed class |
| AnalysisUseCase | Drives RAG → prompt assembly → LiteRT → validation | Returns `Flow<AnalysisProgress>` |
| SessionContextStore | Persists pregnancy/country/skin session prefs | DataStore Preferences, injected singleton |
| VisionPipeline | CameraX ImageAnalysis → ML Kit text + face | Wraps ML Kit Task API in suspending adapters |
| RAGPipeline | INCI lookup → embedding search → context filter → prompt | Pure Kotlin, `Dispatchers.IO` |
| LiteRTInferenceService | Manages Engine/Conversation lifecycle, single-thread executor | Singleton, serialized access, `newSingleThreadExecutor` |
| KnowledgeBaseRepository | Pre-built SQLite (Room) + JSON indices, asset-loaded | Repository pattern, exposes DAO + cosine search |
| OutputValidator | Parses LiteRT JSON output, enforces schema, confidence gate, strips non-KB citations | Pure function, no I/O |

---

## Recommended Project Structure

```
app/src/main/
├── kotlin/com/safeglow/edge/
│   ├── di/
│   │   ├── DatabaseModule.kt       # Room binding
│   │   ├── InferenceModule.kt      # LiteRTInferenceService singleton
│   │   └── VisionModule.kt         # ML Kit client instances
│   │
│   ├── ui/
│   │   ├── scan/
│   │   │   ├── ScanScreen.kt
│   │   │   ├── ScanViewModel.kt
│   │   │   └── ScanUiState.kt      # Sealed: Idle/Scanning/Results/Error
│   │   ├── result/
│   │   │   ├── ResultScreen.kt
│   │   │   └── ResultUiState.kt
│   │   ├── profile/
│   │   │   └── ProfileScreen.kt
│   │   └── theme/
│   │
│   ├── domain/
│   │   ├── usecase/
│   │   │   ├── VisionUseCase.kt
│   │   │   └── AnalysisUseCase.kt
│   │   ├── model/
│   │   │   ├── VisionResult.kt
│   │   │   ├── IngredientTag.kt    # Explain/Caution/Solve/Danger + confidence
│   │   │   ├── ScanResult.kt
│   │   │   └── SessionContext.kt
│   │   └── repository/
│   │       └── KnowledgeBaseRepository.kt  # interface only
│   │
│   ├── data/
│   │   ├── vision/
│   │   │   ├── VisionPipeline.kt
│   │   │   ├── OcrProcessor.kt     # ML Kit suspend wrapper
│   │   │   └── FaceAnalyzer.kt     # Landmarks → SkinProfile
│   │   │
│   │   ├── rag/
│   │   │   ├── RAGPipeline.kt
│   │   │   ├── InciNormalizer.kt   # Synonym JSON lookup + fuzzy fallback
│   │   │   ├── EmbeddingEngine.kt  # gte-tiny via LiteRT Interpreter
│   │   │   ├── ContextFilter.kt
│   │   │   └── PromptAssembler.kt  # Token budget enforcer
│   │   │
│   │   ├── inference/
│   │   │   ├── LiteRTInferenceService.kt   # Engine lifecycle + single-thread executor
│   │   │   └── OutputValidator.kt           # JSON parse + confidence gate + citation strip
│   │   │
│   │   ├── knowledge/
│   │   │   ├── KnowledgeBaseRepositoryImpl.kt
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── IngredientDao.kt         # Exact INCI match + FTS5
│   │   │   │   └── entities/
│   │   │   └── json/
│   │   │       ├── SynonymIndex.kt
│   │   │       └── EmbeddingIndex.kt        # Pre-computed FloatArray per ingredient
│   │   │
│   │   └── session/
│   │       └── SessionContextDataStore.kt
│   │
│   └── MainApplication.kt    # @HiltAndroidApp
│
└── assets/
    ├── knowledge_base.db           # Pre-built Room SQLite (80–500 ingredients)
    ├── inci_synonyms.json          # OCR noise → canonical INCI
    ├── embedding_index.json        # Pre-computed float[384] per ingredient
    └── embedding_model.tflite      # gte-tiny (~25MB); noCompress required
    # NOTE: gemma4_e2b.litertlm NOT here — downloaded to filesDir at first launch
```

---

## Architectural Patterns

### Pattern 1: Repository Interface in Domain Layer

`KnowledgeBaseRepository` is an interface in `domain/repository/`. Room implementation is in `data/knowledge/`, provided by Hilt. Use cases depend only on the interface — enabling unit testing with fake repositories without any Android imports in domain.

```kotlin
interface KnowledgeBaseRepository {
    suspend fun findExactIngredient(inciName: String): IngredientRecord?
    suspend fun semanticSearch(embedding: FloatArray, topK: Int = 5): List<IngredientRecord>
    suspend fun filterByContext(ids: List<Long>, ctx: SessionContext): List<IngredientRecord>
}
```

### Pattern 2: Single-Thread Executor for LiteRT Engine

LiteRT `InterpreterApi` instances are not thread-safe (official docs). GPU delegate must be created and invoked on the same thread. Use `newSingleThreadExecutor` — not `Dispatchers.IO` (which does not guarantee thread affinity).

```kotlin
@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: Engine? = null

    suspend fun initialize() = withContext(inferenceDispatcher) {
        engine = Engine(EngineConfig(
            modelPath = ctx.filesDir.resolve("gemma4_e2b.litertlm").absolutePath,
            backend = if (CompatibilityList().isDelegateSupportedOnThisDevice) Backend.GPU() else Backend.CPU(),
            cacheDir = ctx.cacheDir.absolutePath
        )).also { it.initialize() }
    }

    suspend fun infer(prompt: String): Flow<String> = channelFlow {
        withContext(inferenceDispatcher) {
            engine!!.createConversation().use { convo ->
                convo.sendMessageAsync(prompt).collect { send(it.toString()) }
            }
        }
    }

    fun close() { engine?.close(); inferenceDispatcher.close() }
}
```

### Pattern 3: Sealed Progress Flow for Pipeline Stages

`AnalysisUseCase` emits `Flow<AnalysisProgress>` for each pipeline stage. ViewModel maps to `ScanUiState`. Enables meaningful intermediate UI states (OCR done → retrieving → LLM streaming).

```kotlin
sealed class AnalysisProgress {
    data object OcrComplete : AnalysisProgress()
    data object FaceScanComplete : AnalysisProgress()
    data class RetrievalComplete(val ingredients: List<String>) : AnalysisProgress()
    data class InferenceStreaming(val partial: String) : AnalysisProgress()
    data class ValidationComplete(val result: ScanResult) : AnalysisProgress()
    data class Error(val cause: Throwable) : AnalysisProgress()
}
```

### Pattern 4: Pre-Computed Embedding Index (Hot Path Elimination)

Load `embedding_index.json` into a `HashMap<String, FloatArray>` at startup. At query time, look up pre-computed vectors directly — `EmbeddingEngine` is invoked only for unresolved OCR tokens (~5% of queries).

```kotlin
// Startup: load all pre-computed embeddings into memory (~1–4 MB)
val embeddingIndex: HashMap<String, FloatArray> = loadFromAssets("embedding_index.json")

// Query time: hit cache first
val vec = embeddingIndex[canonicalInci] ?: embeddingEngine.embed(canonicalInci)
```

---

## Data Flow

### End-to-End: Camera to Rendered Tags

```
User taps "Scan Label"
        │
        ▼
CameraX ImageCapture (max resolution — not ImageAnalysis)
        │
        ▼ [Dispatchers.IO]
ML Kit TextRecognizer.process(InputImage.fromMediaImage(..., rotationDegrees))
  → raw OCR text string → close ImageProxy immediately
        │
        ▼ [Dispatchers.Default]
InciNormalizer.normalize(rawOcrText)
  → uppercase + HashMap synonym lookup + Levenshtein fuzzy fallback
  → List<String> canonical INCI names
        │
  ┌─────┴─────────────────────────────────────────┐
  │                                               │
  ▼ [Dispatchers.IO]                              ▼ [Dispatchers.Default]
Room IngredientDao.findExact(inciName)          EmbeddingIndex.lookup(inciName)
  → IngredientRecord? (exact hit)                 → cached FloatArray OR
                                                  EmbeddingEngine.embed(inciName)
                                                  → top-K cosine similarity scan
  │                                              │
  └────────────────────┬─────────────────────────┘
                       │ merge + deduplicate
                       ▼
                ContextFilter.apply(records, sessionContext)
                  → boost pregnancy_risk if pregnancy=true
                  → boost eu_prohibited if country=EU
                  → boost sensitizer_high if skin=sensitive
                       │
                       ▼
                PromptAssembler.build(ingredients, records, ctx)
                  → token budget: system(~200) + context(≤400) + query(~50) + output(300)
                  → truncate low-signal chunks first, preserve citations
                       │
                       ▼ [newSingleThreadExecutor]
                LiteRTInferenceService.infer(prompt)
                  → Flow<String> streaming tokens
                       │
                       ▼ (accumulate full response)
                OutputValidator.validate(json)
                  → parse against IngredientTag schema
                  → strip citations not matching known KB citation IDs
                  → confidence gate: <0.60 grayed / 0.60–0.84 dashed / ≥0.85 solid
                  → Result<ScanResult>
                       │
                       ▼
                ScanViewModel.uiState: StateFlow<ScanUiState.Results>
                       │
                       ▼
                ResultScreen (Compose) — sorted by severity (Danger first)
```

### Face Scan Flow (runs parallel, optional for v1)

```
User taps "Scan Face" OR selects manual profile
        │
        ▼ [if camera selected]
CameraX single-frame capture
ML Kit FaceDetector (LANDMARK_MODE_ALL + CLASSIFICATION_MODE_ALL)
  → FaceAnalyzer.deriveProfile(face) → SkinProfile (Fitzpatrick, concerns)
        │
        ▼ [alternative: manual dropdown]
Manual selector: Normal / Dry / Sensitive / Acne-prone / Pregnant
        │
        ▼
SessionContextStore.updateProfile(profile) [DataStore, Dispatchers.IO]
  → SessionContext updated → ContextFilter uses on next scan
```

### Knowledge Base Loading (app startup, one-shot)

```
Application.onCreate() [@Singleton Hilt init, Dispatchers.IO]
        │
Room.databaseBuilder(...).createFromAsset("knowledge_base.db").build()
SynonymIndex.load(assets, "inci_synonyms.json") → HashMap<String, String>
EmbeddingIndex.load(assets, "embedding_index.json") → HashMap<String, FloatArray>
```

### Model Download Flow (first launch)

```
App detects missing gemma4_e2b.litertlm in filesDir
        │
WorkManager.enqueueUniqueWork("model_download",
    constraints = Constraints(requiresUnmetered = true))
        │
ModelDownloadWorker → downloads to ctx.filesDir → emits WorkInfo.Progress
        │
LiteRTInferenceService.initialize() on completion
```

---

## Threading Model

| Operation | Dispatcher | Rationale |
|-----------|------------|-----------|
| Room DAO queries | `Dispatchers.IO` | Disk I/O |
| JSON / asset loading | `Dispatchers.IO` | Disk I/O |
| OCR / Face detection | `Dispatchers.IO` | ML Kit Task API callbacks |
| Embedding inference | `Dispatchers.Default` | CPU-bound compute |
| LLM inference (LiteRT Engine) | `newSingleThreadExecutor` | NOT thread-safe; GPU delegate requires same thread |
| UI state updates | `Dispatchers.Main` | StateFlow collect in Compose |
| Model download | `WorkManager` | Background, Wi-Fi constrained |

**Key rule:** All LiteRT `Interpreter` and `Engine`/`Conversation` calls must run on the same single-thread executor. `Dispatchers.IO` does not guarantee thread affinity — using it with GPU delegate causes silent crashes.

---

## Asset Loading Strategy

| Asset | Size | Strategy | Notes |
|-------|------|----------|-------|
| `knowledge_base.db` | ~5–15 MB | Room `createFromAsset()` | Copied to internal storage on first run |
| `inci_synonyms.json` | ~500 KB | In-memory `HashMap` at startup | Held for app lifetime |
| `embedding_index.json` | ~1–4 MB | In-memory `HashMap<String, FloatArray>` | Skips EmbeddingEngine for known INHNs |
| `embedding_model.tflite` | ~25 MB | `CompiledModel.create(assetManager, name)` | `aaptOptions { noCompress "tflite" }` required |
| `gemma4_e2b.litertlm` | ~1.5–2 GB | WorkManager download → `filesDir` | Never in APK |

---

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Sharing LiteRT Interpreter across coroutines | Thread-safety violation → crash or data corruption | Single-thread executor for all inference |
| Gemma model in APK assets | APK >150 MB, install fails | Download at runtime to `filesDir` |
| Holding ImageProxy reference after ML Kit | Fills CameraX buffer queue, freezes camera | Close ImageProxy immediately after `InputImage.fromMediaImage()` |
| LLM generating regulatory rationale | Hallucinated thresholds, stale jurisdiction data | Regulatory text = verbatim from KB only; LLM generates health summary only |
| OCR on `ImageAnalysis` 30fps stream | CPU saturated; INCI text doesn't move | Single `ImageCapture` tap-to-capture at max resolution |
| Activity context in Hilt inference singleton | Context leak on rotation | Always `context.applicationContext` |

---

## Suggested Build Order

1. **Phase 1 — Foundation**: Hilt DI, Room + `createFromAsset()`, DataStore, Dispatcher qualifiers. Unblocks everything.
2. **Phase 2 — Vision**: CameraX + `OcrProcessor` + `FaceAnalyzer`. Demoable standalone.
3. **Phase 3 — RAG**: `InciNormalizer` → `EmbeddingEngine` → cosine retrieval → `ContextFilter` → `PromptAssembler` with token budget.
4. **Phase 4 — LiteRT Inference**: `LiteRTInferenceService` + single-thread executor + `OutputValidator`.
5. **Phase 5 — UI Integration**: `ScanViewModel`, `ResultScreen` tag rendering, confidence borders, streaming progress.
6. **Phase 6 — Polish**: Model download flow, regulatory comparison UI, disclaimer overlays.

---

## Sources

- LiteRT thread safety: official docs — "InterpreterApi instances are not thread-safe" (HIGH)
- LiteRT-LM Kotlin API (`Engine`, `EngineConfig`, `Conversation`, Flow streaming): Context7 /google-ai-edge/litert-lm (HIGH)
- GPU delegate threading: official LiteRT GPU delegate docs (HIGH)
- Room pre-populated asset (`createFromAsset`): official Android training docs (HIGH)
- ML Kit Text Recognition + rotation: official ML Kit docs (HIGH)
- sqlite-vec KNN (`vec0` virtual table): Context7 /asg017/sqlite-vec (HIGH)

---
*Confidence: HIGH for threading/LiteRT/Room patterns; MEDIUM for exact library versions (verify at build time)*
