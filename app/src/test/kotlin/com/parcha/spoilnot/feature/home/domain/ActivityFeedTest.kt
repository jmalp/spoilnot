package com.parcha.spoilnot.feature.home.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityFeedTest {
    private fun event(id: String, type: ActivityType) =
        ActivityEvent(id, type, "u1", "Sam", null, null, null, null, null, Instant.EPOCH)

    @Test
    fun `events from before the newest clear are hidden`() {
        val events = listOf(
            event("e5", ActivityType.StockAdded),
            event("e4", ActivityType.HouseholdReset),
            event("e3", ActivityType.Discarded),
            event("e2", ActivityType.HouseholdReset),
            event("e1", ActivityType.StockAdded),
        )
        assertEquals(listOf("e5", "e4"), ActivityFeed.sinceReset(events).map { it.id })
    }

    @Test
    fun `without a clear every event shows`() {
        val events = listOf(event("e2", ActivityType.GroceryAdded), event("e1", ActivityType.StockAdded))
        assertEquals(events, ActivityFeed.sinceReset(events))
    }
}
