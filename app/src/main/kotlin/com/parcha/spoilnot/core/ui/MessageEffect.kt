package com.parcha.spoilnot.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Undo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/** How long an Undo stays available (DESIGN 5.2). */
const val UNDO_WINDOW_MS = 8_000L

/** Shows each message in [snackbar]; messages with an [Undo] offer it for [UNDO_WINDOW_MS]. */
@Composable
fun MessageEffect(messages: Flow<UiMessage>, snackbar: SnackbarHostState, onUndo: (Undo) -> Unit) {
    val resources = LocalResources.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val text = resources.getString(message.text, *message.args.toTypedArray())
            val undo = message.undo
            if (undo == null) {
                snackbar.showSnackbar(text)
            } else {
                val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
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
