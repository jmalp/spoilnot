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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.parcha.spoilnot.feature.notifications.ui.formatHour

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
    // The system's Save dialog picks where the export goes (DESIGN 13.4).
    val saveExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.export(uri)
        }
    LaunchedEffect(state.exported) {
        if (state.exported) {
            snackbar.showSnackbar(exportedText)
            viewModel.dismissExported()
        }
    }
    val content by viewModel.content.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
            ErrorText(state.error?.takeIf { !confirmDelete }?.let { stringResource(it) })
            Card(Modifier.fillMaxWidth()) {
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
            Card(Modifier.fillMaxWidth()) {
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
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                    supportingContent = notifications?.let { prefs ->
                        {
                            Text(
                                if (prefs.enabled) {
                                    stringResource(R.string.settings_notifications_on, formatHour(prefs.hour))
                                } else {
                                    stringResource(R.string.settings_notifications_off)
                                },
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onOpenNotifications).testTag("row_notifications"),
                )
            }
            AppearanceCard(
                device = device,
                units = units,
                onThemeMode = { viewModel.setThemeMode(it) },
                onDynamicColor = { viewModel.setDynamicColor(it) },
                onUnits = viewModel::setUnits,
            )
            DataCard(busy = state.busy, onExport = { saveExport.launch(viewModel.exportFileName()) })
            PrivacyAndAboutCard(
                crashReports = device.crashReports,
                onCrashReports = { viewModel.setCrashReports(it) },
                onOpenAbout = onOpenAbout,
            )
            OutlinedButton(
                onClick = viewModel::signOut,
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
        }
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

@Composable
private fun DeleteAccountDialog(busy: Boolean, error: Int?, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_account_body))
                PasswordField(value = password, onValueChange = { password = it }, onImeAction = { onDelete(password) })
                ErrorText(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDelete(password) },
                enabled = !busy && password.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
