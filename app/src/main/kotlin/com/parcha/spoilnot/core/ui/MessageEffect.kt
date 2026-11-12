package com.parcha.spoilnot.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Undo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/** How long an Undo stays available by default (DESIGN 5.2). */
const val UNDO_WINDOW_MS = 8_000L

/**
 * How long an Undo stays available on this phone: [UNDO_WINDOW_MS], or longer when the phone's Accessibility > "Time
 * to take action" setting or a screen reader asks for more (after v1, review A11Y-02). The snackbar has text and a
 * control, as Material's own timed snackbars report. Never shorter than [UNDO_WINDOW_MS].
 */
fun undoWindowMillis(accessibility: AccessibilityManager?): Long = accessibility
    ?.calculateRecommendedTimeoutMillis(
        UNDO_WINDOW_MS,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    )
    ?.coerceAtLeast(UNDO_WINDOW_MS)
    ?: UNDO_WINDOW_MS

/** Shows each message in [snackbar]; messages with an [Undo] offer it for [undoWindowMillis]. */
@Composable
fun MessageEffect(messages: Flow<UiMessage>, snackbar: SnackbarHostState, onUndo: (Undo) -> Unit) {
    val resources = LocalResources.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val text = resources.getString(message.text, *message.args.toTypedArray())
            val undo = message.undo
            if (undo == null) {
                snackbar.showSnackbar(text)
            } else {
                // Read for each message, so a setting changed while the screen is open applies to the next one.
                val result = withTimeoutOrNull(undoWindowMillis(accessibility)) {
                    snackbar.showSnackbar(
                        text,
                        resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                if (result == null) snackbar.currentSnackbarData?.dismiss()
                if (result == SnackbarResult.ActionPerformed) onUndo(undo)
            }
        }
    }
}
