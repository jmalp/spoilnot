package com.parcha.spoilnot.feature.pantry.domain

import java.time.LocalDate

/**
 * Needs attention (DESIGN 5.2, 5.4, 5.6; one set and one name after v1, review R8): every purchase in stock that has
 * expired or expires within [DAYS] days. Home's card, Pantry's Needs attention filter and the daily reminder's tap use
 * this set; the reminder job computes the same one (`needsAttention` in `backend/reminder/src/digest.ts`) and narrows
 * it by each member's choices. [usable] is what Home lists and Use it up cooks with; [expired] is folded into one line.
 *
 * Pure: callers pass today's date from the injected `Clock` (`todayFlow(clock)`), so tests fix the day.
 */
data class NeedsAttention(
    /** Expires today, tomorrow or in 2 to 3 days: soonest first, then by name. */
    val usable: List<PantryItem.Purchase>,
    /** Past its date: the longest expired first, then by name. */
    val expired: List<PantryItem.Purchase>,
) {
    /** What Pantry's Needs attention filter lists: one row per purchase. */
    val size: Int get() = usable.size + expired.size

    fun isEmpty(): Boolean = size == 0

    companion object {
        /** Expiring within this many days, today included, needs attention (DESIGN 5.2 statuses Today to Soon). */
        const val DAYS = 3

        val EMPTY = NeedsAttention(emptyList(), emptyList())

        /** Whether a purchase with [status] is in the set. */
        fun includes(status: ExpiryStatus): Boolean = status == ExpiryStatus.Expired || usable(status)

        /** Still good to use, and expiring within [DAYS] days. */
        fun usable(status: ExpiryStatus): Boolean =
            status == ExpiryStatus.Today || status == ExpiryStatus.Tomorrow || status == ExpiryStatus.Soon

        /** Whether [item] is in the set (Pantry's filter). Out rows have nothing to use, so they never are. */
        fun includes(item: PantryItem): Boolean = item is PantryItem.Purchase && includes(item.status)

        fun of(products: List<Product>, batches: List<StockBatch>, today: LocalDate): NeedsAttention =
            of(PantryItems.all(products, batches, today))

        /** The set from [PantryItems.all]'s result, so a caller that needs both builds the items once. */
        fun of(items: List<PantryItem>): NeedsAttention {
            val purchases = items.filterIsInstance<PantryItem.Purchase>().filter { includes(it.status) }
            val order = compareBy<PantryItem.Purchase> { it.batch.expiryDate }.thenBy(NaturalOrder) { it.name }
            return NeedsAttention(
                usable = purchases.filter { usable(it.status) }.sortedWith(order),
                expired = purchases.filter { it.status == ExpiryStatus.Expired }.sortedWith(order),
            )
        }
    }
}
