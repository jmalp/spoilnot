package com.parcha.spoilnot.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.PasswordField
import com.parcha.spoilnot.feature.household.domain.ResetOutcome
import com.parcha.spoilnot.feature.household.ui.DeleteAccountDialog
import com.parcha.spoilnot.feature.household.ui.UnsyncedSignOutDialog
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefs
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrompt
import com.parcha.spoilnot.feature.notifications.ui.formatHour
import com.parcha.spoilnot.feature.notifications.ui.rememberNotificationsAllowed

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHousehold: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exportedText = stringResource(R.string.settings_exported)
    // The system's Save dialog picks where the export goes (DESIGN 13.4). It opens once the export has been read from
    // the server, so offline there is no dialog and no empty file, only "You're offline".
    val saveExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.saveExport(uri) else viewModel.cancelExport()
        }
    LaunchedEffect(state.exportReady) {
        state.exportReady?.let { fileName ->
            viewModel.exportDialogOpened()
            saveExport.launch(fileName)
        }
    }
    LaunchedEffect(state.exported) {
        if (state.exported) {
            snackbar.showSnackbar(exportedText)
            viewModel.dismissExported()
        }
    }
    val exportErrorText = state.exportError?.let { stringResource(it) }
    LaunchedEffect(exportErrorText) {
        if (exportErrorText != null) {
            snackbar.showSnackbar(exportErrorText)
            viewModel.dismissExported()
        }
    }
    val content by viewModel.content.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Clear household data (owner, after v1): an "are you sure" step, then the owner's password.
    var clearStep by rememberSaveable { mutableStateOf(ClearStep.None) }
    val clearedText = stringResource(R.string.clear_household_done)
    val clearQueuedText = stringResource(R.string.clear_household_when_online)
    LaunchedEffect(state.householdCleared) {
        val outcome = state.householdCleared ?: return@LaunchedEffect
        clearStep = ClearStep.None
        snackbar.showSnackbar(if (outcome == ResetOutcome.Cleared) clearedText else clearQueuedText)
        viewModel.dismissHouseholdCleared()
    }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    DetailScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier.testTag("screen_settings"),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorText(
                state.error?.takeIf { !confirmDelete && clearStep == ClearStep.None }?.let { stringResource(it) },
            )
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_account),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
                ListItem(
                    headlineContent = { Text(content?.displayName.orEmpty()) },
                    supportingContent = { Text(content?.email.orEmpty()) },
                    leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    colors = transparent,
                )
                if (!state.emailVerified) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.email_unverified)) },
                        supportingContent = if (state.verificationSent) {
                            { Text(stringResource(R.string.verification_sent)) }
                        } else {
                            null
                        },
                        trailingContent = {
                            TextButton(
                                onClick = viewModel::resendVerification,
                                enabled =
                                    !state.busy && !state.verificationSent,
                            ) {
                                Text(stringResource(R.string.action_resend_verification))
                            }
                        },
                        colors = transparent,
                    )
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_household)) },
                    supportingContent = content?.householdName?.let { name ->
                        {
                            Text(
                                pluralStringResource(
                                    R.plurals.settings_household_summary,
                                    content!!.memberCount,
                                    name,
                                    content!!.memberCount,
                                ),
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.Group, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onOpenHousehold).testTag("row_household"),
                )
            }
            NotificationsRow(notifications, rememberNotificationsAllowed(), onOpenNotifications)
            AppearanceCard(
                device = device,
                units = units,
                onThemeMode = { viewModel.setThemeMode(it) },
                onColorTheme = { viewModel.setColorTheme(it) },
                onUnits = viewModel::setUnits,
            )
            DataCard(busy = state.busy, onExport = viewModel::export)
            PrivacyAndAboutCard(
                crashReports = device.crashReports,
                onCrashReports = { viewModel.setCrashReports(it) },
                onOpenAbout = onOpenAbout,
                onTakeTour = viewModel::takeTour,
            )
            OutlinedButton(
                onClick = { viewModel.signOut() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.action_sign_out)) }
            TextButton(
                onClick = {
                    viewModel.dismissError()
                    confirmDelete = true
                },
                enabled = !state.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.action_delete_account)) }
            if (content?.isOwner == true) {
                TextButton(
                    onClick = {
                        viewModel.dismissError()
                        clearStep = ClearStep.AreYouSure
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("button_clear_household"),
                ) { Text(stringResource(R.string.action_clear_household)) }
            }
        }
    }

    when (clearStep) {
        ClearStep.None -> Unit

        ClearStep.AreYouSure -> AlertDialog(
            onDismissRequest = { clearStep = ClearStep.None },
            title = { Text(stringResource(R.string.clear_household_title)) },
            text = {
                Text(stringResource(R.string.clear_household_body, content?.householdName.orEmpty()))
            },
            confirmButton = {
                TextButton(
                    onClick = { clearStep = ClearStep.Password },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("clear_household_yes"),
                ) { Text(stringResource(R.string.clear_household_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { clearStep = ClearStep.None }) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        ClearStep.Password -> PasswordConfirmDialog(
            title = stringResource(R.string.clear_household_password_title),
            body = stringResource(R.string.clear_household_password_body),
            action = stringResource(R.string.clear_household_confirm),
            busy = state.busy,
            error = state.error,
            onDismiss = {
                viewModel.dismissError()
                clearStep = ClearStep.None
            },
            onConfirm = viewModel::clearHouseholdData,
        )
    }

    if (state.askSignOutUnsynced) {
        UnsyncedSignOutDialog(
            onSignOut = { viewModel.signOut(evenIfUnsynced = true) },
            onDismiss = viewModel::dismissSignOutUnsynced,
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            busy = state.busy,
            error = state.error,
            onDismiss = { confirmDelete = false },
            onDelete = viewModel::deleteAccount,
        )
    }
}

/** The steps of Clear household data. */
private enum class ClearStep { None, AreYouSure, Password }

/** A password field with a destructive action, for the last step of Clear household data. */
@Composable
private fun PasswordConfirmDialog(
    title: String,
    body: String,
    action: String,
    busy: Boolean,
    error: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                PasswordField(value = password, onValueChange = {
                    password = it
                }, onImeAction = { onConfirm(password) })
                ErrorText(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !busy && password.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("clear_household_confirm"),
            ) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Settings' Notifications row: the reminder time, "Off", or after v1 (review UX-02) "Off on this phone" when the
 * member's reminder is on but this phone blocks notifications, which used to read as on.
 */
@Composable
internal fun NotificationsRow(prefs: NotificationPrefs?, allowed: Boolean, onOpen: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications)) },
            supportingContent = prefs?.let {
                {
                    Text(
                        when {
                            !prefs.enabled -> stringResource(R.string.settings_notifications_off)

                            NotificationPrompt.blockedHere(allowed, prefs.enabled) ->
                                stringResource(R.string.settings_notifications_blocked)

                            else -> stringResource(R.string.settings_notifications_on, formatHour(prefs.hour))
                        },
                    )
                }
            },
            leadingContent = {
                Icon(
                    if (NotificationPrompt.blockedHere(allowed, prefs?.enabled)) {
                        Icons.Rounded.NotificationsOff
                    } else {
                        Icons.Rounded.Notifications
                    },
                    contentDescription = null,
                )
            },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onOpen).testTag("row_notifications"),
        )
    }
}
