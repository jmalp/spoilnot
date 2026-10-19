package com.parcha.spoilnot.core.firebase

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a collection is read in full and where a changes-only read starts (DESIGN 9.8). */
class DeltaSyncTest {
    private val now = Instant.parse("2026-10-18T20:00:00Z")
    private val newest = Instant.parse("2026-10-18T19:00:00Z")

    @Test
    fun `a recent full read means only changes since the newest cached one, minus a margin`() {
        val lastFull = now.minus(Duration.ofHours(3))
        assertEquals(newest.minus(DeltaSync.CURSOR_MARGIN), DeltaSync.cursor(newest, lastFull, now))
    }

    @Test
    fun `an empty cache or no full read on record reads everything`() {
        assertNull(DeltaSync.cursor(null, now.minus(Duration.ofHours(1)), now))
        assertNull(DeltaSync.cursor(newest, null, now))
    }

    @Test
    fun `a full read at least every 20 hours`() {
        assertNull(DeltaSync.cursor(newest, now.minus(Duration.ofHours(20)), now))
        assertEquals(
            newest.minus(DeltaSync.CURSOR_MARGIN),
            DeltaSync.cursor(newest, now.minus(Duration.ofHours(19)), now),
        )
    }

    @Test
    fun `a clock that moved back reads everything`() {
        assertNull(DeltaSync.cursor(newest, now.plus(Duration.ofHours(1)), now))
    }

    @Test
    fun `tombstones outlive the full-read interval`() {
        // A phone that misses a tombstone always does a full read first, which drops the deleted document.
        assertTrue(Duration.ofDays(Tombstones.KEEP_DAYS) > DeltaSync.FULL_SYNC_EVERY)
    }
}
