package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryLimits
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.ProductEdit
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductDetailViewModel = hiltViewModel<ProductDetailViewModel, ProductDetailViewModel.Factory>(
        key = productId,
        creationCallback = { it.create(productId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var throwing by remember { mutableStateOf<StockBatch?>(null) }
    var moving by remember { mutableStateOf<StockBatch?>(null) }
    var editing by remember { mutableStateOf<StockBatch?>(null) }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)
    val actions = BatchActions(
        onMinus = { viewModel.adjust(it, -1.0) },
        onPlus = { viewModel.adjust(it, 1.0) },
        onUsedUp = viewModel::usedUp,
        onThrowAway = { throwing = it },
        onEdit = { editing = it },
        onMove = { moving = it },
    )

    DetailScaffold(
        title = state.name,
        onBack = onBack,
        modifier = modifier.testTag("screen_product"),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@DetailScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val unit = stringResource(state.product?.unit?.label() ?: R.string.unit_pcs)
            Text(
                stringResource(R.string.product_total, formatQuantity(state.total), unit),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.batches.isNotEmpty()) {
                Section(stringResource(R.string.product_batches)) {
                    state.batches.forEach {
                        BatchRow(it, state.today, actions, containerColor = CardDefaults.cardColors().containerColor)
                    }
                }
            }
            state.product?.let { product ->
                // Re-key so the form resets when someone else changes the product.
                key(product) { SettingsForm(product, state.hasStock, viewModel::save) }
            }
            Section(stringResource(R.string.product_history)) {
                if (state.events.isEmpty()) {
                    Text(
                        stringResource(R.string.product_no_history),
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.events.forEach { EventRow(it, unit) }
                }
            }
        }
    }

    throwing?.let { batch ->
        ThrowAwayDialog(batch.productName, { viewModel.throwAway(batch, it) }, { throwing = null })
    }
    moving?.let { batch -> MoveDialog(batch.location, { viewModel.move(batch, it) }, { moving = null }) }
    editing?.let { batch ->
        EditBatchSheet(batch, state.today, onSave = {
            viewModel.edit(batch, it)
            editing = null
        }, onDismiss = { editing = null })
    }
}

@Composable
private fun SettingsForm(product: Product, hasStock: Boolean, onSave: (ProductEdit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(product.name) }
    var category by rememberSaveable { mutableStateOf(product.category) }
    var location by rememberSaveable { mutableStateOf(product.defaultLocation) }
    var unit by rememberSaveable { mutableStateOf(product.unit) }
    var keepAtLeast by rememberSaveable { mutableStateOf(product.minQuantity?.let(::formatQuantity).orEmpty()) }
    // On by default the first time Keep at least is set, as in the Add item sheet.
    var autoAdd by rememberSaveable { mutableStateOf(product.autoAddToGrocery || product.minQuantity == null) }
    var buy by rememberSaveable { mutableStateOf(product.buyQuantity?.let(::formatQuantity).orEmpty()) }
    var shelfLife by rememberSaveable { mutableStateOf(product.typicalShelfLifeDays?.toString().orEmpty()) }

    val clean = NameKey.clean(name)
    val nameError = when {
        clean.isEmpty() -> stringResource(R.string.error_name_required)
        clean.length > PantryLimits.MAX_NAME_LENGTH -> stringResource(R.string.error_name_80)
        else -> null
    }
    val min = keepAtLeast.takeIf { it.isNotBlank() }?.let(::parseQuantity)
    val minError = if (keepAtLeast.isNotBlank() &&
        (min == null || min < 0)
    ) {
        stringResource(R.string.error_number)
    } else {
        null
    }
    val days = shelfLife.takeIf { it.isNotBlank() }?.toIntOrNull()
    val daysError = if (shelfLife.isNotBlank() &&
        (days == null || days < 0)
    ) {
        stringResource(R.string.error_number)
    } else {
        null
    }

    Section(stringResource(R.string.product_settings), Modifier.testTag("product_settings")) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(value = name, onValueChange = {
                name = it
            }, label = stringResource(R.string.field_name), error = nameError)
            EnumDropdown(
                label = stringResource(R.string.field_unit),
                value = unit,
                options = unitMenu(),
                optionLabel = { stringResource(it.label()) },
                onPick = { unit = it },
                enabled = !hasStock,
                helper = if (hasStock) stringResource(R.string.unit_locked) else null,
            )
            EnumDropdown(stringResource(R.string.field_category), category, Category.entries, {
                stringResource(it.label())
            }, {
                category =
                    it
            })
            EnumDropdown(stringResource(R.string.field_default_location), location, Location.entries, {
                stringResource(it.label())
            }, {
                location =
                    it
            })
            KeepAtLeastFields(keepAtLeast, { keepAtLeast = it }, minError, autoAdd, { autoAdd = it }, buy, { buy = it })
            FormTextField(
                value = shelfLife,
                onValueChange = { shelfLife = it.filter(Char::isDigit).take(4) },
                label = stringResource(R.string.field_shelf_life),
                helper = stringResource(R.string.shelf_life_help),
                error = daysError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            PrimaryButton(stringResource(R.string.action_save), onClick = {
                if (nameError == null && minError == null && daysError == null) {
                    onSave(
                        ProductEdit(
                            name = clean,
                            category = category,
                            defaultLocation = location,
                            unit = unit,
                            minQuantity = min,
                            autoAddToGrocery = autoAdd,
                            buyQuantity = buy.takeIf { it.isNotBlank() }?.let(::parseQuantity),
                            typicalShelfLifeDays = days,
                        ),
                    )
                }
            }, modifier = Modifier.testTag("button_save_product"))
        }
    }
}

@Composable
private fun EventRow(event: PantryEvent, unit: String) {
    val formatter =
        remember {
            DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM,
                FormatStyle.SHORT,
            ).withZone(ZoneId.systemDefault())
        }
    val amount = event.quantity?.let { "${formatQuantity(it)} $unit" }.orEmpty()
    val text = when (event.type) {
        PantryEvent.Type.Added -> stringResource(R.string.event_added, event.actorName, amount)

        PantryEvent.Type.Discarded -> stringResource(
            R.string.event_discarded,
            event.actorName,
            amount,
            stringResource(event.reason?.label() ?: R.string.reason_other),
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        event.createdAt?.let {
            Text(
                formatter.format(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            content()
        }
    }
}
