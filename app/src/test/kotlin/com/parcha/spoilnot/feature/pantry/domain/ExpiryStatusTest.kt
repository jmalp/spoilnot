package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DESIGN 5.2 status rules table, with a fixed today. */
class ExpiryStatusTest {
    private fun status(days: Long?) = ExpiryStatus.of(days?.let { TODAY.plusDays(it) }, TODAY)

    @Test
    fun `every row of the status table`() {
        assertEquals(ExpiryStatus.Expired, status(-30))
        assertEquals(ExpiryStatus.Expired, status(-1))
        assertEquals(ExpiryStatus.Today, status(0))
        assertEquals(ExpiryStatus.Tomorrow, status(1))
        assertEquals(ExpiryStatus.Soon, status(2))
        assertEquals(ExpiryStatus.Soon, status(3))
        assertEquals(ExpiryStatus.Week, status(4))
        assertEquals(ExpiryStatus.Week, status(7))
        assertEquals(ExpiryStatus.Fresh, status(8))
        assertEquals(ExpiryStatus.Fresh, status(400))
        assertEquals(ExpiryStatus.NoDate, status(null))
    }

    @Test
    fun `calendar days across month, year and DST boundaries`() {
        assertEquals(1, ExpiryStatus.daysUntil(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 31)))
        assertEquals(1, ExpiryStatus.daysUntil(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)))
        // US DST ends on 2026-11-01: still one calendar day, not 23 or 25 hours.
        assertEquals(ExpiryStatus.Tomorrow, ExpiryStatus.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
        assertEquals(366, ExpiryStatus.daysUntil(LocalDate.of(2029, 1, 1), LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun `needs attention means expired or within 3 days`() {
        listOf(-1L, 0, 1, 2, 3).forEach { assertTrue("$it", status(it).needsAttention) }
        listOf(4L, 8).forEach { assertFalse("$it", status(it).needsAttention) }
        assertFalse(status(null).needsAttention)
    }
}
