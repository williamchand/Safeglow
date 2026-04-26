#!/usr/bin/env bash
# Build the prebuilt knowledge_base.db Room asset for Phase 1 SC-5 validation.
# Column names MUST match the Room-exported schema (camelCase). Mismatch will trigger
# IllegalStateException("Pre-packaged database has an invalid schema") on first open.
#
# Usage: ./tools/build_seed_db.sh
# Requires: sqlite3 (system binary)
set -euo pipefail

OUT="app/src/main/assets/knowledge_base.db"
SCHEMA_REF="schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"

if [ ! -f "$SCHEMA_REF" ]; then
  echo "ERROR: Run ./gradlew :app:compileDebugKotlin first to generate $SCHEMA_REF"
  exit 1
fi

# Extract identity_hash from the Room-exported schema JSON.
# The identityHash is used by Room to validate the prebuilt DB at open time.
# If json_extract or readfile is unavailable, fall back to the literal hash from the JSON.
IDENTITY_HASH=$(python3 -c "import json,sys; d=json.load(open('$SCHEMA_REF')); print(d['database']['identityHash'])" 2>/dev/null \
  || grep -o '"identityHash": "[^"]*"' "$SCHEMA_REF" | head -1 | sed 's/"identityHash": "//;s/"//')

if [ -z "$IDENTITY_HASH" ]; then
  echo "ERROR: Could not extract identityHash from $SCHEMA_REF"
  exit 1
fi

echo "Using identity_hash: $IDENTITY_HASH"

sqlite3 "$OUT" <<SQL
-- Schema must match Room export exactly. Column names are camelCase per Room codegen.
CREATE TABLE IF NOT EXISTS \`ingredients\` (
    \`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    \`inciName\` TEXT NOT NULL,
    \`commonName\` TEXT NOT NULL,
    \`safetyTag\` TEXT NOT NULL,
    \`healthMechanism\` TEXT NOT NULL,
    \`affectedPopulation\` TEXT NOT NULL,
    \`doseThreshold\` TEXT NOT NULL,
    \`euStatus\` TEXT NOT NULL,
    \`usStatus\` TEXT NOT NULL,
    \`cnStatus\` TEXT NOT NULL,
    \`jpStatus\` TEXT NOT NULL,
    \`citationIds\` TEXT NOT NULL,
    \`confidence\` REAL NOT NULL,
    \`dataValidAsOf\` TEXT NOT NULL
);

-- FTS4 shadow table per Room @Fts4(contentEntity = ...) codegen.
CREATE VIRTUAL TABLE IF NOT EXISTS \`ingredients_fts\`
    USING FTS4(\`inciName\` TEXT NOT NULL, content=\`ingredients\`);

-- Room version metadata table — required for createFromAsset() schema validation.
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$IDENTITY_HASH');

-- 10 seed records (Phase 1 SC-5).
INSERT INTO ingredients (inciName, commonName, safetyTag, healthMechanism, affectedPopulation, doseThreshold, euStatus, usStatus, cnStatus, jpStatus, citationIds, confidence, dataValidAsOf) VALUES
    ('METHYLPARABEN', 'Methylparaben', 'CAUTION', 'Estrogenic activity — binds estrogen receptors; metabolizes to hydroxybenzoic acid', 'Pregnant individuals; infants under 3 months', 'EU limit: 0.4% (single ester), 0.8% (mixed esters)', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1482_12"]', 0.92, '2024-03-15'),
    ('PROPYLPARABEN', 'Propylparaben', 'CAUTION', 'Stronger estrogenic activity than methylparaben', 'Pregnant individuals; infants', 'EU limit: 0.14% (single ester)', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1446_11"]', 0.90, '2024-03-15'),
    ('RETINOL', 'Vitamin A (retinol)', 'CAUTION', 'Teratogenic potential at systemic exposure; recommended avoidance during pregnancy', 'Pregnant individuals', 'EU max 0.05% face leave-on (SCCS 2022)', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1639_22"]', 0.95, '2024-03-15'),
    ('RETINYL_PALMITATE', 'Retinyl palmitate', 'CAUTION', 'Converts to retinol in skin; same teratogenicity concern', 'Pregnant individuals', 'EU max 0.05% face leave-on', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1639_22"]', 0.93, '2024-03-15'),
    ('OXYBENZONE', 'Benzophenone-3', 'DANGER', 'Endocrine disruptor; coral reef toxicity; possible photosensitizer', 'Pregnant individuals; reef-area populations', 'EU max 6%; banned in Hawaii / Palau / Key West', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1625_20"]', 0.94, '2024-03-15'),
    ('AVOBENZONE', 'Butyl methoxydibenzoylmethane', 'EXPLAIN', 'UVA filter; photo-unstable alone; commonly stabilized', 'General', 'EU max 5%; US OTC monograph max 3%', 'ALLOWED', 'ALLOWED', 'ALLOWED', 'ALLOWED', '["FDA_OTC_2021"]', 0.88, '2024-03-15'),
    ('NIACINAMIDE', 'Vitamin B3 (niacinamide)', 'EXPLAIN', 'Anti-inflammatory; barrier-strengthening; well tolerated', 'General', 'No restriction', 'ALLOWED', 'ALLOWED', 'ALLOWED', 'ALLOWED', '["CIR_NIACINAMIDE_2017"]', 0.96, '2024-03-15'),
    ('SALICYLIC_ACID', 'Beta hydroxy acid', 'CAUTION', 'Salicylate; systemic absorption concern in pregnancy', 'Pregnant individuals; sensitive skin', 'EU max 2% leave-on; max 3% rinse-off', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1646_22"]', 0.91, '2024-03-15'),
    ('HYDROQUINONE', 'Hydroquinone', 'DANGER', 'Cytotoxic; ochronosis with prolonged use; banned in cosmetics', 'General', 'EU prohibited (Annex II); US OTC limit 2%', 'PROHIBITED', 'RESTRICTED', 'PROHIBITED', 'PROHIBITED', '["EC_1223_2009"]', 0.97, '2024-03-15'),
    ('PHENOXYETHANOL', 'Phenoxyethanol', 'CAUTION', 'Possible CNS depressant in infants at high concentration', 'Infants under 6 months', 'EU max 1%; SCCS 2016 reaffirmed', 'RESTRICTED', 'ALLOWED', 'RESTRICTED', 'RESTRICTED', '["SCCS_1575_16"]', 0.89, '2024-03-15');

-- Backfill FTS shadow table from main table (Room-style content sync).
INSERT INTO ingredients_fts(rowid, inciName) SELECT id, inciName FROM ingredients;

SELECT 'rows in ingredients: ' || count(*) FROM ingredients;
SQL

echo "Seed DB written to $OUT"
sqlite3 "$OUT" "SELECT count(*) FROM ingredients;"
