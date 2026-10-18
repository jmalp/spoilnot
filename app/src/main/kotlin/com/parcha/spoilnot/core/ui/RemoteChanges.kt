package com.parcha.spoilnot.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import kotlinx.coroutines.delay

/** How long a row changed on another phone stays highlighted (DESIGN 6.4). */
const val REMOTE_HIGHLIGHT_MS = 1_500

/**
 * Tells which rows another phone changed. This phone's own writes always show up first with pending writes, so a
 * row whose data changed while it had none came from someone else. Nothing is highlighted on the first load.
 */
class RemoteChangeTracker<T>(
    private val id: (T) -> String,
    private val pending: (T) -> Boolean,
    private val content: (T) -> Any?,
) {
    private var previous: Map<String, Pair<Any?, Boolean>>? = null

    /** The ids changed remotely since the last call. */
    fun update(items: List<T>): Set<String> {
        val before = previous
        previous = items.associate { id(it) to (content(it) to pending(it)) }
        if (before == null) return emptySet()
        return items.filter { item ->
            if (pending(item)) return@filter false
            val old = before[id(item)] ?: return@filter true
            !old.second && old.first != content(item)
        }.map(id).toSet()
    }
}

/** Ids to highlight now; each one clears itself after [REMOTE_HIGHLIGHT_MS]. */
@Composable
fun <T> rememberRemoteChanges(
    items: List<T>,
    id: (T) -> String,
    pending: (T) -> Boolean,
    content: (T) -> Any?,
): Set<String> {
    val tracker = remember { RemoteChangeTracker(id, pending, content) }
    val active = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(items) {
        val now = System.nanoTime()
        tracker.update(items).forEach { active[it] = now }
        delay(REMOTE_HIGHLIGHT_MS.toLong())
        val cutoff = System.nanoTime() - REMOTE_HIGHLIGHT_MS * 1_000_000L
        active.entries.removeAll { it.value <= cutoff }
    }
    return active.keys.toSet()
}

/** A tonal tint that fades out over [REMOTE_HIGHLIGHT_MS], drawn over the row (rows are opaque). */
@Composable
fun Modifier.remoteHighlight(active: Boolean): Modifier {
    val tint = MaterialTheme.colorScheme.secondaryContainer
    val reduceMotion = rememberReduceMotion()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        alpha.snapTo(0.6f)
        if (reduceMotion) {
            delay(REMOTE_HIGHLIGHT_MS.toLong())
            alpha.snapTo(0f)
        } else {
            alpha.animateTo(0f, tween(REMOTE_HIGHLIGHT_MS, easing = LinearEasing))
        }
    }
    return drawWithContent {
        drawContent()
        if (alpha.value > 0f) drawRect(tint.copy(alpha = alpha.value))
    }
}
