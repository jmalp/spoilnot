package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.StatusTile
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.STATUS_TINT_ALPHA
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.designsystem.theme.StatusTone
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
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
    /** Mark opened (after v1, DESIGN 5.2); offered only while the batch is sealed. */
    val onOpened: (StockBatch) -> Unit = {},
)

/**
 * One purchase (beta feedback) as a row of its group (DESIGN 19.5, 19.6): the status tile, the name, the status in
 * words and the location, the amount. Tap to edit; swipe right to use up, left to throw away; the menu has the same
 * actions plus one less, one more and Move. An Out staple's row adds stock. [position] gives its corners.
 */
@Composable
fun PantryItemRow(
    item: PantryItem,
    today: LocalDate,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
    modifier: Modifier = Modifier,
    position: SegmentPosition = SegmentPosition.Only,
) {
    when (item) {
        is PantryItem.Purchase -> PurchaseRow(item, today, actions, onOpen, modifier, position)
        is PantryItem.Out -> OutRow(item, onOpen, modifier, position)
    }
}

@Composable
private fun PurchaseRow(
    item: PantryItem.Purchase,
    today: LocalDate,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
    modifier: Modifier,
    position: SegmentPosition,
) {
    val batch = item.batch
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(batch.id, batch.quantity) { swipe.reset() }
    val amount = rowAmount(batch.quantity, batch.unit)
    val location = stringResource(batch.location.label())
    val status = ExpiryStatus.of(batch.expiryDate, today)
    val opened = if (batch.openedOn != null) stringResource(R.string.row_opened_suffix) else ""
    val description = stringResource(
        R.string.row_description,
        item.name,
        amountDescription(batch.quantity, batch.unit),
        statusText(batch.expiryDate, batch.dateType, today),
        location,
    ) + opened
    val shape = segmentShape(position)
    // From 1.5× text the amount joins the second line, so the name keeps the row's width (review A11Y-04).
    val large = isLargeText()
    val secondLine = secondLine(
        status = chipText(batch.expiryDate, batch.dateType, today),
        tone = status.tone(),
        rest = listOfNotNull(
            location,
            stringResource(R.string.pantry_opened).takeIf { batch.openedOn != null },
            amount.takeIf { large },
        ),
    )

    Box(modifier.fillMaxWidth().testTag("pantry_item_${batch.id}")) {
        SwipeToDismissBox(
            state = swipe,
            backgroundContent = { SwipeBackground(swipe.dismissDirection, shape) },
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
            // The row's surface is opaque, so the swipe background only shows where the row has moved away. The
            // sentence names the node TalkBack activates, as on Out rows; ⋮ stays its own stop.
            ListSegment(
                position = position,
                onClick = { onOpen(item) },
                onClickLabel = stringResource(R.string.action_edit),
                modifier = Modifier.semantics { contentDescription = description },
                contentPadding = PaddingValues(start = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                StatusTile(status.tone(), status.icon())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NameLine(item.name, low = item.isLow, pending = batch.pendingSync)
                    Text(
                        secondLine,
                        style = SpoilNotText.RowSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!large) amount?.let { Text(it, style = AmountStyle) }
                PurchaseMenu(batch, actions)
            }
        }
    }
}

/** A staple with nothing left (Keep at least set): "Out" in the Expired color, words and tile (DESIGN 19.6). */
@Composable
private fun OutRow(item: PantryItem.Out, onOpen: (PantryItem) -> Unit, modifier: Modifier, position: SegmentPosition) {
    val product = item.product
    val description = stringResource(R.string.row_description_out, product.name)
    val secondLine = secondLine(
        status = stringResource(R.string.badge_out),
        tone = StatusTone.Expired,
        rest = listOf(stringResource(product.defaultLocation.label())),
    )
    ListSegment(
        position = position,
        onClick = { onOpen(item) },
        onClickLabel = stringResource(R.string.action_add_stock),
        modifier = modifier.semantics { contentDescription = description }.testTag("pantry_item_out_${product.id}"),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        StatusTile(StatusTone.Expired, Icons.Rounded.RemoveCircleOutline)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NameLine(product.name, low = false, pending = false)
            Text(secondLine, style = SpoilNotText.RowSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "Tomorrow · Fridge · Opened": the status in its color and heavier, then the rest (DESIGN 19.6). At large text it
 * wraps only after a "·", never inside a part ("× 2", "500 g").
 */
@Composable
private fun secondLine(status: String, tone: StatusTone, rest: List<String>): AnnotatedString {
    // No date isn't a status to notice: it reads like the rest of the line.
    val color = if (tone == StatusTone.NoDate) null else SpoilNotStatus.colors.forTone(tone)
    return buildAnnotatedString {
        if (color == null) {
            append(status)
        } else {
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.ExtraBold)) { append(status) }
        }
        rest.forEach {
            append(NO_BREAK_SPACE)
            append("· ")
            append(it.replace(' ', NO_BREAK_SPACE))
        }
    }
}

private const val NO_BREAK_SPACE = ' '

/** "× 2" or "500 g" at the end of a row. */
private val AmountStyle = SpoilNotText.RowTitle.copy(fontWeight = FontWeight.ExtraBold)

/** The name with its Low badge and the pending icon beside it, or under it from 1.5× text (review A11Y-04). */
@Composable
private fun NameLine(name: String, low: Boolean, pending: Boolean) {
    val marks = @Composable {
        if (low) Badge(stringResource(R.string.badge_low))
        if (pending) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = stringResource(R.string.pending_sync),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val nameText = @Composable { modifier: Modifier ->
        Text(name, style = SpoilNotText.RowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
    if (isLargeText() && low) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nameText(Modifier)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                marks()
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nameText(Modifier.weight(1f, fill = false))
            marks()
        }
    }
}

/**
 * "65 expired items" with Show or Hide (after v1): the expired backlog folded into one row of its group, the first
 * of Use first in the default view (DESIGN 19.12).
 */
@Composable
internal fun ExpiredFoldRow(
    count: Int,
    shown: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    position: SegmentPosition = SegmentPosition.Only,
) {
    val large = isLargeText()
    val toggle = @Composable {
        TextButton(onClick = onToggle, modifier = Modifier.testTag("pantry_expired_toggle")) {
            Text(
                stringResource(if (shown) R.string.pantry_expired_hide else R.string.pantry_expired_show),
                style = SpoilNotText.ButtonMedium,
            )
        }
    }
    ListSegment(
        position = position,
        modifier = modifier.testTag("pantry_expired_header"),
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        StatusTile(StatusTone.Expired, Icons.Rounded.DeleteOutline)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(R.plurals.pantry_expired_count, count, count), style = SpoilNotText.RowTitle)
            if (large) Box(Modifier.align(Alignment.End)) { toggle() }
        }
        if (!large) toggle()
    }
}

/** Every swipe has a menu equivalent (DESIGN 6.8), plus one less, one more and Move. */
@Composable
private fun PurchaseMenu(batch: StockBatch, actions: BatchActions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu_${batch.id}")) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.batch_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOfNotNull(
                R.string.action_use_one to actions.onMinus,
                R.string.action_add_one to actions.onPlus,
                (R.string.action_mark_opened to actions.onOpened).takeIf { batch.openedOn == null },
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

/** The swipe's tint, icon and words, in the row's [shape] (DESIGN 19.6): right is Used up, left is Throw away. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, shape: Shape) {
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
        Modifier.fillMaxSize().background(color.copy(alpha = STATUS_TINT_ALPHA), shape).padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Text(
                stringResource(label),
                color = color,
                style = SpoilNotText.ButtonMedium,
                fontWeight = FontWeight.ExtraBold,
            )
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

/**
 * A dialog that asks for one choice (DESIGN 19.11): its title in [SpoilNotText.DialogTitle] and the choices as a group
 * of rows, each a radio button that picks and closes; Cancel at the end.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val rowColor = SpoilNotSurfaces.current.dialogRow
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = SpoilNotText.DialogTitle) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                options.forEachIndexed { index, option ->
                    val shape = segmentShape(segmentPosition(index, options.size), outer = SpoilNotCorners.NestedOuter)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(rowColor)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                                onPick(option)
                                onDismiss()
                            })
                            .heightIn(min = ListSegmentMinHeightOneLine)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}
