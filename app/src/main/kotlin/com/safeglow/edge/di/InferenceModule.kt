package com.safeglow.edge.di

import android.content.Context
import com.safeglow.edge.data.inference.LiteRTInferenceService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * @Singleton-scoped binding for LiteRTInferenceService.
 *
 * SingletonComponent lifecycle is bound to Application — survives screen rotation.
 * Phase 1 SC-4 verified by HiltRotationTest (re-injection returns same instance).
 *
 * Always inject @ApplicationContext, never Activity (Pitfall in RESEARCH.md line 377):
 * an Activity-scoped Singleton leaks the Activity and re-initializes the model on
 * rotation, defeating the entire @Singleton design.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideLiteRTInferenceService(
        @ApplicationContext context: Context
    ): LiteRTInferenceService = LiteRTInferenceService(context)
}
