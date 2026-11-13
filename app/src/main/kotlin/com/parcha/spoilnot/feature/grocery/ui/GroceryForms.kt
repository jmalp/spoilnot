package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.grocery.domain.AddToPantryRules
import com.parcha.spoilnot.feature.grocery.domain.EntryDefaults
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.grocery.domain.RestockReason
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.ui.DateChips
import com.parcha.spoilnot.feature.pantry.ui.EnumDropdown
import com.parcha.spoilnot.feature.pantry.ui.LocationChoice
import com.parcha.spoilnot.feature.pantry.ui.amountText
import com.parcha.spoilnot.feature.pantry.ui.formatDate
import com.parcha.spoilnot.feature.pantry.ui.formatQuantity
import com.parcha.spoilnot.feature.pantry.ui.icon
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.pantry.ui.parseQuantity
import com.parcha.spoilnot.feature.pantry.ui.unitMenu
import java.time.LocalDate

/** One line of the Add to pantry sheet: its item, where it started and what it holds now. */
private class EntryLine(val item: GroceryItem, val defaults: EntryDefaults, edit: EntryEdit?, val expanded: Boolean) {
    val value: EntryEdit = (
        edit ?: EntryEdit(
            quantity = defaults.quantity?.let(::formatQuantity).orEmpty(),
            unit = defaults.unit,
            expiry = defaults.expiry,
            location = defaults.location,
            skip = false,
        )
        // A product with stock keeps its unit (DESIGN 2; after v1, LOGIC-04), also when it got stock meanwhile.
        ).let { if (defaults.unitLocked) it.copy(unit = defaults.unit) else it }

    val valid: Boolean get() = value.skip || parseQuantity(value.quantity)?.let { it > 0 } == true
}

/**
 * Done shopping (DESIGN 5.3): one line per checked item summing up what will be added (amount, location, date
 * pre-filled from the product's typical shelf life). Tapping a line opens its editor: quantity, unit, expiry quick
 * chips, location and Skip, which removes the item without adding stock. Add all adds them in one write. What the
 * person sets is kept in [drafts] by item id (after v1, REL-08).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPantrySheet(
    items: List<GroceryItem>,
    pantry: PantryData,
    today: LocalDate,
    drafts: AddToPantryDrafts,
    onConfirm: (entries: List<PantryEntry>, skipped: List<GroceryItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_add_to_pantry"),
    ) {
        AddToPantryContent(items, pantry, today, drafts, onConfirm)
    }
}

/** The sheet's lines and Add all button, without the sheet (tested on the JVM). */
@Composable
internal fun AddToPantryContent(
    items: List<GroceryItem>,
    pantry: PantryData,
    today: LocalDate,
    drafts: AddToPantryDrafts = remember { AddToPantryDrafts() },
    onConfirm: (entries: List<PantryEntry>, skipped: List<GroceryItem>) -> Unit,
) {
    // The current items with the edits kept for them: a check-off elsewhere adds a line and keeps the others.
    val lines = items.map { item ->
        EntryLine(
            item,
            AddToPantryRules.defaults(item, pantry, today),
            drafts.edit(item.id),
            drafts.isExpanded(item.id),
        )
    }
    val valid = lines.all { it.valid }
    val count = lines.count { !it.value.skip }

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.grocery_add_to_pantry_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Column {
                lines.forEach { line ->
                    EntryRow(
                        line,
                        today,
                        onChange = { drafts.set(line.item.id, it) },
                        onToggle = { drafts.setExpanded(line.item.id, !line.expanded) },
                    )
                    HorizontalDivider()
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            PrimaryButton(
                text = if (count == 0) {
                    stringResource(R.string.grocery_remove_skipped)
                } else if (count == lines.size && count > 1) {
                    pluralStringResource(R.plurals.grocery_add_all_to_pantry, count, count)
                } else {
                    pluralStringResource(R.plurals.grocery_add_to_pantry, count, count)
                },
                onClick = {
                    if (!valid) {
                        // Show what needs fixing.
                        lines.filter { !it.valid }.forEach { drafts.setExpanded(it.item.id, true) }
                        return@PrimaryButton
                    }
                    val entries = lines.filter { !it.value.skip }.map {
                        PantryEntry(
                            it.item,
                            parseQuantity(it.value.quantity)!!,
                            it.value.unit,
                            it.value.expiry,
                            it.value.location,
                        )
                    }
                    onConfirm(entries, lines.filter { it.value.skip }.map { it.item })
                },
                modifier = Modifier.testTag("button_confirm_add_to_pantry"),
            )
        }
    }
}

/** "2 · Fridge · Oct 26", "Skipped" or the problem, for one line of the sheet. */
@Composable
private fun entrySummary(line: EntryLine, today: LocalDate): String {
    val value = line.value
    if (value.skip) return stringResource(R.string.grocery_entry_skipped)
    val quantity = parseQuantity(value.quantity)?.takeIf { it > 0 } ?: return stringResource(R.string.error_quantity)
    val date = value.expiry?.let { formatDate(it, today) } ?: stringResource(R.string.status_no_date)
    return stringResource(
        R.string.grocery_entry_summary,
        amountText(quantity, value.unit),
        stringResource(value.location.label()),
        date,
    )
}

@Composable
private fun EntryRow(line: EntryLine, today: LocalDate, onChange: (EntryEdit) -> Unit, onToggle: () -> Unit) {
    val summary = entrySummary(line, today)
    Column(Modifier.fillMaxWidth().testTag("entry_${line.item.id}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(
                        if (line.expanded) R.string.grocery_entry_done else R.string.grocery_entry_edit,
                    ),
                    onClick = onToggle,
                )
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Icon(
                if (line.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
            )
        }
        if (line.expanded) EntryEditor(line, today, onChange)
    }
}

@Composable
private fun EntryEditor(line: EntryLine, today: LocalDate, onChange: (EntryEdit) -> Unit) {
    val value = line.value
    val id = line.item.id
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.grocery_skip_help),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.grocery_skip), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = value.skip,
                onCheckedChange = { onChange(value.copy(skip = it)) },
                modifier = Modifier.padding(start = 8.dp).testTag("entry_skip_$id"),
            )
        }
        if (!value.skip) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FormTextField(
                    value = value.quantity,
                    onValueChange = { onChange(value.copy(quantity = it)) },
                    label = stringResource(R.string.field_quantity),
                    error = if (parseQuantity(value.quantity)?.let { it > 0 } != true) {
                        stringResource(R.string.error_quantity)
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.width(140.dp).testTag("entry_quantity_$id"),
                )
                Column(Modifier.weight(1f)) {
                    EnumDropdown(
                        label = stringResource(R.string.field_unit),
                        value = value.unit,
                        options = unitMenu(),
                        optionLabel = { stringResource(it.label()) },
                        onPick = { onChange(value.copy(unit = it)) },
                        enabled = !line.defaults.unitLocked,
                        helper = if (line.defaults.unitLocked) stringResource(R.string.unit_locked_short) else null,
                    )
                }
            }
            Text(
                value.expiry?.let { formatDate(it, today) } ?: stringResource(R.string.status_no_date),
                style = MaterialTheme.typography.labelLarge,
            )
            DateChips(value.expiry, today) { onChange(value.copy(expiry = it)) }
            LocationChoice(value.location) { onChange(value.copy(location = it)) }
        }
    }
}

/** Name (after v1), quantity, unit, note and category (aisle) of one item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemSheet(
    item: GroceryItem,
    onSave: (name: String, quantity: Double?, unit: QuantityUnit?, note: String?, category: Category?) -> Unit,
    onDismiss: () -> Unit,
    reason: RestockReason? = null,
) {
    val sheet = rememberFormSheet()
    var name by rememberSaveable { mutableStateOf(item.name) }
    var quantity by rememberSaveable { mutableStateOf(item.quantity?.let(::formatQuantity).orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(item.unit ?: QuantityUnit.Pieces) }
    var note by rememberSaveable { mutableStateOf(item.note.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(item.category ?: Category.Other) }
    val qty = quantity.takeIf { it.isNotBlank() }?.let(::parseQuantity)
    val qtyError = if (quantity.isNotBlank() &&
        (qty == null || qty <= 0)
    ) {
        stringResource(R.string.error_quantity)
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        modifier = sheet.modifier,
        contentWindowInsets = sheet.contentWindowInsets,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.grocery_edit_item, item.name), style = MaterialTheme.typography.headlineSmall)
            // Why the app put it here (after v1, UX-08): the row's badge says only "Added automatically".
            reason?.let {
                Text(
                    reasonText(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("grocery_auto_reason"),
                )
            }
            FormTextField(
                value = name,
                onValueChange = { name = it.take(GroceryRules.MAX_NAME_LENGTH) },
                label = stringResource(R.string.field_name),
                error = if (name.isBlank()) stringResource(R.string.error_name_required) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FormTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = stringResource(R.string.field_quantity),
                error = qtyError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            EnumDropdown(stringResource(R.string.field_unit), unit, unitMenu(), {
                stringResource(it.label())
            }, {
                unit =
                    it
            })
            EnumDropdown(stringResource(R.string.field_category), category, Category.entries, {
                stringResource(it.label())
            }, {
                category =
                    it
            })
            FormTextField(value = note, onValueChange = {
                note = it.take(200)
            }, label = stringResource(R.string.field_notes))
            PrimaryButton(stringResource(R.string.action_save), onClick = {
                if (qtyError == null &&
                    name.isNotBlank()
                ) {
                    onSave(name, qty, if (qty != null) unit else null, note, category)
                }
            }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

/** "Added automatically: you have 0 and keep at least 2." */
@Composable
private fun reasonText(reason: RestockReason): String = when (reason) {
    is RestockReason.Low -> stringResource(
        R.string.grocery_auto_reason,
        amountText(reason.have, reason.unit),
        amountText(reason.keepAtLeast, reason.unit),
    )

    RestockReason.ProductGone -> stringResource(R.string.grocery_auto_reason_gone)
}
