package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.error.AppError
import kotlinx.coroutines.flow.Flow

/** Who is making a change, for createdBy/updatedBy and the activity log. */
data class Actor(val uid: String, val name: String)

/** The household's products and batches as the listeners currently see them (DESIGN 9.1). */
data class PantryData(
    val products: List<Product>,
    val batches: List<StockBatch>,
    /** Served from the local cache only: offline or still connecting (DESIGN 9.5). */
    val fromCache: Boolean,
)

/**
 * One write in a pantry plan. Plans are built by [PantryService] (pure business rules) and applied by the
 * repository in batched writes, so the rules are testable without Firestore.
 */
sealed interface PantryChange {
    data class CreateProduct(val product: Product) : PantryChange

    /** Writes every editable field of [product]. */
    data class UpdateProduct(val product: Product) : PantryChange

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

/** Products, batches and their history. Implementations throw [com.parcha.spoilnot.core.error.AppError]. */
interface PantryRepository {
    fun observe(householdId: String): Flow<PantryData>

    /** The most recent events for one product, newest first (DESIGN 5.2 product detail). */
    fun observeEvents(householdId: String, productId: String, limit: Int): Flow<List<PantryEvent>>

    fun newProductId(householdId: String): String

    fun newBatchId(householdId: String): String

    fun newEventId(householdId: String): String

    /** Applies the changes in order, in as few batched writes as the 400-operation limit allows. */
    suspend fun apply(householdId: String, actor: Actor, changes: List<PantryChange>)
}

/** Writes the server rejected after they were applied locally, for the UI to report. */
interface PantryWriteErrors {
    val errors: Flow<AppError>
}
