package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN 6.4 for every fixed color theme (Spoil Not green and the five presets), light and dark: text at least 4.5:1
 * on what it sits on, chart and icon colors at least 3:1, the fixed status colors readable on the theme's surfaces and
 * on their own tint, and buttons that keep away from the status hues. Wallpaper (Material You) has no fixed colors.
 */
class ColorThemesContrastTest {
    private data class Case(val name: String, val scheme: ColorScheme, val status: StatusColors, val dark: Boolean)

    private val cases = ColorTheme.entries.filter { it != ColorTheme.Wallpaper }.flatMap { theme ->
        listOf(
            Case("${theme.id} light", theme.scheme(dark = false)!!, LightStatusColors, dark = false),
            Case("${theme.id} dark", theme.scheme(dark = true)!!, DarkStatusColors, dark = true),
        )
    }

    @Test
    fun `every fixed theme is checked in light and dark`() {
        assertEquals((ColorTheme.entries.size - 1) * 2, cases.size)
        assertTrue(cases.any { it.name == "${ColorTheme.SpoilNot.id} light" })
    }

    @Test
    fun `text roles are readable on their backgrounds`() {
        val failures = cases.flatMap { (name, c) ->
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
        val failures = cases.flatMap { (name, c) ->
            listOfNotNull(
                check(name, "primary/surface (used)", c.primary, c.surface, GRAPHICS),
                check(name, "tertiary/surface (thrown away)", c.tertiary, c.surface, GRAPHICS),
                check(name, "outline/surface", c.outline, c.surface, GRAPHICS),
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `status text is readable on every theme's surfaces`() {
        // Status words and icons outside a chip: rows and outlined cards, and the lightest card in light mode or the
        // highest container in dark mode.
        val failures = cases.flatMap { (name, c, status, dark) ->
            val backgrounds = if (dark) {
                listOf("surface" to c.surface, "surfaceContainerHighest" to c.surfaceContainerHighest)
            } else {
                listOf("surface" to c.surface, "surfaceContainerLowest" to c.surfaceContainerLowest)
            }
            backgrounds.flatMap { (bgName, background) ->
                StatusTone.entries.mapNotNull { tone ->
                    check(name, "$tone status on $bgName", status.forTone(tone), background, TEXT)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `status chip and swipe text are readable on their own tint`() {
        // The status chip (Pantry rows, Home's cards) and Pantry's swipe backgrounds tint the surface with the status
        // color at STATUS_TINT_ALPHA, and the text sits on that blend (review A11Y-03).
        val failures = cases.flatMap { (name, c, status) ->
            StatusTone.entries.mapNotNull { tone ->
                val color = status.forTone(tone)
                val tint = color.copy(alpha = STATUS_TINT_ALPHA).compositeOver(c.surface)
                check(name, "$tone status on its tint", color, tint, TEXT)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `buttons keep away from the status hues`() {
        // DESIGN 6.1, color is reserved for status: filled buttons (primary) and the floating buttons
        // (primaryContainer) are at least MIN_HUE_DISTANCE from the tones that ask for attention, so Expired still
        // stands out next to them (review UX-17). Fresh is calm and shares Spoil Not green's hue on purpose.
        val failures = cases.flatMap { (name, c, status) ->
            listOf("primary" to c.primary, "primaryContainer" to c.primaryContainer).flatMap { (role, color) ->
                listOf(StatusTone.Soon, StatusTone.Urgent, StatusTone.Expired).mapNotNull { tone ->
                    val distance = hueDistance(color, status.forTone(tone))
                    if (distance >= MIN_HUE_DISTANCE) {
                        null
                    } else {
                        "$name: $role is %.0f° from the $tone hue, needs %.0f°".format(distance, MIN_HUE_DISTANCE)
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the checks catch what they are for`() {
        // The light status tones before the review: Today's text on its tint was under 4.5:1.
        val oldUrgent = Color(0xFFC2410C)
        val tint = oldUrgent.copy(alpha = STATUS_TINT_ALPHA).compositeOver(LightColors.surface)
        assertTrue(contrast(oldUrgent, tint) < TEXT)
        // Fiery Palette's old dark button (#FFB693) next to the dark Expired color.
        assertTrue(hueDistance(Color(0xFFFFB693), DarkStatusColors.expired) < MIN_HUE_DISTANCE)
    }

    private fun check(theme: String, what: String, fg: Color, bg: Color, min: Double): String? {
        val ratio = contrast(fg, bg)
        return if (ratio >= min) null else "$theme: $what is %.2f:1, needs %.1f:1".format(ratio, min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** The difference of two colors' hue angles in OKLCH, 0 to 180 degrees. */
    private fun hueDistance(a: Color, b: Color): Double {
        val d = abs(hue(a) - hue(b)) % 360.0
        return minOf(d, 360.0 - d)
    }

    private fun hue(color: Color): Double {
        val lab = color.convert(ColorSpaces.Oklab)
        return (Math.toDegrees(atan2(lab.blue.toDouble(), lab.green.toDouble())) + 360.0) % 360.0
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHICS = 3.0
        const val MIN_HUE_DISTANCE = 45.0
    }
}
