package com.parcha.spoilnot.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Business logic takes an injectable [Clock] so tests can fix "today" (DESIGN 7.2). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
