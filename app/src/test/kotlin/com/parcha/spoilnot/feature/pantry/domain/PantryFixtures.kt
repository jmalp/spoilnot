package com.parcha.spoilnot.feature.pantry.domain

import java.time.Instant
import java.time.LocalDate

/** Builders for pantry tests. "Today" is fixed at 2026-10-12. */
object PantryFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 12)

    fun product(
        id: String,
        name: String = id.replaceFirstChar { it.uppercase() },
        min: Double? = null,
        location: Location = Location.Fridge,
        category: Category = Category.Dairy,
        recent: List<Int> = emptyList(),
        unit: QuantityUnit = QuantityUnit.Pieces,
    ) = Product(
        id = id,
        name = name,
        nameKey = NameKey.of(name),
        category = category,
        defaultLocation = location,
        unit = unit,
        minQuantity = min,
        autoAddToGrocery = min != null,
        buyQuantity = null,
        restockArmed = true,
        typicalShelfLifeDays = ShelfLife.median(recent),
        recentShelfLifeDays = recent,
        createdAt = null,
    )

    fun batch(
        id: String,
        productId: String,
        quantity: Double = 1.0,
        inDays: Long? = 5,
        location: Location = Location.Fridge,
        dateType: DateType = DateType.UseBy,
        addedSecondsAgo: Long = 0,
        name: String = productId.replaceFirstChar { it.uppercase() },
    ) = StockBatch(
        id = id,
        productId = productId,
        productName = name,
        unit = QuantityUnit.Pieces,
        category = Category.Dairy,
        quantity = quantity,
        expiryDate = inDays?.let { TODAY.plusDays(it) },
        dateType = dateType,
        location = location,
        note = null,
        createdAt = Instant.parse("2026-10-12T12:00:00Z").minusSeconds(addedSecondsAgo),
    )
}
