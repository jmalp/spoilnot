package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The font scale from which layouts change for large text (DESIGN 6.8, after v1): what sits beside a name (a status
 * chip, a badge, a stepper, a button) goes under it, and segmented buttons become a list of radio buttons. At 1.5×
 * side by side still fits a phone; past it names broke mid-word (review A11Y-04).
 */
const val LARGE_TEXT_FONT_SCALE = 1.5f

/** Whether the phone's font scale is [LARGE_TEXT_FONT_SCALE] or more. */
@Composable
@ReadOnlyComposable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE

/**
 * A name with something beside it at the end, such as a status chip or a badge: in a row, the name taking the room
 * left over, or from 1.5× text the [trailing] part under the name, so the name keeps the whole width and never
 * breaks mid-word (review A11Y-04).
 *
 * [name] gets the modifier to apply to the name.
 */
@Composable
fun NameWithTrailing(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    name: @Composable (Modifier) -> Unit,
    trailing: @Composable () -> Unit,
) {
    if (isLargeText()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name(Modifier)
            trailing()
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            name(Modifier.weight(1f))
            trailing()
        }
    }
}
