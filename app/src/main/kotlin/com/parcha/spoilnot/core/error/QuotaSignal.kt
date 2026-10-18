package com.parcha.spoilnot.core.error

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raised when Firestore answers "resource exhausted" (DESIGN 6.7, 7.5). The free quota resets at midnight Pacific
 * time, so the banner holds until that day ends.
 */
object QuotaSignal {
    val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

    private val _exhaustedOn = MutableStateFlow<LocalDate?>(null)

    /** The quota day (Pacific) on which the limit was reached. */
    val exhaustedOn: StateFlow<LocalDate?> = _exhaustedOn.asStateFlow()

    fun report(clock: Clock = Clock.systemUTC()) {
        _exhaustedOn.value = LocalDate.now(clock.withZone(QUOTA_ZONE))
    }

    fun isExhausted(clock: Clock = Clock.systemUTC()): Boolean =
        _exhaustedOn.value == LocalDate.now(clock.withZone(QUOTA_ZONE))
}
