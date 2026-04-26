package com.safeglow.edge

import com.safeglow.edge.data.inference.LiteRTInferenceService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Phase 1 SC-2: Engine.initialize() + first token from infer() without crash.
 *
 * Pre-condition: Gemma 4 E2B .litertlm model pre-pushed via adb to
 * /sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm
 * (see docs/MODEL_DEPLOY.md). If the file is absent, initialize() throws
 * FileNotFoundException — the test then surfaces that as a clear failure rather
 * than a hang (Pitfall 3 in RESEARCH.md).
 */
@HiltAndroidTest
class InferenceServiceTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var inferenceService: LiteRTInferenceService

    @Before
    fun setUp() { hiltRule.inject() }

    @After
    fun tearDown() { inferenceService.close() }

    @Test
    fun firstTokenProducedWithoutCrash() = runBlocking {
        inferenceService.initialize()

        val gemmaTurnPrompt =
            "<start_of_turn>user\nSay hello.\n<end_of_turn>\n<start_of_turn>model\n"

        val tokens = inferenceService.infer(gemmaTurnPrompt).take(1).toList()

        assertTrue(
            "SC-2: Expected at least one token from Gemma 4 E2B; got 0",
            tokens.isNotEmpty()
        )
    }
}
