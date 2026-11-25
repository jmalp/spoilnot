package com.parcha.spoilnot.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import com.parcha.spoilnot.core.designsystem.component.FocusRing
import com.parcha.spoilnot.core.designsystem.component.hiddenFromScreenReaders

/**
 * The signed-in app's frame (DESIGN 6.2, 6.8): the screen, then the tab bar under it, with a focus ring around
 * whatever has keyboard focus.
 *
 * The screen comes first in the layout, so Tab from a keyboard, a switch or a D-pad reaches its top bar and content
 * before the five tabs (after v1, review A11Y-11; Material's `Scaffold` put the bar first). [overlay] draws on top
 * of everything (the app tour). While [hideBehindOverlay], the screen and the bar are hidden from screen readers so
 * TalkBack stays on the overlay (review A11Y-10).
 */
@Composable
internal fun ShellLayout(
    bar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    hideBehindOverlay: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var barHeight by remember { mutableIntStateOf(0) }
    val barPadding = with(LocalDensity.current) { PaddingValues(bottom = barHeight.toDp()) }
    Box(modifier) {
        FocusRing(Modifier.fillMaxSize().hiddenFromScreenReaders(hideBehindOverlay)) {
            Column(Modifier.fillMaxSize()) {
                // The bar takes the navigation bar's inset; the screens above it don't pad for it again.
                Box(Modifier.weight(1f).consumeWindowInsets(barPadding), content = content)
                bar(Modifier.onSizeChanged { barHeight = it.height })
            }
        }
        overlay()
    }
}

/**
 * How far tab labels grow with the phone's text size: at 1.2× all five fit on one line in a 411 dp wide phone's
 * fifths, and grow no further, while the icons above them say the same thing (DECISIONS).
 */
const val TAB_LABEL_MAX_FONT_SCALE = 1.2f

/**
 * A tab's label on one line (after v1, review UX-14): every label grows with the text size up to
 * [TAB_LABEL_MAX_FONT_SCALE], so they keep one size, and none breaks mid-word ("Pantr / y"). "…" only if a label
 * still doesn't fit (a narrower phone or a longer translation).
 */
@Composable
internal fun TabLabel(text: String) {
    val density = LocalDensity.current
    val capped = Density(density.density, density.fontScale.coerceAtMost(TAB_LABEL_MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
