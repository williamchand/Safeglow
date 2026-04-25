# Project Research Summary

**Project:** SafeGlow Edge — Android on-device cosmetic safety AI
**Domain:** Mobile AI / health-adjacent consumer app (Android, on-device LLM, RAG, computer vision)
**Researched:** 2026-04-25
**Confidence:** MEDIUM-HIGH overall

---

## Executive Summary

SafeGlow Edge is a privacy-first Android app that runs Gemma 4 E2B via LiteRT on-device to analyze cosmetic ingredient labels with citation-backed health safety tags. There is no meaningful direct competitor: every existing app (EWG Skin Deep, Think Dirty, CosDNA, INCI Decoder) is cloud-only, opaque-score-based, and lacks health mechanism explanations or primary-source citations. The recommended build approach is a strict layered architecture — CameraX → ML Kit OCR → INCI normalization → SQLite knowledge base → on-device RAG (cosine similarity) → LiteRT-LM prompt → JSON output validation → Compose UI — with each layer testable in isolation. This architecture is well-documented in official Google/Android sources and maps directly to the hackathon LiteRT special track evaluation criteria.

The single highest-risk area is the knowledge base: data curation for 300–500 ingredients with full health metadata and citations could consume the entire 7-day sprint if unscoped. Research strongly recommends hard-capping the hackathon knowledge base at 80 ingredients (depth over breadth), chosen from the highest-frequency cosmetic ingredients (parabens, retinoids, sunscreen filters, fragrances, preservatives) to achieve >90% real-product coverage with curated records. The second-highest risk is LiteRT threading: the GPU delegate must run on a single-thread executor from Day 1 — retrofitting this after wiring the full pipeline is expensive and the failure mode (intermittent crash) is hard to debug.

The recommended mitigation strategy is to sequence builds so every dangerous dependency is validated on physical hardware before UI work begins: model loading from filesDir on Day 1, GPU delegate threading on Day 1, OCR accuracy on real labels on Day 2, and RAG token budget on Day 3. Face scan via ML Kit is explicitly a stretch goal — a manual skin profile selector delivers identical RAG context-filtering value with zero ML complexity and should be built first as the guaranteed fallback.

---

## Key Findings

### Recommended Stack

The core runtime is LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`) with the `.litertlm` container format — not the deprecated `tasks-genai` MediaPipe LlmInference API and not `org.tensorflow:tensorflow-lite`. The Gemma 4 E2B model (~1.5–2 GB) must be stored in `context.filesDir` and cannot be bundled in the APK. For hackathon purposes, pre-loading via `adb push` is the correct demo strategy; a WorkManager download flow is the production path.

RAG embedding uses gte-tiny (~25 MB quantized TFLite, 384-dim) over all-MiniLM-L6-v2 (~90 MB) — same output dimensionality at a third of the size, and critically, pre-computed ingredient embeddings served from an in-memory HashMap mean the embedding model is almost never invoked at query time. Cosine similarity over 500 ingredients in-memory (~768 KB of float arrays) executes in under 5 ms, making sqlite-vec JNI unnecessary and risky for the 7-day sprint.

**Core technologies:**
- **LiteRT-LM (`litertlm-android`):** Gemma 4 E2B on-device inference — the only supported runtime for `.litertlm` format; hackathon track requirement
- **LiteRT core + GPU delegate (`litert`, `litert-gpu`):** gte-tiny embedding runtime; GPU delegate for inference acceleration
- **ML Kit Text Recognition v2 (bundled):** Offline OCR for label scanning — bundled variant avoids Play Services dependency and first-launch download
- **ML Kit Face Detection (bundled):** On-device face landmark detection for skin profiling (stretch goal)
- **CameraX 1.4.2:** Camera preview + tap-to-capture at max resolution (not 30fps ImageAnalysis stream)
- **Room 2.7.1 + KSP:** Pre-built knowledge base SQLite accessed via `createFromAsset()`; FTS5 for exact INCI match
- **Hilt 2.54 + Coroutines:** Singleton lifecycle management for LiteRTInferenceService; prevents context leaks and model re-initialization on rotation
- **Jetpack Compose BOM 2026.03.00 + Navigation Compose:** Declarative UI; StateFlow drives all screen state
- **DataStore Preferences:** Session context persistence (pregnancy, country, skin concern) — no Room overhead for simple key-value prefs

**Critical version note:** Pin `litertlm-android` to an exact version before submission — `latest.release` is not reproducible.

### Expected Features

No existing competitor combines on-device privacy, health mechanism explanations, primary-source citations, and jurisdiction comparison. SafeGlow Edge is differentiated on all four simultaneously. The risk is execution depth — a sparse knowledge base makes all four differentiators feel hollow.

**Must have (table stakes — P1):**
- OCR label scan via CameraX + ML Kit — primary input; typing is unacceptable friction
- INCI normalization with fuzzy matching — silent health safety failure without this
- Knowledge base of 80+ curated ingredients (hackathon) up to 300–500 (full v1)
- Per-ingredient safety tags (Explain / Caution / Solve / Danger) with health mechanism text
- Citation display from pre-curated SCCS/CIR/ACOG/FDA sources only
- Confidence tier visual (solid/dashed/gray border based on retrieval quality)
- Session context (pregnancy, country, skin concern) without account creation
- Legal disclaimer persistent on all result screens
- "Data Valid As Of [date]" footer
- Local scan save (no cloud sync)
- On-device inference indicator + latency display (LiteRT track judge signal)

**Should have (differentiators — P2):**
- Face scan / skin profiling via ML Kit (fallback: manual dropdown — implement fallback first)
- Safer alternative ingredient suggestions with mechanism match
- Multi-jurisdiction regulatory comparison UI (EU/US/CN/JP — data is in KB; UI is the work)
- Wi-Fi-only bundle update for knowledge base refresh

**Defer (v2+):**
- Barcode scan to product database — requires external product DB; conflicts with privacy-first v1
- iOS port — post-hackathon after Android core is stable
- Knowledge base expansion beyond 500 ingredients
- PDF export / sharing — local save covers v1 need

**Anti-features to explicitly avoid:**
- Single product-level score ("Dirty Meter" equivalent) — obscures ingredient nuance, creates gamification pressure
- "Clean" / "natural" labeling — unregulated marketing language, no scientific basis
- User-submitted ingredient data — health app + unverified data = safety liability
- Any Firebase / analytics SDK — violates zero-cloud privacy principle

### Architecture Approach

The architecture follows a strict 4-layer MVVM pattern (Presentation → ViewModel → Domain Use Cases → Infrastructure/Data) with clean separation at each boundary. `KnowledgeBaseRepository` is an interface in the domain layer, with Room implementation injected by Hilt — enabling unit tests without Android imports. The most critical architectural constraint is threading: all LiteRT `Engine`/`Conversation` calls must run on a `newSingleThreadExecutor`-based dispatcher, never `Dispatchers.IO` (which does not guarantee thread affinity for GPU delegate). `AnalysisUseCase` emits `Flow<AnalysisProgress>` with sealed progress states so the UI can show meaningful intermediate feedback (OCR done → retrieving → LLM streaming tokens) rather than a single blocking wait.

**Major components:**
1. **VisionPipeline** — CameraX `ImageCapture` → ML Kit OCR + face detection; wraps ML Kit Task API in suspending adapters; closes `ImageProxy` immediately after `InputImage.fromMediaImage()`
2. **RAGPipeline** — INCI normalization → exact Room FTS5 lookup → embedding cosine fallback → ContextFilter (boosts pregnancy/jurisdiction/sensitizer signals) → PromptAssembler with hard token budget (system ~200 + context ≤400 + query ~50 + output 300 = ~950 total)
3. **LiteRTInferenceService** — `@Singleton`; `newSingleThreadExecutor` dispatcher; `Engine` initialized once, never per-scan; `close()` called in `onCleared()` and app termination
4. **OutputValidator** — pure function; parses JSON against `IngredientTag` schema; strips any citation not matching a known KB `citation_id`; applies confidence gate (<0.60 grayed, 0.60–0.84 dashed, ≥0.85 solid)
5. **KnowledgeBaseRepository** — Room `createFromAsset("knowledge_base.db")` + in-memory `HashMap` for synonym index and pre-computed embeddings; loaded once at `Application.onCreate()`
6. **ScanViewModel** — orchestrates the full pipeline via `viewModelScope`; exposes `StateFlow<ScanUiState>`; the only component that touches both VisionUseCase and AnalysisUseCase

### Critical Pitfalls

1. **Gemma model in APK assets (Day 1 blocker)** — Model is ~1.5–2 GB; APK install fails. Store in `context.filesDir`; for hackathon demo, `adb push` before presenting. Validate this on physical hardware before any other work.

2. **GPU delegate called on wrong thread (intermittent crash)** — `Dispatchers.IO` does not guarantee thread affinity. Use `newSingleThreadExecutor().asCoroutineDispatcher()` for all Engine initialization and inference. Establish this on Day 1 before wiring UI.

3. **Knowledge base data curation consuming the entire sprint** — 500 ingredients with full health metadata = 40–80 hours of editorial work. Hard cap at 80 ingredients for hackathon. Lock JSON schema on Day 1. Validate against 5 real product labels before Day 3.

4. **Context window overflow silently losing system prompt** — Default `setMaxTokens(1024)` covers total sequence (input + output). Build token budget accounting into `PromptAssembler` from Day 1 with hard limits per section. Warning sign: output ignores safety instructions or switches language mid-response.

5. **Citation hallucination from sparse knowledge base** — Gemma pattern-matches citation formats from training data when retrieval context is sparse. `OutputValidator` must strip all citations not matching a KB-retrieved `citation_id`. For unrecognized ingredients, short-circuit before LLM and return "UNKNOWN — not in safety database" card.

6. **INCI synonym resolution failure (silent health safety miss)** — OCR returns "Retinol Palmitate"; KB is keyed on "RETINYL PALMITATE"; app emits safe tag for risky ingredient. Synonym map must cover brand/trade names, uppercase normalization, common OCR character substitutions. Mark unresolved ingredients explicitly as UNKNOWN, never silently as safe.

7. **Face scan scope creep** — Fitzpatrick classification is not a built-in MediaPipe task; camera-based skin analysis that does not work wastes Days 4–5. Build manual dropdown first. Camera-based skin analysis is a stretch goal only.

---

## Implications for Roadmap

Based on research, the 6-phase build order recommended by both STACK.md and ARCHITECTURE.md is well-justified by dependency analysis. Each phase must produce a demoable, tested artifact before the next phase begins.

### Phase 1: Foundation + Model Validation
**Rationale:** The three hardest-to-recover-from failures (APK size, GPU threading, context leak) must be caught on physical hardware before any feature work. Data curation scope must also be locked on Day 1 — it determines whether the project ships.
**Delivers:** Working project scaffold; Gemma 4 E2B loading from `filesDir` producing inference output on physical device; Room database with 10 seed ingredient records; `LiteRTInferenceService` with correct `newSingleThreadExecutor` threading; Hilt DI wired; knowledge base JSON schema locked; 80-ingredient scope confirmed
**Addresses:** Project setup, schema design, scope lock
**Avoids:** Pitfalls 1 (APK size), 2 (GPU threading), 4 (model load on main thread), 8 (context leak)
**Research flag:** Standard patterns — all components are well-documented. No research phase needed.

### Phase 2: Camera + OCR Pipeline
**Rationale:** OCR is the primary user-facing input and has significant real-world failure risk (curved labels, 6pt INCI type, character confusion). Must be benchmarked on real products before RAG is built on top of it.
**Delivers:** CameraX tap-to-capture at max resolution; ML Kit OCR producing a token list; INCI normalization (uppercase + synonym HashMap + Levenshtein fuzzy); 5 real product labels tested with ≥80% INCI token recovery; knowledge base expanded to 80 curated ingredients with full health metadata
**Uses:** CameraX 1.4.2, ML Kit Text Recognition (bundled), Kotlin Serialization for synonym JSON
**Implements:** VisionPipeline, OcrProcessor, InciNormalizer
**Avoids:** Pitfalls 6 (INCI synonym failure), 7 (OCR accuracy)
**Research flag:** Standard patterns for CameraX + ML Kit. No research phase needed.

### Phase 3: On-Device RAG Pipeline
**Rationale:** RAG is the intelligence layer connecting OCR output to Gemma's context window. Token budget management must be built here — not patched later — to prevent context window overflow in Phase 4.
**Delivers:** EmbeddingEngine (gte-tiny via LiteRT Interpreter); pre-computed embedding index in HashMap; cosine similarity retrieval (<5 ms for 500 ingredients); ContextFilter applying pregnancy/jurisdiction/sensitizer boosts; PromptAssembler with hard token budget (≤950 total); full RAG pipeline tested end-to-end with mock LLM output
**Uses:** LiteRT core 1.0.1, gte-tiny embedding model in assets, Room FTS5 DAO
**Implements:** RAGPipeline, EmbeddingEngine, ContextFilter, PromptAssembler
**Avoids:** Pitfalls 5 (context window overflow), 9 (embedding latency)
**Research flag:** Standard patterns for cosine similarity and Room FTS5. No research phase needed.

### Phase 4: LiteRT Inference + Output Validation
**Rationale:** LiteRT-LM inference wires onto the tested RAG pipeline. Output validation is non-negotiable integrity infrastructure — citation hallucination in a demo is a judge-visible failure.
**Delivers:** LiteRTInferenceService producing streaming token Flow from Gemma 4 E2B; Gemma turn-marker prompt format verified; OutputValidator parsing JSON schema, stripping non-KB citations, applying confidence gate; unknown ingredient short-circuit returning UNKNOWN card; full scan pipeline tested end-to-end on real product label
**Uses:** LiteRT-LM (`litertlm-android`), LiteRT GPU delegate
**Implements:** LiteRTInferenceService, OutputValidator, AnalysisUseCase
**Avoids:** Pitfalls 2 (GPU threading reinforced), 11 (citation hallucination)
**Research flag:** LiteRT-LM API is well-documented via Context7. No research phase needed.

### Phase 5: Results UI + Full Pipeline Integration
**Rationale:** ViewModel and Compose UI wired to the tested pipeline. Streaming progress states and all judge-visible features land here.
**Delivers:** ScanViewModel with StateFlow; ResultScreen rendering 4-tier safety tags sorted by severity (Danger first); confidence border visual (solid/dashed/gray); citation display; health mechanism text; session context profile screen (manual dropdown as guaranteed fallback); on-device inference indicator + latency display; legal disclaimer persistent on results screen; local scan save
**Implements:** ScanViewModel, ResultScreen, ProfileScreen, ScanUiState sealed class
**Avoids:** Pitfall 3 (development on emulator — test on device), Pitfall 4 (ANR — enforce StrictMode)
**Research flag:** Standard Compose + ViewModel patterns. No research phase needed.

### Phase 6: Polish + Stretch Goals
**Rationale:** Hackathon submission polish and stretch goals that time permits. Face scan camera ML is explicitly a stretch goal with a pre-built manual fallback — it does not block submission.
**Delivers:** Model download WorkManager flow (if needed for demo device); regulatory comparison UI (EU/US/CN/JP) for Danger/Caution tags; "Data Valid As Of" footer; UX polish; camera-based skin profiling via ML Kit FaceDetector if Phase 5 completed ahead of schedule
**Avoids:** Pitfall 12 (face scan scope creep — explicitly contained here)
**Research flag:** WorkManager is well-documented. Face scan Fitzpatrick classification needs research if pursued — Fitzpatrick is not a supported MediaPipe task and requires a proxy approach.

### Phase Ordering Rationale

- The dependency chain is strict: OCR must work before RAG can be tested; RAG must produce valid prompts before LiteRT inference can be tuned; inference must produce validated output before UI can be wired. Skipping phases produces rework.
- The three most expensive-to-fix issues (APK size, GPU threading, KB scope) are all Day 1 concerns per PITFALLS.md — each has a 2–4 hour recovery cost if caught late.
- Knowledge base ingredient authoring can run as a parallel workstream during Phases 1–3; all 80 records must be complete before Phase 4 integration testing.
- Face scan is explicitly isolated to Phase 6 with a pre-built manual fallback — the project cannot be blocked by Fitzpatrick classification complexity.
- Phases 2, 3, and 4 each have explicit benchmarking gates (5+ real product labels) before proceeding. This is non-negotiable per PITFALLS.md findings.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 6 (face scan stretch goal):** Fitzpatrick scale via ML Kit is a proxy approach, not a supported task. If pursued, needs research into FaceLandmarker `.task` model size and usable classification approach before committing sprint time.

Phases with standard patterns (skip research-phase):
- **Phase 1:** Android project setup, Hilt DI, Room `createFromAsset()` — all well-documented official patterns
- **Phase 2:** CameraX ImageCapture + ML Kit Text Recognition — official docs + sample code available
- **Phase 3:** Cosine similarity RAG, gte-tiny, Room FTS5 — all standard patterns
- **Phase 4:** LiteRT-LM API verified via Context7; single-thread executor pattern is fully documented
- **Phase 5:** Compose + ViewModel + StateFlow — canonical Android architecture

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | LiteRT-LM API verified via Context7; most versions HIGH confidence; `litertlm-android` exact version must be pinned at build time |
| Features | MEDIUM | Competitor analysis from training data (August 2025 cutoff); core feature gaps are structural and stable; Think Dirty post-2023 changes LOW confidence |
| Architecture | HIGH | All major patterns verified against official LiteRT, Android, ML Kit, Room documentation; threading model confirmed from official GPU delegate docs |
| Pitfalls | HIGH (LiteRT/ML Kit/CameraX); MEDIUM (RAG/data curation) | GPU threading, context window, OCR resolution pitfalls sourced directly from official docs; knowledge base hour estimates are heuristic |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Exact `litertlm-android` version on MavenCentral:** `latest.release` is not reproducible. Must pin to exact version at first build. Check MavenCentral on Day 1.
- **Gemma 4 E2B `.litertlm` exact file size:** Confirmed ~1.5–2 GB range; exact size determines whether `adb push` during demo is feasible or requires pre-staging the night before. Verify in Phase 1.
- **gte-tiny latency on target demo device:** Research recommends gte-tiny; benchmark on actual demo hardware in Phase 3 to confirm <20 ms/call target holds.
- **ML Kit FaceDetector `.task` asset size:** Confirm it fits in APK assets alongside gte-tiny (~25 MB) if face scan is pursued in Phase 6. If combined assets exceed ~40 MB, evaluate tradeoffs.
- **sqlite-vec Android AAR availability post-April 2026:** Research recommends avoiding for hackathon; check if published for v1.x consideration after submission.

---

## Sources

### Primary (HIGH confidence)
- Context7 `/google-ai-edge/litert-lm` — LiteRT-LM Kotlin API: `Engine`, `EngineConfig`, `Conversation`, Flow streaming, single-thread executor requirement
- Official LiteRT GPU delegate docs — `GpuDelegate must run on the same thread where it was initialized`
- Official LiteRT `auto_complete` overview — "output sequence includes the prompt" (context window accounting)
- Official Android Room docs — `createFromAsset()` pre-populated database pattern
- Official ML Kit Text Recognition docs — ≥16×16 px per character minimum; rotation degrees requirement
- Official ML Kit CameraX integration — `ImageProxy` lifecycle, `InputImage.fromMediaImage()`

### Secondary (MEDIUM confidence)
- Training data: EWG Skin Deep, CosDNA, INCI Decoder feature sets — stable products, well-documented as of August 2025
- Training data: gte-tiny vs all-MiniLM-L6-v2 size and quality tradeoffs — community consensus
- Training data: Levenshtein fuzzy matching for INCI normalization — established NLP pattern

### Tertiary (LOW confidence)
- Training data: Think Dirty feature set post-2023 — company had product pivots; exact current state uncertain
- Training data: INCIBeauty feature set — limited English coverage in training data
- Heuristic: knowledge base curation hour estimates (40–80 hours for 500 ingredients) — based on similar editorial projects, not measured for INCI/cosmetics specifically

---

*Research completed: 2026-04-25*
*Ready for roadmap: yes*
