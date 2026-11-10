package com.parcha.spoilnot.core.firebase

import android.content.Context
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The sync log keeps, per collection, the newest change a server snapshot brought, and the cursor comes from it
 * (DESIGN 9.8, after v1 2026-11-09). What lands in the cache some other way (the phone's own writes) never reaches it.
 */
@RunWith(RobolectricTestRunner::class)
class SyncLogTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sync_log_test", Context.MODE_PRIVATE)
    private val path = "households/h1/groceryLists/l1/items"

    private fun log(at: Instant = now, margin: Duration = DeltaSync.CURSOR_MARGIN) =
        SyncLog(prefs, Clock.fixed(at, ZoneOffset.UTC), margin)

    @Test
    fun `no cursor before a full read and a received change`() {
        val log = log()
        assertNull(log.cursor(path))
        log.markFullSync(path)
        assertNull("a full read that brought nothing reads in full again", log.cursor(path))
        log.markSeen(path, now.minus(Duration.ofHours(1)))
        assertEquals(now.minus(Duration.ofHours(1)).minus(DeltaSync.CURSOR_MARGIN), log.cursor(path))
    }

    @Test
    fun `the newest change received never moves back`() {
        val log = log()
        log.markFullSync(path)
        log.markSeen(path, now.minus(Duration.ofMinutes(10)))
        log.markSeen(path, now.minus(Duration.ofHours(2)))
        assertEquals(now.minus(Duration.ofMinutes(10)), log.newestSeen(path))
    }

    @Test
    fun `each collection and meal window has its own cursor`() {
        val log = log()
        val week = "households/h1/meals?date=2026-11-09..2026-11-15"
        log.markFullSync(path)
        log.markFullSync(week)
        log.markSeen(path, now.minus(Duration.ofMinutes(1)))
        assertNull(log.newestSeen(week))
        assertNull(log.cursor(week))
        assertNull(log.newestSeen("households/h1/stock"))
    }

    @Test
    fun `the margin can be shortened for tests`() {
        val log = log(margin = Duration.ofSeconds(2))
        log.markFullSync(path)
        log.markSeen(path, now.minus(Duration.ofMinutes(1)))
        assertEquals(now.minus(Duration.ofMinutes(1)).minus(Duration.ofSeconds(2)), log.cursor(path))
    }

    @Test
    fun `the daily full read still comes after 20 hours`() {
        log().apply {
            markFullSync(path)
            markSeen(path, now)
        }
        assertNull(log(at = now.plus(DeltaSync.FULL_SYNC_EVERY)).cursor(path))
    }

    @Test
    fun `clearing forgets full reads and cursors`() {
        val log = log()
        log.markFullSync(path)
        log.markSeen(path, now)
        log.clear()
        assertNull(log.lastFullSync(path))
        assertNull(log.newestSeen(path))
        assertEquals(emptyMap<String, Any?>(), prefs.all)
    }

    @Test
    fun `the restock engine hears of a full read when it is recorded and when the log is cleared`() = runTest {
        // After v1 (B09) the engine reads the cache only and waits for a full read on record (DESIGN 9.6).
        val log = log()
        log.clear()
        val seen = mutableListOf<Instant?>()
        val job = log.fullSyncs(path).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        log.markFullSync(path)
        runCurrent()
        log.markFullSync("households/h1/stock")
        runCurrent()
        log.clear()
        runCurrent()
        job.cancel()
        assertEquals(listOf(null, now, null), seen)
    }
}
