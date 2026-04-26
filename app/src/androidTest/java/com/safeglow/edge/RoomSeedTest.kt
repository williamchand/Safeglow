package com.safeglow.edge

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Phase 1 SC-5: Room.createFromAsset() opens knowledge_base.db; getAll() returns >= 10 seed records.
 * STUB — implementation lands in Plan 02 once IngredientDao + AppDatabase + asset .db exist.
 */
@HiltAndroidTest
class RoomSeedTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Test
    @Ignore("Awaiting Plan 02: Room + knowledge_base.db asset. Test must inject IngredientDao and assert getAll().size >= 10.")
    fun tenSeedRecordsReadable() {
        // TODO Plan 02: hiltRule.inject(); val records = dao.getAll(); assertTrue(records.size >= 10).
    }
}
