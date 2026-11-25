package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Visual tones for expiry status (DESIGN 6.4). These are fixed and never taken from the dynamic scheme, so
 * status stays recognizable with Material You. A tone is always shown with an icon and text, never color alone.
 */
enum class StatusTone { Fresh, Soon, Urgent, Expired, NoDate }

@Immutable
data class StatusColors(val fresh: Color, val soon: Color, val urgent: Color, val expired: Color, val noDate: Color) {
    fun forTone(tone: StatusTone): Color = when (tone) {
        StatusTone.Fresh -> fresh
        StatusTone.Soon -> soon
        StatusTone.Urgent -> urgent
        StatusTone.Expired -> expired
        StatusTone.NoDate -> noDate
    }
}

/**
 * How strongly a status color tints what sits behind its text: the status chip and the Pantry swipe backgrounds.
 * `ColorThemesContrastTest` checks the text on this blend, not on the bare surface (after v1, review A11Y-03).
 */
const val STATUS_TINT_ALPHA = 0.12f

// Light Fresh, Soon and Today/Tomorrow are about 10% darker than DESIGN's first table (after v1, 2026-11-12), so
// their text reaches 4.5:1 on the chip's tint in every theme (DECISIONS).
internal val LightStatusColors =
    StatusColors(
        fresh = Color(0xFF28702C),
        soon = Color(0xFF8F5500),
        urgent = Color(0xFFB03A0A),
        expired = Color(0xFFBA1A1A),
        noDate = LightColors.onSurfaceVariant,
    )

internal val DarkStatusColors =
    StatusColors(
        fresh = Color(0xFF81C784),
        soon = Color(0xFFFFB74D),
        urgent = Color(0xFFFF8A65),
        expired = Color(0xFFFFB4AB),
        noDate = DarkColors.onSurfaceVariant,
    )

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
