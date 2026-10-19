package com.parcha.spoilnot.feature.household.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.JoinRequest
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Role
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** A confirmation the screen is showing, if any. */
private sealed interface Confirm {
    data object Rename : Confirm

    data object Regenerate : Confirm

    data class Remove(val member: Member) : Confirm

    data class Transfer(val member: Member) : Confirm

    data object Leave : Confirm

    data object Delete : Confirm
}

@Composable
fun HouseholdSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HouseholdViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val c = content
    val household = c?.household

    DetailScaffold(
        title = household?.name ?: stringResource(R.string.settings_household),
        onBack = onBack,
        modifier = modifier.testTag("screen_household"),
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (c?.isOwner == true) {
                IconButton(onClick = { confirm = Confirm.Rename }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                }
            }
        },
    ) { padding ->
        if (c == null || household == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@DetailScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorText(actions.error?.let { stringResource(it) })
            if (c.isOwner && c.requests.isNotEmpty()) {
                JoinRequestsCard(c.requests, actions.busy, viewModel::approve, viewModel::decline)
            }
            InviteCard(
                code = household.inviteCode,
                householdName = household.name,
                isOwner = c.isOwner,
                paused = c.joiningPaused,
                busy = actions.busy,
                snackbar = snackbar,
                onRegenerate = { confirm = Confirm.Regenerate },
                onPause = viewModel::setJoiningPaused,
            )
            MembersCard(
                members = c.members,
                myUid = c.myUid,
                isOwner = c.isOwner,
                onRemove = { confirm = Confirm.Remove(it) },
                onMakeOwner = { confirm = Confirm.Transfer(it) },
            )
            if (c.isOwner) {
                Text(
                    stringResource(R.string.owner_cannot_leave),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DangerButton(stringResource(R.string.action_delete_household), actions.busy) {
                    confirm = Confirm.Delete
                }
            } else {
                DangerButton(stringResource(R.string.action_leave), actions.busy) { confirm = Confirm.Leave }
            }
        }
    }

    val name = household?.name.orEmpty()
    when (val dialog = confirm) {
        null -> Unit

        Confirm.Rename -> RenameDialog(name, onDismiss = { confirm = null }) {
            viewModel.rename(it)
            confirm = null
        }

        Confirm.Regenerate -> ConfirmDialog(
            title = stringResource(R.string.regenerate_title),
            body = stringResource(R.string.regenerate_body),
            confirmLabel = stringResource(R.string.action_regenerate),
            onDismiss = { confirm = null },
        ) { viewModel.regenerateCode() }

        is Confirm.Remove -> ConfirmDialog(
            title = stringResource(R.string.remove_member_title, dialog.member.displayName),
            body = stringResource(R.string.remove_member_body, dialog.member.displayName),
            confirmLabel = stringResource(R.string.action_remove),
            onDismiss = { confirm = null },
        ) { viewModel.remove(dialog.member) }

        is Confirm.Transfer -> ConfirmDialog(
            title = stringResource(R.string.transfer_title, dialog.member.displayName),
            body = stringResource(R.string.transfer_body, dialog.member.displayName),
            confirmLabel = stringResource(R.string.action_make_owner),
            onDismiss = { confirm = null },
        ) { viewModel.makeOwner(dialog.member) }

        Confirm.Leave -> ConfirmDialog(
            title = stringResource(R.string.leave_title, name),
            body = stringResource(R.string.leave_body),
            confirmLabel = stringResource(R.string.action_leave_confirm),
            onDismiss = { confirm = null },
        ) { viewModel.leave() }

        Confirm.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_household_title, name),
            body = stringResource(R.string.delete_household_body),
            confirmLabel = stringResource(R.string.action_delete),
            onDismiss = { confirm = null },
        ) { viewModel.deleteHousehold() }
    }
}

@Composable
private fun JoinRequestsCard(
    requests: List<JoinRequest>,
    busy: Boolean,
    onApprove: (JoinRequest) -> Unit,
    onDecline: (JoinRequest) -> Unit,
) {
    SectionCard(stringResource(R.string.join_requests_title), Modifier.testTag("card_join_requests")) {
        requests.forEach { request ->
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(request.displayName, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        onApprove(request)
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_approve))
                    }
                    OutlinedButton(onClick = {
                        onDecline(request)
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_decline))
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    code: String,
    householdName: String,
    isOwner: Boolean,
    paused: Boolean,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onRegenerate: () -> Unit,
    onPause: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val formatted = InviteCodes.format(code)
    val copied = stringResource(R.string.code_copied)
    val shareText = stringResource(R.string.share_message, householdName, formatted)
    val codeDescription = stringResource(R.string.invite_code_description, formatted.toList().joinToString(" "))

    SectionCard(stringResource(R.string.invite_title), Modifier.testTag("card_invite")) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.invite_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatted,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { contentDescription = codeDescription }.testTag("invite_code"),
            )
            if (paused) {
                Text(
                    stringResource(R.string.invite_paused),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(formatted, formatted)))
                            snackbar.showSnackbar(copied)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text(stringResource(R.string.action_copy), Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(
                    onClick = {
                        val send = Intent(
                            Intent.ACTION_SEND,
                        ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text(stringResource(R.string.action_share), Modifier.padding(start = 8.dp))
                }
            }
            if (isOwner) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRegenerate, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_regenerate))
                    }
                    TextButton(onClick = {
                        onPause(!paused)
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(
                            stringResource(
                                if (paused) R.string.action_resume_joining else R.string.action_pause_joining,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersCard(
    members: List<Member>,
    myUid: String,
    isOwner: Boolean,
    onRemove: (Member) -> Unit,
    onMakeOwner: (Member) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    SectionCard(stringResource(R.string.household_members), Modifier.testTag("card_members")) {
        members.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val isMe = member.uid == myUid
            ListItem(
                headlineContent = {
                    Text(if (isMe) stringResource(R.string.member_you, member.displayName) else member.displayName)
                },
                supportingContent = {
                    val role = stringResource(
                        if (member.role ==
                            Role.Owner
                        ) {
                            R.string.role_owner
                        } else {
                            R.string.role_member
                        },
                    )
                    Text(
                        member.joinedAt?.let { stringResource(R.string.member_summary, role, formatter.format(it)) }
                            ?: role,
                    )
                },
                trailingContent = if (isOwner && !isMe) {
                    { MemberMenu(member, onRemove, onMakeOwner) }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun MemberMenu(member: Member, onRemove: (Member) -> Unit, onMakeOwner: (Member) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.member_options, member.displayName),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_make_owner)) },
                onClick = {
                    open = false
                    onMakeOwner(member)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_member)) },
                onClick = {
                    open = false
                    onRemove(member)
                },
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            content()
        }
    }
}

@Composable
private fun DangerButton(text: String, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(text) }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val valid = HouseholdRules.isNameValid(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.field_household_name),
                error = if (!valid) stringResource(R.string.error_household_name) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
