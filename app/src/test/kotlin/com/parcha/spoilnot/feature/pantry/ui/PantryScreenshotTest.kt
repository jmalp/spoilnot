package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the pantry components in light and dark at font scale 1.0 and 2.0 (DESIGN 13.1). A normal test
 * run renders them (catching crashes and layout exceptions); ./gradlew recordRoborazziDevDebug writes the images
 * to app/build/outputs/roborazzi for review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantryScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))
    }

    @get:Rule
    val compose = createComposeRule()

    private val suffix get() = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"

    /** [onSheet]: the color of a sheet, for forms; otherwise the page's (DESIGN 19.2). */
    private fun snap(name: String, onSheet: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) {
                    // A Surface, as in the app, so text gets the theme's content color.
                    val color = if (onSheet) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        SpoilNotSurfaces.current.page
                    }
                    Surface(color = color) { Column { content() } }
                }
            }
        }
        compose.onRoot().captureRoboImage("${name}_$suffix.png")
    }

    /** A group of rows (DESIGN 19.5, 19.6): every status, opened, low, waiting to sync, Out. */
    @Test
    fun pantryRows() = snap("pantry_rows") {
        val products =
            listOf(
                product("milk"),
                product("yogurt"),
                product("rice", location = Location.Pantry, min = 1000.0, unit = QuantityUnit.Grams),
                product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry),
            )
        val batches = listOf(
            batch("m0", "milk", inDays = -2),
            batch("m1", "milk", quantity = 2.0, inDays = 1).copy(openedOn = TODAY),
            batch("y1", "yogurt", inDays = 3),
            batch("y2", "yogurt", quantity = 3.0, inDays = 10, location = Location.Freezer).copy(pendingSync = true),
            batch("r1", "rice", quantity = 500.0, inDays = null, location = Location.Pantry)
                .copy(unit = QuantityUnit.Grams),
        )
        val actions = BatchActions({}, {}, {}, {}, {})
        val items = PantryItems.build(products, batches, PantryQuery(), TODAY).flatMap { it.items }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            items.forEachIndexed { index, item ->
                PantryItemRow(item, TODAY, actions, {}, position = segmentPosition(index, items.size))
            }
        }
    }

    /** The folded expired backlog, a row of its group (after v1, DESIGN 19.12). */
    @Test
    fun expiredFold() = snap("pantry_expired_header") {
        Column(Modifier.padding(16.dp)) { ExpiredFoldRow(65, shown = false, onToggle = {}) }
    }

    @Test
    fun addItemForm() = snap("add_item_form", onSheet = true) {
        AddItemForm(emptyList(), emptySet(), TODAY) {}
    }

    @Test
    fun addItemFormForAStaple() = snap("add_item_form_staple", onSheet = true) {
        val eggs = product("eggs", min = 6.0, unit = QuantityUnit.Pieces).copy(buyQuantity = 12.0)
        AddItemForm(listOf(eggs), emptySet(), TODAY, prefill = eggs) {}
    }

    /** The Edit window's notice when someone else changed its item (after v1, DESIGN 9.2). */
    @Test
    fun editChangedNotice() = snap("edit_changed_notice", onSheet = true) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChangedNotice(stringResource(R.string.edit_changed_by, "Sam"), onReview = {})
            ChangedNotice(stringResource(R.string.edit_changed), onReview = {})
        }
    }
}
