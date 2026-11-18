package com.parcha.spoilnot.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.HighlightCard
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.PlainCard
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.RoundIconButton
import com.parcha.spoilnot.core.designsystem.component.ScreenHeader
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.auth.domain.PendingJoin
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.Session

/**
 * Shown to a signed-in user without a household; nothing else is reachable until they have one (DESIGN 5.1). In the
 * 2026-11 look (19.7): the page's header with "Welcome, Sam" and a round ⋮ for Sign out and Delete account.
 */
@Composable
fun HouseholdGateScreen(
    session: Session.NoHousehold,
    modifier: Modifier = Modifier,
    viewModel: GateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val page = SpoilNotSurfaces.current.page

    Scaffold(
        modifier = modifier.testTag("screen_gate"),
        containerColor = page,
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.gate_title, session.displayName),
                modifier = Modifier
                    .background(page)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
                actions = {
                    Box {
                        RoundIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more_options), {
                            menuOpen = true
                        })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sign_out)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.signOut()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete_account)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.openDeleteAccount()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GateCards(
                    session.join,
                    state,
                    onCreate = viewModel::createHousehold,
                    onLookUp = viewModel::lookUpCode,
                    onAbandon = viewModel::abandonRequest,
                )
            }
        }
    }

    if (state.askSignOutUnsynced) {
        UnsyncedSignOutDialog(
            onSignOut = { viewModel.signOut(evenIfUnsynced = true) },
            onDismiss = viewModel::dismissSignOutUnsynced,
        )
    }

    if (state.deletingAccount) {
        DeleteAccountDialog(
            busy = state.busy,
            error = state.deleteError,
            onDismiss = viewModel::dismissDeleteAccount,
            onDelete = viewModel::deleteAccount,
        )
    }

    state.confirm?.let { invite ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = {
                Text(
                    stringResource(R.string.join_confirm_title, invite.householdName),
                    style = SpoilNotText.DialogTitle,
                )
            },
            text = { Text(stringResource(R.string.join_confirm_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::sendRequest, enabled = !state.busy) {
                    Text(stringResource(R.string.action_send_request), style = SpoilNotText.ButtonMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) {
                    Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
                }
            },
        )
    }
}

/**
 * The gate's cards (DESIGN 5.1, 19.8): create or join as plain cards under a sentence, or the join request in flight
 * as the highlight card, the one thing on the screen then.
 */
@Composable
internal fun GateCards(
    join: JoinState?,
    state: GateViewModel.UiState,
    onCreate: (String) -> Unit,
    onLookUp: (String) -> Unit,
    onAbandon: (PendingJoin) -> Unit,
) {
    when (join) {
        null -> {
            Text(
                stringResource(R.string.gate_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            CreateCard(state.busy, state.createError, onCreate)
            JoinCard(state.busy, state.joinError, onLookUp)
        }

        else -> JoinStatusCard(join, state.busy, state.requestError) { onAbandon(join.pending) }
    }
}

@Composable
private fun CreateCard(busy: Boolean, error: Int?, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val valid = HouseholdRules.isNameValid(name)
    val submit = {
        submitted = true
        if (valid) onCreate(name)
    }
    GateCard(
        title = stringResource(R.string.gate_create_title),
        body = stringResource(R.string.gate_create_body),
        modifier = Modifier.testTag("card_create"),
    ) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.field_household_name),
            error = if (submitted && !valid) stringResource(R.string.error_household_name) else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        ErrorText(error?.let { stringResource(it) })
        PrimaryButton(stringResource(R.string.action_create), onClick = submit, busy = busy)
    }
}

@Composable
private fun JoinCard(busy: Boolean, error: Int?, onContinue: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    GateCard(
        title = stringResource(R.string.gate_join_title),
        body = stringResource(R.string.gate_join_body),
        modifier = Modifier.testTag("card_join"),
    ) {
        FormTextField(
            value = code,
            // Uppercases as you type and accepts pasted codes with or without the hyphen.
            onValueChange = { code = InviteCodes.normalize(it) },
            label = stringResource(R.string.field_invite_code),
            visualTransformation = InviteCodeTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onContinue(code) }),
        )
        ErrorText(error?.let { stringResource(it) })
        TonalButton(
            text = stringResource(R.string.action_continue),
            onClick = { onContinue(code) },
            enabled = !busy && code.length == InviteCodes.LENGTH,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun JoinStatusCard(join: JoinState, busy: Boolean, error: Int?, onAbandon: () -> Unit) {
    val name = join.pending.householdName
    val (title, body, action) = when (join) {
        is JoinState.Waiting -> Triple(R.string.waiting_title, R.string.waiting_body, R.string.action_cancel_request)
        is JoinState.Declined -> Triple(R.string.declined_title, R.string.declined_body, R.string.action_ok)
        is JoinState.Expired -> Triple(R.string.expired_title, R.string.expired_body, R.string.action_ok)
    }
    HighlightCard(Modifier.testTag("card_join_status")) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = SpoilNotText.CardTitle, modifier = Modifier.semantics { heading() })
            Text(stringResource(body, name), style = MaterialTheme.typography.bodyLarge)
        }
        if (join is JoinState.Waiting) {
            CircularProgressIndicator(
                Modifier.size(32.dp).align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        ErrorText(error?.let { stringResource(it) }, Modifier.padding(horizontal = 8.dp))
        TonalButton(
            text = stringResource(action),
            onClick = onAbandon,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A gate card: a plain card (DESIGN 19.8) with its title, what it is for, then its field and button. */
@Composable
private fun GateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PlainCard(modifier, contentPadding = PaddingValues(20.dp), spacing = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = SpoilNotText.SectionTitle, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}
