package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parcha.spoilnot.R

/**
 * The short explanation shown before asking for the notification permission (DESIGN 5.4): when a household opens with
 * dated food on this phone, or a new one reaches its third dated item (after v1, review UX-02). It names the
 * reminder time (UX-03). [onTurnOn] asks the system; [onLater] remembers the answer without asking.
 */
@Composable
fun NotificationExplainer(hour: Int, onTurnOn: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("notification_explainer"),
        title = { Text(stringResource(R.string.notif_explainer_title)) },
        text = { Text(stringResource(R.string.notif_explainer_body_time, formatHour(hour))) },
        confirmButton = { TextButton(onClick = onTurnOn) { Text(stringResource(R.string.notif_turn_on)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.notif_explainer_later)) } },
    )
}

/**
 * Turn on (after v1, review UX-02): asks for the permission while the system still asks, else opens the app's
 * notification settings (refused twice, or notifications or the channel turned off there). [onFinished] runs once the
 * answer is in or the person is back from the system settings, so the caller can check again and say "You're set".
 */
@Composable
fun rememberTurnOnNotifications(onFinished: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val finished by rememberUpdatedState(onFinished)
    var inSettings by rememberSaveable { mutableStateOf(false) }
    var rationaleBefore by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused with no dialog: the system no longer asks, so its settings are the only way left.
        if (!granted && !rationaleBefore && activity?.let(::shouldExplain) == false) {
            inSettings = true
            openNotificationSettings(context)
        } else {
            finished()
        }
    }
    // Back from the system settings: they stopped this screen (a permission dialog only pauses it).
    var left by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && inSettings) left = true
            if (event == Lifecycle.Event.ON_RESUME && inSettings && left) {
                inSettings = false
                left = false
                finished()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.permissionGranted(context)) {
            rationaleBefore = activity?.let(::shouldExplain) == true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            inSettings = true
            openNotificationSettings(context)
        }
    }
}

/** Whether notifications can appear on this phone, checked again whenever the screen comes back. */
@Composable
fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.enabled(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifications.enabled(context)
        onPauseOrDispose { }
    }
    return allowed
}

private fun shouldExplain(activity: Activity): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
