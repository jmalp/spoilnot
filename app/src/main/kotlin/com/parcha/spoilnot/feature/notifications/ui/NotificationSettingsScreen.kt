package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.feature.notifications.domain.ExpiredMode
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.pantry.ui.EnumDropdown
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** "9:00 AM" in the user's locale. */
fun formatHour(hour: Int): String = LocalTime.of(hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Re-read when returning from system settings.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val allowed = remember(checks) { Notifications.enabled(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }

    DetailScaffold(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        modifier = modifier.testTag("screen_notifications"),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val p = prefs ?: return@DetailScaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!allowed) {
                // DESIGN 5.4: if denied, a banner with a button that opens the system settings.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.notif_blocked), color = MaterialTheme.colorScheme.onErrorContainer)
                        val canAsk =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !Notifications.permissionGranted(context)
                        TextButton(onClick = {
                            if (canAsk) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }) {
                            Text(stringResource(if (canAsk) R.string.notif_turn_on else R.string.notif_open_settings))
                        }
                    }
                }
            }
            ToggleRow(
                stringResource(R.string.notif_enabled),
                stringResource(R.string.notif_enabled_help),
                p.enabled,
            ) { on ->
                viewModel.update { it.copy(enabled = on) }
            }
            if (p.enabled) {
                EnumDropdown(
                    label = stringResource(R.string.notif_time),
                    value = p.hour,
                    options = NotificationPrefs.HOURS.toList(),
                    optionLabel = { formatHour(it) },
                    onPick = { hour -> viewModel.update { it.copy(digestMinutes = hour * 60) } },
                    helper = stringResource(R.string.notif_time_help),
                )
                Text(stringResource(R.string.notif_stages), style = MaterialTheme.typography.titleMedium)
                listOf(
                    3 to R.string.stage_3,
                    2 to R.string.stage_2,
                    1 to R.string.stage_1,
                    0 to R.string.stage_0,
                ).forEach { (stage, label) ->
                    CheckRow(stringResource(label), stage in p.stages) { on ->
                        viewModel.update { it.copy(stages = if (on) it.stages + stage else it.stages - stage) }
                    }
                }
                Text(stringResource(R.string.notif_expired), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    listOf(
                        ExpiredMode.Once to R.string.expired_once,
                        ExpiredMode.Daily to R.string.expired_daily,
                        ExpiredMode.Never to R.string.expired_never,
                    )
                        .forEach { (mode, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(p.expiredMode == mode, role = Role.RadioButton) {
                                        viewModel.update { it.copy(expiredMode = mode) }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = p.expiredMode == mode, onClick = null)
                                Text(stringResource(label), Modifier.padding(start = 12.dp))
                            }
                        }
                }
            }
            OutlinedButton(
                onClick = {
                    if (Notifications.sendTest(context)) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.notif_test_sent)) }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Notifications.permissionGranted(context)
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("button_test_notification"),
            ) { Text(stringResource(R.string.notif_test)) }
            // DESIGN 15: an in-app tip about battery settings; no exact-alarm or exemption permission is requested.
            Text(
                stringResource(R.string.notif_battery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) {
                Text(stringResource(R.string.notif_battery_action))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = 48.dp,
        ).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
