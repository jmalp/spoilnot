package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SEGMENTED_STACK_FONT_SCALE
import com.parcha.spoilnot.core.designsystem.component.Segmented
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.pantry.domain.BatchEdit
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.ItemEdit
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
 * The Add item sheet (DESIGN 5.2, reworked from beta feedback). Choosing an existing product fills unit, category,
 * location, the expiry date from its typical shelf life, and its grocery settings. [prefill] opens it for a product,
 * for example an Out staple.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    products: List<Product>,
    productsWithStock: Set<String>,
    today: LocalDate,
    onSave: (StockDraft) -> Unit,
    onDismiss: () -> Unit,
    prefill: Product? = null,
) {
    val sheet = rememberFormSheet()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_add_item"),
    ) {
        AddItemForm(products, productsWithStock, today, prefill, onSave)
    }
}

/** What the Add and Edit windows hold while typing. Saveable, so rotation keeps it. */
private class ItemFormState(
    name: String,
    quantity: String,
    unit: QuantityUnit,
    category: Category,
    expiry: LocalDate?,
    location: Location,
    autoAdd: Boolean,
    keepAtLeast: String,
    buyQuantity: String,
) {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
    var unit by mutableStateOf(unit)
    var category by mutableStateOf(category)
    var expiry by mutableStateOf(expiry)
    var location by mutableStateOf(location)
    var autoAdd by mutableStateOf(autoAdd)
    var keepAtLeast by mutableStateOf(keepAtLeast)
    var buyQuantity by mutableStateOf(buyQuantity)
    var submitted by mutableStateOf(false)

    fun fillFrom(product: Product, today: LocalDate) {
        name = product.name
        unit = product.unit
        location = product.defaultLocation
        category = product.category
        ShelfLife.suggestExpiry(product.typicalShelfLifeDays, today)?.let { expiry = it }
        autoAdd = product.autoAddToGrocery
        keepAtLeast = product.minQuantity?.let(::formatQuantity).orEmpty()
        buyQuantity = product.buyQuantity?.let(::formatQuantity).orEmpty()
    }

    val cleanName get() = NameKey.clean(name)
    val qty get() = parseQuantity(quantity)
    val min get() = keepAtLeast.takeIf { it.isNotBlank() }?.let(::parseQuantity)
    val buy get() = buyQuantity.takeIf { it.isNotBlank() }?.let(::parseQuantity)

    fun nameOk() = cleanName.isNotEmpty() && cleanName.length <= PantryLimits.MAX_NAME_LENGTH

    /** With auto-add on, Keep at least is required and above 0. */
    fun minOk() = !autoAdd || (min?.let { it > 0 } == true)

    fun buyOk() = !autoAdd || buyQuantity.isBlank() || (buy?.let { it > 0 } == true)

    companion object {
        val Saver = listSaver<ItemFormState, Any?>(
            save = {
                listOf(
                    it.name, it.quantity, it.unit.name, it.category.name, it.expiry?.toString(), it.location.name,
                    it.autoAdd, it.keepAtLeast, it.buyQuantity, it.submitted,
                )
            },
            restore = { v ->
                ItemFormState(
                    v[0] as String, v[1] as String, QuantityUnit.valueOf(v[2] as String),
                    Category.valueOf(v[3] as String), (v[4] as String?)?.let(LocalDate::parse),
                    Location.valueOf(v[5] as String), v[6] as Boolean, v[7] as String, v[8] as String,
                ).also { it.submitted = v[9] as Boolean }
            },
        )
    }
}

/** The fields of the Add item sheet, separate from the sheet so they can be tested and previewed on their own. */
@Composable
fun AddItemForm(
    products: List<Product>,
    productsWithStock: Set<String>,
    today: LocalDate,
    prefill: Product? = null,
    onSave: (StockDraft) -> Unit,
) {
    val form = rememberSaveable(saver = ItemFormState.Saver) {
        ItemFormState("", "1", QuantityUnit.Pieces, Category.Other, null, Location.Fridge, false, "", "")
            .also { state -> prefill?.let { state.fillFrom(it, today) } }
    }
    val match = products.firstOrNull { it.nameKey == NameKey.of(form.name) }
    val unitLocked = match != null && match.id in productsWithStock
    FormSheetColumn(
        stringResource(R.string.add_item_title),
        button = {
            PrimaryButton(
                text = stringResource(R.string.action_add_item),
                onClick = {
                    form.submitted = true
                    val qty = form.qty
                    if (form.nameOk() && qty != null && qty > 0 && form.minOk() && form.buyOk()) {
                        onSave(
                            StockDraft(
                                name = form.cleanName,
                                quantity = qty,
                                unit = if (unitLocked) match!!.unit else form.unit,
                                expiryDate = form.expiry,
                                // Date type and notes left the form (beta feedback): new items are "Use by".
                                dateType = DateType.UseBy,
                                location = form.location,
                                category = form.category,
                                note = null,
                                minQuantity = form.min.takeIf { form.autoAdd },
                                autoAddToGrocery = form.autoAdd,
                                buyQuantity = form.buy.takeIf { form.autoAdd },
                            ),
                        )
                    }
                },
                modifier = Modifier.testTag("button_save_item"),
            )
        },
    ) {
        ItemFields(
            form = form,
            products = products,
            today = today,
            unitLockedTo = if (unitLocked) match!!.unit else null,
            onChooseProduct = { form.fillFrom(it, today) },
            allowZero = false,
        )
    }
}

/**
 * The Edit window, the same form as Add: the purchase's quantity, date and location, and its product's name, unit,
 * category and grocery settings. The unit can change only when this is the product's only purchase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemSheet(
    batch: StockBatch,
    product: Product?,
    canChangeUnit: Boolean,
    today: LocalDate,
    onSave: (ItemEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    val form = rememberSaveable(saver = ItemFormState.Saver) {
        ItemFormState(
            name = product?.name ?: batch.productName,
            quantity = formatQuantity(batch.quantity),
            unit = product?.unit ?: batch.unit,
            category = product?.category ?: batch.category,
            expiry = batch.expiryDate,
            location = batch.location,
            autoAdd = product?.autoAddToGrocery == true,
            keepAtLeast = product?.minQuantity?.let(::formatQuantity).orEmpty(),
            buyQuantity = product?.buyQuantity?.let(::formatQuantity).orEmpty(),
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_edit_item"),
    ) {
        FormSheetColumn(
            title = stringResource(R.string.edit_item_title),
            button = {
                PrimaryButton(
                    stringResource(R.string.action_save),
                    onClick = {
                        form.submitted = true
                        val qty = form.qty
                        if (form.nameOk() && qty != null && qty >= 0 && form.minOk() && form.buyOk()) {
                            onSave(
                                ItemEdit(
                                    name = form.cleanName,
                                    quantity = qty,
                                    unit = form.unit,
                                    category = form.category,
                                    expiryDate = form.expiry,
                                    location = form.location,
                                    autoAddToGrocery = form.autoAdd,
                                    minQuantity = form.min.takeIf { form.autoAdd },
                                    buyQuantity = form.buy.takeIf { form.autoAdd },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.testTag("button_save_item"),
                )
            },
        ) {
            ItemFields(
                form = form,
                products = emptyList(),
                today = today,
                unitLockedTo = if (canChangeUnit) null else (product?.unit ?: batch.unit),
                onChooseProduct = {},
                allowZero = true,
            )
        }
    }
}

/** Name, quantity, unit and category, date, location, and Add to grocery list automatically (beta feedback). */
@Composable
private fun ItemFields(
    form: ItemFormState,
    products: List<Product>,
    today: LocalDate,
    unitLockedTo: QuantityUnit?,
    onChooseProduct: (Product) -> Unit,
    allowZero: Boolean,
) {
    val nameError = when {
        !form.submitted -> null
        form.cleanName.isEmpty() -> stringResource(R.string.error_name_required)
        form.cleanName.length > PantryLimits.MAX_NAME_LENGTH -> stringResource(R.string.error_name_80)
        else -> null
    }
    val qty = form.qty
    val qtyBad = qty == null || if (allowZero) qty < 0 else qty <= 0
    val qtyError = if (form.submitted && qtyBad) stringResource(R.string.error_quantity) else null
    NameField(form.name, { form.name = it }, products, nameError, onChoose = onChooseProduct)
    // A compact stepper next to the unit (after v1): the amount needs a few digits, not the full width.
    val unitField = @Composable { modifier: Modifier ->
        Box(modifier) {
            EnumDropdown(
                label = stringResource(R.string.field_unit),
                value = unitLockedTo ?: form.unit,
                options = unitMenu(),
                optionLabel = { stringResource(it.label()) },
                onPick = { form.unit = it },
                enabled = unitLockedTo == null,
                helper = if (unitLockedTo != null) stringResource(R.string.unit_locked_short) else null,
            )
        }
    }
    if (LocalDensity.current.fontScale >= SEGMENTED_STACK_FONT_SCALE) {
        QuantityRow(form.quantity, { form.quantity = it }, qtyError, compact = false)
        unitField(Modifier.fillMaxWidth())
    } else {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuantityRow(form.quantity, { form.quantity = it }, qtyError)
            unitField(Modifier.weight(1f))
        }
    }
    ExpiryField(form.expiry, today, onChange = { form.expiry = it })
    LocationChoice(form.location) { form.location = it }
    // Usually filled in from the product or the name, so it comes after what changes every time.
    EnumDropdown(
        label = stringResource(R.string.field_category),
        value = form.category,
        options = Category.entries,
        optionLabel = { stringResource(it.label()) },
        onPick = { form.category = it },
    )
    AutoAddFields(form)
}

/** The switch, then Keep at least and Buy quantity while it is on. */
@Composable
private fun AutoAddFields(form: ItemFormState) {
    // The whole row toggles, so the label is a touch target too (DESIGN 6.8).
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = form.autoAdd, role = Role.Switch, onValueChange = { form.autoAdd = it })
            .testTag("switch_auto_add"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.field_auto_add), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = form.autoAdd, onCheckedChange = null)
    }
    AnimatedVisibility(form.autoAdd) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(
                value = form.keepAtLeast,
                onValueChange = { form.keepAtLeast = it },
                label = stringResource(R.string.field_keep_at_least),
                helper = stringResource(R.string.keep_at_least_help),
                error = if (form.submitted && !form.minOk()) stringResource(R.string.error_keep_at_least) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.testTag("field_keep_at_least"),
            )
            FormTextField(
                value = form.buyQuantity,
                onValueChange = { form.buyQuantity = it },
                label = stringResource(R.string.field_buy_quantity),
                helper = stringResource(R.string.optional),
                error = if (form.submitted && !form.buyOk()) stringResource(R.string.error_number) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
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
private fun QuantityRow(value: String, onChange: (String) -> Unit, error: String?, compact: Boolean = true) {
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
            // Compact beside the unit; the full width when stacked at large text sizes.
            modifier = (if (compact) Modifier.width(96.dp) else Modifier.weight(1f)).testTag("field_quantity"),
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
                // Far-off dates read as the date itself; show it once ("Nov 2", not "Nov 2 · Nov 2").
                value?.let {
                    val date = formatDate(it, today)
                    val status = statusText(it, DateType.UseBy, today)
                    if (status == date) date else "$date · $status"
                }
                    ?: stringResource(R.string.status_no_date),
                Modifier.padding(start = 8.dp),
            )
        }
        DateChips(value, today, onChange)
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

/**
 * The quick date chips, shared by Add item and Add to pantry: four equal chips in one row (two rows at large text
 * sizes). Tapping the chosen one again clears the date; there is no "No expiry" chip because no date is the default
 * and the date button already says so (beta feedback).
 */
@Composable
internal fun DateChips(value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    val options = listOf(
        R.string.chip_plus_3_days to today.plusDays(3),
        R.string.chip_plus_1_week to today.plusWeeks(1),
        R.string.chip_plus_2_weeks to today.plusWeeks(2),
        R.string.chip_plus_1_month to today.plusMonths(1),
    )
    val perRow = if (LocalDensity.current.fontScale >= DATE_CHIPS_TWO_ROWS_FONT_SCALE) 2 else options.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(perRow).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { (label, date) ->
                    val selected = value == date
                    ToggleTile(
                        text = stringResource(label),
                        selected = selected,
                        onClick = { onChange(if (selected) null else date) },
                        modifier = Modifier.weight(1f).testTag("date_chip_$date"),
                    )
                }
            }
        }
    }
}

/**
 * A compact on/off tile for equal-width rows (the date chips): Material chips' fixed padding cut "+2 weeks" short in
 * a quarter of the row. Chosen: border, fill and a bold label, so it never depends on color alone.
 */
@Composable
private fun ToggleTile(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** From this font scale the four date chips no longer fit one row. */
private const val DATE_CHIPS_TWO_ROWS_FONT_SCALE = 1.3f

/**
 * Location as four equal tiles, icon over label, in one row (a 2 by 2 grid at large text sizes; after v1, replacing
 * chips that wrapped "Other" onto a line of its own). The chosen one has a border, a fill and a bold label.
 */
@Composable
internal fun LocationChoice(selected: Location, onPick: (Location) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_location), style = MaterialTheme.typography.labelLarge)
        val rows = if (LocalDensity.current.fontScale >= SEGMENTED_STACK_FONT_SCALE) 2 else 1
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Location.entries.chunked(Location.entries.size / rows).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { location ->
                        LocationTile(location, location == selected, { onPick(location) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationTile(location: Location, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("location_${location.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            location.icon(),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(location.label()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
