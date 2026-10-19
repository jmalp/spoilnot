package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The compact Add to pantry sheet (DESIGN 5.3, after v1): one line each, Add all, editors on tap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AddToPantryTest {
    @get:Rule
    val compose = createComposeRule()

    private fun item(id: String, name: String, quantity: Double?, unit: QuantityUnit?, productId: String? = null) =
        GroceryItem(
            id, name, name.lowercase(), quantity, unit, null, Category.Dairy, true, null, ItemSource.Manual,
            productId, null, null, null,
        )

    private val items = listOf(
        item("i1", "Milk", 2.0, null, productId = "milk"),
        item("i2", "Rice", 1.0, QuantityUnit.Kilograms),
    )
    private val pantry = PantryData(listOf(product("milk", location = Location.Fridge)), emptyList(), false)

    private var confirmed: Pair<List<PantryEntry>, List<GroceryItem>>? = null

    private fun show(dark: Boolean = false, fontScale: Float = 1f) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                Surface { AddToPantryContent(items, pantry, TODAY) { e, s -> confirmed = e to s } }
            }
        }
    }

    @Test
    fun eachItemIsOneSummaryLineAndAddAllAddsThem() {
        show()
        compose.onNodeWithText("2 · Fridge", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1 kg · Fridge", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Add all 2 to pantry").performClick()
        val (entries, skipped) = confirmed!!
        assertEquals(listOf("i1", "i2"), entries.map { it.item.id })
        assertEquals(QuantityUnit.Kilograms, entries[1].unit)
        assertEquals(emptyList<GroceryItem>(), skipped)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/add_to_pantry_light_font1.png")
    }

    @Test
    fun tappingALineOpensItsEditorAndSkipLeavesItOut() {
        show(dark = true, fontScale = 2f)
        compose.onNodeWithText("Rice").performClick()
        compose.onNodeWithTag("entry_skip_i2").performClick()
        compose.onNodeWithText("Skipped", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/add_to_pantry_dark_font2.png")
        compose.onNodeWithText("Add 1 to pantry").performClick()
        val (entries, skipped) = confirmed!!
        assertEquals(listOf("i1"), entries.map { it.item.id })
        assertEquals(listOf("i2"), skipped.map { it.id })
    }
}
