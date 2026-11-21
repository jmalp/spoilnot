package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.LabelChip
import com.parcha.spoilnot.core.designsystem.component.LabelChipKind
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.PillField
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.component.listBottomPadding
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.core.ui.rememberHeldWhileScrolling
import com.parcha.spoilnot.core.ui.rememberRemoteChanges
import com.parcha.spoilnot.core.ui.remoteHighlight
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.GroceryView
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.grocery.domain.RestockEngine
import com.parcha.spoilnot.feature.pantry.ui.amountDescription
import com.parcha.spoilnot.feature.pantry.ui.amountText
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import kotlinx.coroutines.launch

@Composable
fun GroceriesScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroceriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    val aisleHint by viewModel.aisleHint.collectAsStateWithLifecycle()
    val text by viewModel.quickAddText.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var shopping by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var listDialog by remember { mutableStateOf<ListDialog?>(null) }
    val listState = rememberLazyListState()
    val clearText = { viewModel.setQuickAddText("") }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    GroceriesTab(
        state = state,
        text = text,
        suggestions = suggestions,
        aisleHint = aisleHint,
        snackbar = snackbar,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        listState = listState,
        handlers = GroceriesHandlers(
            onSelectList = viewModel::selectList,
            onListDialog = { listDialog = it },
            onShop = { shopping = true },
            onRetry = viewModel::retryLoad,
            onText = viewModel::setQuickAddText,
            onAdd = { value -> if (value.isNotBlank()) viewModel.quickAdd(value, onAdded = clearText) },
            onClearCart = viewModel::clearChecked,
            onDismissHint = viewModel::dismissAisleHint,
            onToggle = viewModel::toggle,
            onDelete = viewModel::delete,
            onEdit = { editing = it },
        ),
    )

    duplicate?.let { pending ->
        val canIncrease = pending.request.canIncrease(pending.existing)
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.grocery_already_title), style = SpoilNotText.DialogTitle) },
            text = {
                // Amounts in another unit aren't added together (no conversion, DESIGN 2; after v1, LOGIC-08).
                val existing = pending.existing
                val body = if (canIncrease) {
                    stringResource(
                        R.string.grocery_already_more,
                        amountText(pending.request.quantity ?: 1.0, pending.request.unit ?: existing.unit),
                        existingAmount(existing),
                    )
                } else {
                    val unit = checkNotNull(existing.unit)
                    val amount = existing.quantity?.let { amountText(it, unit) } ?: stringResource(unit.label())
                    stringResource(R.string.grocery_already_other_unit, existing.name, amount)
                }
                Text(body)
            },
            confirmButton = {
                if (canIncrease) {
                    TextButton(
                        onClick = { viewModel.increaseDuplicate(clearText) },
                    ) { Text(stringResource(R.string.grocery_increase)) }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissDuplicate) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { viewModel.addDuplicateAnyway(clearText) },
                    ) { Text(stringResource(R.string.grocery_add_anyway)) }
                }
            },
        )
    }
    if (shopping) {
        AddToPantrySheet(
            items = state.view.inCart,
            pantry = state.pantry,
            today = state.today,
            drafts = viewModel.pantryDrafts,
            onConfirm = { entries, skipped -> viewModel.addToPantry(entries, skipped) { shopping = false } },
            onDismiss = {
                shopping = false
                viewModel.closeAddToPantry()
            },
        )
    }
    editing?.let { item ->
        EditItemSheet(
            item,
            onSave = { name, q, u, n, c ->
                viewModel.edit(item, q, u, n, c, name)
                editing = null
            },
            onDismiss = { editing = null },
            reason = RestockEngine.reason(item, state.pantry),
        )
    }
    listDialog?.let { dialog ->
        ListDialogs(
            dialog,
            state.list?.name.orEmpty(),
            onDismiss = { listDialog = null },
            onCreate = viewModel::createList,
            onRename = viewModel::renameList,
            onDelete = viewModel::deleteList,
        )
    }
}

/** What the Groceries tab does with a tap, so [GroceriesTab] can be drawn without its view model. */
internal data class GroceriesHandlers(
    val onSelectList: (GroceryList) -> Unit,
    val onListDialog: (ListDialog) -> Unit,
    val onShop: () -> Unit,
    val onRetry: () -> Unit,
    val onText: (String) -> Unit,
    val onAdd: (String) -> Unit,
    val onClearCart: () -> Unit,
    val onDismissHint: () -> Unit,
    val onToggle: (GroceryItem) -> Unit,
    val onDelete: (GroceryItem) -> Unit,
    val onEdit: (GroceryItem) -> Unit,
)

/**
 * The Groceries tab (DESIGN 5.3, 19.7): what is left to buy above the list's name, which switches lists, the
 * quick-add field, and the list in aisles, then In cart.
 */
@Composable
internal fun GroceriesTab(
    state: GroceriesViewModel.UiState,
    text: String,
    suggestions: List<String>,
    aisleHint: Boolean,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    handlers: GroceriesHandlers,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    var cartOpen by rememberSaveable { mutableStateOf(true) }
    val checkedCount = state.view.inCart.size
    TabScaffold(
        title = state.list?.name ?: stringResource(R.string.tab_groceries),
        // What is left to buy (DESIGN 19.7).
        eyebrow = if (state.loading) {
            null
        } else {
            pluralStringResource(R.plurals.grocery_eyebrow_to_buy, state.toBuy, state.toBuy)
        },
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_groceries"),
        // The list's name is the switcher (after v1; a bare list icon did not say which list this is).
        titleContent = { ListMenu(state, handlers.onSelectList, handlers.onListDialog) },
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        floatingActionButton = {
            if (checkedCount > 0) {
                SpoilNotFab(
                    text = pluralStringResource(R.plurals.grocery_add_to_pantry, checkedCount, checkedCount),
                    icon = Icons.Rounded.Inventory2,
                    onClick = handlers.onShop,
                    modifier = Modifier.testTag("fab_add_to_pantry"),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loadFailed) {
                LoadError(onRetry = handlers.onRetry)
            } else {
                OfflineBanner(offline = state.offline)
            }
            QuickAddField(text = text, onText = handlers.onText, suggestions = suggestions, onAdd = handlers.onAdd)
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { if (!state.loadFailed) CircularProgressIndicator() }

                state.items.isEmpty() -> EmptyState(Icons.Outlined.ShoppingCart, stringResource(R.string.grocery_empty))

                else -> GroceryList(
                    state = state,
                    listState = listState,
                    aisleHint = aisleHint,
                    cartOpen = cartOpen,
                    onToggleCart = { cartOpen = !cartOpen },
                    onClearCart = handlers.onClearCart,
                    onDismissHint = handlers.onDismissHint,
                    onToggle = handlers.onToggle,
                    onDelete = handlers.onDelete,
                    onEdit = handlers.onEdit,
                )
            }
        }
    }
}

/**
 * The list in aisles, then In cart (DESIGN 5.3, 19.5): each aisle a group of rows under its heading, pinned while its
 * rows scroll. Another phone's change waits while the list moves (DESIGN 6.4, review CODE-09), and each row gets only
 * its own facts, so a change redraws only the rows it touches (PERF-04).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroceryList(
    state: GroceriesViewModel.UiState,
    listState: LazyListState,
    aisleHint: Boolean,
    cartOpen: Boolean,
    onToggleCart: () -> Unit,
    onClearCart: () -> Unit,
    onDismissHint: () -> Unit,
    onToggle: (GroceryItem) -> Unit,
    onDelete: (GroceryItem) -> Unit,
    onEdit: (GroceryItem) -> Unit,
) {
    // Held with the list the rows belong to, so both change in the same frame.
    val (view, viewListId) = rememberHeldWhileScrolling(state.view to state.itemsListId, listState)
    // Rows another phone changed get a short highlight (DESIGN 6.4), told one list at a time: another list's rows
    // aren't new (after v1, 2026-11-18; switching lists highlighted every row of the new one).
    val shown = remember(view) { view.aisles.flatMap { it.second } + view.inCart }
    val remote = rememberRemoteChanges(
        shown,
        scope = viewListId,
        id = { it.id },
        pending = { it.pendingSync },
        content = { it.copy(pendingSync = false) },
    )
    val page = SpoilNotSurfaces.current.page
    val row = @Composable { item: GroceryItem, index: Int, count: Int, modifier: Modifier ->
        val position = segmentPosition(index, count)
        GroceryRow(
            item = item,
            backInStock = item.id in state.backInStock,
            addedBy = state.addedBy[item.id],
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            modifier = modifier
                .padding(top = if (index > 0) ListGroupGap else 0.dp)
                .remoteHighlight(item.id in remote, segmentShape(position)),
            position = position,
        )
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding()),
        modifier = Modifier.testTag("grocery_list"),
    ) {
        view.aisles.forEachIndexed { index, (category, items) ->
            // Sentence case, as everywhere else (DESIGN 6.9). TalkBack's headings navigation jumps from aisle to
            // aisle in the shop (review A11Y-09).
            stickyHeader(key = "aisle-$category", contentType = "heading") {
                GroupHeading(
                    stringResource(category.label()),
                    Modifier
                        .fillMaxWidth()
                        .background(page)
                        .padding(top = if (index == 0) 0.dp else AisleGap, bottom = 6.dp),
                )
            }
            // Under the first aisle's heading, once: why the aisles move (after v1, UX-08).
            if (index == 0 && aisleHint) {
                item(key = "aisle-hint", contentType = "hint") {
                    AisleHint(onDismissHint, Modifier.animateItem().padding(bottom = 8.dp))
                }
            }
            itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "item" }) { i, item ->
                row(item, i, items.size, Modifier.animateItem())
            }
        }
        if (view.inCart.isNotEmpty()) {
            item(key = "cart-header", contentType = "cart-header") {
                CartHeader(
                    view.inCart.size,
                    cartOpen,
                    onToggle = onToggleCart,
                    onClear = onClearCart,
                    modifier = Modifier.animateItem().padding(top = if (view.aisles.isEmpty()) 0.dp else AisleGap),
                )
            }
            if (cartOpen) {
                itemsIndexed(view.inCart, key = { _, item -> item.id }, contentType = { _, _ -> "item" }) { i, item ->
                    row(item, i, view.inCart.size, Modifier.animateItem())
                }
            }
        }
    }
}

/** The space between two aisles, and above In cart. */
private val AisleGap = 12.dp

/** A row with a checkbox is at least 64 dp tall (DESIGN 19.5). */
private val CheckboxRowHeight = 64.dp

/**
 * A tap checks the item off; it moves to the In cart section (DESIGN 5.3). After v1 (UX-10) a swipe right checks it
 * off too (or puts it back from the cart) and a swipe left deletes it with Undo, each shown under the row as it moves;
 * the tap and the ⋮ menu do the same (DESIGN 6.8). A row of its aisle's group (19.5), on a darker surface in the cart.
 * It takes only its own facts, [backInStock] and [addedBy], so it is drawn again only when they change (PERF-04).
 */
@Composable
internal fun GroceryRow(
    item: GroceryItem,
    backInStock: Boolean,
    addedBy: String?,
    onToggle: (GroceryItem) -> Unit,
    onDelete: (GroceryItem) -> Unit,
    onEdit: (GroceryItem) -> Unit,
    modifier: Modifier = Modifier,
    position: SegmentPosition = SegmentPosition.Only,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(item.id, item.checked) { swipe.reset() }
    val toggle = {
        // Light haptic on check-off (DESIGN 6.4).
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        onToggle(item)
    }
    // One sentence for TalkBack, as Pantry's rows (review A11Y-07): "Bananas, 6 pieces, added automatically, added by
    // Sam, ripe ones". The checkbox state is read with it; Remove and ⋮ stay their own stops.
    val description = listOfNotNull(
        item.name,
        item.quantity?.let { amountDescription(it, item.unit) },
        when (item.source) {
            ItemSource.LowStock -> if (backInStock) null else stringResource(R.string.grocery_source_low_stock)
            ItemSource.Recipe -> stringResource(R.string.grocery_source_recipe_description)
            ItemSource.MealPlan -> stringResource(R.string.grocery_source_meal_plan_description)
            ItemSource.Manual -> null
        },
        if (backInStock) stringResource(R.string.grocery_back_in_stock) else null,
        addedBy?.let { stringResource(R.string.grocery_added_by, it) },
        item.note,
    ).joinToString(", ")
    val shape = segmentShape(position)
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.testTag("grocery_item_${item.id}"),
        backgroundContent = { GrocerySwipeBackground(swipe.dismissDirection, item.checked, shape) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> toggle()

                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete(item)
                }

                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { swipe.reset() }
        },
    ) {
        ListSegment(
            position = position,
            modifier = Modifier
                .clip(shape)
                .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { toggle() })
                .semantics { contentDescription = description },
            // The cart is the shop's done pile: a step darker, its names struck through (DESIGN 19.5).
            color = if (item.checked) MaterialTheme.colorScheme.surfaceContainerHigh else SpoilNotSurfaces.current.row,
            minHeight = CheckboxRowHeight,
            contentPadding = PaddingValues(start = 8.dp, end = 4.dp),
        ) {
            Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(4.dp))
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NameLine(item)
                SecondLine(item, backInStock, addedBy, onRemove = { onDelete(item) })
            }
            ItemMenu(item, onEdit, onDelete)
        }
    }
}

/** "Eggs × 12" and the pending icon; the amount wraps under a long name instead of being squeezed out (A11Y-04). */
@Composable
private fun NameLine(item: GroceryItem) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            style = SpoilNotText.RowTitle,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = nameColor(item.checked),
        )
        item.quantity?.let {
            Text(
                "× ${amountText(it, item.unit)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.pendingSync) {
            Icon(
                Icons.Rounded.CloudOff,
                stringResource(R.string.pending_sync),
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where the item came from as a label chip, Back in stock, who added it and its note (DESIGN 19.10). */
@Composable
private fun SecondLine(item: GroceryItem, backInStock: Boolean, addedBy: String?, onRemove: () -> Unit) {
    val source = when (item.source) {
        // What the app did by itself says so (after v1, UX-08; it said "Low stock", which read as a status). Back
        // in stock replaces it: one line, "Back in stock · Remove".
        ItemSource.LowStock -> if (backInStock) {
            null
        } else {
            stringResource(R.string.grocery_source_low_stock) to Icons.AutoMirrored.Rounded.TrendingDown
        }

        ItemSource.Recipe -> stringResource(R.string.grocery_source_recipe) to Icons.Rounded.RestaurantMenu

        // The recipe icon with its own word (DESIGN 5.9): it comes from planned recipes.
        ItemSource.MealPlan -> stringResource(R.string.grocery_source_meal_plan) to Icons.Rounded.RestaurantMenu

        ItemSource.Manual -> null
    }
    if (source == null && !backInStock && addedBy == null && item.note == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Each source has its own icon and word, so the chips differ without relying on color.
        source?.let { (text, icon) -> LabelChip(text, icon = icon) }
        if (backInStock) BackInStockLine(item.name, onRemove)
        addedBy?.let {
            Text(
                stringResource(R.string.grocery_added_by, it),
                style = SpoilNotText.RowSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.note?.let {
            Text(it, style = SpoilNotText.RowSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Back in stock · Remove" (after v1, UX-08): one line instead of a Low stock badge beside a Back in stock one. */
@Composable
private fun BackInStockLine(name: String, onRemove: () -> Unit) {
    // Wraps as whole words at large text sizes instead of breaking Remove mid-word.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Good news in the person chip's colors, with its own icon.
        LabelChip(
            stringResource(R.string.grocery_back_in_stock),
            icon = Icons.Rounded.CheckCircle,
            kind = LabelChipKind.Person,
        )
        Text("·", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val description = stringResource(R.string.grocery_back_in_stock_remove, name)
        Text(
            stringResource(R.string.grocery_remove),
            style = SpoilNotText.ButtonMedium,
            color = MaterialTheme.colorScheme.primary,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onRemove() }
                .semantics { contentDescription = description }
                .heightIn(min = 48.dp)
                .wrapContentHeight()
                .padding(horizontal = 8.dp),
        )
    }
}

/**
 * What a swipe does, under the row as it moves (after v1, UX-10): right checks off (or puts back), left deletes, in
 * the row's [shape]. Icon and word, never color alone (DESIGN 6.1).
 */
@Composable
private fun GrocerySwipeBackground(direction: SwipeToDismissBoxValue, checked: Boolean, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> colors.primaryContainer to colors.onPrimaryContainer
        SwipeToDismissBoxValue.EndToStart -> colors.errorContainer to colors.onErrorContainer
        SwipeToDismissBoxValue.Settled -> return
    }
    val (icon, label) = when {
        direction == SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.DeleteOutline to R.string.action_delete
        checked -> Icons.AutoMirrored.Rounded.Undo to R.string.grocery_swipe_uncheck
        else -> Icons.Rounded.Check to R.string.grocery_swipe_check
    }
    Box(
        Modifier.fillMaxSize().background(container, shape).padding(horizontal = 20.dp),
        contentAlignment = swipeAlignment(direction),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = content)
            Text(
                stringResource(label),
                color = content,
                style = SpoilNotText.ButtonMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** "Aisles follow the order you shop in…" with Got it, shown once per phone (after v1, UX-08). */
@Composable
private fun AisleHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().testTag("grocery_aisle_hint"),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.grocery_aisle_hint),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("grocery_aisle_hint_ok")) {
                Text(stringResource(R.string.grocery_aisle_hint_ok))
            }
        }
    }
}

@Composable
private fun nameColor(checked: Boolean) =
    if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

private fun swipeAlignment(direction: SwipeToDismissBoxValue) =
    if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd

/** "6", "500 g": what is on the list now; an item without an amount counts as 1. */
@Composable
private fun existingAmount(existing: GroceryItem): String = amountText(existing.quantity ?: 1.0, existing.unit)

@Composable
private fun ItemMenu(item: GroceryItem, onEdit: (GroceryItem) -> Unit, onDelete: (GroceryItem) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.grocery_item_options, item.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = {
                open = false
                onEdit(item)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
                open = false
                onDelete(item)
            })
        }
    }
}

/**
 * "In cart (1)" with Clear cart (DESIGN 5.3, 19.5): a heading in `onSurfaceVariant`, the cart being done; a tap
 * folds the cart away or shows it.
 */
@Composable
private fun CartHeader(
    count: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
    Row(
        modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .semantics {
                    heading()
                    stateDescription = state
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.grocery_in_cart, count, count),
                style = SpoilNotText.GroupHeading,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 44.dp)) {
            Text(stringResource(R.string.grocery_clear_checked), style = SpoilNotText.ButtonMedium)
        }
    }
}

/**
 * The quick-add field stays at the top (DESIGN 5.3, 19.10): the page's round field with a filled + at its end;
 * typing suggests pantry products and names already used, which the view model works out (review PERF-04).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddField(text: String, onText: (String) -> Unit, suggestions: List<String>, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && suggestions.isNotEmpty(), onExpandedChange = { open = it }) {
        PillField(
            value = text,
            onValueChange = {
                onText(it)
                open = true
            },
            placeholder = stringResource(R.string.grocery_quick_add),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("grocery_quick_add")
                .coachMark(TourTarget.QuickAdd.key),
        ) {
            FilledIconButton(
                onClick = { onAdd(text) },
                enabled = text.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.grocery_add_to_list))
            }
        }
        ExposedDropdownMenu(expanded = open && suggestions.isNotEmpty(), onDismissRequest = { open = false }) {
            suggestions.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onAdd(name)
                })
            }
        }
    }
}

internal enum class ListDialog { Create, Rename, Delete }

/** List switcher and list management (DESIGN 5.3: users can add, rename and delete lists). */
@Composable
private fun ListMenu(
    state: GroceriesViewModel.UiState,
    onSelect: (GroceryList) -> Unit,
    onDialog: (ListDialog) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val name = state.list?.name ?: stringResource(R.string.tab_groceries)
    val description = stringResource(R.string.grocery_list_switcher, name)
    Box {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.grocery_lists)) { open = true }
                .heightIn(min = 48.dp)
                .padding(end = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag("grocery_list_switcher"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                // The header's title (DESIGN 19.7): the list's name is the screen's title.
                style = SpoilNotText.ScreenTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    leadingIcon = {
                        if (list.id == state.list?.id) Icon(Icons.Rounded.Check, null) else Box(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onSelect(list)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.grocery_new_list)) }, onClick = {
                open = false
                onDialog(ListDialog.Create)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.grocery_rename_list)) }, onClick = {
                open = false
                onDialog(ListDialog.Rename)
            })
            if (state.list?.isDefault == false) {
                DropdownMenuItem(text = { Text(stringResource(R.string.grocery_delete_list)) }, onClick = {
                    open = false
                    onDialog(ListDialog.Delete)
                })
            }
        }
    }
}

@Composable
private fun ListDialogs(
    dialog: ListDialog,
    currentName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    if (dialog == ListDialog.Delete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.grocery_delete_list_title, currentName), style = SpoilNotText.DialogTitle)
            },
            text = { Text(stringResource(R.string.grocery_delete_list_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }
    var name by rememberSaveable { mutableStateOf(if (dialog == ListDialog.Rename) currentName else "") }
    val valid = name.isNotBlank() && name.trim().length <= GroceryRules.MAX_LIST_NAME_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (dialog ==
                        ListDialog.Create
                    ) {
                        R.string.grocery_new_list
                    } else {
                        R.string.grocery_rename_list
                    },
                ),
                style = SpoilNotText.DialogTitle,
            )
        },
        text = {
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.grocery_list_name),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (dialog == ListDialog.Create) onCreate(name) else onRename(name)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
