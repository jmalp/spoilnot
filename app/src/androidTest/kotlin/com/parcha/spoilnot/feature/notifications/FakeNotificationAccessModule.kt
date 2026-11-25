package com.parcha.spoilnot.feature.notifications

import com.parcha.spoilnot.feature.notifications.domain.NotificationAccess
import com.parcha.spoilnot.feature.notifications.domain.YoureSet
import com.parcha.spoilnot.feature.notifications.ui.NotificationAccessModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.Collections
import javax.inject.Singleton

/**
 * Whether notifications appear on the test phone is the emulator's state, which a test can't set back without killing
 * the app. Tests choose it here instead; allowed by default, so the notification explanation (DESIGN 5.4) never covers
 * the screen in tests that aren't about it. `RemindersUiTest` blocks it.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NotificationAccessModule::class])
object FakeNotificationAccessModule {
    @Provides
    @Singleton
    fun fake(): FakeNotificationAccess = FakeNotificationAccess()

    @Provides
    fun access(fake: FakeNotificationAccess): NotificationAccess = fake
}

class FakeNotificationAccess : NotificationAccess {
    @Volatile var allowed: Boolean = true

    /** Every "You're set" shown, in order. */
    val youreSet: MutableList<YoureSet> = Collections.synchronizedList(mutableListOf())

    override fun allowed(): Boolean = allowed

    override fun showYoureSet(content: YoureSet): Boolean {
        if (!allowed) return false
        youreSet += content
        return true
    }
}
