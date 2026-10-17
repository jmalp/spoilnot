package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameKeyAndShelfLifeTest {
    @Test
    fun `name key lowercases, trims, collapses spaces and drops diacritics`() {
        assertEquals("creme fraiche", NameKey.of("  Crème   Fraîche "))
        assertEquals("jalapeno", NameKey.of("JALAPEÑO"))
        assertEquals(NameKey.of("Greek yogurt"), NameKey.of("greek  YOGURT"))
        assertEquals("Greek yogurt", NameKey.clean("  Greek   yogurt "))
    }

    @Test
    fun `shelf life keeps the last 5 and ignores undated or backdated batches`() {
        var recent = emptyList<Int>()
        listOf(3L, 5, 7, 9, 11, 13).forEach { recent = ShelfLife.record(recent, TODAY, TODAY.plusDays(it)) }
        assertEquals(listOf(5, 7, 9, 11, 13), recent)
        assertEquals(recent, ShelfLife.record(recent, TODAY, null))
        assertEquals(recent, ShelfLife.record(recent, TODAY, TODAY.minusDays(1)))
    }

    @Test
    fun `median of odd and even counts`() {
        assertNull(ShelfLife.median(emptyList()))
        assertEquals(7, ShelfLife.median(listOf(7)))
        assertEquals(7, ShelfLife.median(listOf(30, 3, 7)))
        assertEquals(6, ShelfLife.median(listOf(5, 7)))
        assertEquals(7, ShelfLife.median(listOf(5, 8)))
        assertEquals(9, ShelfLife.median(listOf(13, 5, 9, 11, 7)))
    }

    @Test
    fun `suggested expiry comes from the typical shelf life`() {
        assertEquals(TODAY.plusDays(7), ShelfLife.suggestExpiry(7, TODAY))
        assertNull(ShelfLife.suggestExpiry(null, TODAY))
    }
}
