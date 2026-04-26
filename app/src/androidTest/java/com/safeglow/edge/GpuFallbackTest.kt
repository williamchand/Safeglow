package com.safeglow.edge

import com.safeglow.edge.data.inference.LiteRTInferenceService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Phase 1 SC-3: initialize() must NOT propagate an exception, regardless of GPU
 * availability. Devices without OpenCL (e.g. Pixel 8 Pro / Tensor G3) trigger the
 * try/catch GPU->CPU fallback inside the service. Any exception leaking out of
 * initialize() is a fatal Phase 1 regression.
 */
@HiltAndroidTest
class GpuFallbackTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var inferenceService: LiteRTInferenceService

    @Before
    fun setUp() { hiltRule.inject() }

    @After
    fun tearDown() { inferenceService.close() }

    @Test
    fun initializeDoesNotThrowOnAnyDevice() = runBlocking {
        try {
            inferenceService.initialize()
        } catch (t: Throwable) {
            fail(
                "SC-3: initialize() must catch GPU failures and fall back to CPU. " +
                    "Got: ${t::class.java.simpleName}: ${t.message}"
            )
        }
    }
}
