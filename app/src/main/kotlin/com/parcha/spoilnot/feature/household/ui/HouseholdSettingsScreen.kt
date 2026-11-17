package com.parcha.spoilnot.feature.household.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.HighlightCard
import com.parcha.spoilnot.core.designsystem.component.IconTileSize
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.PlainCard
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.component.RoundIconButton
import com.parcha.spoilnot.core.designsystem.component.RowLines
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.sensitiveClip
import com.parcha.spoilnot.feature.household.domain.Household
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
internal sealed interface Confirm {
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

    HouseholdPage(
        c,
        actions,
        snackbar,
        onBack = onBack,
        onApprove = viewModel::approve,
        onDecline = viewModel::decline,
        onConfirm = { confirm = it },
        modifier = modifier,
    )

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

/**
 * The Household page (DESIGN 5.1, 19.7): a pushed screen titled with the household's name, Rename beside Back for the
 * owner, and [HouseholdCards] once the content has loaded.
 */
@Composable
internal fun HouseholdPage(
    c: HouseholdViewModel.Content?,
    actions: HouseholdViewModel.Actions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onApprove: (JoinRequest) -> Unit,
    onDecline: (JoinRequest) -> Unit,
    onConfirm: (Confirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val household = c?.household
    PushedScaffold(
        title = household?.name ?: stringResource(R.string.settings_household),
        onBack = onBack,
        modifier = modifier.testTag("screen_household"),
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        actions = {
            if (c?.isOwner == true) {
                RoundIconButton(Icons.Rounded.Edit, stringResource(R.string.action_rename), {
                    onConfirm(Confirm.Rename)
                })
            }
        },
    ) { padding ->
        if (c == null || household == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PushedScaffold
        }
        HouseholdCards(
            c,
            household,
            actions,
            snackbar,
            onApprove = onApprove,
            onDecline = onDecline,
            onConfirm = onConfirm,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Household settings (DESIGN 5.1, 19) from the screen's content: join requests as the highlight card for the owner,
 * the invite on a plain card, the members as a group of rows, then Delete household or Leave household.
 */
@Composable
internal fun HouseholdCards(
    c: HouseholdViewModel.Content,
    household: Household,
    actions: HouseholdViewModel.Actions,
    snackbar: SnackbarHostState,
    onApprove: (JoinRequest) -> Unit,
    onDecline: (JoinRequest) -> Unit,
    onConfirm: (Confirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErrorText(actions.error?.let { stringResource(it) })
        if (c.isOwner && c.requests.isNotEmpty()) {
            JoinRequestsCard(c.requests, actions.busy, onApprove, onDecline)
        }
        InviteCard(
            code = household.inviteCode,
            householdName = household.name,
            isOwner = c.isOwner,
            busy = actions.busy,
            snackbar = snackbar,
            onRegenerate = { onConfirm(Confirm.Regenerate) },
        )
        MembersGroup(
            members = c.members,
            myUid = c.myUid,
            isOwner = c.isOwner,
            onRemove = { onConfirm(Confirm.Remove(it)) },
            onMakeOwner = { onConfirm(Confirm.Transfer(it)) },
        )
        if (c.isOwner) {
            Text(
                stringResource(R.string.owner_cannot_leave),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            DangerButton(stringResource(R.string.action_delete_household), actions.busy) {
                onConfirm(Confirm.Delete)
            }
        } else {
            DangerButton(stringResource(R.string.action_leave), actions.busy) { onConfirm(Confirm.Leave) }
        }
    }
}

/**
 * The owner's waiting join requests as the highlight card (DESIGN 19.8): each request a nested row with who is asking
 * (their masked sign-in address) and when, then Approve (filled) and Decline (tonal).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoinRequestsCard(
    requests: List<JoinRequest>,
    busy: Boolean,
    onApprove: (JoinRequest) -> Unit,
    onDecline: (JoinRequest) -> Unit,
) {
    HighlightCard(Modifier.testTag("card_join_requests")) {
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.join_requests_title),
                style = SpoilNotText.CardTitle,
                modifier = Modifier.semantics { heading() },
            )
            // After v1: who is asking (their masked sign-in address) and when, not only the name they typed. Requests
            // from builds before this have neither field.
            Text(stringResource(R.string.join_requests_hint), style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            requests.forEachIndexed { index, request ->
                ListSegment(
                    position = segmentPosition(index, requests.size),
                    color = SpoilNotSurfaces.current.nestedRow,
                    outerCorner = SpoilNotCorners.NestedOuter,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.testTag("join_request_${request.uid}"),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(request.displayName, style = SpoilNotText.RowTitle)
                            val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
                            request.emailMasked?.let {
                                Text(it, style = SpoilNotText.RowSupporting, color = detailColor)
                            }
                            request.requestedAt?.let {
                                val now = System.currentTimeMillis()
                                val sent = if (now - it.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) {
                                    stringResource(R.string.activity_just_now)
                                } else {
                                    DateUtils.getRelativeTimeSpanString(
                                        it.toEpochMilli(),
                                        now,
                                        DateUtils.MINUTE_IN_MILLIS,
                                    ).toString()
                                }
                                Text(
                                    stringResource(R.string.join_request_sent, sent),
                                    style = SpoilNotText.RowSupporting,
                                    color = detailColor,
                                )
                            }
                        }
                        // Out of context TalkBack says whom (review A11Y-08): "Approve Robin Lee", "Decline Robin Lee".
                        val approve = stringResource(R.string.join_request_approve_description, request.displayName)
                        val decline = stringResource(R.string.join_request_decline_description, request.displayName)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onApprove(request) },
                                enabled = !busy,
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = approve },
                            ) { Text(stringResource(R.string.action_approve), style = SpoilNotText.ButtonMedium) }
                            TonalButton(
                                text = stringResource(R.string.action_decline),
                                onClick = { onDecline(request) },
                                enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = decline },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Invite people on a plain card: what to do with the code, the code itself (read character by character), Copy and
 * Share as tonal buttons, and New code for the owner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteCard(
    code: String,
    householdName: String,
    isOwner: Boolean,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onRegenerate: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val formatted = InviteCodes.format(code)
    val copied = stringResource(R.string.code_copied)
    val clipLabel = stringResource(R.string.invite_code_clip_label)
    val shareText = stringResource(R.string.share_message, householdName, formatted)
    val codeDescription = stringResource(R.string.invite_code_description, formatted.toList().joinToString(" "))

    PlainCard(Modifier.testTag("card_invite")) {
        Text(
            stringResource(R.string.invite_title),
            style = SpoilNotText.SectionTitle,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.invite_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatted,
            style = SpoilNotText.AccentNumber,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = codeDescription }.testTag("invite_code"),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TonalButton(
                text = stringResource(R.string.action_copy),
                icon = Icons.Rounded.ContentCopy,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(sensitiveClip(clipLabel, formatted)))
                        snackbar.showSnackbar(copied)
                    }
                },
            )
            TonalButton(
                text = stringResource(R.string.action_share),
                icon = Icons.Rounded.Share,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
        // New code only (after v1, R12 removed Pause joining): approval already guards joining.
        if (isOwner) {
            TextButton(onClick = onRegenerate, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_regenerate), style = SpoilNotText.ButtonMedium)
            }
        }
    }
}

/**
 * The members under their group heading, a row each: the name's initial on a tile, the name (with "(you)"), the role
 * and when they joined, and for the owner a ⋮ with Make owner and Remove on everyone else's row.
 */
@Composable
private fun MembersGroup(
    members: List<Member>,
    myUid: String,
    isOwner: Boolean,
    onRemove: (Member) -> Unit,
    onMakeOwner: (Member) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    Column(Modifier.testTag("card_members"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupHeading(stringResource(R.string.household_members))
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            members.forEachIndexed { index, member ->
                val isMe = member.uid == myUid
                ListSegment(
                    position = segmentPosition(index, members.size),
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                ) {
                    InitialTile(member.displayName)
                    val role = stringResource(
                        if (member.role ==
                            Role.Owner
                        ) {
                            R.string.role_owner
                        } else {
                            R.string.role_member
                        },
                    )
                    RowLines(
                        if (isMe) stringResource(R.string.member_you, member.displayName) else member.displayName,
                        member.joinedAt?.let { stringResource(R.string.member_summary, role, formatter.format(it)) }
                            ?: role,
                        Modifier.weight(1f).semantics(mergeDescendants = true) { },
                    )
                    if (isOwner && !isMe) MemberMenu(member, onRemove, onMakeOwner)
                }
            }
        }
    }
}

/** A member's initial on a row's tile; decoration, the row says the name. */
@Composable
private fun InitialTile(name: String) {
    Box(
        Modifier
            .size(IconTileSize)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(percent = 35)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase().orEmpty(),
            style = SpoilNotText.RowTitle,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clearAndSetSemantics { },
        )
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

/** Delete household or Leave household: a text button in the error color, the width of the page. */
@Composable
private fun DangerButton(text: String, busy: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(text, style = SpoilNotText.ButtonMedium) }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val valid = HouseholdRules.isNameValid(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title), style = SpoilNotText.DialogTitle) },
        text = {
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.field_household_name),
                error = if (!valid) stringResource(R.string.error_household_name) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = valid) {
                Text(stringResource(R.string.action_save), style = SpoilNotText.ButtonMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
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
        title = { Text(title, style = SpoilNotText.DialogTitle) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, style = SpoilNotText.ButtonMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}
