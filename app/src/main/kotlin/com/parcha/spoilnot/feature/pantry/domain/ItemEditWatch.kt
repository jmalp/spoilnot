package com.parcha.spoilnot.feature.pantry.domain

/**
 * The purchase and product as the Edit window opened with them (after v1, DESIGN 9.2 and 9.4). Saving compares the
 * form with this and writes only what the person changed; while the window is open, the live copy is compared with it
 * to tell them when someone else changed or removed the item.
 */
data class ItemEditBase(val batch: StockBatch, val product: Product?) {
    val edit: ItemEdit get() = ItemEdit.of(batch, product)
}

/** What happened to the item in the open Edit window, as this phone sees it now. */
sealed interface EditWatch {
    /** Nothing the window shows changed, or only this person changed it (on another device). */
    data object Unchanged : EditWatch

    /** Someone else changed what the window shows: "Sam changed this item. Review". [live] is how it is now. */
    data class Changed(val byUid: String?, val live: ItemEditBase) : EditWatch

    /**
     * The purchase is gone: deleted, or used up to 0. [byUid] is known when it was used up (the screen looks a
     * deletion's author up in the tombstone); when it is this person, the window just closes.
     */
    data class Removed(val byUid: String?) : EditWatch
}

object ItemEditWatch {
    /** Compares the live [data] with [base]. Changes by [myUid] don't count as someone else's. */
    fun check(base: ItemEditBase, data: PantryData, myUid: String): EditWatch {
        val batch = data.batches.firstOrNull { it.id == base.batch.id }
            ?: return EditWatch.Removed(null)
        // Batches at 0 or below are hidden and about to be removed (DESIGN 8.5 invariant 1).
        if (batch.quantity <= 0) return EditWatch.Removed(batch.updatedBy)
        val product = data.products.firstOrNull { it.id == batch.productId }
        val live = ItemEditBase(batch, product)
        val now = live.edit
        val then = base.edit
        if (now == then) return EditWatch.Unchanged
        val batchChanged = now.quantity != then.quantity || now.expiryDate != then.expiryDate ||
            now.location != then.location
        val by = if (batchChanged) batch.updatedBy else product?.updatedBy ?: batch.updatedBy
        return if (by != null && by == myUid) EditWatch.Unchanged else EditWatch.Changed(by, live)
    }

    /**
     * Review: the form shows the item as it is now, with this person's own changes kept. A field they didn't touch
     * takes the [live] value; a changed quantity keeps their difference ("I used 2" stays "2 fewer" than now). Keep at
     * least, Buy quantity and the switch go together.
     */
    fun rebase(draft: ItemEdit, base: ItemEdit, live: ItemEdit): ItemEdit {
        val keepTouched = draft.autoAddToGrocery != base.autoAddToGrocery ||
            draft.minQuantity != base.minQuantity || draft.buyQuantity != base.buyQuantity
        return ItemEdit(
            name = if (NameKey.clean(draft.name) == base.name) live.name else draft.name,
            quantity = (live.quantity + draft.quantity - base.quantity).coerceAtLeast(0.0),
            unit = if (draft.unit == base.unit) live.unit else draft.unit,
            category = if (draft.category == base.category) live.category else draft.category,
            expiryDate = if (draft.expiryDate == base.expiryDate) live.expiryDate else draft.expiryDate,
            location = if (draft.location == base.location) live.location else draft.location,
            autoAddToGrocery = if (keepTouched) draft.autoAddToGrocery else live.autoAddToGrocery,
            minQuantity = if (keepTouched) draft.minQuantity else live.minQuantity,
            buyQuantity = if (keepTouched) draft.buyQuantity else live.buyQuantity,
        )
    }
}
