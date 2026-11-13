package com.parcha.spoilnot.core.designsystem.component

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** The focus ring's width (DESIGN 6.8): 2 dp in the theme's primary color. */
val FocusRingWidth = 2.dp

/** Space between a focused control and its ring. */
private val FocusRingGap = 3.dp

/** The ring's largest corner: round around buttons and chips, softly rounded around rows. */
private val FocusRingMaxCorner = 24.dp

/**
 * Draws a visible ring around whatever has keyboard focus inside [content] (after v1, review A11Y-11): Material marks
 * a focused control only with a faint state layer, hard to find on rows and chips. The ring shows only while the
 * phone is in keyboard mode (a hardware keyboard, a switch or a D-pad moved focus), never for touch.
 *
 * One ring for every control, Material's included, because it follows the focused bounds rather than each
 * component's indication. Sheets, dialogs and menus are their own windows and keep Material's state layer.
 */
@Suppress("DEPRECATION") // onFocusedBoundsChanged: the replacement needs a custom modifier node per control.
@Composable
fun FocusRing(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val inputMode = LocalInputModeManager.current
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Never equal: the same coordinates come back when the focused control moves (a list scrolls it into view).
    var focused by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .onGloballyPositioned { root = it }
            .onFocusedBoundsChanged { focused = it }
            .drawWithContent {
                drawContent()
                if (inputMode.inputMode != InputMode.Keyboard) return@drawWithContent
                val from = root?.takeIf { it.isAttached } ?: return@drawWithContent
                val target = focused?.takeIf { it.isAttached } ?: return@drawWithContent
                val bounds = from.localBoundingBoxOf(target, clipBounds = true)
                if (bounds.width <= 0f || bounds.height <= 0f) return@drawWithContent
                // Outside the control, with a gap, so it never covers it or leaves its round corners out; kept on
                // screen for full-width rows.
                val stroke = FocusRingWidth.toPx()
                val gap = FocusRingGap.toPx()
                val ring = Rect(
                    (bounds.left - gap).coerceAtLeast(stroke / 2),
                    (bounds.top - gap).coerceAtLeast(stroke / 2),
                    (bounds.right + gap).coerceAtMost(size.width - stroke / 2),
                    (bounds.bottom + gap).coerceAtMost(size.height - stroke / 2),
                )
                val radius = minOf(ring.height / 2, FocusRingMaxCorner.toPx())
                drawRoundRect(
                    color = color,
                    topLeft = ring.topLeft,
                    size = ring.size,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(stroke),
                )
            },
    ) { content() }
}

/**
 * Whether a screen reader that explores by touch (TalkBack) is on, kept up to date while it is turned on or off.
 * UI Automator and other services that only read the screen don't count.
 */
@Composable
fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * Hides everything in this element from screen readers while [hidden], for what an overlay covers (the app tour,
 * review A11Y-10): TalkBack can't wander from the overlay into the dimmed screen and act on it there.
 */
fun Modifier.hiddenFromScreenReaders(hidden: Boolean): Modifier = if (hidden) this.clearAndSetSemantics { } else this
