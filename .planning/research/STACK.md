# Stack Research: SafeGlow Edge

**Project:** SafeGlow Edge — Android + LiteRT + On-Device RAG + Cosmetic Safety AI
**Researched:** 2026-04-25
**Confidence:** MEDIUM-HIGH overall

---

## Recommended Stack

### Core Build Config

| Technology | Artifact / Setting | Version | Confidence |
|------------|--------------------|---------|-----------|
| Kotlin | build plugin | 2.1.0 | HIGH |
| AGP | build plugin | 8.7.3 | HIGH |
| Compile SDK | `compileSdk` | 35 | HIGH |
| Min SDK | `minSdk` | 26 | HIGH |
| KSP | annotation processor | 2.1.0-1.0.29 | MEDIUM |

### UI

| Technology | Artifact | Version | Confidence |
|------------|----------|---------|-----------|
| Compose BOM | `androidx.compose:compose-bom` | `2026.03.00` | HIGH |
| Navigation Compose | `androidx.navigation:navigation-compose` | `2.8.9` | MEDIUM |

### AI / ML Runtime

| Technology | Artifact | Version | Confidence |
|------------|----------|---------|-----------|
| **LiteRT-LM (Gemma 4 E2B)** | `com.google.ai.edge.litertlm:litertlm-android` | `latest.release` ⚠️ pin to exact version at build time | HIGH |
| LiteRT core (embedding TFLite) | `com.google.ai.edge.litert:litert` | `1.0.1` | HIGH |
| LiteRT GPU Delegate | `com.google.ai.edge.litert:litert-gpu` | `2.3.0` | HIGH |
| MediaPipe Tasks Vision | `com.google.mediapipe:tasks-vision` | `0.10.x` | MEDIUM |

### Camera & Vision

| Technology | Artifact | Version | Confidence |
|------------|----------|---------|-----------|
| CameraX camera2 | `androidx.camera:camera-camera2` | `1.4.2` | HIGH |
| CameraX lifecycle | `androidx.camera:camera-lifecycle` | `1.4.2` | HIGH |
| CameraX view | `androidx.camera:camera-view` | `1.4.2` | HIGH |
| **ML Kit Text Recognition (bundled)** | `com.google.mlkit:text-recognition` | `16.0.1` | HIGH |
| **ML Kit Face Detection (bundled)** | `com.google.mlkit:face-detection` | `16.1.7` | HIGH |

> Use **bundled** (not `play-services-mlkit-*`) variants — fully offline, no Play Services dependency, no first-launch model download.

### Data / Persistence

| Technology | Artifact | Version | Confidence |
|------------|----------|---------|-----------|
| Room runtime | `androidx.room:room-runtime` | `2.7.1` | HIGH |
| Room KTX | `androidx.room:room-ktx` | `2.7.1` | HIGH |
| Room compiler (KSP) | `androidx.room:room-compiler` | `2.7.1` | HIGH |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | `1.1.3` | MEDIUM |
| Kotlin Serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | MEDIUM |

### DI / Async

| Technology | Artifact | Version | Confidence |
|------------|----------|---------|-----------|
| Hilt | `com.google.dagger:hilt-android` | `2.54` | MEDIUM |
| Coroutines Android | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.9.0` | HIGH |
| Coroutines Guava (CameraX bridge) | `org.jetbrains.kotlinx:kotlinx-coroutines-guava` | `1.9.0` | HIGH |

---

## Critical API Notes

### LiteRT-LM Inference Pattern (Gemma 4 E2B)

The correct runtime for Gemma 4 E2B is **LiteRT-LM** using the `.litertlm` container format — NOT the old `LlmInference` MediaPipe API, and NOT `org.tensorflow:tensorflow-lite`.

```kotlin
import com.google.ai.edge.litertlm.*

// Must run on a single-thread executor — GPU delegate is NOT thread-safe
val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

val config = EngineConfig(
    modelPath = context.filesDir.absolutePath + "/gemma-4-E2B-it.litertlm",
    backend = Backend.GPU(),   // fallback: Backend.CPU()
    cacheDir = context.cacheDir.absolutePath
)
val engine = Engine(config)
engine.initialize()  // blocks several seconds — call on inferenceDispatcher only

engine.createConversation().use { session ->
    session.sendMessageAsync(prompt).collect { token ->
        // stream to UI via StateFlow
    }
}

engine.close()  // CRITICAL — holds native heap; must be closed in onCleared()
```

### Model Delivery

Gemma 4 E2B is **~1.5–2 GB** (`.litertlm`) — cannot be bundled in APK.

- **Hackathon demo**: Pre-load via `adb push` to `/data/data/<pkg>/files/` before demo
- **Production**: WorkManager first-launch download to `context.filesDir`

### Gemma Prompt Format

Gemma requires turn markers — sending raw text without them produces degraded output:

```
<start_of_turn>user
{system_instructions}

{rag_context}

{query}
<end_of_turn>
<start_of_turn>model
```

### On-Device RAG: Embedding Strategy

Use **gte-tiny** (quantized `.tflite`, ~25 MB) over all-MiniLM-L6-v2 (~90 MB):
- Same 384-dim output, sufficient for 300–500 ingredient semantic matching
- Bundle in `src/main/assets/`; add `aaptOptions { noCompress "tflite" }` to build.gradle
- Pre-compute all ingredient embeddings offline at knowledge-base build time
- Only query embedding (OCR output tokens) needs live inference at runtime

**Cosine similarity fallback — recommended over sqlite-vec for hackathon (no JNI risk):**

```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f; var normA = 0f; var normB = 0f
    for (i in a.indices) { dot += a[i] * b[i]; normA += a[i]*a[i]; normB += b[i]*b[i] }
    return dot / (sqrt(normA) * sqrt(normB))
}
// 500 ingredients × 384 floats = ~768 KB in-memory; top-K scan < 5 ms
```

---

## What NOT to Use

| Avoid | Why |
|-------|-----|
| `org.tensorflow:tensorflow-lite` | Renamed; cannot load `.litertlm`; use `litert` or `litertlm-android` |
| `com.google.mediapipe:tasks-genai` `LlmInference` | Pre-LiteRT-LM API; does not support Gemma 4 `.litertlm` format |
| ONNX Runtime | Redundant runtime, no Gemma 4 support without custom conversion |
| `play-services-mlkit-*` variants | Require Play Services + first-use download; breaks offline guarantee |
| `kapt` annotation processor | Deprecated; use `ksp` for Room and Hilt |
| MediaPipe Solutions API (deprecated) | Use Tasks API (`tasks-vision`) |
| Any Firebase / analytics SDK | Violates zero-cloud privacy principle |
| sqlite-vec JNI (for hackathon) | No Android AAR on MavenCentral; JNI setup risky in 7-day sprint |

---

## Suggested Phase Build Order

1. **Phase 1**: AGP 8.7 + Kotlin 2.1 + Compose BOM + Hilt + Room + project structure
2. **Phase 2**: CameraX + ML Kit Text Recognition (bundled) — label scanning, first visible feature
3. **Phase 3**: gte-tiny embedding + SQLite knowledge base + cosine RAG retrieval
4. **Phase 4**: LiteRT-LM integration + prompt template + JSON schema output validator
5. **Phase 5**: ML Kit Face Detection + skin profiling (defer if behind schedule — manual selector fallback)
6. **Phase 6**: Polish — confidence indicators, regulatory comparison UI, disclaimer, model download flow

---

## Open Questions

1. Exact `litertlm-android` version on MavenCentral as of April 2026? (`latest.release` is not reproducible — must pin before submission)
2. Exact `.litertlm` file size for Gemma 4 E2B? (Determines download UX requirements)
3. `sqlite-vec` Android AAR published after April 2026? Check https://github.com/asg017/sqlite-vec/releases
4. MediaPipe FaceLandmarker `.task` model size — confirm fits in APK assets alongside gte-tiny

---
*Confidence: MEDIUM-HIGH | LiteRT-LM API verified via Context7 official docs; Compose BOM version HIGH confidence*
