package com.parcha.spoilnot.feature.pantry.domain

/**
 * One product with its whole stock, for Home's Running low lines (DESIGN 5.6). The Pantry tab lists purchases instead
 * ([PantryItems]).
 */
data class PantryRow(
    val productId: String,
    val name: String,
    val product: Product?,
    /** Everything in stock for this product. */
    val total: Double,
    /** A staple (Keep at least set) with nothing left: shown with an Out badge. */
    val isOut: Boolean,
    /** A staple below its Keep at least. */
    val isLow: Boolean,
)

/** Per-product rows from the raw collections. */
object PantryList {
    /** Rows for every product with stock, plus every staple (even at 0). Empty batches are hidden (DESIGN 8.5). */
    fun rows(products: List<Product>, batches: List<StockBatch>): List<PantryRow> {
        val byProduct = batches.filter { it.quantity > 0 }.groupBy { it.productId }
        val productsById = products.associateBy { it.id }
        val ids = (byProduct.keys + products.filter { it.isStaple }.map { it.id }).distinct()
        return ids.map { id -> row(productsById[id], id, byProduct[id].orEmpty()) }
    }

    private fun row(product: Product?, productId: String, batches: List<StockBatch>): PantryRow {
        val total = batches.sumOf { it.quantity }
        return PantryRow(
            productId = productId,
            name = product?.name ?: batches.firstOrNull()?.productName.orEmpty(),
            product = product,
            total = total,
            isOut = product?.isStaple == true && total <= 0.0,
            isLow = product?.minQuantity?.let { total < it } == true,
        )
    }
}
