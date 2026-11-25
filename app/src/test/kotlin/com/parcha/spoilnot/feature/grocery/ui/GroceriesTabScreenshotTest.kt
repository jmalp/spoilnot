package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.navigation.ShellLayout
import com.parcha.spoilnot.core.navigation.TabBar
import com.parcha.spoilnot.core.navigation.TopLevelDestination
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Groceries tab in the 2026-11 look (DESIGN 19.5, 19.7, 19.10): what is left to buy above the list's name, the
 * round quick-add field, the aisles as groups of rows with their label chips, and In cart. Light and dark, text at 1.0
 * and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GroceriesTabScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun item(
        id: String,
        name: String,
        category: Category,
        quantity: Double? = null,
        source: ItemSource = ItemSource.Manual,
        checked: Boolean = false,
        by: String? = "u1",
    ) = GroceryItem(
        id = id,
        name = name,
        nameKey = name.lowercase(),
        quantity = quantity,
        unit = quantity?.let { QuantityUnit.Pieces },
        note = null,
        category = category,
        checked = checked,
        checkedBy = null,
        source = source,
        productId = null,
        recipeId = null,
        createdBy = by,
        createdAt = null,
    )

    private val items = listOf(
        item("eggs", "Eggs", Category.Dairy, 12.0, by = "u2"),
        item("oil", "Olive oil", Category.Dairy, 1.0, ItemSource.LowStock, by = null),
        item("tortillas", "Tortillas", Category.Bakery, 2.0, ItemSource.MealPlan, by = null),
        item("flour", "Flour", Category.PantryStaples, 2.0, ItemSource.Recipe, by = null),
        item("milk", "Milk", Category.Dairy, 2.0, checked = true),
    )

    private val names = mapOf("u1" to "Sam", "u2" to "Jordan")

    private val state = GroceriesViewModel.UiState(
        loading = false,
        lists = listOf(GroceryList("default", "Groceries", isDefault = true)),
        list = GroceryList("default", "Groceries", isDefault = true),
        items = items,
        view = StoreOrder.sort(GroceryLayout.of(items), emptyList()),
        addedBy = addedBy(items, "u1", names),
        memberNames = names,
        pantry = PantryData(emptyList(), emptyList(), fromCache = false),
        today = TODAY,
        myUid = "u1",
    )

    private val handlers = GroceriesHandlers({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun theListInAislesAndTheCart() {
        compose.setThemed(dark, fontScale) {
            ShellLayout(bar = { TabBar(TopLevelDestination.Groceries, it) {} }) {
                GroceriesTab(
                    state = state,
                    text = "",
                    suggestions = emptyList(),
                    aisleHint = false,
                    snackbar = remember { SnackbarHostState() },
                    onOpenSettings = {},
                    handlers = handlers,
                )
            }
        }

        // Eggs, Olive oil, Tortillas and Flour are left to buy; Milk is in the cart.
        compose.onNodeWithText("4 to buy").assertExists()
        compose.onNode(isHeading() and hasText("Dairy and eggs")).assertExists()
        compose.onNodeWithText("added by Jordan", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Added automatically", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("fab_add_to_pantry").assertHasClickAction()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("groceries_tab", dark, fontScale))
    }
}
