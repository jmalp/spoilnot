package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate

/** Whether reminders can reach this person on this phone, as the prompt rule sees it (DESIGN 5.4). */
data class ReminderAccess(
    /** Notifications can appear on this phone: the permission, the app's switch and the expiry channel. */
    val allowed: Boolean,
    /** The member's daily reminder (`notificationPrefs.enabled`); null while it loads. */
    val remindersOn: Boolean?,
    /** This phone has explained reminders once, answered or not needed. Kept on the phone, not in Firestore. */
    val explained: Boolean,
)

/**
 * When this phone explains reminders and asks for the notification permission, and when Home says they are off (DESIGN
 * 5.4; after v1, 2026-11-12, review UX-02 and CODE-10). The rule used to sit in Pantry's Add item, so a member who
 * joined, or anyone on a new phone, was never asked.
 *
 * - A household that **opens with dated food** asks at once: a member who joined, or a new phone.
 * - A household that opens with none (a new one) asks once it has [DATED_TO_ASK] dated items, whoever added them, so
 *   the owner's first items are not interrupted and reminders already have something to say.
 * - It asks only while notifications are blocked and the member's daily reminder is on, and only once per phone. A
 *   phone that already allows notifications is marked explained without asking ([explainedWithoutAsking]).
 *
 * Pure: callers pass today's date from the injected `Clock`, so tests fix the day.
 */
object NotificationPrompt {
    /** Dated items a household that opened with none needs before the app asks (after v1, 2026-10-19). */
    const val DATED_TO_ASK = 3

    /** Dated food reminders can still warn about: purchases in stock that expire today or later. */
    fun datedFood(batches: List<StockBatch>, today: LocalDate): Int =
        batches.count { batch -> batch.quantity > 0 && batch.expiryDate?.let { !it.isBefore(today) } == true }

    /**
     * Whether to explain reminders and ask now. [datedAtOpen] is the dated food in the first pantry read from the
     * server since the household opened in this run of the app (null until it arrives, so a phone's empty cache is
     * not mistaken for a new household); [datedNow] is the dated food now.
     */
    fun shouldAsk(access: ReminderAccess, datedAtOpen: Int?, datedNow: Int): Boolean = !access.explained &&
        !access.allowed &&
        access.remindersOn == true &&
        datedAtOpen != null &&
        (datedAtOpen > 0 || datedNow >= DATED_TO_ASK)

    /** Notifications already appear: remember the explanation as not needed, so a later block shows Home's line. */
    fun explainedWithoutAsking(access: ReminderAccess): Boolean = !access.explained && access.allowed

    /**
     * Home's "Reminders are off on this phone · Turn on": the member wants reminders, this phone blocks them, and the
     * explanation was already answered (before that, the explanation itself asks).
     */
    fun offOnThisPhone(access: ReminderAccess): Boolean =
        access.explained && !access.allowed && access.remindersOn == true

    /** Settings' Notifications row: "Off on this phone" when the reminder is on but this phone blocks it. */
    fun blockedHere(allowed: Boolean, remindersOn: Boolean?): Boolean = !allowed && remindersOn == true
}
