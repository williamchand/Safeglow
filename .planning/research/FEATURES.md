# Feature Research

**Domain:** Cosmetic ingredient safety / on-device AI analysis app (Android)
**Researched:** 2026-04-25
**Confidence:** MEDIUM — web research tools unavailable; based on training-data knowledge of competitor apps (Think Dirty, EWG Skin Deep, CosDNA, INCI Decoder, INCIBeauty) current as of August 2025. Competitor-specific claims flagged LOW where uncertain.

---

## Competitor Baseline (What Currently Exists)

| App | Core Mechanic | Primary Weakness |
|-----|--------------|-----------------|
| EWG Skin Deep | Score 1–10 per ingredient, product search by barcode | Scores feel opaque/arbitrary; no mechanism explanation; cloud-only; US-centric |
| Think Dirty | "Dirty Meter" 0–10 per product; community-sourced DB | Gamified score hides nuance; no citations; brand-sponsored content erodes trust [LOW] |
| CosDNA | Raw INCI breakdown with function tags; acne/irritant/safety flags | No health mechanism; no pregnancy context; minimal UX; no regulatory jurisdiction |
| INCI Decoder | Ingredient glossary with "what it does" + safety summary | Shallow safety depth; no personalization; no scan; web-only UX |
| INCIBeauty | FR-origin app; scan + ingredient list | Limited English; no health endpoint; basic hazard flags only [LOW] |

**Common gaps across all:** No on-device privacy, no pregnancy-specific evidence, no jurisdiction comparison, no health mechanism explanation, no confidence calibration, no citations from primary sources.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing = product feels broken or untrustworthy.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| OCR label scan (camera input) | Every modern ingredient app uses camera; typing is friction-heavy | MEDIUM | ML Kit Text Recognition v2 handles this; INCI normalization must follow immediately |
| Ingredient list display after scan | Raw extracted ingredients must be visible — users verify OCR accuracy | LOW | Show with confidence highlight for low-OCR-confidence tokens |
| Per-ingredient safety signal (color/icon) | CosDNA, EWG, Think Dirty all have this; users orient by visual triage | LOW | SafeGlow uses 4-tag system (Explain/Caution/Solve/Danger) — satisfies this expectation |
| Plain-language ingredient explanation | INCI names are opaque; users expect "what is this?" in plain English | MEDIUM | LLM excels here; knowledge base must include function description per ingredient |
| "Why is this a concern?" for flagged items | Users won't trust a warning without reason; EWG/Think Dirty fail here | MEDIUM | Health mechanism field in knowledge base satisfies this |
| Offline / no-account access | Privacy-conscious users (the primary audience) are allergic to sign-ups | LOW | Core architectural principle already established in PROJECT.md |
| Legal disclaimer on results | Required to avoid medical liability; users also trust apps that show humility | LOW | PROJECT.md mandates this; must be persistent, not hidden |
| Scan history / local save | Users re-check products; losing a scan result creates frustration | LOW | Local SQLite save; no cloud sync required |
| "Data freshness" indicator | Users want to know how current the safety data is | LOW | "Data Valid As Of [date]" footer already in PROJECT.md requirements |

### Differentiators (Competitive Advantage)

Features that no current competitor delivers well. These are where SafeGlow Edge wins.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| On-device-only inference (privacy-first) | Zero data transmission; camera images never leave device; resonates strongly with pregnant women, skincare-sensitive users, privacy advocates | HIGH | Core architectural constraint; must be demoed explicitly for hackathon judges |
| Health mechanism explanation per ingredient | "This disrupts estrogen signaling at >X ppm" is more actionable than "score: 7/10" — EWG/Think Dirty do not provide this | HIGH | Requires structured health_mechanism field in knowledge base; LLM formats into user-facing prose |
| Primary-source citations (SCCS, CIR, ACOG, FDA) | Trust signal that no competitor provides; differentiates from opaque scores | HIGH | Citation hallucination prevention requires strict output validation layer; pre-curated only |
| Multi-jurisdiction regulatory comparison (EU/US/CN/JP) | Globally-sourced users shop across markets; no competitor does cross-jurisdiction | HIGH | Requires per-ingredient regulatory_status object in knowledge base; 4-jurisdiction coverage |
| Confidence calibration on tags | Honest uncertainty is rare; users with health stakes (pregnancy) value knowing what the model doesn't know | MEDIUM | Three-tier border visual (solid/dashed/gray) already in PROJECT.md requirements |
| Pregnancy-specific safety context | Pregnant users are the highest-stakes segment; ACOG evidence is specific and absent from all competitors | MEDIUM | Requires pregnancy_risk field in knowledge base; session context filters which tags surface |
| Skin profile from face scan (Fitzpatrick + concerns) | Personalizes recommendations without an account; competitors only allow manual input | HIGH | On-device vision model; session-only; Fitzpatrick detection is technically achievable with MediaPipe/ML Kit |
| Safer alternative suggestions with mechanism match | "Use X instead — same emollient function, no endocrine risk" — competitors don't do alternatives at mechanism level | HIGH | Requires alternatives mapping in knowledge base; LLM formats the comparison |
| Ingredient function context ("what it does in the formula") | CosDNA lists INCI functions but without safety integration; SafeGlow can combine function + risk | LOW | Knowledge base field; surfaces "this preservative can be replaced by X" reasoning |
| Hackathon explainability demo (on-device proof) | Judges evaluate LiteRT special track; visible on-device indicator matters | LOW | Add "running on-device" indicator in results UI; show inference latency if possible |

### Anti-Features (Explicitly Avoid)

Features that seem useful but undermine the product's trust and integrity.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Brand/product-level safety score (single number) | Users ask for a quick verdict | Single scores obscure ingredient nuance; Think Dirty's "Dirty Meter" trained users to game scores by reformulation without real safety change; leads to sponsored content pressure | Show ingredient-level tags without aggregating to a product score; let users form their own overall impression |
| "Clean" / "natural" labeling | Users associate "clean" with safe | "Clean beauty" is unregulated marketing language with no scientific basis; using it trains users to distrust synthetic-but-safe ingredients (e.g., niacinamide, retinol esters); creates legal grey area | Use health-endpoint language: "no endocrine risk at typical use levels" instead of "clean" |
| User-submitted ingredient data | Crowdsourced databases feel more complete | CosDNA and Open Food Facts show this leads to low-quality, unverified data that undermines safety credibility; for a health app, bad data is dangerous | Lock knowledge base to officially curated sources only; version-control via Wi-Fi bundle updates |
| Real-time regulatory update feed | Seems like a safety feature | Requires network; breaks offline guarantee; creates dependency on server uptime; regulatory changes rarely require real-time response | Versioned Wi-Fi-only bundle updates (~10–15MB) on user opt-in |
| Social sharing of scan results | Engagement metric | Contradicts privacy-first positioning; users share screenshots of health concerns = PII leakage risk; creates misleading safety claims stripped of context | Local save only; export as PDF with disclaimer attached if sharing is required |
| Premium tier gating safety information | Business model pressure | Hiding safety data behind a paywall creates ethical conflict for health-stakes decisions; erodes trust immediately | Hackathon context: fully free; post-hackathon, monetize via knowledge base subscriptions or bulk scan API, never paywall safety tags |
| Allergy cross-reference database | Frequently requested | Requires medical-grade accuracy; NACDG sensitization data is probabilistic, not individual; misuse risk for users with confirmed allergies acting on app output | Surface sensitization frequency as a risk signal ("common sensitizer in clinical patch testing") with disclaimer; do not frame as personal allergy checker |

---

## Feature Dependencies

```
[OCR Label Scan]
    └──requires──> [INCI Normalization Layer]
                       └──requires──> [Knowledge Base (SQLite + JSON index)]
                                          └──requires──> [RAG Retrieval Engine]
                                                             └──requires──> [Gemma 4 E2B via LiteRT]
                                                                                └──produces──> [Safety Tags per Ingredient]

[Face Scan / Skin Profiling]
    └──requires──> [On-device Vision Model (MediaPipe/ML Kit)]
    └──produces──> [Session Skin Context]
                       └──enhances──> [Safety Tags per Ingredient] (filters by skin concern)

[Session Context (pregnancy, country, skin concern)]
    └──enhances──> [Safety Tags per Ingredient] (pregnancy risk surfaced; jurisdiction regulatory status shown)
    └──enhances──> [Regulatory Jurisdiction Comparison]

[Safer Alternatives Suggestion]
    └──requires──> [Safety Tags per Ingredient]
    └──requires──> [Knowledge Base alternatives_map field]

[Citation Display]
    └──requires──> [Knowledge Base pre-curated citations field]
    └──requires──> [Output Validation Layer] (blocks hallucinated citations)

[Confidence Calibration]
    └──requires──> [RAG Retrieval Engine] (retrieval score feeds confidence)
    └──enhances──> [Safety Tags per Ingredient]

[Local Scan Save]
    └──requires──> [Safety Tags per Ingredient]
    └──conflicts──> [Cloud Sync] (deliberately excluded)

[Wi-Fi Bundle Update]
    └──requires──> [Knowledge Base versioning]
    └──conflicts──> [Always-on network calls] (opt-in only, not background)
```

### Dependency Notes

- **OCR requires INCI Normalization:** Raw OCR text (e.g., "Aqua", "Water", "Eau") must map to canonical INCI names before knowledge base lookup; this layer is not optional and must handle OCR noise and brand-name synonyms.
- **Safety Tags require RAG + LiteRT:** Tags are the app's core output — every upstream dependency (OCR, KB, retrieval, model) must be complete before tags can render.
- **Face Scan enhances but does not block Tags:** Skin profiling is additive; if face scan fails or is skipped, ingredient analysis still runs — skin context is optional enrichment.
- **Citations require Output Validation Layer:** Without strict schema enforcement and citation pre-curation, the model will hallucinate references. This is a non-negotiable integrity gate.
- **Confidence Calibration requires RAG retrieval score:** The confidence tier (solid/dashed/gray border) must be derived from retrieval quality + model confidence, not hardcoded.
- **Wi-Fi Bundle Update conflicts with always-on sync:** Must remain fully opt-in with explicit user trigger; never run in background.

---

## MVP Definition

### Hackathon Launch (v1 — 7-day sprint)

Minimum viable to demonstrate the core value proposition and satisfy LiteRT special track criteria.

- [ ] OCR label scan via CameraX + ML Kit — required to demo the use case
- [ ] INCI normalization with fuzzy matching — required for reliable knowledge base lookup
- [ ] Knowledge base: 300–500 curated ingredients with health metadata and citations — depth over breadth
- [ ] RAG retrieval (exact INCI match + semantic embedding fallback) via on-device SQLite — core differentiator
- [ ] Gemma 4 E2B via LiteRT inference producing structured JSON tags — hackathon LiteRT track requirement
- [ ] Output validation layer (schema enforcement + citation allowlist) — integrity requirement
- [ ] 4-tag safety display (Explain/Caution/Solve/Danger) with health mechanism text — user-facing core
- [ ] Citation display (SCCS/CIR/ACOG/FDA, pre-curated only) — trust differentiator
- [ ] Confidence tier visual (solid/dashed/gray border) — honesty signal
- [ ] Session context (pregnancy, country, skin concern) — personalization without account
- [ ] Legal disclaimer persistent on result screens — liability requirement
- [ ] "Data Valid As Of [date]" footer — transparency requirement
- [ ] Local scan save (no cloud sync) — basic usability

### Add After Validation (v1.x)

- [ ] Face scan / skin profiling — adds personalization depth; complex; defer if time-constrained
- [ ] Safer alternatives suggestion — high user value but requires knowledge base extension
- [ ] Multi-jurisdiction regulatory comparison UI — data is in knowledge base; UI is the work
- [ ] Wi-Fi bundle update mechanism — not needed for hackathon demo

### Future Consideration (v2+)

- [ ] iOS port — post-hackathon, after Android core is stable
- [ ] Knowledge base expansion to 800+ ingredients — post-MVP based on coverage gap feedback
- [ ] Barcode scan → product database lookup — requires external product DB; out of scope for privacy-first v1
- [ ] Export / PDF report — low priority; local save covers v1 need

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Hackathon Weight | Priority |
|---------|------------|---------------------|-----------------|----------|
| OCR label scan | HIGH | MEDIUM | HIGH | P1 |
| INCI normalization | HIGH | MEDIUM | HIGH | P1 |
| Knowledge base (300–500 ingredients) | HIGH | HIGH | HIGH | P1 |
| RAG retrieval (on-device) | HIGH | HIGH | HIGH | P1 |
| Gemma 4 E2B via LiteRT | HIGH | HIGH | HIGH | P1 |
| Output validation layer | HIGH | MEDIUM | HIGH | P1 |
| Safety tags (4-tier) | HIGH | MEDIUM | HIGH | P1 |
| Citation display | HIGH | LOW | HIGH | P1 |
| Confidence tier visual | MEDIUM | LOW | HIGH | P1 |
| Session context (pregnancy/country) | HIGH | LOW | MEDIUM | P1 |
| Legal disclaimer | HIGH | LOW | MEDIUM | P1 |
| Local scan save | MEDIUM | LOW | LOW | P1 |
| Health mechanism explanation | HIGH | MEDIUM | HIGH | P1 |
| Face scan / skin profiling | MEDIUM | HIGH | MEDIUM | P2 |
| Safer alternatives suggestion | HIGH | MEDIUM | LOW | P2 |
| Regulatory jurisdiction comparison UI | MEDIUM | MEDIUM | MEDIUM | P2 |
| Wi-Fi bundle update | MEDIUM | MEDIUM | LOW | P3 |
| Barcode scan + product DB | HIGH | HIGH | LOW | P3 |
| PDF export | LOW | MEDIUM | LOW | P3 |

**Priority key:**
- P1: Must have for hackathon submission
- P2: Include if sprint time allows; strong post-hackathon case
- P3: Post-hackathon or v2

---

## Competitor Feature Analysis

| Feature | EWG Skin Deep | Think Dirty | CosDNA | INCI Decoder | SafeGlow Edge Plan |
|---------|---------------|-------------|--------|--------------|-------------------|
| Per-ingredient rating | Yes (1–10 score) | Yes (0–10 dirty meter) | Yes (acne/irritant/safety flags) | Partial (safety summary) | Yes — 4-tier health tag |
| Health mechanism explanation | No | No | No | Partial (function only) | Yes — required field in KB |
| Primary source citations | No (opaque methodology) | No | No | No | Yes — pre-curated SCCS/CIR/ACOG/FDA |
| Pregnancy context | Partial (some flags) | Partial | No | No | Yes — ACOG evidence + session context |
| Jurisdiction comparison | No (US-only) | No | No | No | Yes — EU/US/CN/JP |
| On-device / offline | No (cloud) | No (cloud) | No (web) | No (web) | Yes — core differentiator |
| Privacy (no data transmission) | No | No | No | No | Yes — zero PII, session-only |
| Face scan / skin profile | No | No | No | No | Yes (P2) |
| Confidence calibration | No | No | No | No | Yes — 3-tier visual |
| Safer alternatives | No | No | No | No | Yes (P2) |
| Barcode scan | Yes | Yes | No | No | Deferred (P3) |
| OCR label scan | No | No | No | No | Yes — primary input method |
| Ingredient function explanation | Partial | No | Yes (INCI function tags) | Yes | Yes — integrated with safety |

**Key insight:** No existing competitor combines on-device privacy + health mechanism explanation + citations + jurisdiction comparison. SafeGlow Edge is differentiated on all four simultaneously. The risk is execution depth — the knowledge base quality at 300–500 ingredients determines whether the differentiators feel real or hollow.

---

## Hackathon-Specific Evaluation Criteria

The Kaggle hackathon evaluates on LiteRT special track + Impact: Health & Sciences. Judges will look for:

| Criterion | What Judges Want to See | Feature That Satisfies It |
|-----------|------------------------|--------------------------|
| LiteRT on-device proof | Model inference visibly running on device; no network calls | "Running on-device" indicator in results UI; show inference latency |
| Health impact clarity | Clear, actionable health outcome — not just a score | Health mechanism text + citation per Caution/Danger tag |
| Safety & Trust track | No hallucinations; honest uncertainty; cites real sources | Output validation layer + confidence tiers + citation allowlist |
| Explainability | Why did the model produce this output? | Health mechanism text is the explanation; confidence tier shows certainty |
| Privacy / responsible AI | Data handling transparency; no unintended data collection | Privacy architecture statement in UI; session-only context clear to user |

**Hackathon MVP minimum:** OCR scan → on-device inference → structured health tags with citations. Everything else is bonus. Do not sacrifice depth of the core loop for breadth of features.

---

## Sources

- EWG Skin Deep feature set: training data (HIGH confidence for core features; MEDIUM for recent changes)
- Think Dirty feature set: training data (MEDIUM confidence — company had pivots post-2023 [LOW])
- CosDNA feature set: training data (HIGH confidence — stable, minimal product changes)
- INCI Decoder feature set: training data (HIGH confidence — web product, well-documented)
- INCIBeauty feature set: training data (LOW confidence — limited English coverage in training data)
- ACOG, SCCS, CIR, FDA cosmetic regulatory frameworks: training data (HIGH confidence — stable public sources)
- Kaggle hackathon LiteRT track criteria: PROJECT.md context provided by user

---
*Feature research for: SafeGlow Edge — cosmetic safety AI app*
*Researched: 2026-04-25*
