package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The household's products and batches as the listeners currently see them (DESIGN 9.1). */
data class PantryData(
    val products: List<Product>,
    val batches: List<StockBatch>,
    /** Served from the local cache only: offline or still connecting (DESIGN 9.5). */
    val fromCache: Boolean,
)

/**
 * One write in a pantry plan. Plans are built by [PantryService] (pure business rules) and applied by the
 * [com.parcha.spoilnot.core.write.HouseholdWriter], so the rules are testable without Firestore.
 */
sealed interface PantryChange : HouseholdChange {
    data class CreateProduct(val product: Product) : PantryChange

    /** Writes every editable field of [product]. */
    data class UpdateProduct(val product: Product) : PantryChange

    /** The restock engine's own flag only, so it never overwrites someone's concurrent product edit (DESIGN 9.6). */
    data class SetRestockArmed(val productId: String, val armed: Boolean) : PantryChange

    /** Marks a batch opened on [openedOn] with its new [expiryDate]; null [openedOn] puts it back (Undo). */
    data class MarkOpened(val batchId: String, val openedOn: LocalDate?, val expiryDate: LocalDate?) : PantryChange

    /** Remembers how long [productId] keeps once opened, without touching its other fields. */
    data class SetOpenedShelfLife(val productId: String, val days: Int) : PantryChange

    /** Remembers that recipes call [productId] [alias] (a name key), without touching its other fields. */
    data class AddAlias(val productId: String, val alias: String) : PantryChange

    data class CreateBatch(val batch: StockBatch) : PantryChange

    /** Adds [delta] with an atomic increment, so +1 and -1 from two phones commute (DESIGN 9.3). */
    data class AddToBatch(val batchId: String, val delta: Double) : PantryChange

    data class UpdateBatch(val batchId: String, val edit: BatchEdit) : PantryChange

    /** Keeps a batch's copies of the product fields in line with the product (DESIGN 8.5, invariant 2). */
    data class SyncBatchProduct(val batchId: String, val name: String, val unit: QuantityUnit, val category: Category) :
        PantryChange

    data class DeleteBatch(val batchId: String) : PantryChange

    /** Puts a deleted batch back under its old id (Undo). */
    data class RestoreBatch(val batch: StockBatch) : PantryChange

    data class LogEvent(
        val eventId: String,
        val type: PantryEvent.Type,
        val productId: String,
        val name: String,
        val quantity: Double?,
        val reason: DiscardReason?,
    ) : PantryChange

    data class DeleteEvent(val eventId: String) : PantryChange
}

/** Products, batches and their history. Writes go through the HouseholdWriter. */
interface PantryRepository {
    fun observe(householdId: String): Flow<PantryData>

    /** The most recent events for one product, newest first (DESIGN 5.2 product detail). */
    fun observeEvents(householdId: String, productId: String, limit: Int): Flow<List<PantryEvent>>
}
