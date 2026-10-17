package com.parcha.spoilnot.feature.pantry.domain

import java.time.LocalDate

/**
 * Learning shelf life (DESIGN 5.2): a product's typical shelf life is the median of (expiry date minus date
 * added) over its last 5 batches. It is only used to pre-fill the expiry date.
 */
object ShelfLife {
    const val WINDOW = 5

    /** The new list of recent shelf lives after a batch added on [added] with [expiry]. */
    fun record(recent: List<Int>, added: LocalDate, expiry: LocalDate?): List<Int> {
        if (expiry == null) return recent
        val days = ExpiryStatus.daysUntil(expiry, added)
        if (days < 0) return recent
        return (recent + days.toInt()).takeLast(WINDOW)
    }

    /** Median, rounding the mean of the two middle values half up for an even count. Null when empty. */
    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid] + 1) / 2
    }

    /** The pre-filled expiry date for a new batch, or null when nothing is known. */
    fun suggestExpiry(typicalDays: Int?, today: LocalDate): LocalDate? =
        typicalDays?.let { today.plusDays(it.toLong()) }
}
