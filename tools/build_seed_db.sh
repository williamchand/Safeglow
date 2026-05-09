#!/usr/bin/env bash
# Build the prebuilt knowledge_base.db Room asset.
# Phase 1: 10 seed records. Phase 2: expanded to 80 records (DATA-01).
#
# Column names MUST match the Room-exported schema (camelCase). Mismatch will trigger
# IllegalStateException("Pre-packaged database has an invalid schema") on first open.
#
# Usage: ./tools/build_seed_db.sh
# Requires: sqlite3 (system binary)
#
# DATA AUTHORING NOTE (Phase 2 / DATA-01):
# This script contains 10 authored records from Phase 1 plus 70 placeholder INSERT
# statements. Each placeholder is marked with a TODO comment indicating which
# ingredient category it belongs to. Operator must:
#   1. Replace each TODO placeholder with a real INSERT sourced from SCCS/CIR/ACOG/FDA.
#   2. Run ./tools/build_seed_db.sh to rebuild the asset DB.
#   3. Confirm the verification step at the bottom outputs exactly 80.
# Priority categories (RESEARCH.md):
#   parabens (×6 additional), retinoids (×4), sunscreen filters (×15),
#   fragrances/allergens (×20), preservatives (×10), solvents/humectants (×10),
#   surfactants/emulsifiers (×5)
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

-- ============================================================
-- PHASE 1 SEED RECORDS (10 rows — authored, do not remove)
-- ============================================================
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

-- ============================================================
-- PHASE 2 EXPANSION: 70 additional rows (DATA-01)
-- Operator: replace each TODO block with a real INSERT.
-- Source citations from: SCCS opinions, CIR reports, FDA OTC monographs,
-- ACOG guidance, IARC/EPA classification. All public domain.
-- Template:
--   INSERT INTO ingredients (inciName, commonName, safetyTag, healthMechanism,
--     affectedPopulation, doseThreshold, euStatus, usStatus, cnStatus, jpStatus,
--     citationIds, confidence, dataValidAsOf) VALUES ('...', ...);
-- ============================================================

-- === PARABENS (6 additional: ethylparaben, butylparaben, isobutylparaben,
--     isopropylparaben, benzylparaben, pentylparaben) ===
-- TODO [paraben-2]: INSERT for ETHYLPARABEN
-- TODO [paraben-3]: INSERT for BUTYLPARABEN
-- TODO [paraben-4]: INSERT for ISOBUTYLPARABEN
-- TODO [paraben-5]: INSERT for ISOPROPYLPARABEN
-- TODO [paraben-6]: INSERT for BENZYLPARABEN
-- TODO [paraben-7]: INSERT for PENTYLPARABEN

-- === RETINOIDS (4 additional: retinaldehyde, tretinoin, retinyl acetate, adapalene) ===
-- TODO [retinoid-2]: INSERT for RETINAL (retinaldehyde)
-- TODO [retinoid-3]: INSERT for TRETINOIN
-- TODO [retinoid-4]: INSERT for RETINYL ACETATE
-- TODO [retinoid-5]: INSERT for ADAPALENE

-- === SUNSCREEN FILTERS (15: ethylhexyl methoxycinnamate, octocrylene, zinc oxide,
--     titanium dioxide, homosalate, ensulizole, mexoryl SX, mexoryl XL,
--     tinosorb S, tinosorb M, drometrizole trisiloxane, bemotrizinol,
--     bisoctrizole, bisdisulizole disodium, ecamsule) ===
-- TODO [sunscreen-01]: INSERT for ETHYLHEXYL METHOXYCINNAMATE
-- TODO [sunscreen-02]: INSERT for OCTOCRYLENE
-- TODO [sunscreen-03]: INSERT for ZINC OXIDE
-- TODO [sunscreen-04]: INSERT for TITANIUM DIOXIDE
-- TODO [sunscreen-05]: INSERT for HOMOSALATE
-- TODO [sunscreen-06]: INSERT for ENSULIZOLE
-- TODO [sunscreen-07]: INSERT for MEXORYL SX (TEREPHTHALYLIDENE DICAMPHOR SULFONIC ACID)
-- TODO [sunscreen-08]: INSERT for MEXORYL XL (DROMETRIZOLE TRISILOXANE)
-- TODO [sunscreen-09]: INSERT for TINOSORB S (BIS-ETHYLHEXYLOXYPHENOL METHOXYPHENYL TRIAZINE)
-- TODO [sunscreen-10]: INSERT for TINOSORB M (METHYLENE BIS-BENZOTRIAZOLYL TETRAMETHYLBUTYLPHENOL)
-- TODO [sunscreen-11]: INSERT for BEMOTRIZINOL
-- TODO [sunscreen-12]: INSERT for BISOCTRIZOLE
-- TODO [sunscreen-13]: INSERT for BISDISULIZOLE DISODIUM
-- TODO [sunscreen-14]: INSERT for ECAMSULE
-- TODO [sunscreen-15]: INSERT for DIOXYBENZONE

-- === FRAGRANCES / FRAGRANCE ALLERGENS (20: parfum + 19 SCCS 26 allergens) ===
-- TODO [fragrance-01]: INSERT for PARFUM (fragrance mix concept entry)
-- TODO [fragrance-02]: INSERT for LINALOOL
-- TODO [fragrance-03]: INSERT for LIMONENE
-- TODO [fragrance-04]: INSERT for EUGENOL
-- TODO [fragrance-05]: INSERT for CITRONELLOL
-- TODO [fragrance-06]: INSERT for GERANIOL
-- TODO [fragrance-07]: INSERT for COUMARIN
-- TODO [fragrance-08]: INSERT for CINNAMAL
-- TODO [fragrance-09]: INSERT for BENZYL ALCOHOL
-- TODO [fragrance-10]: INSERT for BENZYL SALICYLATE
-- TODO [fragrance-11]: INSERT for BENZYL BENZOATE
-- TODO [fragrance-12]: INSERT for CINNAMYL ALCOHOL
-- TODO [fragrance-13]: INSERT for FARNESOL
-- TODO [fragrance-14]: INSERT for HYDROXYCITRONELLAL
-- TODO [fragrance-15]: INSERT for ISOEUGENOL
-- TODO [fragrance-16]: INSERT for AMYL CINNAMAL
-- TODO [fragrance-17]: INSERT for AMYLCINNAMYL ALCOHOL
-- TODO [fragrance-18]: INSERT for ANISE ALCOHOL
-- TODO [fragrance-19]: INSERT for BENZYL CINNAMATE
-- TODO [fragrance-20]: INSERT for ALPHA-ISOMETHYL IONONE

-- === PRESERVATIVES (10: chlorphenesin, caprylyl glycol, dehydroacetic acid,
--     benzalkonium chloride, DMDM hydantoin, formaldehyde, imidazolidinyl urea,
--     diazolidinyl urea, methylisothiazolinone, chloromethylisothiazolinone) ===
-- TODO [preservative-01]: INSERT for CHLORPHENESIN
-- TODO [preservative-02]: INSERT for CAPRYLYL GLYCOL
-- TODO [preservative-03]: INSERT for DEHYDROACETIC ACID
-- TODO [preservative-04]: INSERT for BENZALKONIUM CHLORIDE
-- TODO [preservative-05]: INSERT for DMDM HYDANTOIN
-- TODO [preservative-06]: INSERT for FORMALDEHYDE
-- TODO [preservative-07]: INSERT for IMIDAZOLIDINYL UREA
-- TODO [preservative-08]: INSERT for DIAZOLIDINYL UREA
-- TODO [preservative-09]: INSERT for METHYLISOTHIAZOLINONE
-- TODO [preservative-10]: INSERT for METHYLCHLOROISOTHIAZOLINONE

-- === SOLVENTS / HUMECTANTS (10: glycerol, propylene glycol, butylene glycol,
--     pentylene glycol, hexylene glycol, dipropylene glycol, caprylyl methicone,
--     cyclopentasiloxane, dimethicone, isopropyl myristate) ===
-- TODO [solvent-01]: INSERT for GLYCEROL
-- TODO [solvent-02]: INSERT for PROPYLENE GLYCOL
-- TODO [solvent-03]: INSERT for BUTYLENE GLYCOL
-- TODO [solvent-04]: INSERT for PENTYLENE GLYCOL
-- TODO [solvent-05]: INSERT for HEXYLENE GLYCOL
-- TODO [solvent-06]: INSERT for DIPROPYLENE GLYCOL
-- TODO [solvent-07]: INSERT for CYCLOPENTASILOXANE
-- TODO [solvent-08]: INSERT for CYCLOTETRASILOXANE
-- TODO [solvent-09]: INSERT for DIMETHICONE
-- TODO [solvent-10]: INSERT for ISOPROPYL MYRISTATE

-- === SURFACTANTS / EMULSIFIERS (5: sodium lauryl sulfate, sodium laureth sulfate,
--     cocamidopropyl betaine, polysorbate 80, cetearyl alcohol) ===
-- TODO [surfactant-01]: INSERT for SODIUM LAURYL SULFATE
-- TODO [surfactant-02]: INSERT for SODIUM LAURETH SULFATE
-- TODO [surfactant-03]: INSERT for COCAMIDOPROPYL BETAINE
-- TODO [surfactant-04]: INSERT for POLYSORBATE 80
-- TODO [surfactant-05]: INSERT for CETEARYL ALCOHOL

-- ============================================================
-- FTS backfill — must run after ALL inserts above
-- ============================================================
-- Backfill FTS shadow table from main table (Room-style content sync).
INSERT INTO ingredients_fts(rowid, inciName) SELECT id, inciName FROM ingredients;

SELECT 'rows in ingredients: ' || count(*) FROM ingredients;
SQL

echo "Seed DB written to $OUT"

# ============================================================
# VERIFICATION STEP (DATA-01 gate)
# When all 70 TODO placeholders above are replaced with real INSERTs,
# this command must output exactly 80.
# ============================================================
ROW_COUNT=$(sqlite3 "$OUT" "SELECT COUNT(*) FROM ingredient;")
echo "Row count in ingredients table: $ROW_COUNT"
if [ "$ROW_COUNT" -eq 80 ]; then
  echo "DATA-01 PASSED: knowledge_base.db contains exactly 80 ingredient rows."
else
  echo "DATA-01 PENDING: $ROW_COUNT/80 rows authored. Replace TODO placeholders above to complete DATA-01."
fi
