package com.parcha.spoilnot.core

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentZoneClockTest {
    @Test
    fun `the injected clock follows a time zone change`() {
        // After v1, LOGIC-11: a clock made before a flight across many zones.
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val clock = ClockModule.clock()
            assertEquals(ZoneId.of("Pacific/Kiritimati"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12")) // Landed 26 hours west.
            assertEquals(ZoneId.of("Etc/GMT+12"), clock.zone)
            assertEquals(LocalDate.now(), LocalDate.now(clock))
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @Test
    fun `it can still be given a fixed zone`() {
        assertEquals(ZoneId.of("UTC"), CurrentZoneClock.withZone(ZoneId.of("UTC")).zone)
    }
}
