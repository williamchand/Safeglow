---
phase: 02-camera-ocr-session-profile
plan: "01"
verified: 2026-05-09T00:00:00Z
status: gaps_found
score: 3/5
overrides_applied: 0
gaps:
  - truth: "User can manually type or paste an ingredient list — the manual input path produces the same normalized token list as the OCR path (SCAN-02)"
    status: failed
    reason: "No manual ingredient text-entry UI exists anywhere in the codebase. ProfileScreen only has read-only dropdown selectors for session context. INCINormalizer.normalize() is never called from any screen or ViewModel — it exists as an isolated class with no call sites. The plan success criteria claimed 'Manual input path implemented via ProfileScreen/Compose TextField wiring to INCINormalizer' but neither the TextField for free-form ingredient input nor the INCINormalizer wiring is present."
    artifacts:
      - path: "app/src/main/kotlin/com/safeglow/edge/session/ProfileScreen.kt"
        issue: "Only contains read-only dropdown selectors; no TextField for ingredient list entry; no call to INCINormalizer.normalize()"
      - path: "app/src/main/kotlin/com/safeglow/edge/camera/CameraViewModel.kt"
        issue: "captureAndProcess() returns raw OCR tokens — INCINormalizer is not injected or called; normalization pipeline is not wired to the camera path"
    missing:
      - "A ManualInputScreen (or equivalent) with a multiline TextField for ingredient list entry"
      - "Wiring from the manual input (or CameraScreen success state) through INCINormalizer.normalize() before returning tokens to the UI"
      - "INCINormalizer injection into CameraViewModel or a dedicated ViewModel that wires OCR output through the normalizer"

  - truth: "All 80 priority knowledge base ingredients have full health metadata and authoritative citation records committed to the database asset (DATA-01)"
    status: failed
    reason: "The committed knowledge_base.db asset contains exactly 10 rows, not 80. The 70 remaining INSERT statements in tools/build_seed_db.sh are placeholder TODO comments with no real data. This is explicitly documented as an 'operator data-authoring task' but the roadmap and REQUIREMENTS.md (Phase 2 traceability row) require the 80-ingredient asset to be committed before Phase 3 begins. Additionally, the script's verification step at line 208 queries 'FROM ingredient' (wrong table name) instead of 'FROM ingredients', which will always fail with a SQL error when the operator runs it."
    artifacts:
      - path: "app/src/main/assets/knowledge_base.db"
        issue: "Contains 10 rows; 70 rows missing — does not meet the 80-ingredient requirement"
      - path: "tools/build_seed_db.sh"
        issue: "Line 208: 'SELECT COUNT(*) FROM ingredient;' references non-existent table. Correct table name is 'ingredients'. This bug silently breaks the DATA-01 gate verification every time the script is run."
    missing:
      - "70 real INSERT statements replacing the TODO placeholders in tools/build_seed_db.sh"
      - "A rebuilt knowledge_base.db asset with 80 rows"
      - "Fix line 208: change 'FROM ingredient' to 'FROM ingredients'"

human_verification:
  - test: "SCAN-01: Tap 'Scan Label' and measure OCR round-trip time"
    expected: "Extracted ingredient token list appears within 3 seconds of button tap on a real cosmetic product label"
    why_human: "CameraX + ML Kit runtime requires a physical Android device; timing cannot be measured from static analysis"
  - test: "SCAN-03 / SCAN-01 accuracy: benchmark normalization on 5 real product labels"
    expected: ">=80% of INCI tokens from each label are resolved to canonical KB names or 'UNRESOLVED' (never silently dropped)"
    why_human: "Requires photographing 5 real product labels and comparing OCR + normalizer output to ground truth; no automated equivalent exists"
  - test: "PROF-03: Session values cleared on app close"
    expected: "After setting pregnancy status / country / skin concern and force-closing the app, all three values are reset to NOT_SET on relaunch"
    why_human: "Requires a running device to verify Android ViewModel lifecycle / process death behaviour"
  - test: "CAMERA permission gate: launch app without camera permission"
    expected: "CameraScreen displays 'Grant camera permission' button and does not crash; granting permission shows live camera preview"
    why_human: "Requires device interaction with Android permission dialog"
---

# Phase 02 Plan 01: Camera + OCR + Session Profile — Verification Report

**Phase Goal:** User can photograph a real cosmetic product label and receive a normalized list of INCI ingredient tokens, and can set session context — OCR accuracy is benchmarked on physical labels before RAG is built on top of it
**Verified:** 2026-05-09T00:00:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can tap a capture button in CameraX preview and receive an extracted list of text tokens from the photographed label within 3 seconds | ? HUMAN NEEDED | CameraScreen, CameraViewModel, OcrRepository all exist and are fully wired (CameraScreen → captureAndProcess() → extractRawTokens()); 3-second SLA requires device timing |
| 2 | INCI normalization converts OCR output to canonical uppercase INCI names with ≥80% token recovery verified against 5 real product labels | ? HUMAN NEEDED | INCINormalizer exists with all 4 stages implemented; ≥80% token recovery benchmark requires physical label testing |
| 3 | User can manually type or paste an ingredient list when OCR output is unacceptable — the manual input path produces the same normalized token list as the OCR path | FAILED | No manual ingredient text-entry UI exists; INCINormalizer.normalize() has zero call sites in the codebase |
| 4 | User can set pregnancy status, country, and skin concern type from a dropdown selector without creating an account; closing the app clears all session values | VERIFIED | ProfileScreen has 3 ExposedDropdownMenuBox selectors wired to SessionViewModel setters; SessionViewModel uses plain MutableStateFlow with no persistence (no Room/SharedPreferences/SavedStateHandle) |
| 5 | All 80 priority knowledge base ingredients have full health metadata and authoritative citation records committed to the database asset | FAILED | knowledge_base.db contains 10 rows (not 80); 70 INSERT placeholders are TODO comments in build_seed_db.sh; script's verification step has table name bug on line 208 |

**Score:** 1 fully verified (SC4) + 2 human-pending (SC1, SC2) + 2 failed (SC3, SC5) = **1/5 truths fully verified**
*(PLAN frontmatter must-haves score: 3/5 — camera/OCR/session verified; manual-entry and DATA-01 gap)*

---

### Plan Frontmatter Must-Haves Verification

#### Truths

| # | Must-Have Truth | Status | Evidence |
|---|----------------|--------|----------|
| 1 | User can tap 'Scan Label' and receive raw OCR tokens within 3 seconds (SCAN-01) | ? HUMAN | Pipeline wired; timing requires device |
| 2 | User can paste or type an ingredient list — same normalization pipeline produces canonical INCI tokens (SCAN-02) | FAILED | No manual text-entry UI; INCINormalizer.normalize() unwired |
| 3 | Normalization pipeline (uppercase → synonym → exact FTS → Levenshtein ≤2) resolves tokens to KB names or returns UNRESOLVED (SCAN-03) | VERIFIED | INCINormalizer.kt implements all 4 stages; IngredientDao.findExact + ftsSearch wired at lines 73–77; Levenshtein fallback at line 81–85; returns "UNRESOLVED" |
| 4 | Session profile settable via ProfileScreen and cleared on app process exit (PROF-01, PROF-03) | VERIFIED | ProfileScreen + SessionViewModel wired; no persistence APIs used |
| 5 | KB expanded to 80 ingredients, accessible via IngredientDao.getAll()/findExact()/ftsSearch (DATA-01) | FAILED | DB has 10 rows; 70 TODO placeholders; verification script has bug at line 208 |

---

### Required Artifacts

| Artifact | Min Lines | Status | Details |
|----------|-----------|--------|---------|
| `app/src/main/kotlin/com/safeglow/edge/camera/CameraScreen.kt` | 40 | VERIFIED | 140 lines; AndroidView+PreviewView wired; Scan Label button triggers captureAndProcess() |
| `app/src/main/kotlin/com/safeglow/edge/ocr/OcrRepository.kt` | — | VERIFIED | extractRawTokens() implemented; ML Kit TextRecognition.getClient(DEFAULT_OPTIONS); image.close() in addOnCompleteListener (T-2-02 mitigated) |
| `app/src/main/kotlin/com/safeglow/edge/normalization/INCINormalizer.kt` | — | ORPHANED | normalize() implements all 4 stages correctly; but has zero call sites in the codebase — never invoked by any ViewModel, screen, or test |
| `app/src/main/kotlin/com/safeglow/edge/session/SessionViewModel.kt` | — | VERIFIED | @HiltViewModel; StateFlow<SessionProfile>; setPregnancyStatus/setCountry/setSkinConcern setters; no persistence |
| `tools/build_seed_db.sh` | — | PARTIAL | Script structure correct with 10 authored rows and 70 TODO placeholders; contains verification step but line 208 uses wrong table name ("ingredient" instead of "ingredients") |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `CameraScreen.kt` | `OcrRepository.kt` | `viewModel.captureAndProcess() → ocrRepository.extractRawTokens(capture)` | WIRED | CameraScreen.kt:95 onClick → CameraViewModel.kt:93 extractRawTokens call |
| `INCINormalizer.kt` | `knowledge_base.db` | `IngredientDao.findExact()/ftsSearch()/getAll()` | WIRED (internal) | INCINormalizer lines 55, 73, 76 call IngredientDao; but INCINormalizer itself is never called from outside the normalization package |
| Manual input UI | `INCINormalizer.normalize()` | TextField → ViewModel → normalize() | NOT WIRED | No manual text-entry UI; INCINormalizer has zero external call sites |
| `CameraViewModel` | `INCINormalizer` | captureAndProcess() → normalize(tokens) | NOT WIRED | CameraViewModel returns raw OCR tokens without normalizing them |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `CameraScreen.kt` | `uiState: CameraUiState` | `viewModel.captureAndProcess()` → `ocrRepository.extractRawTokens()` → ML Kit | Yes (real ML Kit inference) | FLOWING — raw tokens |
| `INCINormalizer.kt` | `normalizedTokens: List<String>` | `ingredientDao.getAll()/findExact()/ftsSearch()` | Yes (Room queries) | DISCONNECTED — never called from any screen/ViewModel |
| `ProfileScreen.kt` | `profile: SessionProfile` | `viewModel.profile` StateFlow | Yes (in-memory state) | FLOWING — dropdown selections |

---

### Requirements Coverage

| Requirement | Source | Description | Status | Evidence |
|-------------|--------|-------------|--------|----------|
| SCAN-01 | PLAN frontmatter + ROADMAP Phase 2 | Camera capture → OCR token list | PARTIAL (human needed) | Code path wired; 3s SLA unverified without device |
| SCAN-02 | PLAN frontmatter + ROADMAP Phase 2 | Manual ingredient list entry path | FAILED | No UI; INCINormalizer unwired |
| SCAN-03 | PLAN frontmatter + ROADMAP Phase 2 | Normalization pipeline: uppercase→synonym→FTS→Levenshtein | VERIFIED (code) | INCINormalizer implements all 4 stages; returns "UNRESOLVED" |
| PROF-01 | PLAN frontmatter + ROADMAP Phase 2 | Session profile settable without account | VERIFIED | ProfileScreen 3-dropdown UI + SessionViewModel |
| PROF-03 | PLAN frontmatter + ROADMAP Phase 2 | Session cleared on app close | VERIFIED (code) + HUMAN for runtime | MutableStateFlow only; no persistence APIs found |
| DATA-01 | PLAN must_haves + REQUIREMENTS.md Phase 2 | 80-ingredient KB with full metadata | FAILED | DB has 10/80 rows; 70 TODO placeholders |

**Orphaned requirements check:** REQUIREMENTS.md maps SCAN-01, SCAN-02, SCAN-03, PROF-01, PROF-03 to Phase 2. All five appear in the PLAN frontmatter. No orphaned requirements.

**Note on DATA-01:** REQUIREMENTS.md traceability assigns DATA-01 to Phase 2. ROADMAP.md Phase 3 also lists DATA-01 as a requirement. The Phase 2 PLAN explicitly includes DATA-01 as a must-have truth. It is treated as in-scope for this verification.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `tools/build_seed_db.sh` | 208 | `FROM ingredient` (wrong table name — table is `ingredients`) | Blocker | DATA-01 verification gate will always fail with SQL error when operator runs the script; the gate cannot confirm 80 rows |
| `normalization/INCISynonymMap.kt` | 62 | `// TODO: add remaining ~40 synonym entries` | Warning | Only 20 of ~60 needed synonym entries authored; does not block SCAN-03 compile gate but reduces normalization accuracy for aliased ingredient names |
| `tools/build_seed_db.sh` | 101–190 | 70 `-- TODO [category-N]: INSERT for ...` blocks | Warning (by design) | DB stays at 10 rows until operator fills placeholders; plan documented this as a human data-authoring task, but roadmap SC5 requires 80 rows committed before Phase 3 |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| DB asset has 10 rows | `sqlite3 app/src/main/assets/knowledge_base.db "SELECT COUNT(*) FROM ingredients;"` | `10` | FAIL (expected 80) |
| DB table exists with correct name | `sqlite3 ... ".tables"` | `ingredients`, `ingredients_fts`, `room_master_table` | PASS |
| INCINormalizer has zero external call sites | `grep -rn "normalize\|INCINormalizer" app/src/main/kotlin/ | grep -v normalization/` | 0 external calls found | FAIL (normalization pipeline is orphaned) |
| CameraViewModel wires to OcrRepository | `grep -n "extractRawTokens" app/.../camera/CameraViewModel.kt` | line 93: `ocrRepository.extractRawTokens(capture)` | PASS |
| SessionViewModel uses no persistence | `grep -rn "SharedPreferences\|DataStore\|Room" app/.../session/` | 0 matches | PASS |
| build_seed_db.sh verification table name | `grep "FROM ingredient" tools/build_seed_db.sh` | line 208: `FROM ingredient` (wrong) | FAIL |

---

### Human Verification Required

#### 1. OCR Round-Trip Timing (SCAN-01 SLA)
**Test:** On a physical Android device, tap "Scan Label" while pointing the camera at a real cosmetic product label with visible INCI ingredients.
**Expected:** A non-empty token list appears in the UI within 3 seconds of the tap.
**Why human:** CameraX ImageCapture + ML Kit TextRecognition runtime requires a physical device; static analysis cannot measure timing.

#### 2. Normalization Accuracy Benchmark (SCAN-03 gate)
**Test:** Run the OCR → normalize pipeline on 5 different real cosmetic product labels. Compare the normalizer output against the ground-truth INCI list printed on each label.
**Expected:** ≥80% of INCI tokens per label are resolved to canonical KB names or "UNRESOLVED" (not silently dropped).
**Why human:** Benchmark requires photographing real products and manual ground-truth comparison; no automated equivalent.

#### 3. Session Cleared on App Close (PROF-03 runtime)
**Test:** Open ProfileScreen, set pregnancy status to "Pregnant / Breastfeeding", set country to "EU", set skin concern to "Sensitive". Force-close the app. Reopen it.
**Expected:** All three fields are reset to "Not specified" — no values persist across process death.
**Why human:** Android process death behaviour must be verified at runtime; SessionViewModel lifecycle cannot be confirmed from static analysis alone.

#### 4. Camera Permission Gate (SCAN-01 precondition)
**Test:** Launch the app on a fresh install (no camera permission granted). Navigate to CameraScreen.
**Expected:** Screen shows permission rationale text and "Grant Camera Permission" button without crashing. After granting permission, live camera preview appears.
**Why human:** Accompanist permission state machine and Android runtime permission flow require device interaction.

---

### Gaps Summary

Two gaps block the Phase 2 goal:

**Gap 1 — SCAN-02 / Normalization Unwired:**
The manual ingredient entry path (SCAN-02) was not built. No free-form text input screen exists. More critically, INCINormalizer.normalize() is never invoked anywhere in the codebase — the entire normalization pipeline is unreachable from the camera capture path or any UI. The camera pipeline returns raw OCR tokens directly to the UI without normalization. This means SCAN-03 (normalization pipeline) is code-complete but functionally dead — it cannot be exercised by any user action. Both the manual entry UI and the wiring of INCINormalizer to the camera path (and/or a future manual path) are missing.

**Gap 2 — DATA-01 / 80-Ingredient KB Not Delivered:**
The knowledge_base.db asset committed to the repository contains 10 rows. The roadmap and REQUIREMENTS.md traceability both assign DATA-01 (80-ingredient KB) to Phase 2. The 70 remaining INSERT statements are placeholder TODOs in build_seed_db.sh awaiting operator data authoring. An additional bug on line 208 of build_seed_db.sh (`FROM ingredient` instead of `FROM ingredients`) will cause the DATA-01 verification gate to error out silently rather than confirming the row count.

**Root cause grouping:** Gap 1 has two sub-concerns (missing manual UI + missing normalization wiring) that share the same fix point — a ViewModel that wires OCR output and/or manual input through INCINormalizer. Gap 2 is a data-authoring task plus a one-line script fix.

---

_Verified: 2026-05-09T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
