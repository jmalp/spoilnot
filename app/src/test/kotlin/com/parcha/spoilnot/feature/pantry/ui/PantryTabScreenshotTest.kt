package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.navigation.ShellLayout
import com.parcha.spoilnot.core.navigation.TabBar
import com.parcha.spoilnot.core.navigation.TopLevelDestination
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Pantry tab in the 2026-11 look (DESIGN 19.7, 19.10, 19.12): how many items above the title, the search field,
 * the filter chips, and the default view's two groups with the expired backlog folded into Use first; and the empty
 * pantry. Light and dark, text at 1.0 and 2.0.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantryTabScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private val products = listOf(
        product("cheese"),
        product("milk"),
        product("yogurt", name = "Greek yogurt"),
        product("eggs", category = Category.Dairy),
        product("rice", location = Location.Pantry, category = Category.PantryStaples, unit = QuantityUnit.Grams),
        product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry),
    )
    private val batches = listOf(
        batch("c1", "cheese", inDays = -6),
        batch("c2", "cheese", inDays = -4),
        batch("c3", "cheese", inDays = -2),
        batch("m1", "milk", quantity = 2.0, inDays = 1),
        batch("y1", "yogurt", quantity = 6.0, inDays = 2, name = "Greek yogurt"),
        batch("e1", "eggs", quantity = 12.0, inDays = 6),
        batch(
            "r1",
            "rice",
            quantity = 500.0,
            inDays = null,
            location = Location.Pantry,
        ).copy(unit = QuantityUnit.Grams),
    )

    private fun state(products: List<Product>, batches: List<StockBatch>): PantryViewModel.UiState {
        val all = PantryItems.all(products, batches, TODAY)
        return PantryViewModel.UiState(
            loading = false,
            groups = PantryItems.build(all, PantryQuery()),
            pantryEmpty = all.isEmpty(),
            offline = false,
            itemCount = all.size,
            query = PantryQuery(),
            today = TODAY,
            data = PantryData(products, batches, fromCache = false),
        )
    }

    private val handlers = PantryHandlers(
        onAdd = {},
        onSearch = {},
        onFilter = {},
        onSort = {},
        onGroup = {},
        onRetry = {},
        onOpen = {},
        actions = BatchActions({}, {}, {}, {}, {}),
    )

    private fun show(state: PantryViewModel.UiState) = compose.setThemed(dark, fontScale) {
        ShellLayout(bar = { TabBar(TopLevelDestination.Pantry, it) {} }) {
            PantryTab(
                state = state,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = handlers,
            )
        }
    }

    @Test
    fun theDefaultViewInTwoGroups() {
        show(state(products, batches))

        // 3 cheese, milk, yogurt, eggs, rice and the Out olive oil: every row of the unfiltered list.
        compose.onNodeWithText("8 items").assertExists()
        compose.onNode(isHeading() and hasText("Use first")).assertExists()
        compose.onNode(isHeading() and hasText("Everything else")).assertExists()
        compose.onNodeWithText("3 expired items").assertExists()
        compose.onNodeWithTag("pantry_expired_toggle").assertHasClickAction()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("pantry_tab", dark, fontScale))

        // Shown, the expired rows follow the folded line inside Use first.
        compose.onNodeWithTag("pantry_expired_toggle").performClick()
        compose.onNodeWithTag("pantry_item_c1").assertExists()
    }

    @Test
    fun anEmptyPantry() {
        show(state(emptyList(), emptyList()))
        compose.onNodeWithText("0 items").assertExists()
        compose.onNodeWithTag("button_add_first_item").assertHasClickAction()
        compose.onRoot().captureRoboImage(Screenshots.screenshotName("pantry_tab_empty", dark, fontScale))
    }
}
