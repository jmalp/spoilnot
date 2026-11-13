package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import org.junit.Assert.assertEquals
import org.junit.Test

/** "You're set" after Turn on (DESIGN 5.4; owner's decision 2): the time and tomorrow's reminder. */
class YoureSetTest {
    private val prefs = NotificationPrefs()

    @Test
    fun `names what tomorrow's reminder picks, soonest first, one name per product`() {
        val batches = listOf(
            // Tomorrow is 2026-10-13: these are 0, 1 and 3 days from it.
            batch("s", "spinach", inDays = 2),
            batch("c", "chicken", inDays = 1),
            batch("c2", "chicken", inDays = 3),
            batch("y", "yogurt", inDays = 4),
            // Expires today: expired tomorrow, left out. Too far, undated or used up: left out.
            batch("m", "milk", inDays = 0),
            batch("r", "rice", inDays = 5),
            batch("f", "flour", inDays = null),
            batch("e", "eggs", inDays = 1, quantity = 0.0),
        )
        val content = YoureSet.of(prefs, batches, TODAY)
        assertEquals(9, content.hour)
        assertEquals(listOf("Chicken", "Spinach", "Yogurt"), content.tomorrow)
        assertEquals(0, content.more)
    }

    @Test
    fun `the member's own stages and hour`() {
        val batches = listOf(batch("c", "chicken", inDays = 1), batch("s", "spinach", inDays = 2))
        // Only "the day it expires" and "1 day before": tomorrow names the chicken (0) and the spinach (1).
        val content = YoureSet.of(prefs.copy(digestMinutes = 18 * 60, stages = setOf(0, 1)), batches, TODAY)
        assertEquals(18, content.hour)
        assertEquals(listOf("Chicken", "Spinach"), content.tomorrow)
        // Only 3 days before: neither.
        assertEquals(emptyList<String>(), YoureSet.of(prefs.copy(stages = setOf(3)), batches, TODAY).tomorrow)
    }

    @Test
    fun `more than three products are counted`() {
        val batches = (1..5).map { batch("b$it", "p$it", inDays = 1, name = "Item $it") }
        val content = YoureSet.of(prefs, batches, TODAY)
        assertEquals(listOf("Item 1", "Item 2", "Item 3"), content.tomorrow)
        assertEquals(2, content.more)
    }

    @Test
    fun `nothing due tomorrow`() {
        val content = YoureSet.of(prefs, listOf(batch("r", "rice", inDays = 30)), TODAY)
        assertEquals(emptyList<String>(), content.tomorrow)
        assertEquals(0, content.more)
    }
}
