package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.parcha.spoilnot.core.ui.REMOTE_HIGHLIGHT_MS
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.testing.setThemed
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pantry's rows animate only when the food changes (DESIGN 6.4): a new search or filter replaces them at once, and a
 * row it brings back isn't highlighted as another phone's change, so typing doesn't keep the list animating (after v1,
 * 2026-11-17, JankBenchmark). A row used up still fades out, and another phone's new batch is still highlighted.
 *
 * Compose isn't idle while an animation runs, so the time the test clock needs to settle after a change tells whether
 * anything animated: a few frames when nothing did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantryListMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val products = listOf(
        product("milk"),
        product("yogurt", name = "Greek yogurt"),
        product("eggs", category = Category.Dairy),
    )
    private val batches = listOf(
        batch("m1", "milk", quantity = 2.0, inDays = 1),
        batch("y1", "yogurt", quantity = 6.0, inDays = 2, name = "Greek yogurt"),
        batch("e1", "eggs", quantity = 12.0, inDays = 6),
    )

    private fun state(batches: List<StockBatch>, query: PantryQuery = PantryQuery()): PantryViewModel.UiState {
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

    private var state by mutableStateOf(state(batches))

    private fun show() {
        compose.setThemed(dark = false, fontScale = 1f) {
            PantryTab(
                state = state,
                snackbar = remember { SnackbarHostState() },
                onOpenSettings = {},
                handlers = PantryHandlers(
                    onAdd = {},
                    onSearch = {},
                    onFilter = {},
                    onSort = {},
                    onGroup = {},
                    onRetry = {},
                    onOpen = {},
                    actions = BatchActions({}, {}, {}, {}, {}),
                ),
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

    private fun assertAtOnce(ms: Long, what: String) = assertTrue("$what took $ms ms to settle", ms <= AT_ONCE_MS)

    @Test
    fun aRowThatIsUsedUpStillAnimates() {
        show()
        val ms = settleMs { state = state(batches.filterNot { it.id == "y1" }) }
        assertTrue("used up settled in $ms ms, so nothing animated", ms > AT_ONCE_MS * 3)
        compose.onNodeWithTag("pantry_item_y1").assertDoesNotExist()
    }

    @Test
    fun aSearchReplacesTheRowsAtOnce() {
        show()
        assertAtOnce(settleMs { state = state(batches, PantryQuery(search = "mil")) }, "the first letters")
        assertAtOnce(settleMs { state = state(batches, PantryQuery(search = "milk")) }, "the next letter")
        compose.onNodeWithTag("pantry_item_m1").assertExists()
        compose.onNodeWithTag("pantry_item_y1").assertDoesNotExist()
        compose.onNodeWithTag("pantry_item_e1").assertDoesNotExist()
    }

    @Test
    fun clearingTheSearchBringsTheRowsBackWithoutAHighlight() {
        show()
        settleMs { state = state(batches, PantryQuery(search = "milk")) }
        // Before, every row that came back was highlighted as if another phone had added it (1.5 s).
        assertAtOnce(settleMs { state = state(batches) }, "clearing the search")
        compose.onNodeWithTag("pantry_item_y1").assertExists()
        compose.onNodeWithTag("pantry_item_e1").assertExists()
    }

    /** A sort, like a filter or grouping, picks the rows anew: they take their new order without sliding there. */
    @Test
    fun aNewSortReordersTheRowsAtOnce() {
        show()
        assertAtOnce(settleMs { state = state(batches, PantryQuery(sort = PantrySort.Name)) }, "sorting by name")
        assertAtOnce(settleMs { state = state(batches) }, "the default sort again")
        compose.onNodeWithTag("pantry_item_e1").assertExists()
    }

    @Test
    fun anotherPhonesNewBatchIsStillHighlighted() {
        show()
        val added = batches + batch("m2", "milk", inDays = 3)
        val ms = settleMs { state = state(added) }
        assertTrue("another phone's batch settled in $ms ms, without its highlight", ms >= REMOTE_HIGHLIGHT_MS)
        compose.onNodeWithTag("pantry_item_m2").assertExists()
    }

    private companion object {
        /** A few frames: the hold while scrolling hands the rows over one frame after the state changes. */
        const val AT_ONCE_MS = 100L
    }
}
