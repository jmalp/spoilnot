package com.parcha.spoilnot.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ActionRow
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.HighlightCard
import com.parcha.spoilnot.core.designsystem.component.LeafBadge
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.PasswordField
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.component.RowLines
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.prefs.ThemeMode
import com.parcha.spoilnot.feature.auth.domain.Units
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

    SettingsPage(
        content = content,
        state = state,
        device = device,
        units = units,
        notifications = notifications,
        notificationsAllowed = rememberNotificationsAllowed(),
        // The page's error, unless a dialog shows it.
        error = state.error?.takeIf { !confirmDelete && clearStep == ClearStep.None }?.let { stringResource(it) },
        actions = SettingsActions(
            onBack = onBack,
            onOpenHousehold = onOpenHousehold,
            onOpenNotifications = onOpenNotifications,
            onOpenAbout = onOpenAbout,
            onResendVerification = viewModel::resendVerification,
            onThemeMode = { viewModel.setThemeMode(it) },
            onColorTheme = { viewModel.setColorTheme(it) },
            onUnits = viewModel::setUnits,
            onExport = viewModel::export,
            onCrashReports = { viewModel.setCrashReports(it) },
            onTakeTour = { viewModel.takeTour() },
            onSignOut = { viewModel.signOut() },
            onDeleteAccount = {
                viewModel.dismissError()
                confirmDelete = true
            },
            onClearHousehold = {
                viewModel.dismissError()
                clearStep = ClearStep.AreYouSure
            },
        ),
        modifier = modifier,
        snackbar = snackbar,
    )

    when (clearStep) {
        ClearStep.None -> Unit

        ClearStep.AreYouSure -> AlertDialog(
            onDismissRequest = { clearStep = ClearStep.None },
            title = { Text(stringResource(R.string.clear_household_title), style = SpoilNotText.DialogTitle) },
            text = {
                Text(stringResource(R.string.clear_household_body, content?.householdName.orEmpty()))
            },
            confirmButton = {
                TextButton(
                    onClick = { clearStep = ClearStep.Password },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("clear_household_yes"),
                ) { Text(stringResource(R.string.clear_household_yes), style = SpoilNotText.ButtonMedium) }
            },
            dismissButton = {
                TextButton(onClick = { clearStep = ClearStep.None }) {
                    Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
                }
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

/** What Settings' rows and buttons do; [SettingsScreen] wires them to its view model. */
internal class SettingsActions(
    val onBack: () -> Unit = {},
    val onOpenHousehold: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onResendVerification: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onColorTheme: (ColorTheme) -> Unit = {},
    val onUnits: (Units) -> Unit = {},
    val onExport: () -> Unit = {},
    val onCrashReports: (Boolean) -> Unit = {},
    val onTakeTour: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
    val onClearHousehold: () -> Unit = {},
)

/**
 * Settings (DESIGN 5.6, 19.12) in the 2026-11 look: the account as the highlight card, Household and Notifications,
 * Appearance, Data, the tour and About as groups of rows, then Sign out as a full-width tonal button, Delete account
 * and, for the owner, Clear household data.
 */
@Composable
internal fun SettingsPage(
    content: SettingsViewModel.Content?,
    state: SettingsViewModel.UiState,
    device: SettingsViewModel.Device,
    units: Units,
    notifications: NotificationPrefs?,
    notificationsAllowed: Boolean,
    error: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    PushedScaffold(
        title = stringResource(R.string.settings),
        onBack = actions.onBack,
        modifier = modifier.testTag("screen_settings"),
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(error)
            AccountCard(content, state, actions.onResendVerification)
            Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                ActionRow(
                    position = SegmentPosition.First,
                    icon = Icons.Rounded.Group,
                    title = stringResource(R.string.settings_household),
                    summary = content?.householdName?.let { name ->
                        pluralStringResource(
                            R.plurals.settings_household_summary,
                            content.memberCount,
                            name,
                            content.memberCount,
                        )
                    },
                    onClick = actions.onOpenHousehold,
                    opensPage = true,
                    modifier = Modifier.testTag("row_household"),
                )
                NotificationsRow(
                    notifications,
                    notificationsAllowed,
                    actions.onOpenNotifications,
                    position = SegmentPosition.Last,
                )
            }
            AppearanceCard(
                device = device,
                units = units,
                onThemeMode = actions.onThemeMode,
                onColorTheme = actions.onColorTheme,
                onUnits = actions.onUnits,
            )
            DataSection(
                busy = state.busy,
                crashReports = device.crashReports,
                onExport = actions.onExport,
                onCrashReports = actions.onCrashReports,
            )
            TourAndAbout(onTakeTour = actions.onTakeTour, onOpenAbout = actions.onOpenAbout)
            TonalButton(
                text = stringResource(R.string.action_sign_out),
                onClick = actions.onSignOut,
                icon = Icons.AutoMirrored.Rounded.Logout,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp),
            )
            DangerTextButton(
                stringResource(R.string.action_delete_account),
                enabled = !state.busy,
                onClick = actions.onDeleteAccount,
            )
            if (content?.isOwner == true) {
                DangerTextButton(
                    stringResource(R.string.action_clear_household),
                    enabled = !state.busy,
                    onClick = actions.onClearHousehold,
                    modifier = Modifier.testTag("button_clear_household"),
                )
            }
        }
    }
}

/**
 * The account as Settings' highlight card (DESIGN 19.8, 19.12): the name's initial in a leaf, the name and the email,
 * and while the email isn't verified a nested row with Resend email.
 */
@Composable
private fun AccountCard(content: SettingsViewModel.Content?, state: SettingsViewModel.UiState, onResend: () -> Unit) {
    val name = content?.displayName.orEmpty()
    HighlightCard(Modifier.testTag("card_account")) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LeafBadge(
                size = 56.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                val initial = name.trim().firstOrNull()?.uppercaseChar()
                if (initial == null) {
                    Icon(Icons.Rounded.Person, contentDescription = null)
                } else {
                    Text(initial.toString(), style = SpoilNotText.CardTitle)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = SpoilNotText.CardTitle)
                Text(
                    content?.email.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!state.emailVerified) {
            ListSegment(
                position = SegmentPosition.Only,
                color = SpoilNotSurfaces.current.nestedRow,
                outerCorner = SpoilNotCorners.NestedOuter,
                minHeight = ListSegmentMinHeightOneLine,
                contentPadding = PaddingValues(start = 16.dp, end = 6.dp),
                modifier = Modifier.testTag("row_email_unverified"),
            ) {
                val lines = @Composable { modifier: Modifier ->
                    RowLines(
                        stringResource(R.string.email_unverified),
                        if (state.verificationSent) stringResource(R.string.verification_sent) else null,
                        modifier,
                    )
                }
                val resend = @Composable {
                    TextButton(onClick = onResend, enabled = !state.busy && !state.verificationSent) {
                        Text(stringResource(R.string.action_resend_verification), style = SpoilNotText.ButtonMedium)
                    }
                }
                // From 1.5× text the button goes under the words, so neither is squeezed (DESIGN 6.4).
                if (isLargeText()) {
                    Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                        lines(Modifier)
                        resend()
                    }
                } else {
                    lines(Modifier.weight(1f))
                    resend()
                }
            }
        }
    }
}

/** Delete account and Clear household data: text buttons in the error color, 48 dp, the width of the page. */
@Composable
private fun DangerTextButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(text, style = SpoilNotText.ButtonMedium) }
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
        title = { Text(title, style = SpoilNotText.DialogTitle) },
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
            ) { Text(action, style = SpoilNotText.ButtonMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}

/**
 * Settings' Notifications row: the reminder time, "Off", or after v1 (review UX-02) "Off on this phone" when the
 * member's reminder is on but this phone blocks notifications, which used to read as on. Its tile shows a crossed-out
 * bell then.
 */
@Composable
internal fun NotificationsRow(
    prefs: NotificationPrefs?,
    allowed: Boolean,
    onOpen: () -> Unit,
    position: SegmentPosition = SegmentPosition.Only,
) {
    val blocked = NotificationPrompt.blockedHere(allowed, prefs?.enabled)
    ActionRow(
        position = position,
        icon = if (blocked) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
        title = stringResource(R.string.settings_notifications),
        summary = prefs?.let {
            when {
                !prefs.enabled -> stringResource(R.string.settings_notifications_off)
                blocked -> stringResource(R.string.settings_notifications_blocked)
                else -> stringResource(R.string.settings_notifications_on, formatHour(prefs.hour))
            }
        },
        onClick = onOpen,
        opensPage = true,
        modifier = Modifier.testTag("row_notifications"),
    )
}
