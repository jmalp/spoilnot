package com.parcha.spoilnot.feature.update.domain

import com.parcha.spoilnot.core.error.QuotaSignal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

class QuotaSignalTest {
    private fun at(utc: String) = Clock.fixed(Instant.parse(utc), ZoneOffset.UTC)

    @Test
    fun `the quota banner lasts until midnight Pacific time`() {
        // 07:30 UTC on Oct 18 is still Oct 18 00:30 in Los Angeles (PDT, UTC-7).
        QuotaSignal.report(at("2026-10-18T07:30:00Z"))
        assertTrue(QuotaSignal.isExhausted(at("2026-10-19T06:59:00Z")))
        assertFalse(QuotaSignal.isExhausted(at("2026-10-19T07:01:00Z")))
    }
}
