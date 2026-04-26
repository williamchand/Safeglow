package com.safeglow.edge.data.knowledge.db.entities

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 shadow table for full-text search on inciName.
 *
 * Room has @Fts4 only — @Fts5 does not exist in the Room annotation processor.
 * @Fts4 with contentEntity creates a shadow table whose rowid maps 1:1 to the
 * primary entity's rowid; queries JOIN on that.
 *
 * Available at minSdk 26 (SQLite 3.9+).
 */
@Fts4(contentEntity = IngredientRecord::class)
@Entity(tableName = "ingredients_fts")
data class IngredientFts(
    val inciName: String
)
