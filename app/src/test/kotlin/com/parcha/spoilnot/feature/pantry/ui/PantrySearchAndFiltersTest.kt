package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pantry's search field and filters (DESIGN 5.2; owner, 2026-11-21): the field shows what was typed even while the
 * pantry's state comes back later, and taking a filter off starts the list at the top again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantrySearchAndFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    private fun location(i: Int) = if (i % 2 == 0) Location.Fridge else Location.Pantry

    private val products = (1..30).map { i -> product("p$i", name = "Item $i", location = location(i)) }

    // Nothing needs using up soon, so the default view has no Use first group: one group, keyed as a filtered list's
    // is (the owner's case, 2026-11-21).
    private val batches = (1..30).map { i ->
        batch("b$i", "p$i", inDays = 30L + i, name = "Item $i", location = location(i))
    }

    private fun state(query: PantryQuery = PantryQuery()): PantryViewModel.UiState {
        val all = PantryItems.all(products, batches, TODAY)
        return PantryViewModel.UiState(
            loading = false,
            groups = PantryItems.build(all, query),
            itemCount = all.size,
            query = query,
            today = TODAY,
            data = PantryData(products, batches, fromCache = false),
        )
    }

    private fun handlers(onSearch: (String) -> Unit = {}) = PantryHandlers(
        onAdd = {},
        onSearch = onSearch,
        onFilter = {},
        onSort = {},
        onGroup = {},
        onRetry = {},
        onOpen = {},
        actions = BatchActions({}, {}, {}, {}, {}),
    )

    @Test
    fun theSearchFieldShowsEveryLetterWhileThePantryCatchesUp() {
        var searchText by mutableStateOf("")
        compose.setThemed(dark = false, fontScale = 1f) {
            // The pantry's state never catches up here; on a phone it comes back off the main thread, a little later
            // than each key. The field must not wait for it.
            PantryTab(
                state = state(),
                searchText = searchText,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = handlers(onSearch = { searchText = it }),
            )
        }
        for (c in "item 12") compose.onNodeWithTag("pantry_search").performTextInput(c.toString())
        compose.onNodeWithTag("pantry_search").assertTextEquals("item 12")
        assertEquals("item 12", searchText)
    }

    @Test
    fun takingAFilterOffStartsTheListAtTheTop() {
        var state by mutableStateOf(state())
        val list = LazyListState()
        compose.setThemed(dark = false, fontScale = 1f) {
            PantryTab(
                state = state,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = handlers(),
                listState = list,
            )
        }
        compose.waitForIdle()
        assertEquals(0 to 0, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)

        state = state(PantryQuery(filter = PantryFilter.Fridge))
        compose.waitForIdle()
        assertEquals(0 to 0, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)

        // Before, the default view kept the filtered list's first row in view (Item 2, the Fridge's first), with Item 1
        // above it out of sight: the same key in both lists.
        state = state()
        compose.waitForIdle()
        assertEquals(0 to 0, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    }

    @Test
    fun aNewSearchStartsAtTheTopEvenFromFarDown() {
        var state by mutableStateOf(state())
        val list = LazyListState()
        compose.setThemed(dark = false, fontScale = 1f) {
            PantryTab(
                state = state,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = handlers(),
                listState = list,
            )
        }
        compose.runOnIdle { list.requestScrollToItem(20) }
        compose.waitForIdle()
        state = state(PantryQuery(search = "item"))
        compose.waitForIdle()
        assertEquals(0 to 0, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    }
}
