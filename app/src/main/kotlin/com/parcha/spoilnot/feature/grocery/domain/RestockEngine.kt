package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import kotlin.math.max

/**
 * The restock engine (DESIGN 9.6), as a pure function from the current products and stock to the writes to make.
 * It runs on every phone; the plan is idempotent, so two phones evaluating at once write identical data. The phone
 * whose own change caused it writes at once, the others only after [OTHER_PHONE_DELAY_MS] ([delayMillis]).
 *
 * - Below Keep at least while armed: add `low_{productId}` to the default list once and disarm.
 * - Back at Keep at least or more while disarmed: re-arm.
 * Deleting the item therefore does not bring it back until stock recovers and drops again.
 */
object RestockEngine {
    fun plan(data: PantryData, defaultListId: String?, cachedLowStockIds: Set<String>): List<HouseholdChange> {
        val changes = mutableListOf<HouseholdChange>()
        for (product in data.products) {
            val min = product.minQuantity ?: continue
            if (!product.autoAddToGrocery) continue
            val total = totalOf(product, data)
            if (total >= min) {
                if (!product.restockArmed) changes += PantryChange.SetRestockArmed(product.id, true)
            } else if (product.restockArmed && defaultListId != null) {
                val itemId = GroceryRules.lowStockItemId(product.id)
                changes += GroceryChange.UpsertLowStock(
                    listId = defaultListId,
                    productId = product.id,
                    name = product.name,
                    nameKey = product.nameKey,
                    unit = product.unit,
                    category = product.category,
                    quantity = buyQuantity(product, total),
                    writeCreated = itemId !in cachedLowStockIds,
                )
                changes += PantryChange.SetRestockArmed(product.id, false)
            }
        }
        return changes
    }

    /** How long a phone that did not make the change waits before writing, so the phone that did writes first. */
    const val OTHER_PHONE_DELAY_MS = 5_000L

    /** A change made on this phone this recently counts as the cause of what the engine sees now. */
    const val OWN_CHANGE_WINDOW_MS = 5_000L

    /**
     * Whether a plan applied on this phone can change what the engine decides: stock or a product's settings. The
     * engine's own writes (the item and the armed flag) and history do not count.
     */
    fun movesStock(changes: List<HouseholdChange>): Boolean = changes.any {
        it is PantryChange.CreateProduct ||
            it is PantryChange.UpdateProduct ||
            it is PantryChange.DeleteProduct ||
            it is PantryChange.CreateBatch ||
            it is PantryChange.AddToBatch ||
            it is PantryChange.UpdateBatch ||
            it is PantryChange.DeleteBatch ||
            it is PantryChange.RestoreBatch
    }

    /**
     * How long to wait before writing a plan: nothing when this phone changed stock or products within
     * [OWN_CHANGE_WINDOW_MS] (its own change caused it), otherwise [OTHER_PHONE_DELAY_MS]. A newer snapshot during the
     * wait starts over, so if the other phone's write arrives first, nothing is left to write (DESIGN 9.6).
     */
    fun delayMillis(lastOwnChangeAt: Long?, now: Long): Long =
        if (lastOwnChangeAt != null && now - lastOwnChangeAt in 0..OWN_CHANGE_WINDOW_MS) 0 else OTHER_PHONE_DELAY_MS

    /** Buy quantity, or Keep at least minus what is left, at least 1 (DESIGN 5.3). */
    fun buyQuantity(product: Product, total: Double): Double =
        product.buyQuantity ?: max(1.0, (product.minQuantity ?: 0.0) - total)

    /** Total stock; batches at 0 or below do not count (DESIGN 8.5, invariant 1). */
    fun totalOf(product: Product, data: PantryData): Double =
        data.batches.filter { it.productId == product.id && it.quantity > 0 }.sumOf { it.quantity }

    /** A low-stock item whose product is back at Keep at least shows "Back in stock" with a one-tap remove. */
    fun isBackInStock(item: GroceryItem, data: PantryData): Boolean {
        if (item.source != ItemSource.LowStock || item.checked) return false
        val product = data.products.firstOrNull { it.id == item.productId } ?: return false
        val min = product.minQuantity ?: return false
        return totalOf(product, data) >= min
    }

    /**
     * Why the app put [item] on the list (after v1, UX-08): how much is left and the product's Keep at least, as they
     * are now. Null for an item a person added, and [RestockReason.ProductGone] when its product was deleted or no
     * longer has Keep at least.
     */
    fun reason(item: GroceryItem, data: PantryData): RestockReason? {
        if (item.source != ItemSource.LowStock) return null
        val product = data.products.firstOrNull { it.id == item.productId } ?: return RestockReason.ProductGone
        val min = product.minQuantity ?: return RestockReason.ProductGone
        return RestockReason.Low(have = totalOf(product, data), keepAtLeast = min, unit = product.unit)
    }
}

/** What [RestockEngine.reason] tells the person about an item the app added. */
sealed interface RestockReason {
    data class Low(val have: Double, val keepAtLeast: Double, val unit: QuantityUnit) : RestockReason

    data object ProductGone : RestockReason
}
