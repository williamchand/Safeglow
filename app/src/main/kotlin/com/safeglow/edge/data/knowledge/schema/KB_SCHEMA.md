# SafeGlow Edge — Knowledge Base JSON Schema (LOCKED in Phase 1)

**Status:** LOCKED — changing this schema after Phase 1 requires regenerating the
seed `knowledge_base.db` asset, the embedding index, and Phase 4's OutputValidator.
Do not modify without a phase-level migration plan.

## Canonical IngredientRecord (JSON form)

```json
{
  "inci_name": "METHYLPARABEN",
  "common_name": "Methylparaben",
  "safety_tag": "CAUTION",
  "health_mechanism": "Estrogenic activity — binds estrogen receptors; metabolizes to hydroxybenzoic acid",
  "affected_population": "Pregnant individuals; infants under 3 months",
  "dose_threshold": "EU limit: 0.4% (single ester), 0.8% (mixed esters)",
  "jurisdictions": {
    "eu": { "status": "RESTRICTED", "regulation": "EC No 1223/2009 Annex V entry 12" },
    "us": { "status": "ALLOWED",    "regulation": "FDA 21 CFR 172.725" },
    "cn": { "status": "RESTRICTED", "regulation": "CSAR 2021 max 0.4%" },
    "jp": { "status": "RESTRICTED", "regulation": "MHLW 2023" }
  },
  "citations": [
    {
      "citation_id": "SCCS_1482_12",
      "source": "SCCS",
      "title": "SCCS Opinion on parabens (2012)",
      "url": "https://ec.europa.eu/health/scientific_committees/consumer_safety/docs/sccs_o_132.pdf"
    }
  ],
  "confidence": 0.92,
  "data_valid_as_of": "2024-03-15",
  "embedding_vector": null
}
```

## Field reference

| JSON field | Required | Room column (camelCase) | Enum / format |
|------------|----------|-------------------------|---------------|
| inci_name | yes | inciName | uppercase canonical INCI |
| common_name | yes | commonName | free text |
| safety_tag | yes | safetyTag | "EXPLAIN" \| "CAUTION" \| "SOLVE" \| "DANGER" |
| health_mechanism | yes | healthMechanism | free text |
| affected_population | yes | affectedPopulation | free text |
| dose_threshold | yes | doseThreshold | free text |
| jurisdictions.eu.status | yes | euStatus | "ALLOWED" \| "RESTRICTED" \| "PROHIBITED" |
| jurisdictions.us.status | yes | usStatus | same enum |
| jurisdictions.cn.status | yes | cnStatus | same enum |
| jurisdictions.jp.status | yes | jpStatus | same enum |
| citations[].citation_id | ≥1 | citationIds (JSON array of citation_ids) | string |
| confidence | yes | confidence | 0.0–1.0 |
| data_valid_as_of | yes | dataValidAsOf | ISO 8601 date |
| embedding_vector | no | (NOT a Room column) | float[] in embedding_index.json (Phase 3) |

## Notes

- **No PII.** All seed records contain only public regulatory data (SCCS / CIR / ACOG / IARC).
- **camelCase column names.** Room generates columns from Kotlin property names verbatim.
  The prebuilt `.db` MUST use `inciName`, not `inci_name`. The JSON form (this document)
  uses snake_case for human readability of the source data files; the ingestion pipeline
  maps snake_case → camelCase when writing to Room.
- **Citations.** Phase 2 introduces a separate `citations` table; in Phase 1 the citation
  payload is denormalized into `citationIds` (a JSON array string) so the seed asset can
  be standalone for SC-5 validation.
