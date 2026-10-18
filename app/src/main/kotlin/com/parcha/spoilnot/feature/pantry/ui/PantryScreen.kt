package com.parcha.spoilnot.feature.pantry.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.core.ui.rememberRemoteChanges
import com.parcha.spoilnot.core.ui.remoteHighlight
import com.parcha.spoilnot.feature.notifications.ui.NotificationExplainer
import com.parcha.spoilnot.feature.notifications.ui.Notifications
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryGrouping
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryItemGroup
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate

@Composable
fun PantryScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askForNotifications by viewModel.askForNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }
    // An Out staple opens Add item for that product.
    var addingFor by remember { mutableStateOf<Product?>(null) }
    var throwing by remember { mutableStateOf<StockBatch?>(null) }
    var moving by remember { mutableStateOf<StockBatch?>(null) }
    var editing by remember { mutableStateOf<StockBatch?>(null) }
    val listState = rememberLazyListState()
    // Collapses while scrolling or away from the top (DESIGN 6.5); derived so scrolling does not recompose the screen.
    val fabExpanded by remember {
        derivedStateOf { !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 }
    }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    val actions = BatchActions(
        onMinus = { viewModel.adjust(it, -1.0) },
        onPlus = { viewModel.adjust(it, 1.0) },
        onUsedUp = viewModel::usedUp,
        onThrowAway = { throwing = it },
        onMove = { moving = it },
    )

    TabScaffold(
        title = stringResource(R.string.tab_pantry),
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_pantry"),
        actions = { SortGroupMenu(state.query.sort, state.query.grouping, viewModel::setSort, viewModel::setGrouping) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.pantryEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_item)) },
                    modifier = Modifier.testTag("fab_add_item"),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.offline && !state.loading) OfflineBanner()
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.pantryEmpty -> EmptyPantry { adding = true }

                else -> {
                    SearchField(state.query.search, viewModel::setSearch)
                    FilterRow(state.query.filter, viewModel::setFilter)
                    PantryList(
                        groups = state.groups,
                        today = state.today,
                        listState = listState,
                        actions = actions,
                        onOpen = { item ->
                            when (item) {
                                is PantryItem.Purchase -> editing = item.batch

                                is PantryItem.Out -> {
                                    addingFor = item.product
                                    adding = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (askForNotifications) {
        if (Notifications.permissionGranted(context)) {
            LaunchedEffect(Unit) { viewModel.notificationExplainerDone() }
        } else {
            NotificationExplainer(onDone = viewModel::notificationExplainerDone)
        }
    }
    if (adding) {
        AddItemSheet(
            products = state.data.products,
            productsWithStock = state.data.batches.filter { it.quantity > 0 }.map { it.productId }.toSet(),
            today = state.today,
            onSave = { draft ->
                viewModel.add(draft) {
                    adding = false
                    addingFor = null
                }
            },
            onDismiss = {
                adding = false
                addingFor = null
            },
            prefill = addingFor,
        )
    }
    throwing?.let { batch ->
        ThrowAwayDialog(batch.productName, onReason = {
            viewModel.throwAway(batch, it)
        }, onDismiss = { throwing = null })
    }
    moving?.let { batch ->
        MoveDialog(batch.location, onPick = { viewModel.move(batch, it) }, onDismiss = {
            moving =
                null
        })
    }
    editing?.let { batch ->
        val product = state.data.products.firstOrNull { it.id == batch.productId }
        val others = state.data.batches.any { it.productId == batch.productId && it.id != batch.id && it.quantity > 0 }
        EditItemSheet(
            batch = batch,
            product = product,
            canChangeUnit = !others,
            today = state.today,
            onSave = { viewModel.editItem(batch, it) { editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryList(
    groups: List<PantryItemGroup>,
    today: LocalDate,
    listState: LazyListState,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
) {
    // A remote change must not move the list under the user's finger (DESIGN 6.4): hold updates while scrolling.
    var shown by remember { mutableStateOf(groups) }
    LaunchedEffect(groups, listState.isScrollInProgress) { if (!listState.isScrollInProgress) shown = groups }
    // Rows another phone changed get a short highlight (DESIGN 6.4).
    val remote = rememberRemoteChanges(
        shown.flatMap { it.items }.filterIsInstance<PantryItem.Purchase>().map { it.batch },
        id = { it.id },
        pending = { it.pendingSync },
        content = { it.copy(pendingSync = false) },
    )

    if (shown.isEmpty()) {
        EmptyState(icon = Icons.Rounded.Search, message = stringResource(R.string.pantry_no_matches))
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize().testTag("pantry_list"),
    ) {
        shown.forEach { group ->
            val header = group.location?.label() ?: group.category?.label()
            val groupKey = group.location?.name ?: group.category?.name ?: "all"
            if (header != null) {
                stickyHeader(key = "header-$groupKey") {
                    Text(
                        stringResource(header).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(group.items, key = { "$groupKey-${it.key}" }) { item ->
                PantryItemRow(
                    item = item,
                    today = today,
                    actions = actions,
                    onOpen = onOpen,
                    modifier = Modifier.animateItem().remoteHighlight(item.key in remote),
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.pantry_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = {
                    onChange("")
                }) { Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_cancel)) }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("pantry_search"),
    )
}

@Composable
private fun FilterRow(selected: PantryFilter, onSelect: (PantryFilter) -> Unit) {
    val scroll = rememberScrollState()
    // Where each chip starts, so a filter chosen elsewhere (a digest, a Home card) scrolls into view.
    val starts = remember { mutableStateMapOf<PantryFilter, Int>() }
    LaunchedEffect(selected, starts[selected]) {
        starts[selected]?.let { scroll.animateScrollTo((it - scroll.viewportSize / 3).coerceAtLeast(0)) }
    }
    Row(
        Modifier.horizontalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantryFilter.entries.forEach { filter ->
            FilterChip(
                modifier = Modifier.onPlaced { starts[filter] = it.positionInParent().x.toInt() },
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label())) },
                leadingIcon = if (filter == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
    HorizontalDivider()
}

private fun PantryFilter.label(): Int = when (this) {
    PantryFilter.All -> R.string.filter_all
    PantryFilter.Fridge -> R.string.location_fridge
    PantryFilter.Freezer -> R.string.location_freezer
    PantryFilter.Pantry -> R.string.location_pantry
    PantryFilter.Other -> R.string.location_other
    PantryFilter.ExpiringSoon -> R.string.filter_expiring_soon
    PantryFilter.Expired -> R.string.filter_expired
    PantryFilter.LowStock -> R.string.filter_low_stock
}

@Composable
private fun SortGroupMenu(
    sort: PantrySort,
    grouping: PantryGrouping,
    onSort: (PantrySort) -> Unit,
    onGroup: (PantryGrouping) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_and_group))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeader(R.string.sort_title)
            listOf(
                PantrySort.SoonestExpiry to R.string.sort_soonest,
                PantrySort.Name to R.string.sort_name,
                PantrySort.RecentlyAdded to R.string.sort_recent,
            )
                .forEach { (value, label) -> CheckItem(label, value == sort) { onSort(value) } }
            HorizontalDivider()
            MenuHeader(R.string.group_title)
            listOf(
                PantryGrouping.None to R.string.group_none,
                PantryGrouping.Location to R.string.group_location,
                PantryGrouping.Category to R.string.group_category,
            )
                .forEach { (value, label) -> CheckItem(label, value == grouping) { onGroup(value) } }
        }
    }
}

@Composable
private fun MenuHeader(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CheckItem(label: Int, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        leadingIcon = {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null) else Box(Modifier.size(24.dp))
        },
    )
}

/** DESIGN 6.7: a thin banner while working from the cache. */
@Composable
private fun EmptyPantry(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Outlined.Kitchen,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.pantry_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            stringResource(R.string.action_add_first_item),
            onClick = onAdd,
            modifier = Modifier.testTag("button_add_first_item"),
        )
    }
}
