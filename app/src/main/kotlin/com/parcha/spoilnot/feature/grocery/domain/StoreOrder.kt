package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.feature.pantry.domain.Category
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Aisles in the order the household walks its store (DESIGN 5.3, after v1), learned from check-offs. Each list
 * keeps its own order (`aisleOrder`), since lists are often for different stores.
 *
 * Learning moves as little as possible: when the first item of an aisle is checked off during a shopping trip, and
 * the saved order puts that aisle before one already visited on this trip, it moves to just after the last visited
 * one. Checking off the first aisle of a trip changes nothing, so checking things off at home in any order does
 * little harm, and a few trips settle the order.
 */
object StoreOrder {
    /** The saved order completed with the remaining aisles in the default order. */
    fun effective(saved: List<Category>): List<Category> = saved.distinct() + Category.entries.filter { it !in saved }

    /**
     * The new order after [checked] was visited following [visited] (this trip's aisles so far, in order), or null
     * when the saved order already agrees.
     */
    fun learn(saved: List<Category>, visited: List<Category>, checked: Category): List<Category>? {
        if (checked in visited || visited.isEmpty()) return null
        val order = effective(saved)
        val lastVisited = visited.maxOf { order.indexOf(it) }
        if (order.indexOf(checked) > lastVisited) return null
        val without = order - checked
        val insertAt = without.indexOf(order[lastVisited]) + 1
        return without.take(insertAt) + checked + without.drop(insertAt)
    }

    /** The aisles of [view] in [saved] order. */
    fun sort(view: GroceryView, saved: List<Category>): GroceryView {
        val order = effective(saved)
        return view.copy(aisles = view.aisles.sortedBy { (category, _) -> order.indexOf(category) })
    }
}

/**
 * One shopping trip on this phone (DESIGN 5.3): the aisles in the order their first item was checked off. A trip
 * ends after [GAP] without a check-off, or when another list is used.
 */
class ShoppingTrip(private val clock: Clock) {
    private var listId: String? = null
    private var last: Instant? = null
    private val visited = mutableListOf<Category>()

    /** Records a check-off and returns the aisles visited before it on this trip. */
    fun checked(listId: String, category: Category): List<Category> {
        val now = clock.instant()
        val stale = last?.let { Duration.between(it, now) > GAP } ?: true
        if (stale || this.listId != listId) {
            visited.clear()
            this.listId = listId
        }
        last = now
        val before = visited.toList()
        if (category !in visited) visited += category
        return before
    }

    companion object {
        val GAP: Duration = Duration.ofHours(2)
    }
}
