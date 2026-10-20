package com.parcha.spoilnot.feature.pantry.domain

import java.time.Instant
import java.time.LocalDate

/**
 * One row of the pantry list. Each purchase (batch) is its own row, so two bags of rice with different dates are
 * two rows (beta feedback, replacing the expandable product rows); a staple with nothing left keeps an Out row.
 */
sealed interface PantryItem {
    /** Stable for the list: the batch id, or the product id of an Out row. */
    val key: String
    val productId: String
    val name: String
    val product: Product?
    val category: Category
    val location: Location

    /** The product's whole stock is below Keep at least. */
    val isLow: Boolean

    data class Purchase(
        val batch: StockBatch,
        override val product: Product?,
        val status: ExpiryStatus,
        override val isLow: Boolean,
    ) : PantryItem {
        override val key get() = batch.id
        override val productId get() = batch.productId
        override val name get() = product?.name ?: batch.productName
        override val category get() = product?.category ?: batch.category
        override val location get() = batch.location
    }

    /** A staple (Keep at least set) with no stock: shown with an Out badge; tapping it adds stock. */
    data class Out(override val product: Product) : PantryItem {
        override val key get() = "out-${product.id}"
        override val productId get() = product.id
        override val name get() = product.name
        override val category get() = product.category
        override val location get() = product.defaultLocation
        override val isLow get() = true
    }
}

/** A section of the list; [location] or [category] is set when grouping. */
data class PantryItemGroup(val location: Location?, val category: Category?, val items: List<PantryItem>)

/** Builds the per-purchase pantry list. Pure, so it is tested with a fixed "today". */
object PantryItems {
    fun all(products: List<Product>, batches: List<StockBatch>, today: LocalDate): List<PantryItem> {
        val live = batches.filter { it.quantity > 0 }
        val totals = live.groupBy { it.productId }.mapValues { (_, b) -> b.sumOf { it.quantity } }
        val byId = products.associateBy { it.id }
        fun low(product: Product?, total: Double) = product?.minQuantity?.let { total < it } == true
        val purchases = live.map { batch ->
            val product = byId[batch.productId]
            PantryItem.Purchase(
                batch = batch,
                product = product,
                status = ExpiryStatus.of(batch.expiryDate, today),
                isLow = low(product, totals[batch.productId] ?: 0.0),
            )
        }
        val out = products.filter { it.isStaple && (totals[it.id] ?: 0.0) <= 0.0 }.map { PantryItem.Out(it) }
        return purchases + out
    }

    fun build(
        products: List<Product>,
        batches: List<StockBatch>,
        query: PantryQuery,
        today: LocalDate,
    ): List<PantryItemGroup> {
        val search = NameKey.of(query.search)
        val items = all(products, batches, today)
            .filter { search.isEmpty() || NameKey.of(it.name).contains(search) }
            .filter { matches(it, query.filter) }
        val sorted = sort(items, query.sort)
        return when (query.grouping) {
            PantryGrouping.None -> if (sorted.isEmpty()) emptyList() else listOf(PantryItemGroup(null, null, sorted))

            PantryGrouping.Location -> sorted.groupBy { it.location }.toSortedMap()
                .map { (location, list) -> PantryItemGroup(location, null, list) }

            PantryGrouping.Category -> sorted.groupBy { it.category }.toSortedMap()
                .map { (category, list) -> PantryItemGroup(null, category, list) }
        }
    }

    private fun matches(item: PantryItem, filter: PantryFilter): Boolean = when (filter) {
        PantryFilter.All -> true

        PantryFilter.Fridge -> item.location == Location.Fridge

        PantryFilter.Freezer -> item.location == Location.Freezer

        PantryFilter.Pantry -> item.location == Location.Pantry

        PantryFilter.Other -> item.location == Location.Other

        PantryFilter.ExpiringSoon ->
            item is PantryItem.Purchase && item.status.needsAttention && item.status != ExpiryStatus.Expired

        PantryFilter.Expired -> item is PantryItem.Purchase && item.status == ExpiryStatus.Expired

        PantryFilter.LowStock -> item.isLow
    }

    private fun sort(items: List<PantryItem>, sort: PantrySort): List<PantryItem> {
        val byName = compareBy<PantryItem, String>(NaturalOrder) { it.name }
        val expiry = compareBy<PantryItem, LocalDate?>(nullsLast()) { (it as? PantryItem.Purchase)?.batch?.expiryDate }
        // Out rows have nothing to use up, so they go last.
        val outLast = compareBy<PantryItem> { it is PantryItem.Out }
        return when (sort) {
            PantrySort.SoonestExpiry -> items.sortedWith(outLast.then(expiry).then(byName))

            PantrySort.Name -> items.sortedWith(byName.then(expiry))

            PantrySort.RecentlyAdded -> items.sortedWith(
                outLast.thenByDescending { added(it) }.then(byName),
            )
        }
    }

    /** A purchase still waiting for its server timestamp is the newest of all. */
    private fun added(item: PantryItem): Instant =
        (item as? PantryItem.Purchase)?.let { it.batch.createdAt ?: Instant.MAX } ?: Instant.MIN
}

/**
 * Expired purchases gathered into one header at the top of the pantry list (after v1), so a backlog of old food does
 * not push everything else off the screen. Only when browsing (no search, any filter but Expired) and with at least
 * [MIN] of them; [expired] keeps the list's order and [rest] is the list without them.
 */
data class ExpiredFold(val expired: List<PantryItem.Purchase>, val rest: List<PantryItemGroup>) {
    companion object {
        const val MIN = 3

        fun of(groups: List<PantryItemGroup>, query: PantryQuery): ExpiredFold? {
            if (query.search.isNotBlank() || query.filter == PantryFilter.Expired) return null
            val expired = groups.flatMap { it.items }.filterIsInstance<PantryItem.Purchase>()
                .filter { it.status == ExpiryStatus.Expired }
            if (expired.size < MIN) return null
            val ids = expired.mapTo(HashSet()) { it.key }
            val rest = groups.map { g ->
                g.copy(items = g.items.filterNot { it.key in ids })
            }.filter { it.items.isNotEmpty() }
            return ExpiredFold(expired, rest)
        }
    }
}
