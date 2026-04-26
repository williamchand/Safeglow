package com.safeglow.edge

import com.safeglow.edge.data.knowledge.db.IngredientDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Phase 1 SC-5: Room.createFromAsset opens knowledge_base.db; getAll returns 10 seed records.
 *
 * Failure modes covered:
 * - Asset missing → createFromAsset fails on first open
 * - Schema mismatch (Pitfall 4) → IllegalStateException("Pre-packaged database has an invalid schema")
 * - Hilt graph cannot inject IngredientDao → graph compilation regression
 */
@HiltAndroidTest
class RoomSeedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dao: IngredientDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun tenSeedRecordsReadable() = runBlocking {
        val records = dao.getAll()
        assertTrue(
            "Expected at least 10 seed records, got ${records.size}",
            records.size >= 10
        )
    }

    @Test
    fun methylparabenSeedRecordPresentWithExpectedTag() = runBlocking {
        val mp = dao.findExact("METHYLPARABEN")
        assertTrue("METHYLPARABEN must be present in seed DB", mp != null)
        assertEquals("METHYLPARABEN safetyTag must be CAUTION", "CAUTION", mp!!.safetyTag)
    }
}
