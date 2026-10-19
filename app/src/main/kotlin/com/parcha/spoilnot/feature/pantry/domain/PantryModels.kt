package com.parcha.spoilnot.feature.pantry.domain

import java.time.Instant
import java.time.LocalDate

/** Where a batch is stored. [wire] is the stored value (DESIGN 8.2). */
enum class Location(val wire: String) {
    Fridge("fridge"),
    Freezer("freezer"),
    Pantry("pantry"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Other
    }
}

enum class Category(val wire: String) {
    Produce("produce"),
    Dairy("dairy"),
    MeatFish("meat_fish"),
    Bakery("bakery"),
    Frozen("frozen"),
    PantryStaples("pantry_staples"),
    Drinks("drinks"),
    Snacks("snacks"),

    /** Leftovers and cooked dishes (beta feedback). */
    Cooked("cooked"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Other
    }
}

/** No conversion between units in v1 (DESIGN 2). */
enum class QuantityUnit(val wire: String) {
    Pieces("pcs"),
    Grams("g"),
    Kilograms("kg"),
    Ounces("oz"),
    Pounds("lb"),
    Milliliters("ml"),
    Liters("l"),
    FluidOunces("fl_oz"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Pieces

        /** The unit menu: pieces first, then the preferred system (Settings > Appearance), then the other. */
        fun menu(imperialFirst: Boolean): List<QuantityUnit> {
            val metric = listOf(Grams, Kilograms, Milliliters, Liters)
            val imperial = listOf(Ounces, Pounds, FluidOunces)
            return listOf(Pieces) + if (imperialFirst) imperial + metric else metric + imperial
        }
    }
}

/** Use by is a safety date; best before is a quality date. Changes the warning wording (DESIGN 5.2). */
enum class DateType(val wire: String) {
    UseBy("use_by"),
    BestBefore("best_before"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: UseBy
    }
}

/** households/{hid}/products/{id}: a kind of food (DESIGN 8.2). */
data class Product(
    val id: String,
    val name: String,
    val nameKey: String,
    val category: Category,
    val defaultLocation: Location,
    val unit: QuantityUnit,
    /** Keep at least. */
    val minQuantity: Double?,
    val autoAddToGrocery: Boolean,
    val buyQuantity: Double?,
    val restockArmed: Boolean,
    val typicalShelfLifeDays: Int?,
    /** Shelf lives of the most recent batches, newest last (deviation, see docs/DECISIONS.md). */
    val recentShelfLifeDays: List<Int>,
    val createdAt: Instant?,
    /** How much was added the last time, in [unit]: the usual amount for recipe grocery items (after v1). */
    val lastAddedQuantity: Double? = null,
) {
    val isStaple: Boolean get() = minQuantity != null
}

/** households/{hid}/stock/{id}: one purchase with one date and one location (DESIGN 8.2). */
data class StockBatch(
    val id: String,
    val productId: String,
    val productName: String,
    val unit: QuantityUnit,
    val category: Category,
    val quantity: Double,
    val expiryDate: LocalDate?,
    val dateType: DateType,
    val location: Location,
    val note: String?,
    val createdAt: Instant?,
    /** True while a local change has not reached the server (DESIGN 6.7 pending-sync icon). */
    val pendingSync: Boolean = false,
)

/** What a person enters in the Add item sheet. */
data class StockDraft(
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val expiryDate: LocalDate?,
    val dateType: DateType,
    val location: Location,
    val category: Category,
    val note: String?,
    val minQuantity: Double?,
    val autoAddToGrocery: Boolean,
    val buyQuantity: Double?,
)

/** Changes to one batch from the edit sheet or the Move action. */
data class BatchEdit(
    val quantity: Double,
    val expiryDate: LocalDate?,
    val dateType: DateType,
    val location: Location,
    val note: String?,
)

/**
 * The Edit window (beta feedback): one purchase and its product together. Keep at least and Buy quantity only exist
 * with Add to grocery list automatically on.
 */
data class ItemEdit(
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val category: Category,
    val expiryDate: LocalDate?,
    val location: Location,
    val autoAddToGrocery: Boolean,
    val minQuantity: Double?,
    val buyQuantity: Double?,
)

/** Changes to a product from its detail screen. */
data class ProductEdit(
    val name: String,
    val category: Category,
    val defaultLocation: Location,
    val unit: QuantityUnit,
    val minQuantity: Double?,
    val autoAddToGrocery: Boolean,
    val buyQuantity: Double?,
    val typicalShelfLifeDays: Int?,
)

enum class DiscardReason(val wire: String) {
    Expired("expired"),
    Spoiled("spoiled"),
    DidNotLike("didnt_like"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

/** A pantry event in the activity log (DESIGN 5.6), as shown in product history. */
data class PantryEvent(
    val id: String,
    val type: Type,
    val actorName: String,
    val quantity: Double?,
    val reason: DiscardReason?,
    val createdAt: Instant?,
) {
    enum class Type { Added, Discarded }
}

object PantryLimits {
    const val MAX_NAME_LENGTH = 80
    const val MAX_NOTE_LENGTH = 200

    /** DESIGN 8.5: chunk batched writes at 400 operations. */
    const val BATCH_LIMIT = 400
}
