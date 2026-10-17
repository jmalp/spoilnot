package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.error.AppError
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What an Undo puts back (DESIGN 5.2: every destructive action has Undo for 8 seconds). */
data class Undo(val changes: List<PantryChange>)

/** Result of saving the Add item sheet. */
data class AddResult(val productId: String, val batchId: String, val merged: Boolean)

/**
 * Pantry business rules (DESIGN 5.2, 8.5). Each action builds a plan of [PantryChange]s from the current data and
 * hands it to the repository, which writes it as one batch.
 */
class PantryService @Inject constructor(private val repository: PantryRepository, private val clock: Clock) {
    private fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Saves a new batch. Reuses the product with the same name key (invariant 4), keeps its unit (no conversion),
     * and increments an existing batch with the same product, date and location instead of duplicating it.
     */
    suspend fun add(householdId: String, actor: Actor, draft: StockDraft, data: PantryData): AddResult {
        val name = NameKey.clean(draft.name)
        require(name.isNotEmpty() && name.length <= PantryLimits.MAX_NAME_LENGTH) { "Invalid name" }
        require(draft.quantity > 0) { "Quantity must be above 0" }
        val key = NameKey.of(name)
        val existing = data.products.firstOrNull { it.nameKey == key }
        val changes = mutableListOf<PantryChange>()

        val product = if (existing == null) {
            val recent = ShelfLife.record(emptyList(), today(), draft.expiryDate)
            Product(
                id = repository.newProductId(householdId),
                name = name,
                nameKey = key,
                category = draft.category,
                defaultLocation = draft.location,
                unit = draft.unit,
                minQuantity = draft.minQuantity,
                autoAddToGrocery = draft.minQuantity != null && draft.autoAddToGrocery,
                buyQuantity = draft.buyQuantity.takeIf { draft.minQuantity != null },
                restockArmed = true,
                typicalShelfLifeDays = ShelfLife.median(recent),
                recentShelfLifeDays = recent,
                createdAt = null,
            ).also { changes += PantryChange.CreateProduct(it) }
        } else {
            val recent = ShelfLife.record(existing.recentShelfLifeDays, today(), draft.expiryDate)
            val hasStock = data.batches.any { it.productId == existing.id && it.quantity > 0 }
            val updated = existing.copy(
                // The unit is fixed once a product has stock (invariant 3).
                unit = if (hasStock) existing.unit else draft.unit,
                recentShelfLifeDays = recent,
                typicalShelfLifeDays = ShelfLife.median(recent) ?: existing.typicalShelfLifeDays,
            ).withKeepAtLeast(draft.minQuantity, draft.autoAddToGrocery, draft.buyQuantity, existing)
            if (updated != existing) changes += PantryChange.UpdateProduct(updated)
            updated
        }

        val target = data.batches.firstOrNull {
            it.productId == product.id && it.expiryDate == draft.expiryDate && it.location == draft.location &&
                it.quantity > 0
        }
        val batchId = if (target != null) {
            changes += PantryChange.AddToBatch(target.id, draft.quantity)
            target.id
        } else {
            val id = repository.newBatchId(householdId)
            changes += PantryChange.CreateBatch(
                StockBatch(
                    id = id,
                    productId = product.id,
                    productName = product.name,
                    unit = product.unit,
                    category = product.category,
                    quantity = draft.quantity,
                    expiryDate = draft.expiryDate,
                    dateType = draft.dateType,
                    location = draft.location,
                    note = draft.note?.trim()?.take(PantryLimits.MAX_NOTE_LENGTH)?.ifEmpty { null },
                    createdAt = null,
                ),
            )
            id
        }
        changes += PantryChange.LogEvent(
            repository.newEventId(householdId),
            PantryEvent.Type.Added,
            product.id,
            product.name,
            draft.quantity,
            null,
        )
        repository.apply(householdId, actor, changes)
        return AddResult(product.id, batchId, merged = target != null)
    }

    /** +1 or -1. Going to 0 or below uses the batch up, which can be undone. */
    suspend fun adjust(householdId: String, actor: Actor, batch: StockBatch, delta: Double): Undo? {
        if (batch.quantity + delta <= 0) return usedUp(householdId, actor, batch)
        repository.apply(householdId, actor, listOf(PantryChange.AddToBatch(batch.id, delta)))
        return null
    }

    suspend fun usedUp(householdId: String, actor: Actor, batch: StockBatch): Undo {
        repository.apply(householdId, actor, listOf(PantryChange.DeleteBatch(batch.id)))
        return Undo(listOf(PantryChange.RestoreBatch(batch)))
    }

    /** Deletes the batch and logs why (DESIGN 5.2). Undo restores the batch and removes the log entry. */
    suspend fun throwAway(householdId: String, actor: Actor, batch: StockBatch, reason: DiscardReason): Undo {
        val eventId = repository.newEventId(householdId)
        repository.apply(
            householdId,
            actor,
            listOf(
                PantryChange.DeleteBatch(batch.id),
                PantryChange.LogEvent(
                    eventId,
                    PantryEvent.Type.Discarded,
                    batch.productId,
                    batch.productName,
                    batch.quantity,
                    reason,
                ),
            ),
        )
        return Undo(listOf(PantryChange.RestoreBatch(batch), PantryChange.DeleteEvent(eventId)))
    }

    /** Typed changes. A quantity of 0 uses the batch up. */
    suspend fun edit(householdId: String, actor: Actor, batch: StockBatch, edit: BatchEdit): Undo? {
        if (edit.quantity <= 0) return usedUp(householdId, actor, batch)
        val clean = edit.copy(note = edit.note?.trim()?.take(PantryLimits.MAX_NOTE_LENGTH)?.ifEmpty { null })
        repository.apply(householdId, actor, listOf(PantryChange.UpdateBatch(batch.id, clean)))
        return null
    }

    suspend fun move(householdId: String, actor: Actor, batch: StockBatch, location: Location) = edit(
        householdId,
        actor,
        batch,
        BatchEdit(batch.quantity, batch.expiryDate, batch.dateType, location, batch.note),
    )

    /**
     * Product settings and rename (DESIGN 5.2 product detail). A rename is copied to every batch in the same write
     * (invariant 2); the unit cannot change while there is stock (invariant 3); raising Keep at least re-arms the
     * restock engine (DESIGN 9.6).
     */
    suspend fun updateProduct(
        householdId: String,
        actor: Actor,
        product: Product,
        edit: ProductEdit,
        data: PantryData,
    ) {
        val name = NameKey.clean(edit.name)
        require(name.isNotEmpty() && name.length <= PantryLimits.MAX_NAME_LENGTH) { "Invalid name" }
        val key = NameKey.of(name)
        if (data.products.any { it.id != product.id && it.nameKey == key }) throw AppError.Conflict()
        val batches = data.batches.filter { it.productId == product.id }
        val hasStock = batches.any { it.quantity > 0 }
        val unit = if (hasStock) product.unit else edit.unit
        val raised = edit.minQuantity != null && (product.minQuantity == null || edit.minQuantity > product.minQuantity)
        val updated = product.copy(
            name = name,
            nameKey = key,
            category = edit.category,
            defaultLocation = edit.defaultLocation,
            unit = unit,
            minQuantity = edit.minQuantity,
            autoAddToGrocery = edit.minQuantity != null && edit.autoAddToGrocery,
            buyQuantity = edit.buyQuantity.takeIf { edit.minQuantity != null },
            restockArmed = product.restockArmed || raised,
            typicalShelfLifeDays = edit.typicalShelfLifeDays,
        )
        val changes = mutableListOf<PantryChange>(PantryChange.UpdateProduct(updated))
        if (updated.name != product.name || updated.unit != product.unit || updated.category != product.category) {
            batches.forEach {
                changes +=
                    PantryChange.SyncBatchProduct(it.id, updated.name, updated.unit, updated.category)
            }
        }
        repository.apply(householdId, actor, changes)
    }

    suspend fun undo(householdId: String, actor: Actor, undo: Undo) = repository.apply(householdId, actor, undo.changes)

    /** Batches at 0 or below are hidden and deleted by whichever client notices first (DESIGN 8.5, invariant 1). */
    suspend fun deleteEmptyBatches(householdId: String, actor: Actor, data: PantryData) {
        val empty = data.batches.filter { it.quantity <= 0 && !it.pendingSync }
        if (empty.isNotEmpty()) repository.apply(householdId, actor, empty.map { PantryChange.DeleteBatch(it.id) })
    }

    private fun Product.withKeepAtLeast(min: Double?, autoAdd: Boolean, buy: Double?, before: Product): Product {
        // The sheet only sets Keep at least; leaving it empty keeps what the product already has.
        if (min == null) return this
        val raised = before.minQuantity == null || min > before.minQuantity
        return copy(
            minQuantity = min,
            autoAddToGrocery = autoAdd,
            buyQuantity = buy,
            restockArmed = restockArmed || raised,
        )
    }
}
