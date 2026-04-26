package com.safeglow.edge.data.knowledge.db

import androidx.room.Dao
import androidx.room.Query
import com.safeglow.edge.data.knowledge.db.entities.IngredientRecord

@Dao
interface IngredientDao {

    /** Phase 2 entry point — exact INCI lookup after normalization. */
    @Query("SELECT * FROM ingredients WHERE inciName = :name LIMIT 1")
    suspend fun findExact(name: String): IngredientRecord?

    /** Phase 2 entry point — FTS fallback for partial / synonym matches. */
    @Query(
        "SELECT i.* FROM ingredients i " +
        "JOIN ingredients_fts fts ON i.rowid = fts.rowid " +
        "WHERE ingredients_fts MATCH :query LIMIT 10"
    )
    suspend fun ftsSearch(query: String): List<IngredientRecord>

    /** Phase 1 SC-5 validation only. */
    @Query("SELECT * FROM ingredients")
    suspend fun getAll(): List<IngredientRecord>
}
