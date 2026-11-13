package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** This phone's notifications (DESIGN 10.2): `SystemNotificationAccess` in the app, a fake in instrumented tests. */
interface NotificationAccess {
    /** Notifications can appear: the permission (Android 13 and later), the app's switch and the expiry channel. */
    fun allowed(): Boolean

    /** Shows the local "You're set" notification; false when notifications can't appear. */
    fun showYoureSet(content: YoureSet): Boolean
}

/**
 * Right after Turn on (DESIGN 5.4; owner's decision 2): when the phone now allows notifications and the member's daily
 * reminder is on, shows "You're set" with the reminder time and tomorrow's items from the pantry as this phone has it.
 */
class YoureSetSender @Inject constructor(
    private val session: SessionManager,
    private val pantry: PantryRepository,
    private val prefs: NotificationPrefsRepository,
    private val access: NotificationAccess,
    private val clock: Clock,
) {
    /** True when the notification was shown. */
    suspend fun afterTurnOn(): Boolean {
        if (!access.allowed()) return false
        val me = session.session.value as? Session.InHousehold ?: return false
        val content = withTimeoutOrNull(READ_WAIT_MS) {
            val reminder = prefs.observe(me.user.uid).first()
            if (!reminder.enabled) return@withTimeoutOrNull null
            YoureSet.of(reminder, pantry.observe(me.householdId).first().batches, LocalDate.now(clock))
        } ?: return false
        return access.showYoureSet(content)
    }

    private companion object {
        /** The pantry and the settings are usually in the cache already; offline with nothing cached, no notification. */
        const val READ_WAIT_MS = 5_000L
    }
}
