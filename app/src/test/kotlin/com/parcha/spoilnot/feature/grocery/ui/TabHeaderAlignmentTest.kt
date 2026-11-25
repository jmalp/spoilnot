package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.R
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.ui.BatchActions
import com.parcha.spoilnot.feature.pantry.ui.PantryHandlers
import com.parcha.spoilnot.feature.pantry.ui.PantryTab
import com.parcha.spoilnot.feature.pantry.ui.PantryViewModel
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Groceries' header lines up with every other tab's (DESIGN 19.7; owner, 2026-11-21): its title is the list switcher,
 * a 48 dp button, which made the header taller and put the title and the profile button lower than on Pantry.
 */
@RunWith(RobolectricTestRunner::class)
// Real text metrics: without them a title line isn't 40 sp tall and the header can't be compared.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TabHeaderAlignmentTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.open_settings)

    private val pantry = PantryViewModel.UiState(
        loading = false,
        itemCount = 0,
        pantryEmpty = true,
        today = TODAY,
        data = PantryData(emptyList(), emptyList(), fromCache = false),
    )
    private val list = GroceryList("default", "Groceries", isDefault = true)
    private val groceries = GroceriesViewModel.UiState(
        loading = false,
        lists = listOf(list),
        list = list,
        items = emptyList(),
        view = StoreOrder.sort(GroceryLayout.of(emptyList()), emptyList()),
        pantry = PantryData(emptyList(), emptyList(), fromCache = false),
        today = TODAY,
        myUid = "u1",
    )

    /** The title's and the profile button's bounds on Pantry, then on Groceries. */
    private fun headers(fontScale: Float): Pair<Pair<DpRect, DpRect>, Pair<DpRect, DpRect>> {
        var showGroceries by mutableStateOf(false)
        compose.setThemed(dark = false, fontScale = fontScale) {
            if (showGroceries) {
                GroceriesTab(
                    state = groceries,
                    text = "",
                    suggestions = emptyList(),
                    aisleHint = false,
                    snackbar = remember { SnackbarHostState() },
                    onOpenSettings = {},
                    handlers = GroceriesHandlers({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            } else {
                PantryTab(
                    state = pantry,
                    snackbar = remember { SnackbarHostState() },
                    onOpenSettings = {},
                    handlers = PantryHandlers({}, {}, {}, {}, {}, {}, {}, BatchActions({}, {}, {}, {}, {})),
                )
            }
        }
        val pantryTitle = compose.onNodeWithText("Pantry").getBoundsInRoot()
        val pantrySettings = compose.onNode(hasContentDescription(settings) and hasClickAction()).getBoundsInRoot()
        showGroceries = true
        compose.waitForIdle()
        val groceriesTitle = compose.onNodeWithText("Groceries", useUnmergedTree = true).getBoundsInRoot()
        val groceriesSettings = compose.onNode(hasContentDescription(settings) and hasClickAction()).getBoundsInRoot()
        return (pantryTitle to pantrySettings) to (groceriesTitle to groceriesSettings)
    }

    private fun assertSameTop(what: String, expected: Dp, actual: Dp) =
        assertEquals(what, expected.value, actual.value, 0.5f)

    @Test
    fun theTitleAndTheProfileButtonSitWherePantryHasThem() {
        val (pantry, groceries) = headers(fontScale = 1f)
        assertSameTop("title", pantry.first.top, groceries.first.top)
        assertSameTop("profile button", pantry.second.top, groceries.second.top)
    }

    @Test
    fun alsoAtTwiceTheTextSize() {
        val (pantry, groceries) = headers(fontScale = 2f)
        assertSameTop("title", pantry.first.top, groceries.first.top)
        assertSameTop("profile button", pantry.second.top, groceries.second.top)
    }

    @Test
    fun theSwitcherKeepsA48DpTargetThatWorksAtItsEdges() {
        headers(fontScale = 1f)
        val switcher = compose.onNodeWithTag("grocery_list_switcher")
        val bounds = switcher.getBoundsInRoot()
        assert(bounds.bottom - bounds.top >= 48.dp) { "the switcher is ${bounds.bottom - bounds.top} tall" }
        // A tap near its top edge, outside the title's line, still opens the menu.
        switcher.performTouchInput { click(Offset(centerX, 2f)) }
        compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.grocery_new_list),
        ).assertExists()
    }
}
