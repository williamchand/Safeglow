# Requirements: SafeGlow Edge

**Defined:** 2026-04-25
**Core Value:** A user scans any cosmetic product label and immediately receives citation-backed health safety tags — fully offline, no account required, no data leaving the device.

## v1 Requirements

### Scanning

- [ ] **SCAN-01**: User can capture a cosmetic product label photo and receive an extracted list of INCI ingredients via on-device OCR
- [ ] **SCAN-02**: User can manually enter an ingredient list when OCR fails or is unclear
- [ ] **SCAN-03**: INCI names are normalized (uppercase, synonym resolution, Levenshtein fuzzy matching) before knowledge base lookup
- [ ] **SCAN-04**: Ingredients not resolved by the knowledge base show "UNKNOWN — could not verify safety" card (never silently classified as safe)

### Safety Tags

- [ ] **SAFE-01**: Each ingredient displays a health-first safety tag: 💡 Explain / ⚠️ Caution / 🔄 Solve / 🛑 Danger
- [ ] **SAFE-02**: Each Caution tag shows health mechanism, affected population, dose threshold, and at least one authoritative citation
- [ ] **SAFE-03**: Each Danger tag shows health mechanism, jurisdiction-specific regulatory status, and official scientific rationale (verbatim from SCCS/CIR/ACOG/IARC — never LLM-generated)
- [ ] **SAFE-04**: Each Solve tag shows a safer alternative ingredient with mechanism match and safety improvement explanation
- [ ] **SAFE-05**: Tags display a confidence indicator: solid border (≥0.85), dashed border (0.60–0.84), grayed (< 0.60)
- [ ] **SAFE-06**: Results are sorted by severity (Danger first, then Caution, Solve, Explain)

### Regulatory Compliance

- [ ] **COMP-01**: Each Danger/Caution tag shows regulatory status for the user's selected country (EU / US / CN / JP)
- [ ] **COMP-02**: User can expand a "Why Restricted?" card showing official scientific rationale with source citation
- [ ] **COMP-03**: User can toggle to compare regulatory status across all 4 jurisdictions (EU / US / CN / JP) for any ingredient

### Session Context & Profile

- [ ] **PROF-01**: User can set session context (pregnancy status, country, skin concern type) without creating an account
- [ ] **PROF-02**: Session context boosts relevant RAG retrieval (pregnancy risk records if pregnant; sensitizer records if sensitive; jurisdiction records for selected country)
- [ ] **PROF-03**: Session context is cleared when the app closes — zero persistent personal data retained

### Privacy & On-Device Processing

- [ ] **PRIV-01**: All inference, RAG retrieval, and image analysis run on-device via LiteRT — no network calls during analysis
- [ ] **PRIV-02**: No user account or login required for any feature
- [ ] **PRIV-03**: No captured images or health data transmitted to any external server at any time
- [ ] **PRIV-04**: User can optionally save a scan result locally (no cloud sync)

### Knowledge Base & Data

- [ ] **DATA-01**: Knowledge base covers 80 priority INCI ingredients at launch (parabens, retinoids, sunscreen filters, fragrances, preservatives) with health metadata and authoritative citations
- [ ] **DATA-02**: Results screen shows "Data Valid As Of [date]" footer on every result
- [ ] **DATA-03**: User can optionally download an updated knowledge base bundle on Wi-Fi

### UI & Trust

- [ ] **UI-01**: Analysis progress is visible during each pipeline stage (OCR done → retrieving → AI analyzing → results)
- [ ] **UI-02**: App displays disclaimer on all result screens: "Educational only — not medical or regulatory advice. Consult a licensed professional."

## v2 Requirements

### Vision

- **VIS-01**: Camera-based face scan detects Fitzpatrick scale and skin concerns for session profile (v1 uses manual selector)

### Knowledge Base Expansion

- **DATA-04**: Knowledge base expanded from 80 to 300–500 INCI ingredients
- **DATA-05**: Health endpoint filter badges (🧬 Endocrine, 🤰 Reproductive, 🛡️ Sensitizer, ☀️ Phototoxic) as results filter chips

## Out of Scope

| Feature | Reason |
|---------|--------|
| User accounts / login | Core privacy principle — zero-account by design |
| Cloud inference or cloud RAG | On-device is non-negotiable for LiteRT track + privacy |
| Medical diagnosis or treatment recommendations | Educational only; legal liability |
| Product brand safety ratings | Anti-feature — invites sponsorship bias (Think Dirty failure mode) |
| "Clean / natural" ingredient labeling | Unscientific marketing language; corrupts health-science credibility |
| User-submitted ingredient data | Health apps cannot absorb crowdsourced inaccuracy |
| iOS / cross-platform | Android-first for LiteRT hackathon track |
| Real-time regulatory updates without opt-in | Versioned offline bundles only |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SCAN-01 | Phase 2 | Pending |
| SCAN-02 | Phase 2 | Pending |
| SCAN-03 | Phase 2 | Pending |
| SCAN-04 | Phase 4 | Pending |
| SAFE-01 | Phase 4 | Pending |
| SAFE-02 | Phase 4 | Pending |
| SAFE-03 | Phase 4 | Pending |
| SAFE-04 | Phase 4 | Pending |
| SAFE-05 | Phase 5 | Pending |
| SAFE-06 | Phase 5 | Pending |
| COMP-01 | Phase 4 | Pending |
| COMP-02 | Phase 6 | Pending |
| COMP-03 | Phase 6 | Pending |
| PROF-01 | Phase 2 | Pending |
| PROF-02 | Phase 3 | Pending |
| PROF-03 | Phase 2 | Pending |
| PRIV-01 | Phase 1 | Pending |
| PRIV-02 | Phase 1 | Pending |
| PRIV-03 | Phase 1 | Pending |
| PRIV-04 | Phase 5 | Pending |
| DATA-01 | Phase 2 | Pending |
| DATA-02 | Phase 6 | Pending |
| DATA-03 | Phase 6 | Pending |
| UI-01 | Phase 5 | Pending |
| UI-02 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-25*
*Last updated: 2026-04-25 after roadmap creation (traceability finalized)*
