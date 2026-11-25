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
    fun `the 2026-11 look's text is readable on its page, rows and cards`() {
        // DESIGN 19.2: a tinted page with rows one step away; names, second lines, group headings and text buttons
        // sit on the page, a row, a row inside a highlight card, or a field on a sheet.
        val failures = cases.flatMap { (name, c, _, dark) ->
            val s = appSurfaces(c, dark)
            listOf(
                "onSurface/page" to (c.onSurface to s.page),
                "onSurfaceVariant/page" to (c.onSurfaceVariant to s.page),
                "primary/page (group headings, text buttons)" to (c.primary to s.page),
                "onSurface/row" to (c.onSurface to s.row),
                "onSurfaceVariant/row" to (c.onSurfaceVariant to s.row),
                "primary/row (text buttons)" to (c.primary to s.row),
                "onSurface/nestedRow" to (c.onSurface to s.nestedRow),
                "onSurfaceVariant/nestedRow" to (c.onSurfaceVariant to s.nestedRow),
                "onSurface/field" to (c.onSurface to s.field),
                "onSurfaceVariant/field" to (c.onSurfaceVariant to s.field),
                "onSurfaceVariant/tile" to (c.onSurfaceVariant to s.tile),
                "onSurfaceVariant/surfaceContainerHigh (connected buttons)" to
                    (c.onSurfaceVariant to c.surfaceContainerHigh),
                "inverseOnSurface/inverseSurface (snackbar)" to (c.inverseOnSurface to c.inverseSurface),
                "inversePrimary/inverseSurface (Undo)" to (c.inversePrimary to c.inverseSurface),
                "onErrorContainer/errorContainer (banners)" to (c.onErrorContainer to c.errorContainer),
                "onSurface/surfaceContainer (navigation bar)" to (c.onSurface to c.surfaceContainer),
                "onSurfaceVariant/surfaceContainer (navigation bar)" to (c.onSurfaceVariant to c.surfaceContainer),
                "onSurface/surfaceContainerHighest (offline banner)" to (c.onSurface to c.surfaceContainerHighest),
                "primaryContainer/onPrimaryContainer (Needs attention's count)" to
                    (c.primaryContainer to c.onPrimaryContainer),
                // Step 3 (DESIGN 19.10, 19.11): a field's label, focused or in error, and its message; a stepper's
                // buttons; the chosen location tile and filter chip; a dialog's choices.
                "primary/field (focused label)" to (c.primary to s.field),
                "error/field (error label)" to (c.error to s.field),
                "error/surfaceContainerLow (error message on a sheet)" to (c.error to c.surfaceContainerLow),
                "onSecondaryContainer/secondaryContainer (stepper)" to
                    (c.onSecondaryContainer to c.secondaryContainer),
                "onPrimaryContainer/primaryContainer (chosen location)" to
                    (c.onPrimaryContainer to c.primaryContainer),
                "onPrimary/primary (chosen filter chip)" to (c.onPrimary to c.primary),
                "onSurface/dialogRow" to (c.onSurface to s.dialogRow),
                // Step 5 (DESIGN 19.12): Cooked and + on today's nested rows, Move's chips on the dialog row color.
                "primary/nestedRow (Plan's Cooked)" to (c.primary to s.nestedRow),
                "onSurfaceVariant/dialogRow (Move's chips)" to (c.onSurfaceVariant to s.dialogRow),
            ).mapNotNull { (pair, colors) -> check(name, pair, colors.first, colors.second, TEXT) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `status words and tiles are readable on the 2026-11 look's rows`() {
        // DESIGN 19.6: a row says its status in words in the status color, beside a tile that tints the row with
        // that color at STATUS_TINT_ALPHA; the swipe backgrounds put their words on the same tint.
        val failures = cases.flatMap { (name, c, status, dark) ->
            val s = appSurfaces(c, dark)
            listOf("row" to s.row, "nestedRow" to s.nestedRow).flatMap { (bgName, background) ->
                StatusTone.entries.flatMap { tone ->
                    val color = status.forTone(tone)
                    val tile = if (tone == StatusTone.NoDate) {
                        s.tile
                    } else {
                        color.copy(alpha = STATUS_TINT_ALPHA).compositeOver(background)
                    }
                    listOfNotNull(
                        check(name, "$tone status words on $bgName", color, background, TEXT),
                        check(name, "$tone status on its tile over $bgName", color, tile, TEXT),
                    )
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `Home's Your kitchen ring stands out on its card`() {
        // DESIGN 19.8: on the primary card the used arc is primaryContainer and the dashed thrown-away arc onPrimary.
        val failures = cases.flatMap { (name, c) ->
            listOfNotNull(
                check(name, "primaryContainer/primary (used arc)", c.primaryContainer, c.primary, GRAPHICS),
                check(name, "onPrimary/primary (thrown-away arc)", c.onPrimary, c.primary, GRAPHICS),
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the 2026-11 look's rows are one step away from the page in every theme`() {
        val failures = cases.mapNotNull { (name, c, _, dark) ->
            val s = appSurfaces(c, dark)
            // Lighter in both modes: white on the tinted page in light, a raised surface on the dark page. The same
            // for fields on a sheet and a dialog's choices on the dialog.
            listOfNotNull(
                "$name: rows don't stand off the page".takeUnless { s.row.luminance() > s.page.luminance() },
                "$name: fields don't stand off a sheet"
                    .takeUnless { s.field.luminance() > c.surfaceContainerLow.luminance() },
                "$name: a dialog's choices don't stand off the dialog"
                    .takeUnless { s.dialogRow.luminance() > c.surfaceContainerHigh.luminance() },
            ).joinToString("\n").ifEmpty { null }
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
