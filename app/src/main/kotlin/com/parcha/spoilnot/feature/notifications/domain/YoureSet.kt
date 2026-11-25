package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.feature.pantry.domain.NaturalOrder
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The local "You're set" notification shown right after Turn on (DESIGN 5.4; owner's decision 2, 2026-11-08, review
 * UX-03): when reminders come, and what tomorrow's reminder would name. The first real reminder comes only with the
 * reminder job's next run after the chosen hour (10.3), hours later at best, so this one shows at once that it works.
 *
 * Tomorrow's items are what the member's own stages pick for tomorrow (3, 2, 1 or 0 days before), soonest first, one
 * name per product; expired food is left out, since whether it is named again depends on what was already reported.
 */
data class YoureSet(
    /** The member's reminder hour, 0 to 23. */
    val hour: Int,
    /** Up to [NAMES] product names, soonest first. */
    val tomorrow: List<String>,
    /** Products tomorrow's reminder names beyond [tomorrow]. */
    val more: Int,
) {
    companion object {
        const val NAMES = 3

        /** Pure: [today] comes from the injected `Clock`. */
        fun of(prefs: NotificationPrefs, batches: List<StockBatch>, today: LocalDate): YoureSet {
            val tomorrow = today.plusDays(1)
            val due = batches
                .filter { it.quantity > 0 }
                .mapNotNull { batch ->
                    val date = batch.expiryDate ?: return@mapNotNull null
                    val days = ChronoUnit.DAYS.between(tomorrow, date).toInt()
                    if (days in prefs.stages) batch to date else null
                }
                .sortedWith(
                    compareBy<Pair<StockBatch, LocalDate>> {
                        it.second
                    }.thenBy(NaturalOrder) { it.first.productName },
                )
                .distinctBy { it.first.productId }
                .map { it.first.productName }
            return YoureSet(prefs.hour, due.take(NAMES), (due.size - NAMES).coerceAtLeast(0))
        }
    }
}
