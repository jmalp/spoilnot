package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Grocery list rows on the JVM (DESIGN 13.1), with screenshots in light/dark at font 1.0 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GroceryRowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun item(
        id: String,
        name: String,
        source: ItemSource = ItemSource.Manual,
        checked: Boolean = false,
        by: String? = "u2",
    ) = GroceryItem(
        id = id,
        name = name,
        nameKey = name.lowercase(),
        quantity = 2.0,
        unit = QuantityUnit.Pieces,
        note = null,
        category = Category.Dairy,
        checked = checked,
        checkedBy = null,
        source = source,
        productId = null,
        recipeId = null,
        createdBy = by,
        createdAt = null,
    )

    private val state = GroceriesViewModel.UiState(
        loading = false,
        memberNames = mapOf("u1" to "Jordan", "u2" to "Sam"),
        myUid = "u1",
        backInStock = setOf("low_milk"),
    )

    private val rows = listOf(
        item("low_milk", "Milk", ItemSource.LowStock, by = null),
        item("eggs", "Eggs"),
        item("flour", "Flour", ItemSource.Recipe, by = "u1"),
        item("bread", "Bread", checked = true, by = "u1"),
    )

    private fun show(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) =
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) { Surface { Column { content() } } }
            }
        }

    @Test
    fun rowShowsQuantitySourceAndWhoAddedIt() {
        show { rows.forEach { GroceryRow(it, state, {}, {}, {}) } }
        compose.onNodeWithText("Low stock", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Recipe", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("added by Sam", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Back in stock", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tapChecksOffAndMenuDeletes() {
        val toggled = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        show { GroceryRow(item("eggs", "Eggs"), state, { toggled += it.id }, { deleted += it.id }, {}) }
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.onNodeWithContentDescription("Options for Eggs").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("eggs"), toggled)
        assertEquals(listOf("eggs"), deleted)
    }

    @Test
    fun checkedRowReadsAsOn() {
        show { GroceryRow(item("bread", "Bread", checked = true), state, {}, {}, {}) }
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun backInStockRemoveDeletes() {
        val deleted = mutableListOf<String>()
        show { GroceryRow(rows[0], state, {}, { deleted += it.id }, {}) }
        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf("low_milk"), deleted)
    }

    @Test
    fun screenshots() {
        var variant by mutableStateOf(false to 1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, variant.second)) {
                SpoilNotTheme(darkTheme = variant.first, colorTheme = ColorTheme.SpoilNot) {
                    Surface { Column { rows.forEach { GroceryRow(it, state, {}, {}, {}) } } }
                }
            }
        }
        listOf(false to 1f, true to 1f, false to 2f, true to 2f).forEach { next ->
            variant = next
            compose.waitForIdle()
            val name = "grocery_rows_${if (next.first) "dark" else "light"}_font${next.second.toInt()}"
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        }
    }
}
