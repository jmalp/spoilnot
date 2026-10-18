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
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
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
    val onMove: (StockBatch) -> Unit,
)

/**
 * One purchase (beta feedback): status, name, date and location, quantity. Tap to edit; swipe right to use up, left
 * to throw away; the menu has the same actions plus one less, one more and Move. An Out staple's row adds stock.
 */
@Composable
fun PantryItemRow(
    item: PantryItem,
    today: LocalDate,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is PantryItem.Purchase -> PurchaseRow(item, today, actions, onOpen, modifier)
        is PantryItem.Out -> OutRow(item, onOpen, modifier)
    }
}

@Composable
private fun PurchaseRow(
    item: PantryItem.Purchase,
    today: LocalDate,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
    modifier: Modifier,
) {
    val batch = item.batch
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(batch.id, batch.quantity) { swipe.reset() }
    val unit = stringResource(batch.unit.label())
    val location = stringResource(batch.location.label())
    val status = statusText(batch.expiryDate, batch.dateType, today)
    val description =
        stringResource(R.string.row_description, item.name, formatQuantity(batch.quantity), unit, status, location)
    val editLabel = stringResource(R.string.action_edit)

    Column(modifier.fillMaxWidth().testTag("pantry_item_${batch.id}")) {
        SwipeToDismissBox(
            state = swipe,
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
            Surface {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = editLabel) { onOpen(item) }
                        .heightIn(min = 64.dp)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            item.status.icon(),
                            contentDescription = null,
                            tint = SpoilNotStatus.colors.forTone(item.status.tone()),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            NameLine(item.name, low = item.isLow, out = false, pending = batch.pendingSync)
                            // Wraps instead of squeezing at large font sizes (DESIGN 6.8: 200% font scale).
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusChip(batch.expiryDate, batch.dateType, today)
                                LocationLabel(batch.location, location)
                            }
                        }
                        Text(
                            "${formatQuantity(batch.quantity)} $unit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    PurchaseMenu(batch, actions)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun OutRow(item: PantryItem.Out, onOpen: (PantryItem) -> Unit, modifier: Modifier) {
    val product = item.product
    val unit = stringResource(product.unit.label())
    val location = stringResource(product.defaultLocation.label())
    val description = stringResource(R.string.row_description_out, product.name)
    Column(modifier.fillMaxWidth().testTag("pantry_item_out_${product.id}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.action_add_stock)) { onOpen(item) }
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Remove,
                contentDescription = null,
                tint = SpoilNotStatus.colors.forTone(StatusTone.NoDate),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NameLine(product.name, low = false, out = true, pending = false)
                LocationLabel(product.defaultLocation, location)
            }
            Text("0 $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
    }
}

@Composable
private fun NameLine(name: String, low: Boolean, out: Boolean, pending: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (out) {
            Badge(stringResource(R.string.badge_out))
        } else if (low) {
            Badge(stringResource(R.string.badge_low))
        }
        if (pending) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = stringResource(R.string.pending_sync),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationLabel(location: Location, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            location.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Every swipe has a menu equivalent (DESIGN 6.8), plus one less, one more and Move. */
@Composable
private fun PurchaseMenu(batch: StockBatch, actions: BatchActions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu_${batch.id}")) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.batch_options))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                R.string.action_use_one to actions.onMinus,
                R.string.action_add_one to actions.onPlus,
                R.string.action_used_up to actions.onUsedUp,
                R.string.action_throw_away to actions.onThrowAway,
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
