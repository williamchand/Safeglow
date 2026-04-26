package com.safeglow.edge.data.knowledge.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Source of truth for an ingredient knowledge-base record.
 *
 * SCHEMA LOCK: Phase 1 commits these 14 fields. Phase 2 ingests 80 records against
 * this shape; Phase 3 builds an embedding index keyed by inciName; Phase 4's
 * OutputValidator validates Gemma JSON output against this entity.
 *
 * embedding_vector is intentionally absent — pre-computed embeddings live in a
 * separate embedding_index.json file (Phase 3 concern), not in Room.
 *
 * Citations are stored as a JSON array string of citation_id values; the full
 * citation records are joined from a separate `citations` table introduced in
 * Phase 2 (out of Phase 1 scope).
 */
@Entity(tableName = "ingredients")
data class IngredientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inciName: String,
    val commonName: String,
    val safetyTag: String,           // "EXPLAIN" | "CAUTION" | "SOLVE" | "DANGER"
    val healthMechanism: String,
    val affectedPopulation: String,
    val doseThreshold: String,
    val euStatus: String,            // "ALLOWED" | "RESTRICTED" | "PROHIBITED"
    val usStatus: String,
    val cnStatus: String,
    val jpStatus: String,
    val citationIds: String,         // JSON array of citation_id strings
    val confidence: Float,
    val dataValidAsOf: String        // ISO 8601 date, e.g. "2024-03-15"
)
