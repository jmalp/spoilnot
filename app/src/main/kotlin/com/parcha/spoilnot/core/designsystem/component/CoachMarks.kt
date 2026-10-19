package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Where marked elements are on screen and which was tapped last, for a tour's [CoachMarkOverlay] (after v1,
 * DESIGN 5.8). Provided by [LocalCoachMarks]; without one, [coachMark] does nothing.
 */
@Stable
class CoachMarks(private val onTapped: (String) -> Unit = {}) {
    private val bounds = mutableStateMapOf<String, Rect>()
    private val owners = mutableMapOf<String, Any>()

    /** The element's bounds in the window, or null when nothing with [key] is on screen. */
    fun boundsOf(key: String): Rect? = bounds[key]

    internal fun place(key: String, owner: Any, rect: Rect) {
        owners[key] = owner
        bounds[key] = rect
    }

    internal fun remove(key: String, owner: Any) {
        // Another screen may already have the key (a tab's twin); only its own owner removes it.
        if (owners[key] === owner) {
            owners.remove(key)
            bounds.remove(key)
        }
    }

    internal fun tapped(key: String) = onTapped(key)
}

val LocalCoachMarks = staticCompositionLocalOf<CoachMarks?> { null }

/**
 * Marks this element as [key] for a tour: records its bounds, and reports a tap on it without taking the tap, so
 * the element still does what it always does.
 */
fun Modifier.coachMark(key: String): Modifier = composed {
    val marks = LocalCoachMarks.current ?: return@composed Modifier
    val owner = remember { Any() }
    DisposableEffect(marks, key) { onDispose { marks.remove(key, owner) } }
    Modifier
        .onGloballyPositioned { marks.place(key, owner, it.boundsInWindow()) }
        .pointerInput(marks, key) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Release) marks.tapped(key)
                }
            }
        }
}

/**
 * A dimmed screen with a hole around [target] (window coordinates; null for a centered card) and a card with
 * [text], [progress] ("3 of 9"), Skip and Next. Taps in the hole reach the element below, so the user can follow
 * along; taps on the dimmed part do nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachMarkOverlay(
    target: Rect?,
    text: String,
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    progress: String? = null,
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    val ring = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }.testTag("coach_mark_overlay"),
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val pad = with(density) { 8.dp.toPx() }
        val hole = target
            ?.translate(-origin)
            ?.inflate(pad)
            ?.let {
                Rect(
                    it.left.coerceAtLeast(0f),
                    it.top.coerceAtLeast(0f),
                    it.right.coerceAtMost(width),
                    it.bottom.coerceAtMost(height),
                )
            }
            ?.takeIf { it.width > 0 && it.height > 0 }
        if (hole == null) {
            Scrim(Rect(0f, 0f, width, height), scrim)
        } else {
            Scrim(Rect(0f, 0f, width, hole.top), scrim)
            Scrim(Rect(0f, hole.bottom, width, height), scrim)
            Scrim(Rect(0f, hole.top, hole.left, hole.bottom), scrim)
            Scrim(Rect(hole.right, hole.top, width, hole.bottom), scrim)
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    ring,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        val gap = with(density) { 12.dp.toPx() }
        val room = with(density) { MIN_CARD_ROOM.toPx() }
        val below = hole?.let { height - it.bottom - 2 * gap } ?: 0f
        val above = hole?.let { it.top - 2 * gap } ?: 0f
        val cardModifier = when {
            hole == null -> Modifier.align(Alignment.Center)

            // Below a target in the top half, above one in the bottom half, wherever there is room.
            below >= room && (hole.center.y < height / 2 || above < room) ->
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { (hole.bottom + gap).toDp() })
                    .heightIn(max = with(density) { below.toDp() })

            above >= room ->
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { (height - hole.top + gap).toDp() })
                    .heightIn(max = with(density) { above.toDp() })

            // A target as tall as the screen: the card covers its lower part.
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        }
        ElevatedCard(
            cardModifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .semantics {
                    paneTitle = title ?: text
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag("coach_mark_card"),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                progress?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                Text(text, style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    skipLabel?.let {
                        TextButton(onClick = onSkip, modifier = Modifier.testTag("coach_mark_skip")) { Text(it) }
                    }
                    Button(onClick = onNext, modifier = Modifier.testTag("coach_mark_next")) { Text(nextLabel) }
                }
            }
        }
    }
}

/** Space the card needs beside its target before it covers the target instead. */
private val MIN_CARD_ROOM = 160.dp

/** A dimmed rectangle that swallows every touch on it. */
@Composable
private fun Scrim(rect: Rect, color: androidx.compose.ui.graphics.Color) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .background(color)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    )
}
