package com.parcha.spoilnot.feature.pantry.domain

import java.time.Instant
import java.time.LocalDate

/** One pantry row: a product with its batches (DESIGN 5.2). */
data class PantryRow(
    val productId: String,
    val name: String,
    val unit: QuantityUnit,
    val category: Category,
    val product: Product?,
    /** Soonest expiry first, undated last. */
    val batches: List<StockBatch>,
    /** Sum of [batches]; under a location filter or grouping, only that location's batches. */
    val total: Double,
    /** Status of the soonest-expiring batch, or NoDate for an empty staple. */
    val status: ExpiryStatus,
    val soonest: StockBatch?,
    val location: Location,
    /** Everything in stock for this product, whatever is filtered. */
    val overallTotal: Double,
    /** A staple (Keep at least set) with nothing left: shown at 0 with an Out badge. */
    val isOut: Boolean,
    /** A staple below its Keep at least. */
    val isLow: Boolean,
    val pendingSync: Boolean,
)

enum class PantryFilter { All, Fridge, Freezer, Pantry, Other, ExpiringSoon, Expired, LowStock }

enum class PantrySort { SoonestExpiry, Name, RecentlyAdded }

enum class PantryGrouping { None, Location, Category }

data class PantryQuery(
    val filter: PantryFilter = PantryFilter.All,
    val sort: PantrySort = PantrySort.SoonestExpiry,
    val grouping: PantryGrouping = PantryGrouping.None,
    val search: String = "",
)

/** A section of the list; [location] or [category] is set when grouping. */
data class PantryGroup(val location: Location?, val category: Category?, val rows: List<PantryRow>)

/** Builds the pantry list from the raw collections. Pure, so it is tested with a fixed "today". */
object PantryList {
    /** Rows for every product with stock, plus every staple (even at 0). Empty batches are hidden (DESIGN 8.5). */
    fun rows(products: List<Product>, batches: List<StockBatch>, today: LocalDate): List<PantryRow> {
        val live = batches.filter { it.quantity > 0 }
        val byProduct = live.groupBy { it.productId }
        val productsById = products.associateBy { it.id }
        val ids = (byProduct.keys + products.filter { it.isStaple }.map { it.id }).distinct()
        return ids.map { id -> row(productsById[id], id, byProduct[id].orEmpty(), today) }
    }

    fun build(
        products: List<Product>,
        batches: List<StockBatch>,
        query: PantryQuery,
        today: LocalDate,
    ): List<PantryGroup> {
        val searchKey = NameKey.of(query.search)
        val all = rows(products, batches, today)
            .filter { searchKey.isEmpty() || NameKey.of(it.name).contains(searchKey) }
            .mapNotNull { applyFilter(it, query.filter, today) }

        val sorted = sort(all, query.sort)
        return when (query.grouping) {
            PantryGrouping.None -> if (sorted.isEmpty()) emptyList() else listOf(PantryGroup(null, null, sorted))

            PantryGrouping.Category -> sorted.groupBy { it.category }.toSortedMap()
                .map { (category, rows) -> PantryGroup(null, category, rows) }

            PantryGrouping.Location -> {
                // A product stored in two places appears in both, each with that place's batches.
                val split = sorted.flatMap { row ->
                    if (row.batches.isEmpty()) {
                        listOf(row)
                    } else {
                        row.batches.groupBy { it.location }.map { (_, here) ->
                            row(row.product, row.productId, here, today, fallback = row)
                        }
                    }
                }
                sort(split, query.sort).groupBy { it.location }.toSortedMap()
                    .map { (location, rows) -> PantryGroup(location, null, rows) }
            }
        }
    }

    private fun applyFilter(row: PantryRow, filter: PantryFilter, today: LocalDate): PantryRow? {
        fun keep(predicate: (StockBatch) -> Boolean, emptyStapleMatches: Boolean = false): PantryRow? {
            val matching = row.batches.filter(predicate)
            return when {
                matching.isNotEmpty() -> row(row.product, row.productId, matching, today, fallback = row)
                row.batches.isEmpty() && emptyStapleMatches -> row
                else -> null
            }
        }
        fun at(location: Location) = keep({ it.location == location }, row.product?.defaultLocation == location)
        return when (filter) {
            PantryFilter.All -> row

            PantryFilter.Fridge -> at(Location.Fridge)

            PantryFilter.Freezer -> at(Location.Freezer)

            PantryFilter.Pantry -> at(Location.Pantry)

            PantryFilter.Other -> at(Location.Other)

            PantryFilter.ExpiringSoon -> keep({
                ExpiryStatus.of(it.expiryDate, today).let { s ->
                    s.needsAttention &&
                        s != ExpiryStatus.Expired
                }
            })

            PantryFilter.Expired -> keep({ ExpiryStatus.of(it.expiryDate, today) == ExpiryStatus.Expired })

            PantryFilter.LowStock -> row.takeIf { it.isLow }
        }
    }

    private fun sort(rows: List<PantryRow>, sort: PantrySort): List<PantryRow> {
        val byName = compareBy<PantryRow, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        return when (sort) {
            PantrySort.Name -> rows.sortedWith(byName)

            PantrySort.SoonestExpiry -> rows.sortedWith(
                compareBy<PantryRow> { it.isOut }
                    .thenBy(nullsLast()) { it.soonest?.expiryDate }
                    .then(byName),
            )

            PantrySort.RecentlyAdded -> rows.sortedWith(
                compareBy<PantryRow> { it.isOut }
                    .thenByDescending { newest(it) }
                    .then(byName),
            )
        }
    }

    /** A batch still waiting for its server timestamp is the newest of all. */
    private fun newest(row: PantryRow): Instant = row.batches.maxOfOrNull { it.createdAt ?: Instant.MAX } ?: Instant.MIN

    private fun isLow(product: Product, total: Double) = product.minQuantity?.let { total < it } == true

    /** [fallback] is the unfiltered row, whose overall total decides Out and Low. */
    private fun row(
        product: Product?,
        productId: String,
        batches: List<StockBatch>,
        today: LocalDate,
        fallback: PantryRow? = null,
    ): PantryRow {
        val sorted = batches.sortedWith(compareBy(nullsLast()) { it.expiryDate })
        val soonest = sorted.firstOrNull()
        val sum = batches.sumOf { it.quantity }
        val first = batches.firstOrNull()
        val allTotal = fallback?.overallTotal ?: sum
        return PantryRow(
            productId = productId,
            name = product?.name ?: first?.productName ?: fallback?.name.orEmpty(),
            unit = product?.unit ?: first?.unit ?: QuantityUnit.Pieces,
            category = product?.category ?: first?.category ?: Category.Other,
            product = product,
            batches = sorted,
            total = sum,
            status = ExpiryStatus.of(soonest?.expiryDate, today),
            soonest = soonest,
            location = soonest?.location ?: product?.defaultLocation ?: Location.Other,
            overallTotal = allTotal,
            isOut = product?.isStaple == true && allTotal <= 0.0,
            isLow = product != null && isLow(product, allTotal),
            pendingSync = batches.any { it.pendingSync },
        )
    }
}
