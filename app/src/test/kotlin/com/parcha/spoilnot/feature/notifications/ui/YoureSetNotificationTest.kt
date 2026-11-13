package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.YoureSet
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The local "You're set" notification right after Turn on (DESIGN 5.4; owner's decision 2). */
@RunWith(RobolectricTestRunner::class)
class YoureSetNotificationTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun Notification.bigText() = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

    @Test
    fun `built with the reminder time and tomorrow's items`() {
        // Tomorrow (2026-10-13): Chicken on the day, Spinach a day before; milk will have expired.
        val batches = listOf(
            batch("s", "spinach", inDays = 2),
            batch("c", "chicken", inDays = 1),
            batch("m", "milk", inDays = 0),
        )
        val content = YoureSet.of(NotificationPrefs(digestMinutes = 18 * 60), batches, TODAY)
        val notification = Notifications.youreSet(app, content)

        val body = "Spoil Not reminds you once a day, around ${formatHour(18)}, when food is about to expire. " +
            "Tomorrow's reminder: Chicken, Spinach."
        assertEquals("You're set", notification.title())
        assertEquals(body, notification.text())
        // The whole sentence shows when expanded.
        assertEquals(body, notification.bigText())
        assertEquals(Notifications.CHANNEL_EXPIRY, notification.channelId)
        // It opens like a digest, on Pantry's Needs attention.
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun `more than three products end with the count, and none says so`() {
        val more = Notifications.youreSet(app, YoureSet(9, listOf("Spinach", "Chicken", "Milk"), 2))
        assertTrue(more.text().endsWith("Tomorrow's reminder: Spinach, Chicken, Milk and 2 more."))
        assertTrue(more.text().contains("around ${formatHour(9)},"))

        val none = Notifications.youreSet(app, YoureSet(7, emptyList(), 0))
        assertEquals(
            "Spoil Not reminds you once a day, around ${formatHour(7)}, when food is about to expire. " +
                "Nothing is due tomorrow.",
            none.text(),
        )
    }

    @Test
    fun `it shows only once notifications are allowed`() {
        Notifications.createChannel(app)
        val content = YoureSet(9, listOf("Spinach"), 0)
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))

        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Notifications.sendYoureSet(app, content))
        assertEquals(0, manager.allNotifications.size)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(Notifications.sendYoureSet(app, content))
        assertEquals("You're set", manager.allNotifications.single().title())
    }
}
