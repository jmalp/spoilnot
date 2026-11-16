package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ConnectedButtonGroup
import com.parcha.spoilnot.core.designsystem.component.FieldBorderWidth
import com.parcha.spoilnot.core.designsystem.component.FieldBox
import com.parcha.spoilnot.core.designsystem.component.FieldLabel
import com.parcha.spoilnot.core.designsystem.component.FieldMessage
import com.parcha.spoilnot.core.designsystem.component.FieldMinHeight
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
import com.parcha.spoilnot.core.designsystem.component.SpoilNotTextField
import com.parcha.spoilnot.core.designsystem.component.errorSemantics
import com.parcha.spoilnot.core.designsystem.component.fieldLabelColor
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.grocery.domain.AisleGuess
import com.parcha.spoilnot.feature.pantry.domain.Amounts
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.ItemEdit
import com.parcha.spoilnot.feature.pantry.domain.ItemEditBase
import com.parcha.spoilnot.feature.pantry.domain.ItemEditWatch
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

/** Parses a typed quantity; accepts "1,5" as well as "1.5", and the phone's own digits ([Amounts]). */
fun parseQuantity(text: String): Double? = Amounts.parse(text)

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
    SpoilNotSheet(
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

    /** Category was picked by hand, so the aisle guess no longer changes it (after v1, UX-09). */
    var categoryChosen by mutableStateOf(false)

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
                    it.autoAdd, it.keepAtLeast, it.buyQuantity, it.submitted, it.categoryChosen,
                )
            },
            restore = { v ->
                ItemFormState(
                    v[0] as String, v[1] as String, QuantityUnit.valueOf(v[2] as String),
                    Category.valueOf(v[3] as String), (v[4] as String?)?.let(LocalDate::parse),
                    Location.valueOf(v[5] as String), v[6] as Boolean, v[7] as String, v[8] as String,
                ).also {
                    it.submitted = v[9] as Boolean
                    it.categoryChosen = v[10] as Boolean
                }
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
    // A new name takes the aisle Groceries would guess (after v1, UX-09), until a category is picked by hand; a
    // product chosen from the suggestions brings its own.
    LaunchedEffect(form.name, match == null) {
        if (match == null && !form.categoryChosen) form.category = AisleGuess.forNewProduct(form.name, products)
    }
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
            // The keyboard opens on Name (after v1, UX-09), except for a product chosen beforehand (an Out row).
            focusName = prefill == null,
        )
    }
}

/**
 * The Edit window, the same form as Add: the purchase's quantity, date and location, and its product's name, unit,
 * category and grocery settings. The unit can change only when this is the product's only purchase.
 *
 * It compares with [base] (what it opened with, DESIGN 9.4). When someone else changed the item ([changed], the item
 * as it is now), it keeps the draft and says so (9.2); Review shows the item as it is now with this person's changes
 * kept, and hands [changed] to [onReviewed].
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
    base: ItemEdit = ItemEdit.of(batch, product),
    changed: ItemEditBase? = null,
    changedBy: String? = null,
    onReviewed: (ItemEditBase) -> Unit = {},
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
    SpoilNotSheet(
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
            if (changed != null) {
                ChangedNotice(
                    text = changedBy?.let { stringResource(R.string.edit_changed_by, it) }
                        ?: stringResource(R.string.edit_changed),
                    onReview = {
                        form.review(base, changed)
                        onReviewed(changed)
                    },
                )
            }
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

/** "Sam changed this item." with Review (DESIGN 9.2); the words say it, not a color (6.8). */
@Composable
internal fun ChangedNotice(text: String, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("notice_item_changed"),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onReview, modifier = Modifier.testTag("button_review_changes")) {
                Text(stringResource(R.string.action_review))
            }
        }
    }
}

/**
 * Review: the fields the person didn't touch take the item's values as they are now ([live]); the ones they changed
 * keep theirs, and a changed quantity keeps its difference (ItemEditWatch.rebase).
 */
private fun ItemFormState.review(base: ItemEdit, live: ItemEditBase) {
    val now = live.edit
    val draft = ItemEdit(
        name = name,
        quantity = qty ?: base.quantity,
        unit = unit,
        category = category,
        expiryDate = expiry,
        location = location,
        autoAddToGrocery = autoAdd,
        minQuantity = min.takeIf { autoAdd },
        buyQuantity = buy.takeIf { autoAdd },
    )
    val rebased = ItemEditWatch.rebase(draft, base, now)
    if (rebased.name != draft.name) name = rebased.name
    if (qty != null) quantity = formatQuantity(rebased.quantity)
    unit = rebased.unit
    category = rebased.category
    expiry = rebased.expiryDate
    location = rebased.location
    val keepUntouched = draft.autoAddToGrocery == base.autoAddToGrocery && draft.minQuantity == base.minQuantity &&
        draft.buyQuantity == base.buyQuantity
    if (keepUntouched) {
        autoAdd = now.autoAddToGrocery
        keepAtLeast = live.product?.minQuantity?.let(::formatQuantity).orEmpty()
        buyQuantity = live.product?.buyQuantity?.let(::formatQuantity).orEmpty()
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
    focusName: Boolean = false,
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
    NameField(form.name, { form.name = it }, products, nameError, onChoose = onChooseProduct, focus = focusName)
    // A compact stepper beside the unit (after v1): the amount needs a few digits, not the full width.
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
    if (isLargeText()) {
        QuantityStepper(form.quantity, { form.quantity = it }, qtyError, fill = true)
        unitField(Modifier.fillMaxWidth())
    } else {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuantityStepper(form.quantity, { form.quantity = it }, qtyError)
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
        onPick = {
            form.category = it
            form.categoryChosen = true
        },
    )
    AutoAddFields(form)
}

/**
 * Keep some in stock (after v1, UX-07; "Add to grocery list automatically" before): the switch says what it does
 * while off, then Keep at least and Buy quantity while it is on.
 */
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.field_auto_add), style = SpoilNotText.RowTitle)
            if (!form.autoAdd) {
                Text(
                    stringResource(R.string.field_auto_add_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = form.autoAdd, onCheckedChange = null)
    }
    AnimatedVisibility(form.autoAdd) {
        Column(verticalArrangement = Arrangement.spacedBy(FormFieldGap)) {
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

/** The space between two fields of a form sheet (DESIGN 19.10). */
private val FormFieldGap = 18.dp

/**
 * Scrollable fields with the save button pinned below them, so saving never needs a scroll. The title in
 * [SpoilNotText.SheetTitle], 20 dp from the sheet's sides (DESIGN 19.11).
 */
@Composable
private fun FormSheetColumn(title: String, button: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(FormFieldGap),
        ) {
            Text(title, style = SpoilNotText.SheetTitle)
            content()
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) { button() }
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
    focus: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Once per sheet, not again after rotation (the field keeps its own focus then).
    var focused by rememberSaveable { mutableStateOf(false) }
    if (focus && !focused) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            focused = true
        }
    }
    val key = NameKey.of(name)
    val suggestions = if (key.isEmpty()) {
        emptyList()
    } else {
        products.filter {
            it.nameKey.contains(key) && it.nameKey != key
        }.sortedBy { !it.nameKey.startsWith(key) }.take(5)
    }
    ExposedDropdownMenuBox(expanded = open && suggestions.isNotEmpty(), onExpandedChange = { open = it }) {
        SpoilNotTextField(
            value = name,
            onValueChange = {
                onChange(it)
                open = true
            },
            label = stringResource(R.string.field_name),
            error = error,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .focusRequester(focusRequester)
                .testTag("field_name"),
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

/**
 * Quantity as a stepper (DESIGN 19.9): − and + joined to the typed amount, its label above; decimals allowed (5.2).
 * One text field for screen readers, named by its label, with − and + as their own buttons. [fill] stretches the
 * amount across the row, at large text sizes; Add to pantry gives each line's stepper its own [tag].
 */
@Composable
internal fun QuantityStepper(
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    fill: Boolean = false,
    tag: String = "field_quantity",
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val boxShape = RoundedCornerShape(SpoilNotCorners.Connected)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .semantics(mergeDescendants = true) {}
            .errorSemantics(error)
            .testTag(tag),
        singleLine = true,
        textStyle = StepperValue.merge(TextStyle(color = colors.onSurface, textAlign = TextAlign.Center)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        interactionSource = interactions,
        cursorBrush = SolidColor(if (error != null) colors.error else colors.primary),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.field_quantity), color = fieldLabelColor(focused, error != null))
                Row(
                    if (fill) Modifier.fillMaxWidth() else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(ListGroupGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton(Icons.Rounded.Remove, stringResource(R.string.action_minus_one), start = true) {
                        parseQuantity(value)?.let { onChange(formatQuantity((it - 1).coerceAtLeast(0.0))) }
                    }
                    Box(
                        (if (fill) Modifier.weight(1f) else Modifier.width(StepperValueWidth))
                            .heightIn(min = FieldMinHeight)
                            .background(SpoilNotSurfaces.current.field, boxShape)
                            .border(
                                FieldBorderWidth,
                                when {
                                    error != null -> colors.error
                                    focused -> colors.primary
                                    else -> Color.Transparent
                                },
                                boxShape,
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { inner() }
                    StepButton(Icons.Rounded.Add, stringResource(R.string.action_plus_one), start = false) {
                        onChange(formatQuantity((parseQuantity(value) ?: 0.0) + 1))
                    }
                }
                FieldMessage(error = error, helper = null)
            }
        },
    )
}

/** − or + of a stepper: 56 dp, `secondaryContainer`, fully round on the outside and 8 dp where it joins the value. */
@Composable
internal fun StepButton(
    icon: ImageVector,
    label: String,
    start: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val round = CornerSize(percent = 50)
    val joined = CornerSize(SpoilNotCorners.Connected)
    val shape = if (start) {
        RoundedCornerShape(topStart = round, topEnd = joined, bottomEnd = joined, bottomStart = round)
    } else {
        RoundedCornerShape(topStart = joined, topEnd = round, bottomEnd = round, bottomStart = joined)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer.let { if (enabled) it else it.copy(alpha = 0.5f) },
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer.let {
            if (enabled) it else it.copy(alpha = 0.38f)
        },
        modifier = modifier.size(FieldMinHeight).semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
    }
}

/** The typed amount of a stepper: larger and heavier than a field's text. */
private val StepperValue = SpoilNotText.FieldText.copy(
    fontSize = 20.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.ExtraBold,
)

/** The amount's width between − and +: room for a few digits. */
private val StepperValueWidth = 72.dp

/**
 * The expiration date (DESIGN 5.2, 19.10): a picker that looks like a field, with the date and its status, then the
 * quick dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryField(value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(SpoilNotCorners.Field)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.field_expiry))
        FieldBox(
            focused = false,
            isError = false,
            endPadding = 12.dp,
            modifier = Modifier
                .clip(shape)
                .clickable(role = Role.Button) { picking = true }
                .testTag("field_expiry"),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                // Far-off dates read as the date itself; show it once ("Nov 2", not "Nov 2 · Nov 2").
                value?.let {
                    val date = formatDate(it, today)
                    val status = statusText(it, DateType.UseBy, today)
                    if (status == date) date else "$date · $status"
                }
                    ?: stringResource(R.string.status_no_date),
                style = SpoilNotText.FieldText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
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
 * The quick dates, shared by Add item and Add to pantry: a connected group of four (DESIGN 19.9), a list of radio
 * buttons from 1.5× text. One date at a time, radio buttons for screen readers (review A11Y-08). Tapping the chosen
 * one again clears the date; there is no "No expiry" choice because no date is the default and the date picker
 * already says so (beta feedback).
 */
@Composable
internal fun DateChips(value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    val options = listOf(
        R.string.chip_plus_3_days to today.plusDays(3),
        R.string.chip_plus_1_week to today.plusWeeks(1),
        R.string.chip_plus_2_weeks to today.plusWeeks(2),
        R.string.chip_plus_1_month to today.plusMonths(1),
    )
    ConnectedButtonGroup(
        options = options,
        selected = options.firstOrNull { it.second == value },
        optionLabel = { stringResource(it.first) },
        onPick = { (_, date) -> onChange(if (date == value) null else date) },
        optionTag = { "date_chip_${it.second}" },
    )
}

/**
 * Location as four equal tiles, icon over label, in one row (a 2 by 2 grid at large text sizes; after v1, replacing
 * chips that wrapped "Other" onto a line of its own). The chosen one is filled `primaryContainer`, rounder and
 * heavier (DESIGN 19.10), so it never depends on color alone.
 */
@Composable
internal fun LocationChoice(selected: Location, onPick: (Location) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.field_location))
        val rows = if (isLargeText()) 2 else 1
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
    val shape = RoundedCornerShape(if (selected) SpoilNotCorners.Highlight else SpoilNotCorners.Field)
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else SpoilNotSurfaces.current.field)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = LocationTileHeight)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("location_${location.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(location.icon(), contentDescription = null, tint = content)
        Text(
            stringResource(location.label()),
            style = SpoilNotText.ButtonMedium,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A location tile's height: icon over label. */
private val LocationTileHeight = 76.dp

/** A choice from a list, as a field that opens a menu (DESIGN 19.10): its label above, the choice in the box. */
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
        SpoilNotTextField(
            value = optionLabel(value),
            onValueChange = {},
            label = label,
            readOnly = true,
            enabled = enabled,
            helper = helper,
            trailingIcon = {
                Box(Modifier.padding(end = 12.dp)) { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) }
            },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
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
