package com.safeglow.edge

import com.safeglow.edge.data.inference.LiteRTInferenceService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Phase 1 SC-4: LiteRTInferenceService is @Singleton — re-injection from the same
 * Hilt SingletonComponent must return the exact same instance. This is the proxy
 * for "survives screen rotation without re-initializing the model" because the
 * SingletonComponent lifecycle outlives any Activity instance, so a recreated
 * Activity injecting the service receives the existing Engine.
 */
@HiltAndroidTest
class HiltRotationTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var firstReference: LiteRTInferenceService
    @Inject lateinit var secondReference: LiteRTInferenceService

    @Test
    fun singletonReturnsSameInstanceAcrossInjections() {
        hiltRule.inject()
        val a = firstReference
        val b = secondReference
        assertSame(
            "SC-4: @Singleton LiteRTInferenceService must return the same instance " +
                "across re-injections (proxy for surviving screen rotation)",
            a, b
        )
    }
}
