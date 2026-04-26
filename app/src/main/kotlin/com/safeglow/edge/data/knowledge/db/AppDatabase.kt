package com.safeglow.edge.data.knowledge.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.safeglow.edge.data.knowledge.db.entities.IngredientFts
import com.safeglow.edge.data.knowledge.db.entities.IngredientRecord

/**
 * exportSchema = true is REQUIRED so KSP writes
 * schemas/com.safeglow.edge.data.knowledge.db.AppDatabase/1.json after compile.
 * That JSON is the authoritative reference when building the prebuilt
 * knowledge_base.db asset in SQLite Browser / via the build_seed_db.sh script.
 */
@Database(
    entities = [IngredientRecord::class, IngredientFts::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ingredientDao(): IngredientDao
}
