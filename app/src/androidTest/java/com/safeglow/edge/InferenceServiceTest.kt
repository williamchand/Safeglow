package com.safeglow.edge

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Phase 1 SC-2: LiteRTInferenceService.initialize() + first token without crash.
 * STUB — implementation lands in Plan 03 once LiteRTInferenceService exists.
 */
@HiltAndroidTest
class InferenceServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Test
    @Ignore("Awaiting Plan 03: LiteRTInferenceService implementation. Test must call initialize() then collect 1 token from infer() with Gemma turn format.")
    fun firstTokenProducedWithoutCrash() {
        // TODO Plan 03: hiltRule.inject(); inferenceService.initialize(); collect first token; assert non-empty.
    }
}
