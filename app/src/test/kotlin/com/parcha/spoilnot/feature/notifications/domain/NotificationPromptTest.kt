package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DESIGN 5.4 after v1 (review UX-02, CODE-10): when this phone explains reminders and asks. */
class NotificationPromptTest {
    private val blocked = ReminderAccess(allowed = false, remindersOn = true, explained = false)

    @Test
    fun `a household that opens with dated food asks at once`() {
        // A member who joined, or a new phone: one dated item is enough.
        assertTrue(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 1, datedNow = 1))
        // It still asks if the food was used up since the household opened.
        assertTrue(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 2, datedNow = 0))
    }

    @Test
    fun `a household that opens with none asks at the third dated item, whoever adds it`() {
        assertFalse(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 0, datedNow = 0))
        assertFalse(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 0, datedNow = 1))
        assertFalse(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 0, datedNow = 2))
        assertTrue(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 0, datedNow = NotificationPrompt.DATED_TO_ASK))
        assertTrue(NotificationPrompt.shouldAsk(blocked, datedAtOpen = 0, datedNow = 7))
    }

    @Test
    fun `nothing is asked before the household has loaded from the server`() {
        // A new phone's cache is empty at first: that is not a new household.
        assertFalse(NotificationPrompt.shouldAsk(blocked, datedAtOpen = null, datedNow = 5))
    }

    @Test
    fun `it asks once, only while blocked and only when the member wants reminders`() {
        assertFalse(NotificationPrompt.shouldAsk(blocked.copy(explained = true), 3, 3))
        assertFalse(NotificationPrompt.shouldAsk(blocked.copy(allowed = true), 3, 3))
        assertFalse(NotificationPrompt.shouldAsk(blocked.copy(remindersOn = false), 3, 3))
        // The member's settings have not loaded yet: wait rather than ask someone who turned reminders off.
        assertFalse(NotificationPrompt.shouldAsk(blocked.copy(remindersOn = null), 3, 3))
    }

    @Test
    fun `a phone that already allows notifications is marked explained without asking`() {
        assertTrue(NotificationPrompt.explainedWithoutAsking(blocked.copy(allowed = true)))
        assertFalse(NotificationPrompt.explainedWithoutAsking(blocked))
        assertFalse(NotificationPrompt.explainedWithoutAsking(blocked.copy(allowed = true, explained = true)))
    }

    @Test
    fun `Home says reminders are off once the explanation was answered and the phone still blocks them`() {
        assertTrue(NotificationPrompt.offOnThisPhone(blocked.copy(explained = true)))
        // Before the explanation, the explanation itself asks.
        assertFalse(NotificationPrompt.offOnThisPhone(blocked))
        assertFalse(NotificationPrompt.offOnThisPhone(blocked.copy(explained = true, allowed = true)))
        // Turned off in Settings on purpose: Settings already says Off.
        assertFalse(NotificationPrompt.offOnThisPhone(blocked.copy(explained = true, remindersOn = false)))
        assertFalse(NotificationPrompt.offOnThisPhone(blocked.copy(explained = true, remindersOn = null)))
    }

    @Test
    fun `Settings says Off on this phone when the reminder is on but this phone blocks it`() {
        assertTrue(NotificationPrompt.blockedHere(allowed = false, remindersOn = true))
        assertFalse(NotificationPrompt.blockedHere(allowed = true, remindersOn = true))
        assertFalse(NotificationPrompt.blockedHere(allowed = false, remindersOn = false))
        assertFalse(NotificationPrompt.blockedHere(allowed = false, remindersOn = null))
    }

    @Test
    fun `dated food is what is in stock and not yet expired, on the clock's day`() {
        val batches = listOf(
            batch("a", "milk", inDays = 0),
            batch("b", "eggs", inDays = 3),
            batch("c", "yogurt", inDays = -1),
            batch("d", "rice", inDays = null),
            batch("e", "cheese", inDays = 5, quantity = 0.0),
        )
        assertEquals(2, NotificationPrompt.datedFood(batches, TODAY))
        // The next day, today's milk has expired.
        assertEquals(1, NotificationPrompt.datedFood(batches, TODAY.plusDays(1)))
        // Callers pass today from the injected Clock, in its zone.
        val clock = Clock.fixed(
            ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant(),
            ZoneId.of("Asia/Tokyo"),
        )
        assertEquals(LocalDate.of(2026, 10, 13), LocalDate.now(clock))
        assertEquals(1, NotificationPrompt.datedFood(batches, LocalDate.now(clock)))
    }
}
