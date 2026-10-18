package com.parcha.spoilnot.feature.pantry.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.pantry.domain.BatchEdit
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryLimits
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.ShelfLife
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Parses a typed quantity; accepts "1,5" as well as "1.5". Null when not a number. */
fun parseQuantity(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * The Add item sheet (DESIGN 5.2). Choosing an existing product fills unit, location, category and the expiry date
 * from its typical shelf life. The unit is fixed once the product has stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    products: List<Product>,
    productsWithStock: Set<String>,
    today: LocalDate,
    onSave: (StockDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        modifier = sheet.modifier.testTag("sheet_add_item"),
    ) {
        AddItemForm(products, productsWithStock, today, onSave)
    }
}

/** The fields of the Add item sheet, separate from the sheet so they can be tested and previewed on their own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemForm(
    products: List<Product>,
    productsWithStock: Set<String>,
    today: LocalDate,
    onSave: (StockDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(QuantityUnit.Pieces) }
    var expiry by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var dateType by rememberSaveable { mutableStateOf(DateType.UseBy) }
    var location by rememberSaveable { mutableStateOf(Location.Fridge) }
    var category by rememberSaveable { mutableStateOf(Category.Other) }
    var note by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var keepAtLeast by rememberSaveable { mutableStateOf("") }
    var autoAdd by rememberSaveable { mutableStateOf(true) }
    var buyQuantity by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val match = products.firstOrNull { it.nameKey == NameKey.of(name) }
    val unitLocked = match != null && match.id in productsWithStock
    val cleanName = NameKey.clean(name)
    val nameError = when {
        !submitted -> null
        cleanName.isEmpty() -> stringResource(R.string.error_name_required)
        cleanName.length > PantryLimits.MAX_NAME_LENGTH -> stringResource(R.string.error_name_80)
        else -> null
    }
    val qty = parseQuantity(quantity)
    val qtyError = if (submitted && (qty == null || qty <= 0)) stringResource(R.string.error_quantity) else null
    val min = keepAtLeast.takeIf { it.isNotBlank() }?.let(::parseQuantity)
    val minError = if (submitted && keepAtLeast.isNotBlank() &&
        (min == null || min < 0)
    ) {
        stringResource(R.string.error_number)
    } else {
        null
    }
    val buy = buyQuantity.takeIf { it.isNotBlank() }?.let(::parseQuantity)

    fun choose(product: Product) {
        name = product.name
        unit = product.unit
        location = product.defaultLocation
        category = product.category
        ShelfLife.suggestExpiry(product.typicalShelfLifeDays, today)?.let { expiry = it }
    }

    FormSheetColumn(
        stringResource(R.string.add_item_title),
        button = {
            PrimaryButton(
                text = stringResource(R.string.action_add_item),
                onClick = {
                    submitted = true
                    val valid = cleanName.isNotEmpty() && cleanName.length <= PantryLimits.MAX_NAME_LENGTH &&
                        qty != null && qty > 0 && minError == null && !(keepAtLeast.isNotBlank() && min == null)
                    if (valid) {
                        onSave(
                            StockDraft(
                                name = cleanName,
                                quantity = qty!!,
                                unit = if (unitLocked) match!!.unit else unit,
                                expiryDate = expiry,
                                dateType = dateType,
                                location = location,
                                category = category,
                                note = note,
                                minQuantity = min,
                                autoAddToGrocery = autoAdd,
                                buyQuantity = buy,
                            ),
                        )
                    }
                },
                modifier = Modifier.testTag("button_save_item"),
            )
        },
    ) {
        NameField(name, { name = it }, products, nameError, onChoose = ::choose)
        QuantityRow(quantity, { quantity = it }, qtyError)
        EnumDropdown(
            label = stringResource(R.string.field_unit),
            value = if (unitLocked) match!!.unit else unit,
            options = unitMenu(),
            optionLabel = { stringResource(it.label()) },
            onPick = { unit = it },
            enabled = !unitLocked,
            helper = if (unitLocked) stringResource(R.string.unit_locked) else null,
        )
        ExpiryField(expiry, today, onChange = { expiry = it })
        DateTypeChoice(dateType) { dateType = it }
        LocationChoice(location) { location = it }
        EnumDropdown(
            label = stringResource(R.string.field_category),
            value = category,
            options = Category.entries,
            optionLabel = { stringResource(it.label()) },
            onPick = { category = it },
        )
        NoteField(note) { note = it }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            Text(stringResource(R.string.advanced), Modifier.padding(start = 8.dp))
        }
        if (advanced) {
            KeepAtLeastFields(keepAtLeast, { keepAtLeast = it }, minError, autoAdd, { autoAdd = it }, buyQuantity, {
                buyQuantity =
                    it
            })
        }
    }
}

/** Edit one batch: quantity, date, date type, location and note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBatchSheet(batch: StockBatch, today: LocalDate, onSave: (BatchEdit) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberFormSheet()
    var quantity by rememberSaveable { mutableStateOf(formatQuantity(batch.quantity)) }
    var expiry by rememberSaveable { mutableStateOf(batch.expiryDate) }
    var dateType by rememberSaveable { mutableStateOf(batch.dateType) }
    var location by rememberSaveable { mutableStateOf(batch.location) }
    var note by rememberSaveable { mutableStateOf(batch.note.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val qty = parseQuantity(quantity)
    val qtyError = if (submitted && (qty == null || qty < 0)) stringResource(R.string.error_quantity) else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        modifier = sheet.modifier.testTag("sheet_edit_batch"),
    ) {
        FormSheetColumn(
            title = stringResource(R.string.edit_batch_title, batch.productName),
            button = {
                PrimaryButton(stringResource(R.string.action_save), onClick = {
                    submitted = true
                    if (qty != null && qty >= 0) onSave(BatchEdit(qty, expiry, dateType, location, note))
                })
            },
        ) {
            QuantityRow(quantity, { quantity = it }, qtyError)
            ExpiryField(expiry, today, onChange = { expiry = it })
            DateTypeChoice(dateType) { dateType = it }
            LocationChoice(location) { location = it }
            NoteField(note) { note = it }
        }
    }
}

/** Scrollable fields with the save button pinned below them, so saving never needs a scroll. */
@Composable
private fun FormSheetColumn(title: String, button: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { button() }
    }
}

/** Name with autocomplete from existing products, case-insensitive (DESIGN 5.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameField(
    name: String,
    onChange: (String) -> Unit,
    products: List<Product>,
    error: String?,
    onChoose: (Product) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val key = NameKey.of(name)
    val suggestions = if (key.isEmpty()) {
        emptyList()
    } else {
        products.filter {
            it.nameKey.contains(key) && it.nameKey != key
        }.sortedBy { !it.nameKey.startsWith(key) }.take(5)
    }
    ExposedDropdownMenuBox(expanded = open && suggestions.isNotEmpty(), onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                onChange(it)
                open = true
            },
            label = { Text(stringResource(R.string.field_name)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryEditable,
            ).testTag("field_name"),
        )
        ExposedDropdownMenu(expanded = open && suggestions.isNotEmpty(), onDismissRequest = { open = false }) {
            suggestions.forEach { product ->
                DropdownMenuItem(
                    text = { Text(product.name) },
                    onClick = {
                        onChoose(product)
                        open = false
                    },
                )
            }
        }
    }
}

/** Stepper plus keyboard; decimals allowed (DESIGN 5.2). */
@Composable
private fun QuantityRow(value: String, onChange: (String) -> Unit, error: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { parseQuantity(value)?.let { onChange(formatQuantity((it - 1).coerceAtLeast(0.0))) } }) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.action_minus_one))
        }
        FormTextField(
            value = value,
            onValueChange = onChange,
            label = stringResource(R.string.field_quantity),
            error = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag("field_quantity"),
        )
        IconButton(onClick = { onChange(formatQuantity((parseQuantity(value) ?: 0.0) + 1)) }) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_plus_one))
        }
    }
}

/** Date picker plus quick chips (DESIGN 5.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryField(value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_expiry), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field_expiry"),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            Text(
                value?.let { "${formatDate(it, today)} · ${statusText(it, DateType.UseBy, today)}" }
                    ?: stringResource(R.string.status_no_date),
                Modifier.padding(start = 8.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.chip_plus_3_days to today.plusDays(3),
                R.string.chip_plus_1_week to today.plusWeeks(1),
                R.string.chip_plus_2_weeks to today.plusWeeks(2),
                R.string.chip_plus_1_month to today.plusMonths(1),
                R.string.chip_no_expiry to null,
            ).forEach { (label, date) ->
                FilterChip(selected = value == date, onClick = {
                    onChange(date)
                }, label = { Text(stringResource(label)) })
            }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit) {
    FormTextField(
        value = value,
        onValueChange = { onChange(it.take(PantryLimits.MAX_NOTE_LENGTH)) },
        label = stringResource(R.string.field_notes),
        helper = "${value.length} / ${PantryLimits.MAX_NOTE_LENGTH}",
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
    )
}

@Composable
fun KeepAtLeastFields(
    keepAtLeast: String,
    onKeepAtLeast: (String) -> Unit,
    error: String?,
    autoAdd: Boolean,
    onAutoAdd: (Boolean) -> Unit,
    buyQuantity: String,
    onBuyQuantity: (String) -> Unit,
) {
    val set = keepAtLeast.isNotBlank()
    FormTextField(
        value = keepAtLeast,
        onValueChange = onKeepAtLeast,
        label = stringResource(R.string.field_keep_at_least),
        helper = stringResource(R.string.keep_at_least_help),
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
    // The whole row toggles, so the label is a touch target too (DESIGN 6.8).
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = set && autoAdd, enabled = set, role = Role.Switch, onValueChange = onAutoAdd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.field_auto_add), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = set && autoAdd, onCheckedChange = null, enabled = set)
    }
    FormTextField(
        value = buyQuantity,
        onValueChange = onBuyQuantity,
        label = stringResource(R.string.field_buy_quantity),
        enabled = set,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
    )
}

@Composable
private fun DateTypeChoice(selected: DateType, onPick: (DateType) -> Unit) =
    Segmented(stringResource(R.string.field_date_type), DateType.entries, selected, {
        stringResource(it.label())
    }, onPick)

/** Location as wrapping chips: four labels do not fit a segmented row at large font sizes. */
@Composable
private fun LocationChoice(selected: Location, onPick: (Location) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_location), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            Location.entries.forEach { location ->
                FilterChip(
                    selected = location == selected,
                    onClick = { onPick(location) },
                    label = { Text(stringResource(location.label())) },
                    leadingIcon = { Icon(location.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
    }
}

/** A single-choice segmented control (DESIGN 6.5). */
@Composable
fun <T> Segmented(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(optionLabel(option), maxLines = 1) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    enabled: Boolean = true,
    helper: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (enabled) open = it }) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            supportingText = helper?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = {
                    onPick(option)
                    open = false
                })
            }
        }
    }
}
