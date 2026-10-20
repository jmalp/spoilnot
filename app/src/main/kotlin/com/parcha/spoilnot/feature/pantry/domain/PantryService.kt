package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.insights.domain.StatsChange
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Result of saving the Add item sheet. */
data class AddResult(val productId: String, val batchId: String, val merged: Boolean)

/**
 * The writes for one added batch, plus the data as it will look afterwards, so several additions can be planned
 * into one batched write (DESIGN 5.3 "Add to pantry").
 */
data class AddPlan(val changes: List<HouseholdChange>, val result: AddResult, val after: PantryData)

/**
 * Pantry business rules (DESIGN 5.2, 8.5). Each action builds a plan of [PantryChange]s from the current data and
 * hands it to the [HouseholdWriter], which writes it as one batch.
 */
class PantryService @Inject constructor(private val writer: HouseholdWriter, private val clock: Clock) {
    private fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Saves a new batch. Reuses the product with the same name key (invariant 4), keeps its unit (no conversion),
     * and increments an existing batch with the same product, date and location instead of duplicating it.
     */
    suspend fun add(householdId: String, actor: Actor, draft: StockDraft, data: PantryData): AddResult {
        val plan = planAdd(draft, data)
        writer.apply(householdId, actor, plan.changes)
        return plan.result
    }

    /** The plan behind [add], without writing it. */
    fun planAdd(draft: StockDraft, data: PantryData): AddPlan {
        val name = NameKey.clean(draft.name)
        require(name.isNotEmpty() && name.length <= PantryLimits.MAX_NAME_LENGTH) { "Invalid name" }
        require(draft.quantity > 0) { "Quantity must be above 0" }
        val key = NameKey.of(name)
        val existing = data.products.firstOrNull { it.nameKey == key }
        val changes = mutableListOf<HouseholdChange>()

        val product = if (existing == null) {
            val recent = ShelfLife.record(emptyList(), today(), draft.expiryDate)
            Product(
                id = writer.newId(),
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
                lastAddedQuantity = draft.quantity,
            ).also { changes += PantryChange.CreateProduct(it) }
        } else {
            val recent = ShelfLife.record(existing.recentShelfLifeDays, today(), draft.expiryDate)
            val hasStock = data.batches.any { it.productId == existing.id && it.quantity > 0 }
            // The unit is fixed once a product has stock (invariant 3).
            val unit = if (hasStock) existing.unit else draft.unit
            val updated = existing.copy(
                unit = unit,
                recentShelfLifeDays = recent,
                typicalShelfLifeDays = ShelfLife.median(recent) ?: existing.typicalShelfLifeDays,
                // An amount in another unit would be meaningless in this one.
                lastAddedQuantity = if (unit == draft.unit) draft.quantity else existing.lastAddedQuantity,
            ).withKeepAtLeast(draft.minQuantity, draft.autoAddToGrocery, draft.buyQuantity, existing)
            if (updated != existing) changes += PantryChange.UpdateProduct(updated)
            updated
        }

        val target = data.batches.firstOrNull {
            // An opened batch keeps to itself: new, sealed stock is not added to it.
            it.productId == product.id && it.expiryDate == draft.expiryDate && it.location == draft.location &&
                it.quantity > 0 && it.openedOn == null
        }
        var batches = data.batches
        val batchId = if (target != null) {
            changes += PantryChange.AddToBatch(target.id, draft.quantity)
            batches = batches.map { if (it.id == target.id) it.copy(quantity = it.quantity + draft.quantity) else it }
            target.id
        } else {
            val id = writer.newId()
            val batch = StockBatch(
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
            )
            changes += PantryChange.CreateBatch(batch)
            batches = batches + batch
            id
        }
        changes += PantryChange.LogEvent(
            writer.newId(),
            PantryEvent.Type.Added,
            product.id,
            product.name,
            draft.quantity,
            null,
        )
        val products = data.products.filter { it.id != product.id } + product
        return AddPlan(
            changes,
            AddResult(product.id, batchId, merged = target != null),
            data.copy(products = products, batches = batches),
        )
    }

    /** +1 or -1. Going to 0 or below uses the batch up, which can be undone. */
    suspend fun adjust(householdId: String, actor: Actor, batch: StockBatch, delta: Double): Undo? {
        if (batch.quantity + delta <= 0) return usedUp(householdId, actor, batch)
        writer.apply(householdId, actor, listOf(PantryChange.AddToBatch(batch.id, delta)))
        return null
    }

    /** Deletes the batch and counts it as used for Insights (DESIGN 5.7). Undo restores it and the count. */
    suspend fun usedUp(householdId: String, actor: Actor, batch: StockBatch): Undo {
        val counted = StatsChange.usedUp(today())
        writer.apply(householdId, actor, listOf(PantryChange.DeleteBatch(batch.id), counted))
        return Undo(listOf(PantryChange.RestoreBatch(batch), StatsChange.undo(counted)))
    }

    /**
     * Marks [count] of [batch] opened today, to be used within [days] (DESIGN 5.2, after v1). The date only ever
     * moves earlier. Opening some of a batch kept in pieces ("1 of 6 yogurts") moves those to a batch of their own;
     * otherwise the whole batch is opened. [product] remembers [days] for next time. Undo puts the batch back as it
     * was (the remembered days stay).
     */
    suspend fun markOpened(
        householdId: String,
        actor: Actor,
        batch: StockBatch,
        count: Double,
        days: Int,
        product: Product?,
    ): Undo {
        require(days in 1..PantryLimits.MAX_OPENED_DAYS) { "Invalid days" }
        val opened = today()
        val byDays = opened.plusDays(days.toLong())
        val expiry = batch.expiryDate?.let { minOf(it, byDays) } ?: byDays
        val changes = mutableListOf<HouseholdChange>()
        val undo = mutableListOf<HouseholdChange>()
        val splits = batch.unit == QuantityUnit.Pieces && count > 0 && count < batch.quantity - EPSILON
        if (splits) {
            val part = batch.copy(
                id = writer.newId(),
                quantity = count,
                expiryDate = expiry,
                openedOn = opened,
                createdAt = null,
            )
            changes += listOf(PantryChange.AddToBatch(batch.id, -count), PantryChange.CreateBatch(part))
            undo += listOf(PantryChange.DeleteBatch(part.id), PantryChange.AddToBatch(batch.id, count))
        } else {
            changes += PantryChange.MarkOpened(batch.id, opened, expiry)
            undo += PantryChange.MarkOpened(batch.id, batch.openedOn, batch.expiryDate)
        }
        if (product != null &&
            product.openedShelfLifeDays != days
        ) {
            changes += PantryChange.SetOpenedShelfLife(product.id, days)
        }
        writer.apply(householdId, actor, changes)
        return Undo(undo)
    }

    /**
     * Takes [amounts] (product id to amount, in the product's unit) off the pantry in one write (DESIGN 5.5, "I cooked
     * this"): the batch that expires first goes first, a batch taken to 0 is used up and counted for Insights, and
     * [extra] (such as the activity event) is written with it. Amounts above the stock take what there is. One Undo
     * puts every batch, count and [undoExtra] back. Returns null when there is nothing to take.
     */
    suspend fun use(
        householdId: String,
        actor: Actor,
        amounts: Map<String, Double>,
        data: PantryData,
        extra: List<HouseholdChange> = emptyList(),
        undoExtra: List<HouseholdChange> = emptyList(),
    ): Undo? {
        val changes = mutableListOf<HouseholdChange>()
        val undo = mutableListOf<HouseholdChange>()
        for ((productId, amount) in amounts) {
            var left = amount
            val batches = data.batches
                .filter { it.productId == productId && it.quantity > 0 }
                .sortedWith(compareBy(nullsLast()) { it: StockBatch -> it.expiryDate }.thenBy { it.createdAt })
            for (batch in batches) {
                if (left <= EPSILON) break
                if (left >= batch.quantity - EPSILON) {
                    val counted = StatsChange.usedUp(today())
                    changes += listOf(PantryChange.DeleteBatch(batch.id), counted)
                    undo += listOf(PantryChange.RestoreBatch(batch), StatsChange.undo(counted))
                    left -= batch.quantity
                } else {
                    changes += PantryChange.AddToBatch(batch.id, -left)
                    undo += PantryChange.AddToBatch(batch.id, left)
                    left = 0.0
                }
            }
        }
        if (changes.isEmpty()) return null
        writer.apply(householdId, actor, changes + extra)
        return Undo(undo + undoExtra)
    }

    /**
     * Deletes the batch, logs why (DESIGN 5.2) and counts it for Insights. Undo restores the batch, removes the log
     * entry and takes the count back off the same month.
     */
    suspend fun throwAway(householdId: String, actor: Actor, batch: StockBatch, reason: DiscardReason): Undo =
        throwAwayAll(householdId, actor, listOf(batch), reason)!!

    /**
     * Throws [batches] away for [reason] in one write, each logged, counted for Insights once. One Undo puts them all
     * back. Null when there is nothing to do. ([throwAway] uses it for one batch.)
     */
    suspend fun throwAwayAll(
        householdId: String,
        actor: Actor,
        batches: List<StockBatch>,
        reason: DiscardReason,
    ): Undo? {
        if (batches.isEmpty()) return null
        val changes = mutableListOf<HouseholdChange>()
        val undo = mutableListOf<HouseholdChange>()
        for (batch in batches) {
            val eventId = writer.newId()
            changes += listOf(
                PantryChange.DeleteBatch(batch.id),
                PantryChange.LogEvent(
                    eventId,
                    PantryEvent.Type.Discarded,
                    batch.productId,
                    batch.productName,
                    batch.quantity,
                    reason,
                ),
            )
            undo += listOf(PantryChange.RestoreBatch(batch), PantryChange.DeleteEvent(eventId))
        }
        // One count for all of them: one write to this month's stats instead of one each.
        val counted = StatsChange.thrownAway(today(), reason, batches.size)
        changes += counted
        undo += StatsChange.undo(counted)
        writer.apply(householdId, actor, changes)
        return Undo(undo)
    }

    /** Typed changes. A quantity of 0 uses the batch up. */
    suspend fun edit(householdId: String, actor: Actor, batch: StockBatch, edit: BatchEdit): Undo? {
        if (edit.quantity <= 0) return usedUp(householdId, actor, batch)
        val clean = edit.copy(note = edit.note?.trim()?.take(PantryLimits.MAX_NOTE_LENGTH)?.ifEmpty { null })
        writer.apply(householdId, actor, listOf(PantryChange.UpdateBatch(batch.id, clean)))
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
        val changes = mutableListOf<HouseholdChange>(PantryChange.UpdateProduct(updated))
        if (updated.name != product.name || updated.unit != product.unit || updated.category != product.category) {
            batches.forEach {
                changes +=
                    PantryChange.SyncBatchProduct(it.id, updated.name, updated.unit, updated.category)
            }
        }
        writer.apply(householdId, actor, changes)
    }

    /**
     * The Edit window: the purchase's quantity, date and location, and its product's name, unit, category and restock
     * settings, in one write. A quantity of 0 uses the purchase up instead. The unit can change only when this is the
     * product's only purchase (no unit conversion, DESIGN 2).
     */
    suspend fun editItem(
        householdId: String,
        actor: Actor,
        batch: StockBatch,
        edit: ItemEdit,
        data: PantryData,
    ): Undo? {
        if (edit.quantity <= 0) return usedUp(householdId, actor, batch)
        writer.apply(householdId, actor, planEdit(batch, edit, data))
        return null
    }

    fun planEdit(batch: StockBatch, edit: ItemEdit, data: PantryData): List<HouseholdChange> {
        val name = NameKey.clean(edit.name)
        require(name.isNotEmpty() && name.length <= PantryLimits.MAX_NAME_LENGTH) { "Invalid name" }
        val key = NameKey.of(name)
        val product = data.products.firstOrNull { it.id == batch.productId }
        if (data.products.any { it.id != batch.productId && it.nameKey == key }) throw AppError.NameTaken()
        val others = data.batches.filter { it.productId == batch.productId && it.id != batch.id && it.quantity > 0 }
        val unit = if (others.isEmpty()) edit.unit else product?.unit ?: batch.unit
        val min = edit.minQuantity?.takeIf { edit.autoAddToGrocery }
        val changes = mutableListOf<HouseholdChange>(
            PantryChange.UpdateBatch(
                batch.id,
                BatchEdit(edit.quantity, edit.expiryDate, batch.dateType, edit.location, batch.note),
            ),
        )
        if (product != null) {
            val raised = min != null && (product.minQuantity == null || min > product.minQuantity)
            val updated = product.copy(
                name = name,
                nameKey = key,
                category = edit.category,
                unit = unit,
                minQuantity = min,
                autoAddToGrocery = min != null,
                buyQuantity = edit.buyQuantity?.takeIf { min != null },
                restockArmed = product.restockArmed || raised,
            )
            if (updated != product) changes += PantryChange.UpdateProduct(updated)
        }
        // Every purchase carries a copy of the product's name, unit and category (invariant 2).
        if (name != batch.productName || unit != batch.unit || edit.category != batch.category) {
            (others + batch).forEach { changes += PantryChange.SyncBatchProduct(it.id, name, unit, edit.category) }
        }
        return changes
    }

    suspend fun undo(householdId: String, actor: Actor, undo: Undo) = writer.apply(householdId, actor, undo.changes)

    /** Batches at 0 or below are hidden and deleted by whichever client notices first (DESIGN 8.5, invariant 1). */
    suspend fun deleteEmptyBatches(householdId: String, actor: Actor, data: PantryData) {
        val empty = data.batches.filter { it.quantity <= 0 && !it.pendingSync }
        if (empty.isNotEmpty()) writer.apply(householdId, actor, empty.map { PantryChange.DeleteBatch(it.id) })
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

/** Quantities are doubles; amounts this close count as equal. */
private const val EPSILON = 1e-9
