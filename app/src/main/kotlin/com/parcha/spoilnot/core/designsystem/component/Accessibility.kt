package com.parcha.spoilnot.core.designsystem.component

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.getFocusedRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The focus ring's width (DESIGN 6.8): 2 dp in the theme's primary color. */
val FocusRingWidth = 2.dp

/** Space between a focused control and its ring. */
internal val FocusRingGap = 3.dp

/** The ring's largest corner: round around buttons and chips, softly rounded around rows. */
private val FocusRingMaxCorner = 24.dp

/**
 * Draws a visible ring around whatever has keyboard focus inside [content] (after v1, review A11Y-11): Material marks
 * a focused control only with a faint state layer, hard to find on rows and chips. The ring shows only while the
 * phone is in keyboard mode (a hardware keyboard, a switch or a D-pad moved focus), never for touch.
 *
 * One ring for every control, Material's included, because it asks where focus is rather than relying on each
 * component's indication. Sheets, dialogs and menus are their own windows and keep Material's state layer.
 */
@Composable
fun FocusRing(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val inputModes = LocalInputModeManager.current
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.then(FocusRingElement(color, inputModes))) { content() }
}

private data class FocusRingElement(val color: Color, val inputModes: InputModeManager) :
    ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(color, inputModes)

    override fun update(node: FocusRingNode) {
        node.color = color
        node.inputModes = inputModes
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
    }
}

/**
 * Compose 1.13 removed the focused-bounds observer the ring was built on (after v1, 2026-11-24): the ring now asks
 * where focus is when it draws ([getFocusedRect], relative to this node). Keyboard focus moves with key events, so
 * after every key event and every focus change entering or leaving [content] it redraws each frame for a moment (a
 * list scrolling the focused control into view), then looks a few more times whether focus moved without a key (a
 * form focusing the field a server error belongs to) and redraws only if it did. Leaving keyboard mode hides it at
 * once: the draw reads the input mode, so it runs again when that changes.
 */
private class FocusRingNode(var color: Color, var inputModes: InputModeManager) :
    DelegatingNode(),
    DrawModifierNode,
    KeyInputModifierNode {
    // Never focused itself: it only tells where focus is among its children, and when it enters or leaves them.
    private val focus = delegate(FocusTargetModifierNode(focusability = Focusability.Never) { _, _ -> follow() })
    private var following: Job? = null

    /** Where the ring was last drawn: the later checks redraw only when focus moved from there. */
    private var drawn: Rect? = null

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        follow()
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    private fun follow() {
        if (!isAttached) return
        following?.cancel()
        following = coroutineScope.launch {
            repeat(FOLLOW_FRAMES) {
                withFrameNanos { }
                invalidateDraw()
            }
            repeat(LOOKS) {
                delay(LOOK_EVERY_MS)
                if (inputModes.inputMode == InputMode.Keyboard && focus.getFocusedRect() != drawn) invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        drawn = null
        if (inputModes.inputMode != InputMode.Keyboard) return
        val bounds = focus.getFocusedRect() ?: return
        drawn = bounds
        if (bounds.width <= 0f || bounds.height <= 0f) return
        // Outside the control, with a gap, so it never covers it or leaves its round corners out; kept on screen for
        // full-width rows.
        val stroke = FocusRingWidth.toPx()
        val gap = FocusRingGap.toPx()
        val ring = Rect(
            (bounds.left - gap).coerceAtLeast(stroke / 2),
            (bounds.top - gap).coerceAtLeast(stroke / 2),
            (bounds.right + gap).coerceAtMost(size.width - stroke / 2),
            (bounds.bottom + gap).coerceAtMost(size.height - stroke / 2),
        )
        if (ring.width <= 0f || ring.height <= 0f) return
        val radius = minOf(ring.height / 2, FocusRingMaxCorner.toPx())
        drawRoundRect(
            color = color,
            topLeft = ring.topLeft,
            size = ring.size,
            cornerRadius = CornerRadius(radius),
            style = Stroke(stroke),
        )
    }

    private companion object {
        /** About 0.75 s at 60 Hz: enough for a list to finish scrolling the focused control into view. */
        const val FOLLOW_FRAMES = 45

        /** Then every 0.1 s for 2.5 s: focus that code moves after a key (a server's answer) is ringed within 0.1 s. */
        const val LOOKS = 25
        const val LOOK_EVERY_MS = 100L
    }
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
