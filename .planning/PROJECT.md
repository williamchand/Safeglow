# SafeGlow Edge

## What This Is

SafeGlow Edge is an on-device Android app that scans cosmetic product labels and the user's face, then uses Gemma 4 E2B via LiteRT to generate evidence-backed, health-first safety insights about cosmetic ingredients. All AI inference, RAG retrieval, and skin analysis run entirely on-device — zero accounts, zero data transmission, session-only context. Built for a Kaggle hackathon targeting the LiteRT special track and Impact: Health & Sciences.

## Core Value

A user scans any cosmetic product label and immediately receives citation-backed health safety tags — fully offline, no account required, no data leaving the device.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can scan a cosmetic product label and see a list of extracted ingredients via on-device OCR
- [ ] User can scan their face to generate a skin profile (Fitzpatrick scale, skin concerns) via on-device vision
- [ ] User can set session context (pregnancy status, country, skin concern) without creating an account
- [ ] Each ingredient shows a health-first safety tag: 💡 Explain / ⚠️ Caution / 🔄 Solve / 🛑 Danger
- [ ] Each Caution/Danger tag includes health mechanism, affected population, dose threshold, and authoritative citations
- [ ] Each Danger tag includes jurisdiction-specific regulatory status with scientific rationale for restrictions
- [ ] User can view regulatory comparison across EU, US, China, and Japan for any ingredient
- [ ] User can see safer ingredient alternatives with mechanism match and safety improvement explanation
- [ ] Tags display a confidence indicator (≥0.85 solid border, 0.60–0.84 dashed, <0.60 grayed)
- [ ] All inference and retrieval runs on-device via LiteRT — no network calls during analysis
- [ ] Knowledge base is a bundled SQLite + JSON index of 300–500 INCI-normalized ingredients with health metadata and citations
- [ ] User can optionally save a scan result locally (no cloud sync)
- [ ] App displays a "Data Valid As Of [date]" footer on results screen
- [ ] Optional Wi-Fi-only bundle update to refresh knowledge base (~10–15MB compressed)

### Out of Scope

- User accounts or login — zero-account is a core privacy principle
- Cloud inference or cloud RAG — all processing must be on-device
- Real-time regulatory updates without user opt-in — versioned offline bundles only
- Medical diagnosis or treatment recommendations — educational only, not medical advice
- More than 500 INCI ingredients in v1 — depth over breadth wins the hackathon
- iOS / cross-platform — Android-first for LiteRT hackathon track

## Context

- **Platform**: Android (Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition)
- **AI Runtime**: LiteRT (Google's TFLite successor) with Gemma 4 E2B on-device model
- **RAG Strategy**: Hybrid exact INCI match + semantic embedding retrieval (all-MiniLM-L6-v2 or gte-tiny) over SQLite/JSON knowledge base; context-filtered by pregnancy status, country, skin concern
- **Knowledge Base Sources**: CosIng (EU Annex II–VI), SCCS opinions, CIR reports, IARC/EPA carcinogenicity data, ACOG pregnancy guidance, NACDG sensitization data, FDA cosmetic framework — all public, legally redistributable for educational use
- **Hackathon**: Kaggle hackathon — Main Track, Impact: Health & Sciences, Special Tech: LiteRT, Safety & Trust
- **Build Window**: 7-day hackathon sprint (2026-04-25 to 2026-05-02)
- **Privacy Architecture**: No PII collected, no images transmitted, session context cleared on app close, no analytics SDKs
- **Legal Guardrail**: "Educational only — not medical or regulatory advice. Consult a licensed professional." displayed on all result screens

## Constraints

- **Tech Stack**: Android + Kotlin + Jetpack Compose + LiteRT — Gemma 4 E2B must run via LiteRT on-device; no server-side inference
- **Memory Budget**: Knowledge base index ≤ 800 ingredients to fit device RAM; mobile-optimized embeddings required
- **Performance**: End-to-end latency (face scan + label scan + RAG + inference) < 8 seconds on reference Android hardware
- **Timeline**: 7-day hard deadline (2026-05-02 for hackathon submission)
- **Data Sources**: Only publicly accessible, legally redistributable data — no licensed ingredient databases
- **Output Integrity**: Generated regulatory rationale is forbidden — only pre-curated official text from SCCS/CIR/ACOG can appear as citations
- **Medical Liability**: Disclaimer required on all output screens; no treatment recommendations

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| On-device RAG via SQLite + embeddings | Avoids network dependency; supports offline demo for hackathon; privacy-first | — Pending |
| Gemma 4 E2B via LiteRT | Hackathon LiteRT special track; small model footprint suitable for mobile | — Pending |
| Strict JSON schema + output validation layer | Prevents hallucinated citations; enforces health-first structure; catches malformed inference | — Pending |
| Session-only context (no accounts) | Core privacy principle; zero friction for vulnerable users (pregnant women) | — Pending |
| Health endpoint drives tags, regulatory is secondary | Tox/clinical evidence is more durable and jurisdiction-agnostic | — Pending |
| INCI normalization for all ingredient names | Required for reliable lookup despite OCR noise and brand-name synonyms | — Pending |
| 300–500 ingredient depth limit for v1 | Depth of health evidence per ingredient > breadth of coverage; fits memory budget | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-25 after initialization*
