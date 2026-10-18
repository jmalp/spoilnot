package com.parcha.spoilnot.feature.grocery.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.ShelfLife
import com.parcha.spoilnot.feature.pantry.ui.EnumDropdown
import com.parcha.spoilnot.feature.pantry.ui.formatDate
import com.parcha.spoilnot.feature.pantry.ui.formatQuantity
import com.parcha.spoilnot.feature.pantry.ui.icon
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.pantry.ui.parseQuantity
import com.parcha.spoilnot.feature.pantry.ui.unitMenu
import java.time.LocalDate

/** One editable row of the Add to pantry sheet. */
private class EntryDraft(
    val item: GroceryItem,
    quantity: String,
    unit: QuantityUnit,
    expiry: LocalDate?,
    location: Location,
) {
    var quantity by mutableStateOf(quantity)
    var unit by mutableStateOf(unit)
    var expiry by mutableStateOf(expiry)
    var location by mutableStateOf(location)
    var skip by mutableStateOf(false)
}

/**
 * Done shopping (DESIGN 5.3): one row per checked item with quantity, unit, expiry quick chips (pre-filled from the
 * product's typical shelf life) and location. Confirm adds them all in one write; Skip removes an item without
 * adding stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPantrySheet(
    items: List<GroceryItem>,
    pantry: PantryData,
    today: LocalDate,
    onConfirm: (entries: List<PantryEntry>, skipped: List<GroceryItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    val drafts = remember(items) {
        mutableStateListOf(
            *items.map { item ->
                val product = pantry.products.firstOrNull { it.id == item.productId }
                    ?: pantry.products.firstOrNull { it.nameKey == NameKey.of(item.name) }
                EntryDraft(
                    item = item,
                    quantity = formatQuantity(item.quantity ?: 1.0),
                    unit = item.unit ?: product?.unit ?: QuantityUnit.Pieces,
                    expiry = ShelfLife.suggestExpiry(product?.typicalShelfLifeDays, today),
                    location = product?.defaultLocation ?: Location.Fridge,
                )
            }.toTypedArray(),
        )
    }
    val valid = drafts.all { it.skip || (parseQuantity(it.quantity)?.let { q -> q > 0 } == true) }
    val count = drafts.count { !it.skip }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        modifier = sheet.modifier.testTag("sheet_add_to_pantry"),
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.grocery_add_to_pantry_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                drafts.forEach { draft -> EntryCard(draft, today) }
            }
            Column(Modifier.padding(16.dp)) {
                PrimaryButton(
                    text = pluralStringResource(R.plurals.grocery_add_to_pantry, count, count),
                    onClick = {
                        if (!valid) return@PrimaryButton
                        val entries = drafts.filter { !it.skip }.map {
                            PantryEntry(it.item, parseQuantity(it.quantity)!!, it.unit, it.expiry, it.location)
                        }
                        onConfirm(entries, drafts.filter { it.skip }.map { it.item })
                    },
                    modifier = Modifier.testTag("button_confirm_add_to_pantry"),
                )
            }
        }
    }
}

@Composable
private fun EntryCard(draft: EntryDraft, today: LocalDate) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(draft.item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.grocery_skip), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = draft.skip, onCheckedChange = {
                    draft.skip = it
                }, modifier = Modifier.padding(start = 8.dp))
            }
            if (!draft.skip) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormTextField(
                        value = draft.quantity,
                        onValueChange = { draft.quantity = it },
                        label = stringResource(R.string.field_quantity),
                        error = if (parseQuantity(draft.quantity)?.let { it > 0 } !=
                            true
                        ) {
                            stringResource(R.string.error_quantity)
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.width(140.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        EnumDropdown(stringResource(R.string.field_unit), draft.unit, unitMenu(), {
                            stringResource(it.label())
                        }, {
                            draft.unit =
                                it
                        })
                    }
                }
                Text(
                    draft.expiry?.let { formatDate(it, today) } ?: stringResource(R.string.status_no_date),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        R.string.chip_plus_3_days to today.plusDays(3),
                        R.string.chip_plus_1_week to today.plusWeeks(1),
                        R.string.chip_plus_2_weeks to today.plusWeeks(2),
                        R.string.chip_plus_1_month to today.plusMonths(1),
                        R.string.chip_no_expiry to null,
                    ).forEach { (label, date) ->
                        FilterChip(selected = draft.expiry == date, onClick = {
                            draft.expiry = date
                        }, label = { Text(stringResource(label)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Location.entries.forEach { location ->
                        FilterChip(
                            selected = draft.location == location,
                            onClick = { draft.location = location },
                            label = { Text(stringResource(location.label())) },
                            leadingIcon = {
                                Icon(location.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Quantity, unit, note and category (aisle) of one item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemSheet(
    item: GroceryItem,
    onSave: (quantity: Double?, unit: QuantityUnit?, note: String?, category: Category?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet.state, modifier = sheet.modifier) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.grocery_edit_item, item.name), style = MaterialTheme.typography.headlineSmall)
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
                if (qtyError == null) onSave(qty, if (qty != null) unit else null, note, category)
            }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
