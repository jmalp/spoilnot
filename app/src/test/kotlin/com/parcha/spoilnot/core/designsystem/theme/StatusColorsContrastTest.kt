package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** DESIGN 6.4: status text needs a contrast of at least 4.5:1 in both themes. */
class StatusColorsContrastTest {
    @Test
    fun `light status colors are readable on light surfaces`() {
        assertReadable(LightStatusColors, LightColors.surface, "light surface")
        assertReadable(LightStatusColors, LightColors.surfaceContainerLowest, "light surfaceContainerLowest")
    }

    @Test
    fun `dark status colors are readable on dark surfaces`() {
        assertReadable(DarkStatusColors, DarkColors.surface, "dark surface")
        assertReadable(DarkStatusColors, DarkColors.surfaceContainerHighest, "dark surfaceContainerHighest")
    }

    private fun assertReadable(colors: StatusColors, background: Color, name: String) {
        StatusTone.entries.forEach { tone ->
            val ratio = contrast(colors.forTone(tone), background)
            assertTrue("$tone on $name is %.2f:1, needs 4.5:1".format(ratio), ratio >= MIN_TEXT_CONTRAST)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
