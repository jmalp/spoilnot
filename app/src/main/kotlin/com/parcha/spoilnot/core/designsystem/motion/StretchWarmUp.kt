package com.parcha.spoilnot.core.designsystem.motion

import android.os.Build
import androidx.activity.FullyDrawnReporter
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Compiles the GPU shaders of Android's stretch overscroll before a list first reaches its end (after v1, DESIGN
 * 13.1). From Android 12 a list pulled past its end draws its rows into an offscreen layer and draws that layer onto
 * the screen with a stretch shader. The first time an app does this after an install or update, the GPU driver compiles
 * two programs on the RenderThread (4 to 11 ms each on the benchmark emulator), so that frame is late; Plan's week is
 * short enough for its first scroll to reach the end. The compiled programs are kept on disk after that.
 *
 * [WARM_UP_DELAY_MS] after the start-up screen reports itself drawn, this pulls a one-row list through Compose's own
 * overscroll effect, keeps it stretched for [STRETCHED_FRAMES] frames and leaves the composition, so the programs are
 * compiled while nothing moves. It has no semantics. Give it the size of an opaque surface drawn over it that is
 * smaller than the screen and as wide as the lists (the tab bar): a full-screen opaque surface would not do, since the
 * GPU library drops whatever a frame drew before one, and a narrower list draws its layer with other programs. On later
 * starts the programs come from the disk cache. Android before 12 glows instead of stretching, and it does nothing.
 */
@Composable
fun StretchOverscrollWarmUp(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    var stage by remember { mutableStateOf(WarmUpStage.Waiting) }
    val reporter = LocalFullyDrawnReporterOwner.current?.fullyDrawnReporter
    LaunchedEffect(reporter) {
        reporter?.awaitReportDrawn()
        delay(WARM_UP_DELAY_MS)
        stage = WarmUpStage.Stretching
    }
    if (stage != WarmUpStage.Stretching) return
    // Null when overscroll is turned off for the app: then there is nothing to compile.
    val effect = rememberOverscrollEffect()
    LaunchedEffect(effect) {
        if (effect != null) {
            // The effect knows its size only once it has drawn.
            withFrameNanos { }
            effect.applyToScroll(Offset(0f, PULL_PX), NestedScrollSource.UserInput) { Offset.Zero }
            repeat(STRETCHED_FRAMES) { withFrameNanos { } }
        }
        stage = WarmUpStage.Done
    }
    if (effect == null) return
    // Compose's list, so the layer is drawn with the same clip and programs as the tabs' lists, around a row as they
    // draw it (DESIGN 19.5): the row surface clipped to its group shape, with a tinted icon.
    LazyColumn(modifier.clearAndSetSemantics { }, overscrollEffect = effect, userScrollEnabled = false) {
        item {
            ListSegment(position = SegmentPosition.First) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private enum class WarmUpStage { Waiting, Stretching, Done }

/** Suspends until the activity has reported itself fully drawn (at once when it already has). */
private suspend fun FullyDrawnReporter.awaitReportDrawn() {
    if (isFullyDrawnReported) return
    suspendCancellableCoroutine { continuation ->
        val listener = { if (continuation.isActive) continuation.resume(Unit) }
        addOnReportDrawnListener(listener)
        continuation.invokeOnCancellation { removeOnReportDrawnListener(listener) }
    }
}

/** After the start-up screen's own frames, so it shares no frame with them or with the start-up measurement. */
internal const val WARM_UP_DELAY_MS = 500L

/** How far the row is pulled, in pixels: any pull stretches it. */
private const val PULL_PX = 40f

/** Frames the row stays stretched; the first compiles both programs. */
private const val STRETCHED_FRAMES = 2
