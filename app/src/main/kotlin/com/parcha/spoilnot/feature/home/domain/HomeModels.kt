package com.parcha.spoilnot.feature.home.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.NeedsAttention
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryList
import com.parcha.spoilnot.feature.pantry.domain.PantryRow
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** households/{hid}/activity types (DESIGN 8.2). */
enum class ActivityType(val wire: String) {
    StockAdded("stock_added"),
    GroceryAdded("grocery_added"),

    /** Written by builds before R15 (after v1); still shown while such events are among the newest. */
    GroceryChecked("grocery_checked"),
    RecipeSaved("recipe_saved"),
    MemberJoined("member_joined"),
    Discarded("discarded"),

    /** After v1: someone used "I cooked this" on a recipe. */
    Cooked("recipe_cooked"),

    /** After v1: the owner cleared the household's data (DESIGN 5.1); older events are not shown. */
    HouseholdReset("household_reset"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

/** One line of Recent activity, such as "Sam added Eggs to Groceries" (DESIGN 5.6). */
data class ActivityEvent(
    val id: String,
    val type: ActivityType,
    val actorUid: String,
    val actorName: String,
    val name: String?,
    val quantity: Double?,
    val reason: DiscardReason?,
    val listName: String?,
    /** Items added at once, from the recipe called [name]. */
    val count: Int?,
    val createdAt: Instant?,
)

/** Recent activity rules (after v1). */
object ActivityFeed {
    /**
     * [events] (newest first) up to and including the newest household_reset: events from before a reset can still
     * sit in a phone's cache (changes-only reads, DESIGN 9.8) but describe data that is gone.
     */
    fun sinceReset(events: List<ActivityEvent>): List<ActivityEvent> {
        val reset = events.indexOfFirst { it.type == ActivityType.HouseholdReset }
        return if (reset < 0) events else events.take(reset + 1)
    }

    /**
     * [events] with each author's name from the member list ([names], uid to display name), so a line always names
     * who wrote it (the rules check `actorUid`, not the stored `actorName`). Someone who has left keeps the name
     * stored with the event.
     */
    fun named(events: List<ActivityEvent>, names: Map<String, String>): List<ActivityEvent> = events.map { event ->
        val name = names[event.actorUid]
        if (name == null || name == event.actorName) event else event.copy(actorName = name)
    }
}

interface ActivityRepository {
    /** The newest [limit] events, newest first. Unknown types (from newer app versions) are skipped. */
    fun observeRecent(householdId: String, limit: Int): Flow<List<ActivityEvent>>
}

/** What the Home tab shows (DESIGN 5.6). */
data class HomeSummary(
    /** The one Needs attention set (after v1, R8): Home lists [NeedsAttention.usable] and folds the expired. */
    val attention: NeedsAttention,
    /** Products below Keep at least, out of stock first; shown in the Grocery list card (after v1, R1). */
    val lowStock: List<PantryRow>,
    val groceryCount: Int,
    /** No product has Keep some in stock on yet, so the Grocery list card says how to start (after v1, UX-07). */
    val noStaples: Boolean,
    /** Nothing is in the pantry yet, so Needs attention says how to start instead of "all clear" (after v1, UX-07). */
    val pantryEmpty: Boolean,
    val recent: List<ActivityEvent>,
)

object HomeDashboard {
    fun summary(
        pantry: PantryData,
        groceryItems: List<GroceryItem>,
        recent: List<ActivityEvent>,
        today: LocalDate,
    ): HomeSummary {
        val items = PantryItems.all(pantry.products, pantry.batches, today)
        val rows = PantryList.rows(pantry.products, pantry.batches, today)
        return HomeSummary(
            attention = NeedsAttention.of(items),
            lowStock = rows.filter {
                it.isLow || it.isOut
            }.sortedWith(compareByDescending<PantryRow> { it.isOut }.thenBy { it.name.lowercase() }),
            groceryCount = groceryItems.count { !it.checked },
            noStaples = pantry.products.none { it.isStaple },
            pantryEmpty = items.none { it is PantryItem.Purchase },
            recent = recent,
        )
    }
}
