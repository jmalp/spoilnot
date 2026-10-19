package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN 6.4 for every fixed color theme (the Spoil Not green and the presets), light and dark: text at least 4.5:1
 * on what it sits on, chart and icon colors at least 3:1, and the fixed status colors readable on the theme's
 * surfaces.
 */
class ColorThemesContrastTest {
    private val themes = ColorTheme.entries.filter { it != ColorTheme.Wallpaper }

    private fun schemes(): List<Triple<String, ColorScheme, StatusColors>> = themes.flatMap { theme ->
        listOf(
            Triple("${theme.id} light", theme.scheme(dark = false)!!, LightStatusColors),
            Triple("${theme.id} dark", theme.scheme(dark = true)!!, DarkStatusColors),
        )
    }

    @Test
    fun `text roles are readable on their backgrounds`() {
        val failures = schemes().flatMap { (name, c, _) ->
            listOf(
                "onPrimary/primary" to (c.onPrimary to c.primary),
                "onPrimaryContainer/primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
                "onSecondary/secondary" to (c.onSecondary to c.secondary),
                "onSecondaryContainer/secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
                "onTertiary/tertiary" to (c.onTertiary to c.tertiary),
                "onTertiaryContainer/tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
                "onSurface/surface" to (c.onSurface to c.surface),
                "onSurfaceVariant/surface" to (c.onSurfaceVariant to c.surface),
                "onSurface/surfaceContainerHighest" to (c.onSurface to c.surfaceContainerHighest),
                "onSurfaceVariant/surfaceContainerHighest" to (c.onSurfaceVariant to c.surfaceContainerHighest),
                "primary/surface (text buttons)" to (c.primary to c.surface),
                "onError/error" to (c.onError to c.error),
            ).mapNotNull { (pair, colors) -> check(name, pair, colors.first, colors.second, TEXT) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `chart and icon colors stand out from the surface`() {
        val failures = schemes().flatMap { (name, c, _) ->
            listOf(
                check(name, "primary/surface (used)", c.primary, c.surface, GRAPHICS),
                check(name, "tertiary/surface (thrown away)", c.tertiary, c.surface, GRAPHICS),
                check(name, "outline/surface", c.outline, c.surface, GRAPHICS),
            ).mapNotNull { it }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `fixed status colors are readable on every theme's surfaces`() {
        // The surfaces status text sits on, as in StatusColorsContrastTest: rows and outlined cards in light mode,
        // up to the highest container in dark mode.
        val failures = schemes().flatMap { (name, c, status) ->
            val light = status == LightStatusColors
            val backgrounds =
                if (light) listOf(c.surface, c.surfaceContainerLowest) else listOf(c.surface, c.surfaceContainerHighest)
            backgrounds.flatMap { background ->
                StatusTone.entries
                    .filter { it != StatusTone.NoDate } // on-surface-variant of the green theme, checked above
                    .mapNotNull { tone -> check(name, "$tone status", status.forTone(tone), background, TEXT) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun check(theme: String, what: String, fg: Color, bg: Color, min: Double): String? {
        val ratio = contrast(fg, bg)
        return if (ratio >= min) null else "$theme: $what is %.2f:1, needs %.1f:1".format(ratio, min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHICS = 3.0
    }
}
