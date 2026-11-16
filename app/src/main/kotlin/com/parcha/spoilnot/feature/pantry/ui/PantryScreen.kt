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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SearchField
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFilterChip
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.component.listBottomPadding
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.LeafShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.core.ui.rememberHeldWhileScrolling
import com.parcha.spoilnot.core.ui.rememberRemoteChanges
import com.parcha.spoilnot.core.ui.remoteHighlight
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryBlocks
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryGrouping
import com.parcha.spoilnot.feature.pantry.domain.PantryHeading
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryItemGroup
import com.parcha.spoilnot.feature.pantry.domain.PantryLine
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import java.time.LocalDate

@Composable
fun PantryScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }
    // An Out staple opens Add item for that product.
    var addingFor by remember { mutableStateOf<Product?>(null) }
    var throwing by remember { mutableStateOf<StockBatch?>(null) }
    var moving by remember { mutableStateOf<StockBatch?>(null) }
    var opening by remember { mutableStateOf<StockBatch?>(null) }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    PantryTab(
        state = state,
        snackbar = snackbar,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        handlers = PantryHandlers(
            onAdd = { adding = true },
            onSearch = viewModel::setSearch,
            onFilter = viewModel::setFilter,
            onSort = viewModel::setSort,
            onGroup = viewModel::setGrouping,
            onRetry = viewModel::retryLoad,
            onOpen = { item ->
                when (item) {
                    is PantryItem.Purchase -> viewModel.openEdit(item.batch)

                    is PantryItem.Out -> {
                        addingFor = item.product
                        adding = true
                    }
                }
            },
            actions = BatchActions(
                onMinus = { viewModel.adjust(it, -1.0) },
                onPlus = { viewModel.adjust(it, 1.0) },
                onUsedUp = viewModel::usedUp,
                onThrowAway = { throwing = it },
                onMove = { moving = it },
                onOpened = { opening = it },
            ),
        ),
    )

    if (adding) {
        AddItemSheet(
            products = state.data.products,
            // Once per change of the pantry, not on every recomposition while the sheet is open (review PERF-08).
            productsWithStock = remember(state.data.batches) {
                state.data.batches.filter { it.quantity > 0 }.mapTo(HashSet()) { it.productId }
            },
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
    opening?.let { batch ->
        val product = state.data.products.firstOrNull { it.id == batch.productId }
        OpenedSheet(
            batch = batch,
            rememberedDays = product?.openedShelfLifeDays,
            today = state.today,
            onSave = { count, days ->
                viewModel.markOpened(batch, count, days, product)
                opening = null
            },
            onDismiss = { opening = null },
        )
    }
    // The Edit window watches its item while open (after v1, DESIGN 9.2); the view model closes it when the item goes.
    state.edit?.let { edit ->
        key(edit.base.batch.id) {
            EditItemSheet(
                batch = edit.base.batch,
                product = edit.base.product,
                canChangeUnit = edit.canChangeUnit,
                today = state.today,
                onSave = viewModel::saveEdit,
                onDismiss = viewModel::closeEdit,
                base = edit.base.edit,
                changed = edit.changed,
                changedBy = edit.changedBy,
                onReviewed = viewModel::reviewEdit,
            )
        }
    }
}

/** What the Pantry tab does with a tap, so [PantryTab] can be drawn without its view model. */
internal data class PantryHandlers(
    val onAdd: () -> Unit,
    val onSearch: (String) -> Unit,
    val onFilter: (PantryFilter) -> Unit,
    val onSort: (PantrySort) -> Unit,
    val onGroup: (PantryGrouping) -> Unit,
    val onRetry: () -> Unit,
    val onOpen: (PantryItem) -> Unit,
    val actions: BatchActions,
)

/**
 * The Pantry tab (DESIGN 5.2, 19.12): how many rows the list has above the title, the search field, the filter chips
 * and the list, in two groups in the default view.
 */
@Composable
internal fun PantryTab(
    state: PantryViewModel.UiState,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    handlers: PantryHandlers,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    TabScaffold(
        title = stringResource(R.string.tab_pantry),
        eyebrow = if (state.loading) {
            null
        } else {
            pluralStringResource(R.plurals.pantry_eyebrow_items, state.itemCount, state.itemCount)
        },
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_pantry"),
        actions = { SortGroupMenu(state.query.sort, state.query.grouping, handlers.onSort, handlers.onGroup) },
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.pantryEmpty) {
                SpoilNotFab(
                    text = stringResource(R.string.action_add_item),
                    icon = Icons.Rounded.Add,
                    onClick = handlers.onAdd,
                    modifier = Modifier.testTag("fab_add_item").coachMark(TourTarget.AddItem.key),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loadFailed) {
                LoadError(onRetry = handlers.onRetry)
            } else if (state.offline && !state.loading) {
                OfflineBanner()
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!state.loadFailed) CircularProgressIndicator()
                }

                state.pantryEmpty -> EmptyPantry(handlers.onAdd)

                else -> {
                    SearchField(
                        value = state.query.search,
                        onValueChange = handlers.onSearch,
                        placeholder = stringResource(R.string.pantry_search),
                        clearLabel = stringResource(R.string.action_cancel),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("pantry_search"),
                    )
                    FilterRow(state.query.filter, handlers.onFilter)
                    PantryList(
                        groups = state.groups,
                        query = state.query,
                        today = state.today,
                        listState = listState,
                        actions = handlers.actions,
                        onOpen = handlers.onOpen,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryList(
    groups: List<PantryItemGroup>,
    query: PantryQuery,
    today: LocalDate,
    listState: LazyListState,
    actions: BatchActions,
    onOpen: (PantryItem) -> Unit,
) {
    // A remote change must not move the list under the user's finger (DESIGN 6.4): hold updates while scrolling.
    val shown = rememberHeldWhileScrolling(groups, listState)
    // Rows another phone changed get a short highlight (DESIGN 6.4).
    val batches =
        remember(shown) { shown.flatMap { it.items }.filterIsInstance<PantryItem.Purchase>().map { it.batch } }
    val remote = rememberRemoteChanges(
        batches,
        id = { it.id },
        pending = { it.pendingSync },
        content = { it.copy(pendingSync = false) },
    )

    if (shown.isEmpty()) {
        EmptyState(icon = Icons.Rounded.Search, message = stringResource(R.string.pantry_no_matches))
        return
    }
    // A backlog of expired food folds into one row (after v1); its rows show on request.
    var showExpired by rememberSaveable { mutableStateOf(false) }
    val blocks = remember(shown, query, showExpired) { PantryBlocks.of(shown, query, showExpired) }
    val page = SpoilNotSurfaces.current.page
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding()),
        modifier = Modifier.fillMaxSize().testTag("pantry_list").coachMark(TourTarget.PantryList.key),
    ) {
        blocks.forEachIndexed { blockIndex, block ->
            // Groups sit 12 dp apart; a heading carries the gap above its group.
            val gap = if (blockIndex == 0) 0.dp else BlockGap
            block.heading?.let { heading ->
                stickyHeader(key = "heading-${block.key}", contentType = "heading") {
                    // On the page's color, so rows scrolling under it don't show through. TalkBack's headings
                    // navigation jumps from group to group (review A11Y-09).
                    GroupHeading(
                        heading.text(),
                        Modifier.fillMaxWidth().background(page).padding(top = gap, bottom = 6.dp),
                    )
                }
            }
            // A scrolled-off slot is reused only for the same kind of row (review PERF-08).
            itemsIndexed(
                block.lines,
                key = { _, line -> "${block.key}-${line.key}" },
                contentType = { _, line -> line.contentType() },
            ) { index, line ->
                val position = segmentPosition(index, block.lines.size)
                val top = when {
                    index > 0 -> ListGroupGap
                    block.heading == null -> gap
                    else -> 0.dp
                }
                val rowModifier = Modifier.animateItem().padding(top = top)
                when (line) {
                    is PantryLine.Folded -> ExpiredFoldRow(
                        count = line.count,
                        shown = showExpired,
                        onToggle = { showExpired = !showExpired },
                        modifier = rowModifier,
                        position = position,
                    )

                    is PantryLine.Item -> PantryItemRow(
                        item = line.item,
                        today = today,
                        actions = actions,
                        onOpen = onOpen,
                        modifier = rowModifier.remoteHighlight(line.item.key in remote, segmentShape(position)),
                        position = position,
                    )
                }
            }
        }
    }
}

private fun PantryLine.contentType(): String = when (this) {
    is PantryLine.Folded -> "folded"
    is PantryLine.Item -> if (item is PantryItem.Out) "out" else "purchase"
}

/** The space between two groups of the list. */
private val BlockGap = 12.dp

@Composable
private fun PantryHeading.text(): String = when (this) {
    PantryHeading.UseFirst -> stringResource(R.string.pantry_use_first)
    PantryHeading.EverythingElse -> stringResource(R.string.pantry_everything_else)
    is PantryHeading.At -> stringResource(location.label())
    is PantryHeading.Kind -> stringResource(category.label())
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
        Modifier.horizontalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // No "All" chip (beta feedback): showing everything is the default, and tapping the chosen filter again
        // goes back to it.
        PantryFilter.entries.filter { it != PantryFilter.All }.forEach { filter ->
            SpoilNotFilterChip(
                text = stringResource(filter.label()),
                selected = filter == selected,
                onClick = { onSelect(if (filter == selected) PantryFilter.All else filter) },
                icon = filter.location()?.icon(),
                modifier = Modifier
                    .onPlaced { starts[filter] = it.positionInParent().x.toInt() }
                    .testTag("pantry_filter_${filter.name}"),
            )
        }
    }
}

private fun PantryFilter.label(): Int = when (this) {
    PantryFilter.All -> R.string.filter_all
    PantryFilter.Fridge -> R.string.location_fridge
    PantryFilter.Freezer -> R.string.location_freezer
    PantryFilter.Pantry -> R.string.location_pantry
    PantryFilter.Other -> R.string.location_other
    PantryFilter.NeedsAttention -> R.string.filter_needs_attention
    PantryFilter.Expired -> R.string.filter_expired
    PantryFilter.LowStock -> R.string.filter_low_stock
}

/** The place a location filter shows, for its chip's icon. */
private fun PantryFilter.location() = when (this) {
    PantryFilter.Fridge -> Location.Fridge
    PantryFilter.Freezer -> Location.Freezer
    PantryFilter.Pantry -> Location.Pantry
    PantryFilter.Other -> Location.Other
    else -> null
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

/** An empty pantry: the kitchen in a leaf (DESIGN 19.3), one sentence, and the main button. */
@Composable
private fun EmptyPantry(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(EmptyLeafSize).background(MaterialTheme.colorScheme.primaryContainer, LeafShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Kitchen,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            stringResource(R.string.pantry_empty_body),
            style = EmptySentence,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            stringResource(R.string.action_add_first_item),
            onClick = onAdd,
            modifier = Modifier.testTag("button_add_first_item").coachMark(TourTarget.AddItem.key),
        )
    }
}

/** The empty pantry's sentence, 18 / 26 (the mockup's). */
private val EmptySentence = SpoilNotText.FieldText.copy(fontSize = 18.sp, lineHeight = 26.sp)

/** The leaf of an empty pantry. */
private val EmptyLeafSize = 132.dp
