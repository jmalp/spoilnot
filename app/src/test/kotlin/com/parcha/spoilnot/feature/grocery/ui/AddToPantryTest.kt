package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
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
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
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
    fun anEditedLineKeepsWhatWasTypedWhenSomeoneChecksOffAnotherItem() {
        // After v1, REL-08: the lines follow the cart, the edits stay by item id.
        var cart by mutableStateOf(items)
        val drafts = AddToPantryDrafts()
        compose.setContent {
            SpoilNotTheme(darkTheme = false, colorTheme = ColorTheme.SpoilNot) {
                Surface { AddToPantryContent(cart, pantry, TODAY, drafts) { e, s -> confirmed = e to s } }
            }
        }
        compose.onNodeWithText("Milk").performClick()
        compose.onNodeWithTag("entry_quantity_i1").performTextReplacement("5")
        compose.onNodeWithText("Milk").performClick()
        compose.onNodeWithText("5 · Fridge", substring = true).assertIsDisplayed()

        // Another member checks off Bread, then unchecks Rice.
        cart = items + item("i3", "Bread", null, null)
        compose.waitForIdle()
        compose.onNodeWithText("5 · Fridge", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Bread").assertIsDisplayed()
        cart = cart.filter { it.id != "i2" }
        compose.waitForIdle()
        compose.onNodeWithText("Rice").assertDoesNotExist()
        compose.onNodeWithText("Add all 2 to pantry").performClick()
        val (entries, _) = confirmed!!
        assertEquals(listOf("i1", "i3"), entries.map { it.item.id })
        assertEquals(5.0, entries[0].quantity, 0.0)
    }

    @Test
    fun theDraftsComeBackAfterTheProcessIsRecreated() {
        // After v1, REL-08: the edits are in the saved state, which Android keeps when it closes the app.
        val handle = SavedStateHandle()
        AddToPantryDrafts(handle).apply {
            set("i1", EntryEdit("3", QuantityUnit.Pieces, TODAY.plusDays(14), Location.Freezer, skip = false))
            set("i2", EntryEdit("1", QuantityUnit.Kilograms, null, Location.Pantry, skip = true))
            setExpanded("i1", true)
        }
        val saved = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val restored = AddToPantryDrafts(saved)
        assertEquals(
            EntryEdit("3", QuantityUnit.Pieces, TODAY.plusDays(14), Location.Freezer, skip = false),
            restored.edit("i1"),
        )
        assertEquals(true, restored.edit("i2")?.skip)
        assertEquals(true, restored.isExpanded("i1"))
        // Closing the sheet clears what is saved too.
        restored.clear()
        assertEquals(
            null,
            AddToPantryDrafts(
                SavedStateHandle(
                    saved.keys().associateWith {
                        saved.get<Any>(it)
                    },
                ),
            ).edit("i1"),
        )
    }

    @Test
    fun aProductWithStockLocksTheUnitAndAnAmountInAnotherUnitIsLeftToType() {
        // After v1, LOGIC-04: "2 kg rice" on the list, Rice kept in grams with stock.
        val rice = product("rice", unit = QuantityUnit.Grams)
        val stocked = PantryData(listOf(rice), listOf(batch("r1", "rice", quantity = 500.0)), false)
        val kilos = item("i1", "Rice", 2.0, QuantityUnit.Kilograms, productId = "rice")
        compose.setContent {
            SpoilNotTheme(darkTheme = false, colorTheme = ColorTheme.SpoilNot) {
                Surface { AddToPantryContent(listOf(kilos), stocked, TODAY) { e, s -> confirmed = e to s } }
            }
        }
        compose.onNodeWithText("Add 1 to pantry").performClick()
        assertEquals("nothing is added until the amount is typed", null, confirmed)
        compose.onNodeWithText("Set while in stock").assertIsDisplayed()
        compose.onNodeWithTag("entry_quantity_i1").performTextReplacement("2000")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/add_to_pantry_unit_locked_light_font1.png")
        compose.onNodeWithText("Add 1 to pantry").performClick()
        val entry = confirmed!!.first.single()
        assertEquals(QuantityUnit.Grams, entry.unit)
        assertEquals(2000.0, entry.quantity, 0.0)
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
