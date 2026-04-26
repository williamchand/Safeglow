---
phase: 01-foundation-model-validation
plan: 02
subsystem: room-knowledge-base
tags: [android, room, hilt, fts, sqlite, seed-db, instrumented-tests]
dependency_graph:
  requires:
    - 01-01 (Hilt skeleton, version catalog, app/build.gradle.kts with Room KSP config)
  provides:
    - room-knowledge-base-layer
    - ingredient-dao-singleton
    - seed-database-asset
    - kb-schema-lock
  affects:
    - 01-04 (Phase 2 ingestion builds on IngredientDao.findExact + ftsSearch)
    - 01-05 (Phase 3 embedding index keyed by inciName from this schema)
tech_stack:
  added: []
  patterns:
    - "Room.databaseBuilder().createFromAsset(\"knowledge_base.db\") for prebuilt seed DB"
    - "@Database(exportSchema = true) + KSP schemaLocation → schemas/.../1.json"
    - "FTS4 shadow table (IngredientFts) joined via rowid for O(log n) INCI lookup"
    - "@Singleton AppDatabase + non-singleton IngredientDao (thread-safe DAO backed by singleton DB)"
    - "runBlocking in instrumented test — JUnit4 on Android does not support suspend test functions"
key_files:
  created:
    - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientRecord.kt
    - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/entities/IngredientFts.kt
    - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/IngredientDao.kt
    - app/src/main/kotlin/com/safeglow/edge/data/knowledge/db/AppDatabase.kt
    - app/src/main/kotlin/com/safeglow/edge/di/DatabaseModule.kt
    - app/src/main/assets/knowledge_base.db
    - app/src/main/kotlin/com/safeglow/edge/data/knowledge/schema/KB_SCHEMA.md
    - schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json
    - app/schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json
    - tools/build_seed_db.sh
  modified:
    - app/src/androidTest/java/com/safeglow/edge/RoomSeedTest.kt
decisions:
  - "IngredientRecord uses 14 fields matching KB_SCHEMA.md; embedding_vector intentionally absent (Phase 3 concern)"
  - "citationIds stored as JSON array string; full citation records deferred to Phase 2 separate table"
  - "FTS4 via IngredientFts @Fts4(contentEntity = IngredientRecord::class) shadow table for MATCH queries"
  - "Seed DB built with 10+ rows including METHYLPARABEN(CAUTION) to satisfy SC-5 plus name-based assertion"
  - "tools/build_seed_db.sh is executable shell script for reproducible rebuild of knowledge_base.db"
metrics:
  duration_minutes: 9
  completed_date: "2026-04-26"
  tasks_completed: 3
  tasks_total: 3
  files_created: 10
  files_modified: 1
---

# Phase 1 Plan 2: Room Knowledge-Base Layer Summary

Room persistence layer implemented end-to-end: `IngredientRecord` entity with 14 schema-locked fields, FTS4 shadow table, `IngredientDao` with exact + FTS search, `AppDatabase` with `createFromAsset`, `DatabaseModule` Hilt binding, exported schema JSON, prebuilt `knowledge_base.db` seed asset with 10 records, and `RoomSeedTest` replacing its stub with real SC-5 assertions.

## What Was Built

### Task 1: Room Entities, DAO, AppDatabase, and Schema Lock

**`IngredientRecord.kt`** — 14-field `@Entity(tableName = "ingredients")` data class. Schema locked in `KB_SCHEMA.md` so Phases 2/3/4 cannot drift:

```kotlin
@Entity(tableName = "ingredients")
data class IngredientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inciName: String,
    val commonName: String,
    val safetyTag: String,           // "EXPLAIN" | "CAUTION" | "SOLVE" | "DANGER"
    val healthMechanism: String,
    val affectedPopulation: String,
    val doseThreshold: String,
    val euStatus: String,
    val usStatus: String,
    val cnStatus: String,
    val jpStatus: String,
    val citationIds: String,         // JSON array of citation_id strings
    val confidence: Float,
    val dataValidAsOf: String        // ISO 8601 date
)
```

**`IngredientFts.kt`** — `@Fts4(contentEntity = IngredientRecord::class)` shadow table enabling `MATCH` queries without duplicating data.

**`IngredientDao.kt`** — three queries:
- `findExact(name)` — exact INCI lookup (Phase 2 primary path)
- `ftsSearch(query)` — FTS JOIN for partial/synonym matches (Phase 2 fallback)
- `getAll()` — Phase 1 SC-5 validation only

**`AppDatabase.kt`** — `@Database(entities = [IngredientRecord::class, IngredientFts::class], version = 1, exportSchema = true)`. KSP writes `schemas/.../1.json` which is the authoritative schema reference.

Schema JSON exported to both `schemas/` (project root, Gradle default) and `app/schemas/` per `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.

### Task 2: DatabaseModule, Seed Asset, and Build Script

**`DatabaseModule.kt`** — `@Singleton AppDatabase` via `createFromAsset("knowledge_base.db")` + non-singleton `IngredientDao`:

```kotlin
@Provides
@Singleton
fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "knowledge_base.db")
        .createFromAsset("knowledge_base.db")
        .build()
```

`createFromAsset` copies the bundled `.db` file to internal storage on first open, then uses the copy for all subsequent opens. Schema mismatch with the asset throws `IllegalStateException` (Pitfall 4 in RESEARCH.md) — caught early by RoomSeedTest.

**`app/src/main/assets/knowledge_base.db`** — Prebuilt SQLite database with 10 seed records including:
- METHYLPARABEN (safetyTag: CAUTION) — asserted by name in `methylparabenSeedRecordPresentWithExpectedTag()`
- 9 additional cosmetic ingredient records across EXPLAIN/CAUTION/SOLVE/DANGER tags

**`tools/build_seed_db.sh`** — Executable shell script for reproducible rebuild of the seed DB from CSV source data.

### Task 3: RoomSeedTest — SC-5 Implementation

`@Ignore` stub replaced with two real assertions:

| Test | Success Criterion | Key Assertion |
|------|------------------|---------------|
| `tenSeedRecordsReadable()` | SC-5: ≥10 seed records | `assertTrue(records.size >= 10)` |
| `methylparabenSeedRecordPresentWithExpectedTag()` | SC-5 named record | `assertEquals("CAUTION", mp!!.safetyTag)` |

Both tests use `runBlocking` — JUnit4 on Android does not support `suspend` test functions.

## KB_SCHEMA.md Lock

`app/src/main/kotlin/com/safeglow/edge/data/knowledge/schema/KB_SCHEMA.md` canonicalizes the 14-field schema. Phases 2/3/4 must not alter field names or types without bumping Room's `version` and providing a migration.

## Verification Results

| Check | Command | Result |
|-------|---------|--------|
| Kotlin compilation | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| Schema JSON exported | `ls schemas/.../1.json` | FOUND |
| Asset present | `ls app/src/main/assets/knowledge_base.db` | FOUND |
| Hilt graph resolves | `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| Test compilation | `./gradlew compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| No @Ignore in RoomSeedTest | `grep -c '@Ignore' RoomSeedTest.kt` | 0 |

## Deviations from Plan

None. All files match the plan specification. Schema fields, FTS configuration, and Hilt binding follow RESEARCH.md and PATTERNS.md exactly.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| IngredientRecord.kt (14 fields) | FOUND |
| IngredientFts.kt (@Fts4) | FOUND |
| IngredientDao.kt (3 queries) | FOUND |
| AppDatabase.kt (exportSchema=true) | FOUND |
| DatabaseModule.kt (createFromAsset) | FOUND |
| knowledge_base.db asset | FOUND |
| KB_SCHEMA.md | FOUND |
| schemas/.../1.json | FOUND |
| tools/build_seed_db.sh | FOUND |
| RoomSeedTest.kt (no @Ignore, 2 assertions) | FOUND |
| commit 15d9ea0 | FOUND |
| commit a91f2a5 | FOUND |
| commit df78a92 | FOUND |
