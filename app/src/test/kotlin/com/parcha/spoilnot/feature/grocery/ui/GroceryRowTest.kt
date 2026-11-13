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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
        item("tortillas", "Tortillas", ItemSource.MealPlan, by = null),
        item("bread", "Bread", checked = true, by = "u1"),
        item("low_butter", "Butter", ItemSource.LowStock, by = null),
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
        // What the app did by itself says so (after v1, UX-08).
        compose.onNodeWithText("Added automatically", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Recipe", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Meal plan", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("added by Sam", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Back in stock", useUnmergedTree = true).assertIsDisplayed()
    }

    /** One line, "Back in stock · Remove", instead of a Low stock badge next to a Back in stock one (UX-08). */
    @Test
    fun backInStockReplacesTheAutomaticBadge() {
        show { GroceryRow(rows[0], state, {}, {}, {}) }
        compose.onNodeWithText("Back in stock", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Added automatically", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Milk from the list, it's back in stock").assertIsDisplayed()
    }

    /** After v1 (UX-10): right checks off, left deletes; each says so under the row while it moves. */
    @Test
    fun swipeRightChecksOffAndSwipeLeftDeletes() {
        val toggled = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        show { GroceryRow(item("eggs", "Eggs"), state, { toggled += it.id }, { deleted += it.id }, {}) }
        compose.onNodeWithTag("grocery_item_eggs").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("eggs"), toggled)
        assertEquals(emptyList<String>(), deleted)

        compose.onNodeWithTag("grocery_item_eggs").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("eggs"), toggled)
        assertEquals(listOf("eggs"), deleted)
    }

    @Test
    fun swipeRightPutsACheckedItemBack() {
        val toggled = mutableListOf<Boolean>()
        show { GroceryRow(item("bread", "Bread", checked = true), state, { toggled += it.checked }, {}, {}) }
        compose.onNodeWithTag("grocery_item_bread").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(true), toggled)
    }

    @Test
    fun swipeBackgroundsNameTheirAction() {
        show { GroceryRow(item("eggs", "Eggs"), state, {}, {}, {}) }
        compose.onNodeWithTag("grocery_item_eggs").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.3f, 0f))
        }
        compose.onNodeWithText("Check off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("grocery_item_eggs").performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(-width * 0.6f, 0f))
        }
        compose.onNodeWithText("Delete", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("grocery_item_eggs").performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.3f, 0f))
            up()
        }
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
            compose.onRoot().captureRoboImage("$name.png")
        }
    }
}
