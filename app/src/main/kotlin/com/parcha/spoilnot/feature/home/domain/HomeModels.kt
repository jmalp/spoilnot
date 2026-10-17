package com.parcha.spoilnot.feature.home.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryList
import com.parcha.spoilnot.feature.pantry.domain.PantryRow
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** households/{hid}/activity types (DESIGN 8.2). */
enum class ActivityType(val wire: String) {
    StockAdded("stock_added"),
    GroceryAdded("grocery_added"),
    GroceryChecked("grocery_checked"),
    RecipeSaved("recipe_saved"),
    MemberJoined("member_joined"),
    Discarded("discarded"),
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

interface ActivityRepository {
    /** The newest [limit] events, newest first. Unknown types (from newer app versions) are skipped. */
    fun observeRecent(householdId: String, limit: Int): Flow<List<ActivityEvent>>
}

/** What the Home tab shows (DESIGN 5.6). */
data class HomeSummary(
    /** Expired and expiring within 3 days, soonest first. */
    val attention: List<PantryRow>,
    val expiredCount: Int,
    /** Staples below Keep at least, out of stock first. */
    val lowStock: List<PantryRow>,
    val groceryCount: Int,
    val recent: List<ActivityEvent>,
)

object HomeDashboard {
    fun summary(
        pantry: PantryData,
        groceryItems: List<GroceryItem>,
        recent: List<ActivityEvent>,
        today: LocalDate,
    ): HomeSummary {
        val rows = PantryList.rows(pantry.products, pantry.batches, today)
        val attention = rows
            .filter { it.batches.any { b -> ExpiryStatus.of(b.expiryDate, today).needsAttention } }
            .sortedWith(compareBy(nullsLast()) { it.soonest?.expiryDate })
        return HomeSummary(
            attention = attention,
            expiredCount = attention.count { it.status == ExpiryStatus.Expired },
            lowStock = rows.filter {
                it.isLow || it.isOut
            }.sortedWith(compareByDescending<PantryRow> { it.isOut }.thenBy { it.name.lowercase() }),
            groceryCount = groceryItems.count { !it.checked },
            recent = recent,
        )
    }
}
