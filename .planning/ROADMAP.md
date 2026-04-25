# SafeGlow Edge — Roadmap

**Milestone:** v1 Hackathon Submission
**Build window:** 2026-04-25 to 2026-05-02 (7-day sprint)
**Granularity:** Standard
**Requirements coverage:** 25/25 v1 requirements mapped

---

## Phases

- [ ] **Phase 1: Foundation + Model Validation** — Project scaffold, LiteRTInferenceService with GPU/CPU fallback, model loads from filesDir on physical device, Room with seed data, Hilt wired, JSON schema locked
- [ ] **Phase 2: Camera + OCR + Session Profile** — CameraX tap-to-capture, ML Kit OCR pipeline, INCI normalization (uppercase + synonym + Levenshtein), manual session context selector, 80-ingredient knowledge base complete
- [ ] **Phase 3: Knowledge Base + RAG Pipeline** — EmbeddingEngine (gte-tiny), cosine retrieval, ContextFilter, PromptAssembler with hard token budget, full RAG pipeline testable end-to-end with mock LLM
- [ ] **Phase 4: LiteRT Inference + Output Validation** — Gemma 4 E2B wired to RAG pipeline, JSON schema validation, citation strip, UNKNOWN card short-circuit, full scan pipeline verified on real product label
- [ ] **Phase 5: Results UI + Full Pipeline Integration** — ScanViewModel, ResultScreen (severity sort, confidence borders, citations, health mechanisms), ProfileScreen, streaming progress, disclaimer, local save, on-device inference indicator
- [ ] **Phase 6: Regulatory Comparison + Polish** — "Why Restricted?" cards, jurisdiction toggle (EU/US/CN/JP), data version footer, Wi-Fi KB update bundle, hackathon demo prep

---

## Phase Details

### Phase 1: Foundation + Model Validation
**Goal**: Gemma 4 E2B loads from filesDir and produces inference output on physical device hardware with correct GPU threading — all fatal-to-recover pitfalls validated before any feature work begins
**Depends on**: Nothing (first phase)
**Requirements**: PRIV-01, PRIV-02, PRIV-03
**Success Criteria** (what must be TRUE):
  1. App builds and runs on a physical Android device without exceeding APK size limits (model stored in filesDir, not bundled in APK)
  2. LiteRTInferenceService initializes Gemma 4 E2B exactly once using a newSingleThreadExecutor dispatcher and produces a token output for a test prompt without crashing
  3. GPU delegate initializes and falls back to CPU without throwing an exception when GPU is unavailable
  4. Hilt dependency graph resolves without circular dependency errors and LiteRTInferenceService survives a screen rotation without re-initializing the model
  5. Room database opens via createFromAsset() with 10 seed ingredient records readable via DAO; knowledge base JSON schema is locked and documented
**Plans**: TBD

### Phase 2: Camera + OCR + Session Profile
**Goal**: User can photograph a real cosmetic product label and receive a normalized list of INCI ingredient tokens, and can set session context — OCR accuracy is benchmarked on physical labels before RAG is built on top of it
**Depends on**: Phase 1
**Requirements**: SCAN-01, SCAN-02, SCAN-03, PROF-01, PROF-03
**Success Criteria** (what must be TRUE):
  1. User can tap a capture button in CameraX preview and receive an extracted list of text tokens from the photographed label within 3 seconds
  2. INCI normalization converts OCR output to canonical uppercase INCI names (synonym resolution and Levenshtein fuzzy matching) with ≥80% token recovery verified against 5 real product labels
  3. User can manually type or paste an ingredient list when OCR output is unacceptable — the manual input path produces the same normalized token list as the OCR path
  4. User can set pregnancy status, country, and skin concern type from a dropdown selector without creating an account; closing the app clears all session values
  5. All 80 priority knowledge base ingredients have full health metadata and authoritative citation records committed to the database asset
**Plans**: TBD
**UI hint**: yes

### Phase 3: Knowledge Base + RAG Pipeline
**Goal**: A normalized INCI ingredient list produces a context-filtered, token-budgeted prompt ready for Gemma inference — retrieval latency and prompt integrity verified before wiring the real LLM
**Depends on**: Phase 2
**Requirements**: PROF-02, DATA-01
**Success Criteria** (what must be TRUE):
  1. Exact INCI match via Room FTS5 returns a matching ingredient record in under 10 ms for any of the 80 knowledge base ingredients
  2. Cosine similarity over the pre-computed embedding HashMap returns the top-k most relevant ingredient records in under 5 ms for a 20-ingredient input list
  3. ContextFilter correctly boosts pregnancy-risk records when session context is "pregnant", jurisdiction records for the selected country, and sensitizer records when skin concern is "sensitive"
  4. PromptAssembler produces a prompt that fits within 950 total tokens (system ~200 + context ≤400 + query ~50 + output 300) verified with a token counter before any real inference call
  5. Full RAG pipeline (INCI input → retrieval → context filter → prompt assembly) produces a valid mock-LLM-ready prompt for a 10-ingredient test list with no token budget overflow
**Plans**: TBD

### Phase 4: LiteRT Inference + Output Validation
**Goal**: The full scan pipeline from label photo to validated JSON safety output runs end-to-end on a real product label — citation integrity and UNKNOWN handling verified before any UI is built on top of the output
**Depends on**: Phase 3
**Requirements**: SCAN-04, SAFE-01, SAFE-02, SAFE-03, SAFE-04, COMP-01
**Success Criteria** (what must be TRUE):
  1. LiteRTInferenceService produces a streaming token Flow from Gemma 4 E2B for a RAG-assembled prompt; the turn-marker format is verified to produce valid JSON output
  2. OutputValidator parses the Gemma JSON response against the IngredientTag schema and rejects any response that does not conform — malformed output surfaces as an error state, not a silent incorrect tag
  3. Any citation ID in the Gemma output that does not match a record in the knowledge base is stripped; the tag is still displayed with remaining valid citations or as citation-free if none remain
  4. Any ingredient that cannot be resolved by the knowledge base returns an explicit "UNKNOWN — could not verify safety" card and does not receive an AI-generated safety classification
  5. A full scan of a real cosmetic product label (OCR → INCI normalization → RAG → inference → validation) completes end-to-end in under 8 seconds on reference hardware with correct Explain/Caution/Solve/Danger tags for known ingredients
**Plans**: TBD

### Phase 5: Results UI + Full Pipeline Integration
**Goal**: Users can view a complete, judge-demonstrable results screen showing sorted safety tags with confidence indicators, health mechanisms, citations, and session profile — every judge-visible UI feature is present and wired to real pipeline output
**Depends on**: Phase 4
**Requirements**: SAFE-05, SAFE-06, COMP-02, PRIV-04, UI-01, UI-02
**Success Criteria** (what must be TRUE):
  1. ResultScreen renders ingredients sorted Danger → Caution → Solve → Explain, each with the correct confidence border style (solid ≥0.85, dashed 0.60–0.84, grayed <0.60) driven by real OutputValidator confidence values
  2. Each Caution and Danger tag expands to show health mechanism text, affected population, dose threshold, and at least one citation from the knowledge base — all text is sourced from pre-curated records, never LLM-generated strings
  3. Analysis progress is visible to the user as distinct stage labels (OCR complete → retrieving → AI analyzing → results ready) during the full pipeline execution
  4. The legal disclaimer "Educational only — not medical or regulatory advice. Consult a licensed professional." is persistently visible on every results screen and cannot be dismissed
  5. User can save a scan result locally; the saved result is retrievable from within the app; no data is transmitted to any external server during save or retrieval
**Plans**: TBD
**UI hint**: yes

### Phase 6: Regulatory Comparison + Polish
**Goal**: The app is submission-ready — "Why Restricted?" regulatory detail cards, jurisdiction comparison toggle, data version footer, and optional KB update are complete; hackathon demo is rehearsed and reproducible
**Depends on**: Phase 5
**Requirements**: COMP-03, DATA-02, DATA-03
**Success Criteria** (what must be TRUE):
  1. User can expand a "Why Restricted?" card on any Danger or Caution tag to see official scientific rationale with source citation (verbatim from SCCS/CIR/ACOG/IARC — no LLM-generated regulatory text)
  2. User can toggle a jurisdiction comparison view for any ingredient and see regulatory status for all four jurisdictions (EU, US, China, Japan) side by side
  3. Every results screen displays a "Data Valid As Of [date]" footer showing the knowledge base version date
  4. User can initiate a Wi-Fi-only knowledge base bundle download from within the app; the download is blocked on mobile data; the updated bundle replaces the previous knowledge base without requiring an app update
**Plans**: TBD
**UI hint**: yes

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation + Model Validation | 0/? | Not started | - |
| 2. Camera + OCR + Session Profile | 0/? | Not started | - |
| 3. Knowledge Base + RAG Pipeline | 0/? | Not started | - |
| 4. LiteRT Inference + Output Validation | 0/? | Not started | - |
| 5. Results UI + Full Pipeline Integration | 0/? | Not started | - |
| 6. Regulatory Comparison + Polish | 0/? | Not started | - |

---

*Roadmap created: 2026-04-25*
*Last updated: 2026-04-25 after initial creation*
