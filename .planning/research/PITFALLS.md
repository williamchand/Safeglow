# Pitfalls Research: SafeGlow Edge

**Domain:** Android + LiteRT + On-device RAG + Cosmetic Safety (7-day hackathon)
**Researched:** 2026-04-25
**Confidence:** HIGH for LiteRT/MediaPipe/ML Kit pitfalls (official docs); MEDIUM for RAG/data curation pitfalls

---

## Critical Pitfalls

### Pitfall 1: Gemma Model Bundled Inside APK

**What goes wrong:** Model (~1.5–2 GB) placed in `src/main/assets/`. APK exceeds 150 MB Play Store limit. APK fails to install or crashes on first-run extraction. Hackathon demo fails before it starts.

**Prevention:** Store model in `context.filesDir`, reference by absolute path in `EngineConfig(modelPath = ...)`. For hackathon demo, pre-load via `adb push /data/data/<pkg>/files/gemma4_e2b.litertlm`.

**Warning signs:** APK build takes unusually long; APK size >500 MB in APK Analyzer; Gradle warning about `noCompress "tflite"` unset.

**Phase to address:** Phase 1 (Day 1) — validate model loading from `filesDir` on physical device before any feature work.

---

### Pitfall 2: GPU Delegate Called on Wrong Thread (Silent Crash)

**What goes wrong:** `GpuDelegate` or `Engine` initialized on Thread A, invoked from `Dispatchers.IO` on Thread B. Crash: `TfLiteGpuDelegate Invoke: GpuDelegate must run on the same thread where it was initialized.`

Kotlin `Dispatchers.IO` does NOT guarantee thread affinity — pool threads rotate. This crash is intermittent and hard to reproduce.

**Prevention:**
```kotlin
val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
// Initialize AND invoke on inferenceDispatcher only. Never Dispatchers.IO.
```

**Warning signs:** Crashes only on GPU path; intermittent (thread pool rotation); `TfLiteGpuDelegate Invoke:` in Logcat.

**Phase to address:** Phase 1 (Day 1) — establish threading model before wiring UI.

---

### Pitfall 3: Development on Emulator — GPU Delegate Fails

**What goes wrong:** `CompatibilityList().isDelegateSupportedOnThisDevice` returns `false` on emulator. MediaPipe LLM Inference explicitly requires a physical Android device — no emulator support. CPU-only fallback makes inference 5–10× slower, blowing the 8s latency budget.

**Prevention:** Test exclusively on a physical device with ≥4 GB RAM from Day 1. Always implement GPU/CPU compatibility check explicitly.

**Warning signs:** `isDelegateSupportedOnThisDevice` = false; inference >30s; "GPU delegate not supported" in Logcat.

**Phase to address:** Phase 1 (Day 1) — single inference call on target hardware before any other work.

---

### Pitfall 4: Model Load on Main Thread (ANR)

**What goes wrong:** `Engine.initialize()` called in `onCreate` or `LaunchedEffect` without `withContext`. Synchronous CPU-heavy load freezes UI for 10–20 seconds, triggering ANR dialog.

**Prevention:** All model loading in `withContext(inferenceDispatcher)`. Show "Preparing safety engine..." loading state. Enable `StrictMode` in debug builds.

**Warning signs:** UI freezes after tapping "Analyze"; ANR dialog on slower devices; StrictMode violations in Logcat.

**Phase to address:** Phase 2 (inference + UI wiring) — enforce with StrictMode from the start.

---

### Pitfall 5: Context Window Overflow Silently Truncating RAG Context

**What goes wrong:** With default `setMaxTokens(1024)` (= total sequence: input + output), a 10-ingredient RAG prompt overflows. LiteRT silently truncates input from the left, losing the system prompt and safety instructions. Model produces hallucinated or unconstrained output including fabricated citations. LiteRT docs confirm: "the output sequence includes the prompt."

**Prevention:** Pre-count tokens with `~4 chars/token` heuristic. Hard budget: system prompt (~200 tokens) + retrieved context (max 400 tokens) + query (~50 tokens) + output (300 tokens) = ~950 total. Build token budget accounting into `PromptAssembler` from Day 1 — truncate retrieved chunks to fit, always preserve system prompt.

**Warning signs:** Output ignores safety instructions; output omits known-dangerous ingredients from retrieval; output language switches mid-response.

**Phase to address:** Phase 3 (RAG pipeline, Day 3) — build into `PromptAssembler` from the first iteration.

---

### Pitfall 6: INCI Synonym Resolution Failures (Silent Health Safety Miss)

**What goes wrong:** OCR returns "Retinol Palmitate" → KB keyed on "RETINYL PALMITATE" → no lookup hit → app silently emits "Explain" (safe) tag for a genuinely risky ingredient. This is a health safety failure, not just a UI bug.

**Prevention:** Synonym map covering: (1) brand/trade names → INCI, (2) uppercase normalization, (3) common OCR errors (CI 77891 vs CI77891). Apply Levenshtein ≤2 fuzzy fallback. Mark unresolved ingredients explicitly as "UNKNOWN — could not verify safety" rather than passing silently as safe.

**Warning signs:** "titanium dioxide" returns no results (correct INCI: TITANIUM DIOXIDE); common parabens not recognized from real label scans; lookup hit rate <90% on 20 real product labels.

**Phase to address:** Phase 2 (knowledge base, Days 1–2) — validate synonym coverage against 5 real product labels before building inference.

---

### Pitfall 7: OCR Accuracy Failure on Cosmetic Labels

**What goes wrong:** ML Kit Text Recognition returns garbled text for INCI lists printed at 6–8pt on curved bottle surfaces. ML Kit official docs require ≥16×16 pixels per character; standard camera distance gives 8–12 px/char on a curved bottle. Characters I/l/1, O/0, RN/M commonly confused. "METHYLISOTHIAZOLINONE" → "METHYLISOTHIAZ0LINONE" (zero instead of O) → lookup breaks.

**Prevention:** Force `ImageCapture` to maximum available resolution (not default `ImageAnalysis` 640×480). Apply pre-processing: grayscale + adaptive thresholding + contrast enhancement. Guide users to photograph the back panel flat. Post-process OCR with dictionary-backed spell corrector using the INCI synonym map.

**Warning signs:** OCR on a real product returns <5 tokens when label has 15+; recognized tokens contain digits in place of letters; test accuracy <80% on 5 real product photos.

**Phase to address:** Phase 2 (CameraX + OCR pipeline, Day 2) — benchmark on 10 real product labels before wiring to RAG.

---

### Pitfall 8: Context Leak from Activity in Inference Singleton

**What goes wrong:** `Engine.initialize(context, ...)` called with `Activity` context stored in `ViewModel`. Screen rotation destroys Activity but ViewModel holds reference → memory leak. Model may re-initialize on every rotation → 10–20s delay between scans.

**Prevention:** Always pass `context.applicationContext`. Initialize model once in `@Singleton` Hilt component. Call `engine.close()` in `ViewModel.onCleared()`.

**Warning signs:** LeakCanary reports Activity leak; Logcat shows model re-initialized on rotation; Memory Profiler shows Activity instances not GC'd.

**Phase to address:** Phase 1 (Day 1) — enforce `applicationContext` in `LiteRTInferenceService` from the start.

---

### Pitfall 9: Embedding Latency Blowing the 8-Second Budget

**What goes wrong:** `all-MiniLM-L6-v2` (~22 MB TFLite) adds 200–400 ms per embedding call. With 10 ingredient tokens needing embedding at query time = 2–4 seconds in embedding alone, before LLM inference even starts.

**Prevention:** Pre-compute all ingredient embeddings at knowledge-base build time. Store in `embedding_index.json`. At query time, serve from `HashMap<String, FloatArray>` (~1–4 MB in memory). Use `gte-tiny` (~4 MB, ~20 ms/call) not `all-MiniLM-L6-v2`. EmbeddingEngine is invoked only for unresolved OCR tokens (~5% of queries).

**Warning signs:** End-to-end latency >8s on Pixel 6; profile shows >50% time in embedding calls; embedding model .tflite >10 MB.

**Phase to address:** Phase 3 (RAG pipeline, Day 3) — measure embedding latency independently before full pipeline integration.

---

### Pitfall 10: Knowledge Base Data Curation Consuming All 7 Days

**What goes wrong:** Parsing EU CosIng Annex II–VI PDFs + CIR reports + writing per-ingredient health summaries with citations for 500 ingredients = 40–80 hours of editorial work. Day 4 arrives with 20% coverage. App never ships.

**Prevention:** Hard scope to **80 ingredients** for hackathon. Choose the 80 most-scanned cosmetic ingredients (parabens, retinoids, sunscreen filters, fragrances, preservatives — covers >90% of real products). Lock JSON schema on Day 1. Write 10 complete records as template. Draft remaining 70 from official PDF excerpts; human review of Caution/Danger tags only. Validate against 5 real product labels before Day 3.

**Warning signs:** Day 2 ends with <30 ingredients in KB with citations; team still debating schema on Day 3; no real product tested end-to-end by Day 3.

**Phase to address:** Phase 1 (Day 1) — lock schema and seed 10 records; hard cap of 80 by end of Day 2.

---

### Pitfall 11: Citation Hallucination from Sparse Knowledge Base

**What goes wrong:** Unknown ingredient → Gemma generates "According to SCCS/1234/17, this ingredient is safe at ≤0.5%." Citation number fabricated. Health safety failure + legal liability. Even small 2B-parameter LLMs pattern-match citation formats from training data when retrieval context is sparse.

**Prevention:** `OutputValidator` strips all citations not matching a known `citation_id` from retrieved KB records. For ingredients with no KB entry, short-circuit before inference and return "UNKNOWN — not in safety database" card. Never call LLM for unrecognized ingredients.

**Warning signs:** Manual review finds citation numbers not in retrieved context; output contains "SCCS" or "CIR" references when retrieval had no such entries; judges ask to verify a citation and it can't be found.

**Phase to address:** Phase 3–4 (Days 3–4) — citation validation is a first-class concern, not an afterthought.

---

### Pitfall 12: Face Scan Consuming Hackathon Time Budget

**What goes wrong:** Fitzpatrick scale classification is not a supported MediaPipe task — it requires a custom model or proxy approach. Face scan consumes Days 4–5, leaving insufficient time to polish core ingredient analysis. Submission has two half-built features.

**Prevention:** Implement skin concern as a **manual user selection** (dropdown: Normal / Dry / Sensitive / Acne-prone / Pregnant) for the hackathon. Delivers identical RAG context-filtering value with zero ML complexity. Camera-based skin analysis is a stretch goal only if Days 1–4 complete ahead of schedule.

**Warning signs:** Day 3 arrives and face scan not returning reliable Fitzpatrick predictions; core ingredient analysis not tested end-to-end; time spent on face scan UI before ingredient analysis works.

**Phase to address:** Phase 1 (Day 1 scope lock) — manual selector implemented first; camera ML deferred explicitly.

---

## Technical Debt Shortcuts (Hackathon-Acceptable)

| Shortcut | Immediate Benefit | Long-term Cost | Acceptable? |
|----------|-------------------|----------------|------------|
| Hardcoded model path in `filesDir` | No download manager | Breaks on fresh device | Hackathon only — mark TODO |
| 4 chars/token heuristic for budget | No tokenizer dependency | ±20–30% error for chemical names | Yes, with conservative buffer |
| 80 ingredients vs 500 | Shippable in 2 days | Misses uncommon ingredients | Yes — depth beats breadth |
| Manual skin selector vs face scan | No extra ML model | Weaker UX | Yes — call it explicit |
| In-memory cosine scan vs sqlite-vec | No JNI complexity | O(n) at >800 ingredients | Yes for ≤500 ingredients |
| Skip GPU compat fallback test | Faster dev setup | Crashes on demo device if unsupported | **Never** — test Day 1 |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| LiteRT GPU delegate | Create on background thread, invoke on different thread | `newSingleThreadExecutor` for all inference |
| ML Kit CameraX | Default 640×480 for OCR | `ImageCapture` at max resolution; tap-to-capture |
| ML Kit `InputImage` | Omit `rotationDegrees` | Always pass `imageProxy.imageInfo.rotationDegrees` |
| LiteRT `.tflite` in assets | `aaptOptions { noCompress }` unset | Required — memory-mapped loading fails without it |
| `Engine.close()` | Never called, holding GPU memory | Call in `ViewModel.onCleared()` + app termination |
| Gemma prompt format | Raw text without turn markers | `<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model` |

---

## Performance Traps

| Trap | Symptoms | Prevention |
|------|----------|------------|
| Re-initializing `Engine` on every scan | 10–20s between scans | `@Singleton` — initialize once |
| Embedding all OCR tokens including stopwords | 2–4s latency | Filter tokens <4 chars; skip numbers, percentages |
| CameraX `ImageAnalysis` at 30fps for OCR | CPU saturated | Single `ImageCapture` tap-to-capture |
| No pre-computed embedding index | Every query hits `EmbeddingEngine` | Pre-compute all 80–500 embeddings at KB build time |

---

## "Looks Done But Isn't" Checklist

- [ ] GPU delegate fallback tested on device where `isDelegateSupportedOnThisDevice` = false
- [ ] INCI synonym resolution: ≥90% hit rate on 5 real product labels
- [ ] Token budget: 15-ingredient product tested — system prompt still present in output
- [ ] Citation validation: unknown ingredient sent — zero fabricated citations in output
- [ ] Context leak: screen rotated during inference — model not re-initialized
- [ ] OCR accuracy: real curved surface under indoor lighting — ≥80% INCI token recovery
- [ ] KB coverage: 5 real products analyzed — ≥70% ingredient recognition
- [ ] Disclaimer visible on all result screens without scrolling

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| APK too large | HIGH (2–4h) | Move model to `filesDir`, add first-run copy flow |
| GPU thread violation crash | MEDIUM (1–2h) | Wrap in `newSingleThreadExecutor`, propagate dispatcher |
| Context window overflow | MEDIUM (2–3h) | Add token counting + truncation to `PromptAssembler` |
| KB <50 ingredients at Day 3 | HIGH (scope change) | Hard-pivot to 50 ingredients; drop semantic embedding, FTS5 only |
| OCR accuracy <60% | HIGH (feature re-scope) | Add manual ingredient entry fallback |
| Citation hallucination in demo | MEDIUM (1–2h) | Add post-processing citation strip regex; short-circuit unknown ingredients |
| Face scan not working | LOW (descope) | Replace with manual selector; ship as v2 feature |

---

## Sources

- LiteRT GPU delegate threading: official LiteRT GPU docs — `GpuDelegate must run on the same thread where it was initialized` (HIGH)
- LiteRT context window: official auto_complete overview — "output sequence includes the prompt" (HIGH)
- LiteRT Gradle noCompress: official metadata/codegen docs (HIGH)
- MediaPipe LLM Inference README: physical device only; `filesDir` absolute path required (HIGH)
- ML Kit Text Recognition: ≥16×16 px per character minimum; image quality directly affects accuracy (HIGH)
- ML Kit CameraX: rotation degrees from `ImageProxy.getImageInfo()` required (HIGH)

---
*Confidence: HIGH for LiteRT/MediaPipe/ML Kit pitfalls; MEDIUM for RAG/data curation estimates*
