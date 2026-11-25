package com.parcha.spoilnot.feature.grocery.ui

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** The facts the view model works out once per snapshot for Groceries' rows (review PERF-04). */
class GroceryRowFactsTest {
    private fun item(id: String, by: String?, source: ItemSource = ItemSource.Manual) = GroceryItem(
        id = id,
        name = id,
        nameKey = id,
        quantity = null,
        unit = null,
        note = null,
        category = null,
        checked = false,
        checkedBy = null,
        source = source,
        productId = null,
        recipeId = null,
        createdBy = by,
        createdAt = null,
    )

    @Test
    fun `only what someone else typed in says who added it`() {
        val names = mapOf("me" to "Sam", "other" to "Jordan")
        val facts = addedBy(
            listOf(
                item("theirs", "other"),
                item("mine", "me"),
                item("auto", "other", ItemSource.LowStock),
                item("planned", "other", ItemSource.MealPlan),
                item("stranger", "gone"),
                item("unknown", null),
            ),
            myUid = "me",
            names = names,
        )
        assertEquals(mapOf("theirs" to "Jordan"), facts)
    }
}
