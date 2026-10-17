package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import kotlin.math.max

/**
 * The restock engine (DESIGN 9.6), as a pure function from the current products and stock to the writes to make.
 * It runs on every phone; the plan is idempotent, so two phones evaluating at once write identical data.
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
}
