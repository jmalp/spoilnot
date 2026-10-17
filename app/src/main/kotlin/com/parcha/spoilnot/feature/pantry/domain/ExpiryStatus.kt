package com.parcha.spoilnot.feature.pantry.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Derived from the expiry date in the user's local calendar (DESIGN 5.2 status rules). */
enum class ExpiryStatus {
    Expired,
    Today,
    Tomorrow,

    /** 2 to 3 days. */
    Soon,

    /** 4 to 7 days. */
    Week,
    Fresh,
    NoDate,
    ;

    /** Within 3 days or already past: what the Expiring soon filter and Home card show. */
    val needsAttention: Boolean get() = this == Expired || this == Today || this == Tomorrow || this == Soon

    companion object {
        /** Calendar days from [today] to [expiry]; negative once expired. */
        fun daysUntil(expiry: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiry)

        fun of(expiry: LocalDate?, today: LocalDate): ExpiryStatus {
            if (expiry == null) return NoDate
            val days = daysUntil(expiry, today)
            return when {
                days < 0 -> Expired
                days == 0L -> Today
                days == 1L -> Tomorrow
                days <= 3 -> Soon
                days <= 7 -> Week
                else -> Fresh
            }
        }
    }
}
