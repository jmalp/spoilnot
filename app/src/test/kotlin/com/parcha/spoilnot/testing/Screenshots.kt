package com.parcha.spoilnot.testing

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.robolectric.RuntimeEnvironment

/**
 * The screenshot matrix (DESIGN 13.1, 6.8): light and dark at font scale 1.0 and 2.0. Use as a parameterized
 * Robolectric test's parameters, then [setThemed] and [screenshotName].
 */
object Screenshots {
    fun params(): List<Array<Any>> =
        listOf(arrayOf(false, 1f), arrayOf(true, 1f), arrayOf(false, 2f), arrayOf(true, 2f))

    /** "home_cards_dark_font2.png": relative to the committed references folder (app/build.gradle.kts). */
    fun screenshotName(name: String, dark: Boolean, fontScale: Float) =
        "${name}_${if (dark) "dark" else "light"}_font${fontScale.toInt()}.png"
}

/**
 * Sets [content] in the app's theme (Spoil Not green), [dark] or not, at [fontScale], on a surface. The font scale is
 * the phone's too, so sheets and dialogs, which are their own windows, get it as well.
 */
fun ComposeContentTestRule.setThemed(dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
    RuntimeEnvironment.setFontScale(fontScale)
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SpoilNotTheme(darkTheme = dark, colorTheme = ColorTheme.SpoilNot) { Surface { content() } }
        }
    }
}
