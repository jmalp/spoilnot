package com.parcha.spoilnot.feature.notifications.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPrefsTest {
    /** Settings offers 5:00 to 21:00 and names the hour the job stops sending at (after v1, 2026-11-22). */
    @Test
    fun hours_endBeforeTheJobStopsSending() {
        assertEquals(5..21, NotificationPrefs.HOURS.first..NotificationPrefs.HOURS.last)
        // backend/reminder/src/digest.ts LATEST_MINUTES.
        assertEquals(22, NotificationPrefs.LATEST_HOUR)
    }

    @Test
    fun hour_isTheWholeHourOfTheDigestTime() {
        assertEquals(9, NotificationPrefs(digestMinutes = 9 * 60 + 30).hour)
    }
}
