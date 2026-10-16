package com.parcha.spoilnot.feature.household.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinState
import com.parcha.spoilnot.feature.household.domain.Session

/** Shown to a signed-in user without a household; nothing else is reachable until they have one (DESIGN 5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdGateScreen(
    session: Session.NoHousehold,
    modifier: Modifier = Modifier,
    viewModel: GateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("screen_gate"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gate_title, session.displayName)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sign_out)) },
                            onClick = {
                                menuOpen = false
                                viewModel.signOut()
                            },
                        )
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val join = session.join) {
                    null -> {
                        Text(
                            stringResource(R.string.gate_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CreateCard(state.busy, state.createError, viewModel::createHousehold)
                        JoinCard(state.busy, state.joinError, viewModel::lookUpCode)
                    }

                    else -> JoinStatusCard(join, state.busy, state.requestError) {
                        viewModel.abandonRequest(join.pending)
                    }
                }
            }
        }
    }

    state.confirm?.let { invite ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.join_confirm_title, invite.householdName)) },
            text = { Text(stringResource(R.string.join_confirm_body, invite.householdName)) },
            confirmButton = {
                TextButton(onClick = viewModel::sendRequest, enabled = !state.busy) {
                    Text(stringResource(R.string.action_send_request))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
        OutlinedButton(
            onClick = { onContinue(code) },
            enabled = !busy && code.length == InviteCodes.LENGTH,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.action_continue)) }
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
    GateCard(
        title = stringResource(title),
        body = stringResource(body, name),
        modifier = Modifier.testTag("card_join_status"),
    ) {
        if (join is JoinState.Waiting) {
            CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
        }
        ErrorText(error?.let { stringResource(it) })
        OutlinedButton(
            onClick = onAbandon,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(action)) }
    }
}

@Composable
private fun GateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
