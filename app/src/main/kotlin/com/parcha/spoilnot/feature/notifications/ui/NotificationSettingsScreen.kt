package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.SwitchRow
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
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
    // Turn on, then "You're set" with the time and tomorrow's items (after v1, owner's decision 2).
    val turnOn = rememberTurnOnNotifications {
        checks++
        viewModel.turnOnFinished()
    }

    PushedScaffold(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        modifier = modifier.testTag("screen_notifications"),
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
    ) { padding ->
        val p = prefs ?: return@PushedScaffold
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.permissionGranted(context)
        NotificationSettingsContent(
            p,
            allowed = allowed,
            canAsk = canAsk,
            onUpdate = viewModel::update,
            onTurnOn = turnOn,
            onTest = {
                if (Notifications.sendTest(context)) {
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.notif_test_sent)) }
                } else if (canAsk) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onBattery = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Settings > Notifications (DESIGN 5.4, 10) in the 2026-11 look: the blocked banner, the reminder and Meals I'm cooking
 * as switch rows, its time, the stages as check rows and the expired mode as radio rows under their headings, Send test
 * notification as a tonal button, and the battery tip.
 */
@Composable
internal fun NotificationSettingsContent(
    p: NotificationPrefs,
    allowed: Boolean,
    canAsk: Boolean,
    onUpdate: ((NotificationPrefs) -> NotificationPrefs) -> Unit,
    onTurnOn: () -> Unit,
    onTest: () -> Unit,
    onBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!allowed) {
            // DESIGN 5.4: if denied, a banner with a button that opens the system settings (19.11: rounded).
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SpoilNotCorners.GroupOuter),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.notif_blocked), style = MaterialTheme.typography.bodyLarge)
                    TextButton(
                        onClick = onTurnOn,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(
                            stringResource(if (canAsk) R.string.notif_turn_on else R.string.notif_open_settings),
                            style = SpoilNotText.ButtonMedium,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            SwitchRow(
                position = if (p.enabled) SegmentPosition.First else SegmentPosition.Only,
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.notif_enabled),
                summary = stringResource(R.string.notif_enabled_help),
                checked = p.enabled,
                onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
                modifier = Modifier.testTag("row_notif_enabled"),
            )
            if (p.enabled) {
                SwitchRow(
                    position = SegmentPosition.Last,
                    icon = Icons.Rounded.SoupKitchen,
                    title = stringResource(R.string.notif_cooking),
                    summary = stringResource(R.string.notif_cooking_help),
                    checked = p.cooking,
                    onCheckedChange = { on -> onUpdate { it.copy(cooking = on) } },
                    modifier = Modifier.testTag("row_notif_cooking"),
                )
            }
        }
        if (p.enabled) {
            EnumDropdown(
                label = stringResource(R.string.notif_time),
                value = p.hour,
                options = NotificationPrefs.HOURS.toList(),
                optionLabel = { formatHour(it) },
                onPick = { hour -> onUpdate { it.copy(digestMinutes = hour * 60) } },
                helper = stringResource(R.string.notif_time_help, formatHour(NotificationPrefs.LATEST_HOUR)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupHeading(stringResource(R.string.notif_stages))
                val stages = listOf(
                    3 to R.string.stage_3,
                    2 to R.string.stage_2,
                    1 to R.string.stage_1,
                    0 to R.string.stage_0,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                    stages.forEachIndexed { index, (stage, label) ->
                        ChoiceRow(
                            position = segmentPosition(index, stages.size),
                            label = stringResource(label),
                            chosen = stage in p.stages,
                            radio = false,
                        ) {
                            val on = stage !in p.stages
                            onUpdate { it.copy(stages = if (on) it.stages + stage else it.stages - stage) }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupHeading(stringResource(R.string.notif_expired))
                val modes = listOf(
                    ExpiredMode.Once to R.string.expired_once,
                    ExpiredMode.Daily to R.string.expired_daily,
                    ExpiredMode.Never to R.string.expired_never,
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                    modes.forEachIndexed { index, (mode, label) ->
                        ChoiceRow(
                            position = segmentPosition(index, modes.size),
                            label = stringResource(label),
                            chosen = p.expiredMode == mode,
                            radio = true,
                        ) { onUpdate { it.copy(expiredMode = mode) } }
                    }
                }
            }
        }
        TonalButton(
            text = stringResource(R.string.notif_test),
            onClick = onTest,
            icon = Icons.Rounded.NotificationsActive,
            modifier = Modifier.fillMaxWidth().testTag("button_test_notification"),
        )
        // DESIGN 15: an in-app tip about battery settings; no exact-alarm or exemption permission is requested.
        Column(Modifier.padding(horizontal = 6.dp)) {
            Text(
                stringResource(R.string.notif_battery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onBattery, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.notif_battery_action), style = SpoilNotText.ButtonMedium)
            }
        }
    }
}

/**
 * A one-line row of a group with a checkbox, or a radio button ([radio]), at its start: the whole row is the control,
 * read with its label and state.
 */
@Composable
private fun ChoiceRow(position: SegmentPosition, label: String, chosen: Boolean, radio: Boolean, onClick: () -> Unit) {
    val shape = segmentShape(position)
    val control = if (radio) {
        Modifier.clip(shape).selectable(chosen, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clip(shape).toggleable(chosen, role = Role.Checkbox, onValueChange = { onClick() })
    }
    ListSegment(
        position = position,
        modifier = control,
        minHeight = ListSegmentMinHeightOneLine,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
    ) {
        if (radio) {
            RadioButton(selected = chosen, onClick = null)
        } else {
            Checkbox(checked = chosen, onCheckedChange = null)
        }
        Text(label, style = SpoilNotText.RowTitle, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
    }
}
