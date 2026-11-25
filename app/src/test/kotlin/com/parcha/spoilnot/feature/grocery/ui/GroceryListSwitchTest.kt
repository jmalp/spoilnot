package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.parcha.spoilnot.core.ui.REMOTE_HIGHLIGHT_MS
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching grocery lists highlights none of the other list's rows (DESIGN 6.4; after v1, 2026-11-18): they aren't
 * another phone's changes, only rows this screen hasn't seen yet. Another phone's new item on the list shown still is.
 *
 * The view model gives the new list with the old list's items until the new list's items arrive, as it does on a
 * phone. Compose isn't idle while an animation runs, so the time the test clock needs to settle after a change tells
 * whether a 1.5 s highlight ran (PantryListMotionTest does the same).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GroceryListSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    private val groceries = GroceryList("default", "Groceries", isDefault = true)
    private val costco = GroceryList("costco", "Costco", isDefault = false)
    private val lists = listOf(groceries, costco)

    private fun item(id: String, name: String, category: Category = Category.Dairy) = GroceryItem(
        id = id,
        name = name,
        nameKey = name.lowercase(),
        quantity = null,
        unit = null,
        note = null,
        category = category,
        checked = false,
        checkedBy = null,
        source = ItemSource.Manual,
        productId = null,
        recipeId = null,
        createdBy = "u2",
        createdAt = null,
    )

    private val groceryItems = listOf(item("milk", "Milk"), item("bread", "Bread", Category.Bakery))
    private val costcoItems = listOf(item("towels", "Paper towels", Category.Other), item("rice", "Rice"))

    /** [list] on screen with [items], which belong to [itemsOf]: the old list's for a moment after a switch. */
    private fun state(list: GroceryList, items: List<GroceryItem>, itemsOf: GroceryList = list) =
        GroceriesViewModel.UiState(
            loading = false,
            lists = lists,
            list = list,
            items = items,
            itemsListId = itemsOf.id,
            view = StoreOrder.sort(GroceryLayout.of(items), emptyList()),
            pantry = PantryData(emptyList(), emptyList(), fromCache = false),
            today = TODAY,
            myUid = "u1",
        )

    private var state by mutableStateOf(state(groceries, groceryItems))

    private fun show() {
        compose.setThemed(dark = false, fontScale = 1f) {
            GroceriesTab(
                state = state,
                text = "",
                suggestions = emptyList(),
                aisleHint = false,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = GroceriesHandlers({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            )
        }
        compose.waitForIdle()
    }

    /** How long, on the test clock, the screen takes to settle after [change]. */
    private fun settleMs(change: () -> Unit): Long {
        val start = compose.mainClock.currentTime
        change()
        compose.waitForIdle()
        return compose.mainClock.currentTime - start
    }

    /** Switches to [list] as the view model does: the new list with the old items, then the new list's own. */
    private fun switchTo(list: GroceryList, items: List<GroceryItem>) = settleMs {
        state = state(list, state.items, itemsOf = lists.first { it.id == state.itemsListId })
        compose.waitForIdle()
        state = state(list, items)
    }

    @Test
    fun switchingListsHighlightsNone() {
        show()
        val there = switchTo(costco, costcoItems)
        assertTrue("switching to Costco took $there ms: its rows were highlighted", there < REMOTE_HIGHLIGHT_MS)
        compose.onNodeWithTag("grocery_item_rice").assertExists()
        val back = switchTo(groceries, groceryItems)
        assertTrue("switching back took $back ms: its rows were highlighted", back < REMOTE_HIGHLIGHT_MS)
        compose.onNodeWithTag("grocery_item_milk").assertExists()
    }

    @Test
    fun anotherPhonesNewItemOnTheListShownIsStillHighlighted() {
        show()
        switchTo(costco, costcoItems)
        val ms = settleMs { state = state(costco, costcoItems + item("eggs", "Eggs")) }
        assertTrue("another phone's item settled in $ms ms, without its highlight", ms >= REMOTE_HIGHLIGHT_MS)
        compose.onNodeWithTag("grocery_item_eggs").assertExists()
    }
}
