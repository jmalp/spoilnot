package com.parcha.spoilnot.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * [value] as a list should show it: held while [state] scrolls, so a change from another phone never moves the list
 * under the user's finger (DESIGN 6.4), and taken as soon as the scroll stops.
 *
 * The scroll state is read in a `snapshotFlow`, outside composition, so a fling starting and stopping doesn't recompose
 * the caller (review PERF-08); the caller recomposes only when what it shows changes.
 */
@Composable
fun <T> rememberHeldWhileScrolling(value: T, state: ScrollableState): T {
    var shown by remember { mutableStateOf(value) }
    val latest by rememberUpdatedState(value)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to latest }.collect { (scrolling, now) -> if (!scrolling) shown = now }
    }
    return shown
}
