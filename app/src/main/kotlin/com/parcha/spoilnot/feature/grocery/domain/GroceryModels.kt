package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** households/{hid}/groceryLists/{listId} (DESIGN 8.2). */
data class GroceryList(val id: String, val name: String, val isDefault: Boolean)

enum class ItemSource(val wire: String) {
    Manual("manual"),
    LowStock("low_stock"),
    Recipe("recipe"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Manual
    }
}

/** households/{hid}/groceryLists/{listId}/items/{itemId} (DESIGN 8.2). */
data class GroceryItem(
    val id: String,
    val name: String,
    val nameKey: String,
    val quantity: Double?,
    val unit: QuantityUnit?,
    val note: String?,
    val category: Category?,
    val checked: Boolean,
    val checkedBy: String?,
    val source: ItemSource,
    val productId: String?,
    val recipeId: String?,
    val createdBy: String?,
    val createdAt: Instant?,
    val pendingSync: Boolean = false,
)

/** One write in a grocery plan, applied by the HouseholdWriter together with any pantry changes. */
sealed interface GroceryChange : HouseholdChange {
    data class CreateList(val list: GroceryList) : GroceryChange

    data class RenameList(val listId: String, val name: String) : GroceryChange

    data class DeleteList(val listId: String) : GroceryChange

    data class CreateItem(val listId: String, val item: GroceryItem) : GroceryChange

    /** Puts a deleted item back under its old id (Undo). */
    data class RestoreItem(val listId: String, val item: GroceryItem) : GroceryChange

    data class UpdateItem(
        val listId: String,
        val itemId: String,
        val quantity: Double?,
        val unit: QuantityUnit?,
        val note: String?,
        val category: Category?,
    ) : GroceryChange

    data class AddToItem(val listId: String, val itemId: String, val delta: Double) : GroceryChange

    data class SetChecked(val listId: String, val itemId: String, val checked: Boolean) : GroceryChange

    data class DeleteItem(val listId: String, val itemId: String) : GroceryChange

    /**
     * The restock engine's merge-write of `low_{productId}` (DESIGN 9.6). Never writes `checked`, so an item already
     * in the cart stays there; [writeCreated] is false when the item already exists in the local cache.
     */
    data class UpsertLowStock(
        val listId: String,
        val productId: String,
        val name: String,
        val nameKey: String,
        val unit: QuantityUnit,
        val category: Category,
        val quantity: Double,
        val writeCreated: Boolean,
    ) : GroceryChange
}

object GroceryRules {
    const val MAX_NAME_LENGTH = 80
    const val MAX_LIST_NAME_LENGTH = 60

    /** DESIGN 8.2: low-stock items use a deterministic id, so two phones write the same document. */
    fun lowStockItemId(productId: String) = "low_$productId"
}

/** Grocery lists and their items. Writes go through the HouseholdWriter. */
interface GroceryRepository {
    /** List names, always listened to (DESIGN 9.1). */
    fun observeLists(householdId: String): Flow<List<GroceryList>>

    /** Items of one list, while it is visible (DESIGN 9.1). */
    fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>>

    /** Whether an item is in the local cache, so the restock engine knows whether to write createdAt. */
    suspend fun isCached(householdId: String, listId: String, itemId: String): Boolean

    /** Every item id of a list, read from the server, for deleting a list. */
    suspend fun itemIds(householdId: String, listId: String): List<String>
}
