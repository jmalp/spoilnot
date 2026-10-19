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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.core.ui.rememberRemoteChanges
import com.parcha.spoilnot.core.ui.remoteHighlight
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.ui.amountText
import com.parcha.spoilnot.feature.pantry.ui.formatQuantity
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroceriesScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroceriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var cartOpen by rememberSaveable { mutableStateOf(true) }
    var shopping by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GroceryItem?>(null) }
    var listDialog by remember { mutableStateOf<ListDialog?>(null) }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    val checkedCount = state.view.inCart.size
    TabScaffold(
        title = state.list?.name ?: stringResource(R.string.tab_groceries),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_groceries"),
        actions = { ListMenu(state, viewModel::selectList) { listDialog = it } },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (checkedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { shopping = true },
                    icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
                    text = { Text(pluralStringResource(R.plurals.grocery_add_to_pantry, checkedCount, checkedCount)) },
                    modifier = Modifier.testTag("fab_add_to_pantry"),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            QuickAddField(
                text = text,
                onText = { text = it },
                suggestions = GroceryLayout.suggestions(text, state.items, state.pantry),
                onAdd = { value -> if (value.isNotBlank()) viewModel.quickAdd(value) { text = "" } },
            )
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.items.isEmpty() -> EmptyState(Icons.Outlined.ShoppingCart, stringResource(R.string.grocery_empty))

                else -> {
                    // Rows another phone changed get a short highlight (DESIGN 6.4).
                    val remote = rememberRemoteChanges(
                        state.items,
                        id = { it.id },
                        pending = { it.pendingSync },
                        content = { it.copy(pendingSync = false) },
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.testTag("grocery_list"),
                    ) {
                        state.view.aisles.forEach { (category, items) ->
                            stickyHeader(
                                key = "aisle-$category",
                            ) { SectionHeader(stringResource(category.label()).uppercase()) }
                            items(items, key = { it.id }) { item ->
                                GroceryRow(
                                    item,
                                    state,
                                    viewModel::toggle,
                                    viewModel::delete,
                                    { editing = it },
                                    Modifier.animateItem().remoteHighlight(item.id in remote),
                                )
                            }
                        }
                        if (state.view.inCart.isNotEmpty()) {
                            item(key = "cart-header") {
                                CartHeader(
                                    checkedCount,
                                    cartOpen,
                                    onToggle = { cartOpen = !cartOpen },
                                    onClear = viewModel::clearChecked,
                                )
                            }
                            if (cartOpen) {
                                items(state.view.inCart, key = { it.id }) { item ->
                                    GroceryRow(
                                        item,
                                        state,
                                        viewModel::toggle,
                                        viewModel::delete,
                                        { editing = it },
                                        Modifier.animateItem().remoteHighlight(item.id in remote),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    duplicate?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.grocery_already_title)) },
            text = { Text(stringResource(R.string.grocery_already_body, pending.existing.name)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.increaseDuplicate { text = "" } },
                ) { Text(stringResource(R.string.grocery_increase)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissDuplicate) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { viewModel.addDuplicateAnyway { text = "" } },
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
            onConfirm = { entries, skipped -> viewModel.addToPantry(entries, skipped) { shopping = false } },
            onDismiss = { shopping = false },
        )
    }
    editing?.let { item ->
        EditItemSheet(item, onSave = { name, q, u, n, c ->
            viewModel.edit(item, q, u, n, c, name)
            editing = null
        }, onDismiss = { editing = null })
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

/** A tap checks the item off; it moves to the In cart section (DESIGN 5.3). Swipe either way deletes, with Undo. */
@Composable
internal fun GroceryRow(
    item: GroceryItem,
    state: GroceriesViewModel.UiState,
    onToggle: (GroceryItem) -> Unit,
    onDelete: (GroceryItem) -> Unit,
    onEdit: (GroceryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(item.id) { swipe.reset() }
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.testTag("grocery_item_${item.id}"),
        backgroundContent = {
            if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.errorContainer,
                    ).padding(horizontal = 20.dp),
                    contentAlignment = swipeAlignment(swipe.dismissDirection),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        onDismiss = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onDelete(item)
            scope.launch { swipe.reset() }
        },
    ) {
        Surface {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = {
                        // Light haptic on check-off (DESIGN 6.4).
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onToggle(item)
                    })
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                            color = nameColor(item.checked),
                        )
                        item.quantity?.let {
                            Text(
                                "× ${amountText(it, item.unit)}",
                                style = MaterialTheme.typography.bodyMedium,
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (item.source) {
                            ItemSource.LowStock -> SourceBadge(stringResource(R.string.grocery_source_low_stock))
                            ItemSource.Recipe -> SourceBadge(stringResource(R.string.grocery_source_recipe))
                            ItemSource.Manual -> Unit
                        }
                        val by = addedBy(item, state)
                        if (by != null) {
                            Text(
                                stringResource(R.string.grocery_added_by, by),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (item.id in state.backInStock) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourceBadge(stringResource(R.string.grocery_back_in_stock), good = true)
                            TextButton(onClick = { onDelete(item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.grocery_remove))
                            }
                        }
                    }
                }
                ItemMenu(item, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun nameColor(checked: Boolean) =
    if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

private fun swipeAlignment(direction: SwipeToDismissBoxValue) =
    if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd

/** "added by Sam" for items someone else typed in. */
private fun addedBy(item: GroceryItem, state: GroceriesViewModel.UiState): String? = item.createdBy
    ?.takeIf { it != state.myUid && item.source == ItemSource.Manual }
    ?.let { state.memberNames[it] }

/** A small label: tertiary for where an item came from, secondary for good news such as Back in stock. */
@Composable
private fun SourceBadge(text: String, good: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val container = if (good) colors.secondaryContainer else colors.tertiaryContainer
    val content = if (good) colors.onSecondaryContainer else colors.onTertiaryContainer
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier.background(
            container,
            MaterialTheme.shapes.small,
        ).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun ItemMenu(item: GroceryItem, onEdit: (GroceryItem) -> Unit, onDelete: (GroceryItem) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.grocery_item_options, item.name))
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.surface,
        ).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CartHeader(count: Int, open: Boolean, onToggle: () -> Unit, onClear: () -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().clickable(
                onClick = onToggle,
            ).heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.grocery_in_cart, count, count).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            TextButton(
                onClick = onClear,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.grocery_clear_checked)) }
        }
    }
}

/** The quick-add field stays at the top; typing suggests pantry products and names already used (DESIGN 5.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddField(text: String, onText: (String) -> Unit, suggestions: List<String>, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && suggestions.isNotEmpty(), onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                onText(it)
                open = true
            },
            placeholder = { Text(stringResource(R.string.grocery_quick_add)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.grocery_add_to_list))
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("grocery_quick_add")
                .coachMark(TourTarget.QuickAdd.key),
        )
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

private enum class ListDialog { Create, Rename, Delete }

/** List switcher and list management (DESIGN 5.3: users can add, rename and delete lists). */
@Composable
private fun ListMenu(
    state: GroceriesViewModel.UiState,
    onSelect: (GroceryList) -> Unit,
    onDialog: (ListDialog) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = stringResource(R.string.grocery_lists))
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
            title = { Text(stringResource(R.string.grocery_delete_list_title, currentName)) },
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
