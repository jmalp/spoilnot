package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.StatusTone
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryRow
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What can be done to one batch. Every swipe has a menu equivalent (DESIGN 5.2, 6.8). */
data class BatchActions(
    val onMinus: (StockBatch) -> Unit,
    val onPlus: (StockBatch) -> Unit,
    val onUsedUp: (StockBatch) -> Unit,
    val onThrowAway: (StockBatch) -> Unit,
    val onEdit: (StockBatch) -> Unit,
    val onMove: (StockBatch) -> Unit,
)

/** One product row: name, total, status chip and location; tap to expand into its batches (DESIGN 5.2). */
@Composable
fun PantryRowItem(
    row: PantryRow,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenProduct: () -> Unit,
    actions: BatchActions,
    modifier: Modifier = Modifier,
) {
    val unit = stringResource(row.unit.label())
    val location = stringResource(row.location.label())
    val status = statusText(row.soonest?.expiryDate, row.soonest?.dateType ?: DateType.UseBy, today)
    val description = if (row.isOut) {
        stringResource(R.string.row_description_out, row.name)
    } else {
        stringResource(R.string.row_description, row.name, formatQuantity(row.total), unit, status, location)
    }
    val toggleLabel = stringResource(if (expanded) R.string.collapse_batches else R.string.expand_batches, row.name)

    Column(modifier.fillMaxWidth().testTag("pantry_row_${row.productId}")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = toggleLabel
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tone = if (row.isOut) StatusTone.NoDate else row.status.tone()
            Icon(
                if (row.isOut) Icons.Rounded.Remove else row.status.icon(),
                contentDescription = null,
                tint = SpoilNotStatus.colors.forTone(tone),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenProduct),
                    )
                    if (row.isOut) {
                        Badge(stringResource(R.string.badge_out))
                    } else if (row.isLow) {
                        Badge(stringResource(R.string.badge_low))
                    }
                    if (row.pendingSync) {
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = stringResource(R.string.pending_sync),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Wraps instead of squeezing at large font sizes (DESIGN 6.8: 200% font scale).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!row.isOut) StatusChip(row.soonest?.expiryDate, row.soonest!!.dateType, today)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            row.location.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            Text(
                "${formatQuantity(row.total)} $unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.batches.isNotEmpty()) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }
        AnimatedVisibility(expanded && row.batches.isNotEmpty()) {
            Column(Modifier.padding(start = 24.dp)) {
                row.batches.forEach { batch -> BatchRow(batch, today, actions) }
            }
        }
        HorizontalDivider()
    }
}

/**
 * One batch: quantity, date, location and date type, with -1/+1 and a menu. Swipe right to use up, left to throw
 * away (asks why).
 */
@Composable
fun BatchRow(
    batch: StockBatch,
    today: LocalDate,
    actions: BatchActions,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(batch.id, batch.quantity) { swipe.reset() }

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.testTag("batch_${batch.id}"),
        backgroundContent = { SwipeBackground(swipe.dismissDirection) },
        onDismiss = { direction ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> actions.onUsedUp(batch)
                SwipeToDismissBoxValue.EndToStart -> actions.onThrowAway(batch)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { swipe.reset() }
        },
    ) {
        // Opaque, so the swipe background only shows where the row has moved away.
        Surface(color = containerColor) {
            BatchContent(batch, today, actions)
        }
    }
}

@Composable
private fun BatchContent(batch: StockBatch, today: LocalDate, actions: BatchActions) {
    val unit = stringResource(batch.unit.label())
    val status = statusText(batch.expiryDate, batch.dateType, today)
    val location = stringResource(batch.location.label())
    val dateType = stringResource(batch.dateType.label())
    val description = stringResource(R.string.batch_description, formatQuantity(batch.quantity), unit, status, location)
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).padding(vertical = 8.dp).semantics(mergeDescendants = true) {
                contentDescription =
                    description
            },
        ) {
            Text("${formatQuantity(batch.quantity)} $unit", style = MaterialTheme.typography.bodyLarge)
            Text(
                "$status · $location · $dateType",
                style = MaterialTheme.typography.bodyMedium,
                color = SpoilNotStatus.colors.forTone(ExpiryStatus.of(batch.expiryDate, today).tone()),
            )
            batch.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { actions.onMinus(batch) }) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.action_minus_one))
        }
        IconButton(onClick = { actions.onPlus(batch) }) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_plus_one))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.batch_options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    R.string.action_used_up to actions.onUsedUp,
                    R.string.action_throw_away to actions.onThrowAway,
                    R.string.action_edit to actions.onEdit,
                    R.string.action_move to actions.onMove,
                ).forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            menu = false
                            action(batch)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, icon, label, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Quad(SpoilNotStatus.colors.fresh, Icons.Rounded.Done, R.string.action_used_up, Alignment.CenterStart)

        SwipeToDismissBoxValue.EndToStart ->
            Quad(
                SpoilNotStatus.colors.expired,
                Icons.Rounded.DeleteOutline,
                R.string.action_throw_away,
                Alignment.CenterEnd,
            )

        SwipeToDismissBoxValue.Settled -> return
    }
    Box(
        Modifier.fillMaxSize().background(color.copy(alpha = 0.16f)).padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Text(stringResource(label), color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** Throw away asks for a reason (DESIGN 5.2). */
@Composable
fun ThrowAwayDialog(name: String, onReason: (DiscardReason) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.throw_away_title, name),
        options = DiscardReason.entries,
        label = { stringResource(it.label()) },
        selected = null,
        onPick = onReason,
        onDismiss = onDismiss,
    )
}

@Composable
fun MoveDialog(current: Location, onPick: (Location) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.move_title),
        options = Location.entries.filter { it != current },
        label = { stringResource(it.label()) },
        selected = null,
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                                onPick(option)
                                onDismiss()
                            }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
