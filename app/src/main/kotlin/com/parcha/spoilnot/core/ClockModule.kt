package com.parcha.spoilnot.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Business logic takes an injectable [Clock] so tests can fix "today" (DESIGN 7.2). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = CurrentZoneClock
}

/**
 * The system clock in the phone's time zone as it is now (after v1, LOGIC-11). `Clock.systemDefaultZone()` keeps the
 * zone it was created in, so a screen open across a flight would work out "today" in the old zone until it was
 * created again. Android resets the default zone when the phone's time zone changes; this reads it on every call.
 */
object CurrentZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
