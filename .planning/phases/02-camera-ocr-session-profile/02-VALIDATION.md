---
phase: 2
slug: camera-ocr-session-profile
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-28
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 / Espresso / Robolectric (Android Gradle) |
| **Config file** | `app/build.gradle.kts` — `testImplementation` and `androidTestImplementation` blocks |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` |
| **Estimated runtime** | ~45 seconds (unit) / ~3 minutes (connected) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | SCAN-01 | — | Camera permission required before capture | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | SCAN-01 | — | ImageCapture produces InputImage without crash | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 2 | SCAN-01 | — | TextRecognizer returns non-empty result on test image | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | SCAN-03 | — | INCINormalizer uppercase+strip produces canonical output | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 1 | SCAN-03 | — | Synonym map resolves known aliases to canonical INCI | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-02-03 | 02 | 1 | SCAN-03 | — | Levenshtein fuzzy match recovers ≥80% of test tokens | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-02-04 | 02 | 2 | SCAN-02 | — | Manual input path produces same token list as OCR path | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 1 | PROF-01 | — | SessionViewModel state cleared after simulated app close | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-03-02 | 03 | 1 | PROF-01 | — | SessionViewModel holds pregnancy/country/skinConcern fields | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 02-03-03 | 03 | 1 | PROF-03 | — | No SharedPreferences or SavedStateHandle used for session data | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/dev/safeglow/edge/ocr/INCINormalizerTest.kt` — stubs for SCAN-03
- [ ] `app/src/test/java/dev/safeglow/edge/ocr/OCRPipelineTest.kt` — stubs for SCAN-01
- [ ] `app/src/test/java/dev/safeglow/edge/session/SessionViewModelTest.kt` — stubs for PROF-01, PROF-03

*Existing JUnit 4 infrastructure from Phase 1 covers all framework needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| ≥80% INCI token recovery on 5 real product labels | SCAN-03 | Requires physical cosmetic product labels | Photograph 5 products (cleanser, moisturizer, sunscreen, serum, toner); measure recovered tokens / total tokens ≥ 0.80 |
| OCR capture within 3 seconds on physical device | SCAN-01 | Requires physical device with camera | Time from capture button tap to normalized token list display using stopwatch; must complete within 3s on demo device |
| Session context cleared after app force-stop | PROF-03 | Requires OS-level app lifecycle | Set pregnancy=true, close via recent apps, reopen — values must be cleared |
| Camera permission prompt triggers on first launch | SCAN-01 | System dialog behavior | Fresh install, open scan screen — system permission dialog must appear before preview renders |
| 80 ingredients in database asset | DATA-01 | Database content validation | Run `adb shell` + sqlite3 on filesDir DB, `SELECT COUNT(*) FROM ingredients` → must be ≥ 80 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
