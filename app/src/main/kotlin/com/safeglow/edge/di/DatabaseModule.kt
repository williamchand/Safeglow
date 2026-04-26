package com.safeglow.edge.di

import android.content.Context
import androidx.room.Room
import com.safeglow.edge.data.knowledge.db.AppDatabase
import com.safeglow.edge.data.knowledge.db.IngredientDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * @Singleton + createFromAsset enforces that the prebuilt asset is copied to
     * internal storage exactly once on first open. Schema mismatch with the asset
     * throws IllegalStateException — Pitfall 4 in RESEARCH.md.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "knowledge_base.db")
            .createFromAsset("knowledge_base.db")
            .build()

    /** No @Singleton — Room generates a thread-safe DAO backed by the singleton DB. */
    @Provides
    fun provideIngredientDao(db: AppDatabase): IngredientDao = db.ingredientDao()
}
