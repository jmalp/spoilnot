package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose component tests on the JVM (DESIGN 13.1), on a phone-sized screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantryComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val noActions = BatchActions({}, {}, {}, {}, {})

    private fun chip(days: Long?, type: DateType = DateType.UseBy) = compose.setContent {
        SpoilNotTheme(dynamicColor = false) { StatusChip(days?.let { TODAY.plusDays(it) }, type, TODAY) }
    }

    @Test
    fun statusChipShowsShortTextAndReadsTheFullWording() {
        chip(1)
        compose.onNodeWithText("Tomorrow", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Expires tomorrow")).assertIsDisplayed()
    }

    @Test
    fun expiredUseByTellsYouToThrowItAway() {
        chip(-2)
        compose.onNodeWithText("Expired", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Expired 2 days ago. Throw it away")).assertIsDisplayed()
    }

    @Test
    fun bestBeforeUsesSofterWording() {
        chip(-2, DateType.BestBefore)
        compose.onNodeWithText("Past best before", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Past its best before date. Check before using")).assertIsDisplayed()
    }

    @Test
    fun soonAndWeekCountDays() {
        chip(3)
        compose.onNode(hasContentDescription("Expires in 3 days")).assertIsDisplayed()
    }

    private fun items(products: List<Product>, batches: List<StockBatch>) = PantryItems.all(products, batches, TODAY)

    @Test
    fun rowReadsNameQuantityStatusAndLocation() {
        val item = items(listOf(product("milk")), listOf(batch("m1", "milk", quantity = 2.0, inDays = 1))).single()
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) { PantryItemRow(item, TODAY, noActions, {}) }
        }
        compose.onNode(hasContentDescription("Milk, 2 pcs, Expires tomorrow, Fridge")).assertIsDisplayed()
    }

    @Test
    fun staplesAtZeroShowOutAndOpenToAddStock() {
        val item = items(listOf(product("oil", name = "Olive oil", min = 1.0)), emptyList()).single()
        var opened: PantryItem? = null
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) { PantryItemRow(item, TODAY, noActions, { opened = it }) }
        }
        compose.onNode(hasContentDescription("Olive oil, out of stock")).assertIsDisplayed()
        compose.onNodeWithText("Out", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Olive oil, out of stock")).performClick()
        assertTrue(opened is PantryItem.Out)
    }

    @Test
    fun tappingAPurchaseOpensItForEditing() {
        val item = items(listOf(product("milk")), listOf(batch("m1", "milk"))).single()
        var opened: PantryItem? = null
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) { PantryItemRow(item, TODAY, noActions, { opened = it }) }
        }
        compose.onNodeWithTag("pantry_item_m1").onChildAt(0).performClick()
        assertEquals("m1", (opened as PantryItem.Purchase).batch.id)
    }

    @Test
    fun everySwipeActionHasAMenuEquivalent() {
        val calls = mutableListOf<String>()
        val actions = BatchActions(
            onMinus = { calls += "minus" },
            onPlus = { calls += "plus" },
            onUsedUp = { calls += "used" },
            onThrowAway = { calls += "throw" },
            onMove = { calls += "move" },
        )
        val item = items(listOf(product("milk")), listOf(batch("m1", "milk", quantity = 2.0))).single()
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) { PantryItemRow(item, TODAY, actions, {}) }
        }
        listOf("Use 1", "Add 1", "Used up", "Throw away", "Move").forEach { entry ->
            compose.onNodeWithContentDescription("Options for this batch").performClick()
            compose.onNodeWithText(entry).performClick()
        }
        assertEquals(listOf("minus", "plus", "used", "throw", "move"), calls)
    }

    @Test
    fun addFormValidatesAndSavesDecimals() {
        var saved: StockDraft? = null
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) {
                AddItemForm(emptyList(), emptySet(), TODAY) {
                    saved =
                        it
                }
            }
        }
        compose.onNodeWithTag("button_save_item").performClick()
        compose.onNodeWithText("Enter a name.").assertIsDisplayed()
        assertNull(saved)

        compose.onNodeWithTag("field_name").performTextInput("  Rice ")
        compose.onNodeWithTag("field_quantity").performTextClearance()
        compose.onNodeWithTag("field_quantity").performTextInput("1,5")
        // performScrollTo only scrolls the nearest scrollable (the chip row), so bring the form's date section up first.
        compose.onNodeWithTag("field_expiry").performScrollTo()
        compose.onNodeWithText("+1 week").performClick()
        compose.onNodeWithTag("button_save_item").performClick()

        assertEquals("Rice", saved?.name)
        assertEquals(1.5, saved!!.quantity, 0.0)
        assertEquals(TODAY.plusWeeks(1), saved!!.expiryDate)
    }

    @Test
    fun choosingAnExistingProductFillsItsDefaultsAndLocksItsUnit() {
        val rice = product("rice", unit = QuantityUnit.Kilograms, recent = listOf(30))
        var saved: StockDraft? = null
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) {
                AddItemForm(listOf(rice), setOf("rice"), TODAY) {
                    saved =
                        it
                }
            }
        }
        compose.onNodeWithTag("field_name").performTextInput("ri")
        compose.onNode(hasText("Rice")).performClick()
        compose.onNodeWithText("Set while in stock").assertIsDisplayed()
        compose.onNodeWithTag("button_save_item").performClick()
        assertEquals(QuantityUnit.Kilograms, saved?.unit)
        assertEquals(TODAY.plusDays(30), saved?.expiryDate)
    }

    @Test
    fun theAutoAddSwitchRevealsKeepAtLeastAndRequiresIt() {
        var saved: StockDraft? = null
        compose.setContent {
            SpoilNotTheme(dynamicColor = false) { AddItemForm(emptyList(), emptySet(), TODAY) { saved = it } }
        }
        compose.onNodeWithTag("field_name").performTextInput("Eggs")
        compose.onNodeWithTag("field_keep_at_least").assertDoesNotExist()
        compose.onNodeWithTag("switch_auto_add").performScrollTo().performClick()
        compose.onNodeWithTag("button_save_item").performClick()
        compose.onNodeWithText("Enter how many to keep, above 0.").performScrollTo().assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithTag("field_keep_at_least").performTextInput("6")
        compose.onNodeWithTag("button_save_item").performClick()
        assertEquals(6.0, saved!!.minQuantity!!, 0.0)
        assertTrue(saved!!.autoAddToGrocery)
        assertEquals(DateType.UseBy, saved!!.dateType)
        assertNull(saved!!.note)
    }
}
