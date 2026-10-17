package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R

/**
 * The short explanation shown before asking for the notification permission, after the first dated item is
 * saved (DESIGN 5.4). Asks the system only if the user chooses Turn on.
 */
@Composable
fun NotificationExplainer(onDone: () -> Unit) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.notif_explainer_title)) },
        text = { Text(stringResource(R.string.notif_explainer_body)) },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onDone()
                }
            }) { Text(stringResource(R.string.notif_turn_on)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.notif_explainer_later)) } },
    )
}
