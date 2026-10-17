package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryList
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

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, dynamicColor = false) {
                    // A Surface, as in the app, so text gets the theme's content color.
                    Surface { Column { content() } }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png")
    }

    @Test
    fun statusChips() = snap("status_chips") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-3L, 0L, 1L, 3L, 6L, 30L, null).forEach {
                StatusChip(it?.let(TODAY::plusDays), DateType.UseBy, TODAY)
            }
            StatusChip(TODAY.minusDays(1), DateType.BestBefore, TODAY)
        }
    }

    @Test
    fun pantryRows() = snap("pantry_rows") {
        val products =
            listOf(
                product("milk"),
                product("yogurt"),
                product("oil", name = "Olive oil", min = 1.0, location = Location.Pantry),
            )
        val batches = listOf(
            batch("m1", "milk", quantity = 2.0, inDays = 1),
            batch("y1", "yogurt", inDays = 3),
            batch("y2", "yogurt", quantity = 3.0, inDays = 10, location = Location.Freezer).copy(pendingSync = true),
        )
        val actions = BatchActions({}, {}, {}, {}, {}, {})
        PantryList.rows(products, batches, TODAY).sortedBy { it.name }.forEach { row ->
            PantryRowItem(row, TODAY, expanded = row.name == "Yogurt", onToggle = {
            }, onOpenProduct = {}, actions = actions)
        }
    }

    @Test
    fun addItemForm() = snap("add_item_form") {
        AddItemForm(emptyList(), emptySet(), TODAY) {}
    }
}
