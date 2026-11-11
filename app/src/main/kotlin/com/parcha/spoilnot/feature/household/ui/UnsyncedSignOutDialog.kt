package com.parcha.spoilnot.feature.household.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R

/**
 * Sign out found changes that haven't reached the server (after v1, SYNC-09): signing out clears this phone's copy,
 * so they would be lost. From Settings or the household gate.
 */
@Composable
internal fun UnsyncedSignOutDialog(onSignOut: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_unsynced_title)) },
        text = { Text(stringResource(R.string.sign_out_unsynced_body)) },
        confirmButton = {
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("sign_out_anyway"),
            ) { Text(stringResource(R.string.action_sign_out_anyway)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
