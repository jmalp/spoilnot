package com.parcha.spoilnot.feature.update.domain

import com.google.firebase.firestore.FirebaseFirestoreException
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.QuotaSignal
import com.parcha.spoilnot.core.firebase.toAppError
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGateTest {
    @Test
    fun `only builds older than minSupportedBuild must update`() {
        assertFalse(UpdateGate.required(null, 5))
        assertFalse(UpdateGate.required(AppConfig(1, null, null), 5))
        assertFalse(UpdateGate.required(AppConfig(5, null, null), 5))
        assertTrue(UpdateGate.required(AppConfig(6, "Please update", null), 5))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuotaSignalTest {
    private fun at(utc: String) = Clock.fixed(Instant.parse(utc), ZoneOffset.UTC)

    @Test
    fun `the quota banner lasts until midnight Pacific time`() {
        // 07:30 UTC on Oct 18 is still Oct 18 00:30 in Los Angeles (PDT, UTC-7).
        QuotaSignal.report(at("2026-10-18T07:30:00Z"))
        assertTrue(QuotaSignal.isExhausted(at("2026-10-19T06:59:00Z")))
        assertFalse(QuotaSignal.isExhausted(at("2026-10-19T07:01:00Z")))
    }

    @Test
    fun `a resource-exhausted error raises the banner`() {
        QuotaSignal.reset()
        val error = FirebaseFirestoreException("Quota exceeded.", FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)
        assertTrue(error.toAppError() is AppError.QuotaExceeded)
        assertTrue(QuotaSignal.isExhausted())
        QuotaSignal.reset()
    }

    @Test
    fun `the banner turns itself off at midnight Pacific without a redraw`() = runTest {
        // A clock that follows the test's virtual time, starting at 23:58 in Los Angeles (PDT).
        val start = Instant.parse("2026-10-19T06:58:00Z")
        val clock = object : Clock() {
            override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)

            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = fixed(instant(), zone)
        }
        QuotaSignal.reset()
        val shown = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            QuotaSignal.banner(clock).collect {
                shown +=
                    it
            }
        }
        assertEquals(listOf(false), shown)

        QuotaSignal.report(clock)
        assertEquals(listOf(false, true), shown)
        assertEquals(Duration.ofMinutes(2), QuotaSignal.untilReset(clock))

        advanceTimeBy(Duration.ofSeconds(119).toMillis())
        assertEquals(listOf(false, true), shown)
        advanceTimeBy(Duration.ofSeconds(2).toMillis())
        assertEquals(listOf(false, true, false), shown)
        QuotaSignal.reset()
    }
}
