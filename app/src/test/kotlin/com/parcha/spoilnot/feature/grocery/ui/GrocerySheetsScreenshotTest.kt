package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.RestockReason
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Edit grocery item in its own window, light and dark at 1.0× and 2.0× text (after v1, review CODE-07). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GrocerySheetsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    /** An item the app added: the sheet says why (after v1, UX-08). */
    @Test
    fun editItem() {
        val item = GroceryItem(
            id = "i1",
            name = "Milk",
            nameKey = "milk",
            quantity = 2.0,
            unit = QuantityUnit.Liters,
            note = "Lactose free",
            category = Category.Dairy,
            checked = false,
            checkedBy = null,
            source = ItemSource.LowStock,
            productId = "milk",
            recipeId = null,
            createdBy = "u1",
            createdAt = null,
        )
        compose.setThemed(dark, fontScale) {
            EditItemSheet(item, onSave = { _, _, _, _, _ ->
            }, onDismiss = {
            }, reason = RestockReason.Low(have = 0.5, keepAtLeast = 1.0, unit = QuantityUnit.Liters))
        }
        compose.waitForIdle()
        captureScreenRoboImage(Screenshots.screenshotName("grocery_edit_item", dark, fontScale))
    }
}
