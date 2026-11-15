package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's snackbars (DESIGN 19.11): Material's colors (`inverseSurface`, with Undo in `inversePrimary`) and
 * 18 dp corners. Every screen and the shell use it, so Undo looks the same everywhere.
 */
@Composable
fun SpoilNotSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> Snackbar(data, shape = RoundedCornerShape(18.dp)) }
}
