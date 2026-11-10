package com.parcha.spoilnot.core.error

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * Raised when Firestore answers "resource exhausted" (DESIGN 6.7, 7.5). The free quota resets at midnight Pacific
 * time, so the banner holds until that day ends.
 */
object QuotaSignal {
    val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

    /** How often the banner checks the clock while it shows; a delay doesn't count the time a phone sleeps. */
    private val RECHECK_EVERY: Duration = Duration.ofMinutes(1)

    private val _exhaustedOn = MutableStateFlow<LocalDate?>(null)

    /** The quota day (Pacific) on which the limit was reached. */
    val exhaustedOn: StateFlow<LocalDate?> = _exhaustedOn.asStateFlow()

    fun report(clock: Clock = Clock.systemUTC()) {
        _exhaustedOn.value = quotaDay(clock)
    }

    fun isExhausted(clock: Clock = Clock.systemUTC()): Boolean = _exhaustedOn.value == quotaDay(clock)

    /** Time left until the quota resets (midnight Pacific after [clock]'s instant). */
    fun untilReset(clock: Clock): Duration {
        val reset = quotaDay(clock).plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant()
        return Duration.between(clock.instant(), reset)
    }

    /**
     * Whether the quota banner shows (after v1, REL-02): true from a report until midnight Pacific, when it turns
     * itself off without waiting for anything else to redraw the screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun banner(clock: Clock): Flow<Boolean> = exhaustedOn
        .transformLatest {
            while (isExhausted(clock)) {
                emit(true)
                delay(minOf(untilReset(clock), RECHECK_EVERY).toMillis().coerceAtLeast(1))
            }
            emit(false)
        }
        .distinctUntilChanged()

    /** Tests only: forget the report. */
    internal fun reset() {
        _exhaustedOn.value = null
    }

    private fun quotaDay(clock: Clock): LocalDate = LocalDate.now(clock.withZone(QUOTA_ZONE))
}
