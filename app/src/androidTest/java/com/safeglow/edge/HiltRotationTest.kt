package com.safeglow.edge

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Phase 1 SC-4: @Singleton LiteRTInferenceService survives screen rotation (re-injection returns same instance).
 * STUB — implementation lands in Plan 03 once LiteRTInferenceService is bound in InferenceModule.
 */
@HiltAndroidTest
class HiltRotationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Test
    @Ignore("Awaiting Plan 03: LiteRTInferenceService Hilt @Singleton binding. Test must inject twice and assertSame on the references.")
    fun singletonSurvivesReInjection() {
        // TODO Plan 03: hiltRule.inject() twice, assertSame(first, second).
    }
}
