package com.safeglow.edge

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Phase 1 SC-3: Engine.initialize() falls back to CPU without exception when GPU unavailable.
 * STUB — implementation lands in Plan 03.
 */
@HiltAndroidTest
class GpuFallbackTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Test
    @Ignore("Awaiting Plan 03: try/catch GPU->CPU fallback in LiteRTInferenceService.initialize(). Test must call initialize() and assert no exception propagated.")
    fun initializeDoesNotThrowOnAnyDevice() {
        // TODO Plan 03: hiltRule.inject(); inferenceService.initialize(); assert no exception.
    }
}
