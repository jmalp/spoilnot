package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * A form's bottom sheet state, the modifier that measures the sheet for it, and the sheet's content insets. Pass all
 * three to `ModalBottomSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
class FormSheet(val state: SheetState, val modifier: Modifier, val contentWindowInsets: @Composable () -> WindowInsets)

private val contentWindowInsets: @Composable () -> WindowInsets =
    { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }

/** How far a form sheet must be dragged down before it closes, as a share of its height. */
const val FORM_SHEET_DISMISS_FRACTION = 0.5f

/**
 * For sheets holding a form (Add item, Edit, Add to pantry): only a drag past half the sheet's height closes it, and
 * a quick flick never does, so scrolling back up inside the form cannot throw away what was typed (beta feedback).
 *
 * The sheet stops below the status bar instead of padding its content by the status bar. Material's padding shrinks
 * as the sheet moves down, so a form taller than the screen changed height while it settled, which moved its resting
 * place and made it shake without end after a flick down (beta feedback, Add to pantry with two items).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFormSheet(): FormSheet {
    val height = remember { mutableFloatStateOf(0f) }
    val positional = { height.floatValue * FORM_SHEET_DISMISS_FRACTION }
    val velocity = { Float.MAX_VALUE }
    val state = rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = positional,
            velocityThreshold = velocity,
            confirmValueChange = { true },
            skipHiddenState = false,
        ),
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = positional,
            velocityThreshold = velocity,
            initialValue = SheetValue.Hidden,
            confirmValueChange = { true },
            skipHiddenState = false,
        )
    }
    val statusBars = WindowInsets.statusBars
    val modifier = remember(statusBars) {
        Modifier.windowInsetsPadding(statusBars).onSizeChanged { height.floatValue = it.height.toFloat() }
    }
    return remember(state) { FormSheet(state, modifier, contentWindowInsets) }
}
