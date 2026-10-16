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

internal val LightStatusColors =
    StatusColors(
        fresh = Color(0xFF2E7D32),
        soon = Color(0xFFA05F00), // DESIGN says #B26A00 (4.04:1); darkened for 4.5:1, see DECISIONS
        urgent = Color(0xFFC2410C),
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
