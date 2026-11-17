package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeight
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SearchField
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFab
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFilterChip
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.component.listBottomPadding
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeFilter
import com.parcha.spoilnot.feature.recipes.domain.RecipeSort
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import kotlinx.coroutines.launch

/** Where the recipe box can send the user. */
class RecipesNavigation(
    val openSettings: () -> Unit,
    val openRecipe: (String) -> Unit,
    val importLink: (String) -> Unit,
    val openBrowser: () -> Unit,
    val createManually: () -> Unit,
)

@Composable
fun RecipesScreen(
    navigation: RecipesNavigation,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    RecipesTab(
        state = state,
        snackbar = snackbar,
        navigation = navigation,
        gridState = gridState,
        modifier = modifier,
        handlers = RecipesHandlers(
            onSort = viewModel::setSort,
            onRetry = viewModel::retryLoad,
            onSearch = viewModel::setSearch,
            onFilter = viewModel::setFilter,
            onToggleFavorite = viewModel::toggleFavorite,
            onAdd = { choosing = true },
            onPaste = { pasting = true },
        ),
    )

    if (choosing) {
        AddRecipeSheet(
            onPaste = {
                choosing = false
                pasting = true
            },
            onBrowse = {
                choosing = false
                navigation.openBrowser()
            },
            onManual = {
                choosing = false
                navigation.createManually()
            },
            onDismiss = { choosing = false },
        )
    }
    if (pasting) {
        PasteLinkDialog(
            onImport = {
                pasting = false
                navigation.importLink(it)
            },
            onDismiss = { pasting = false },
        )
    }
}

/** What the Recipes tab's controls do; the sheets and dialogs they open belong to [RecipesScreen]. */
internal class RecipesHandlers(
    val onSort: (RecipeSort) -> Unit,
    val onRetry: () -> Unit,
    val onSearch: (String) -> Unit,
    val onFilter: (RecipeFilter) -> Unit,
    val onToggleFavorite: (Recipe) -> Unit,
    val onAdd: () -> Unit,
    val onPaste: () -> Unit,
)

/**
 * The Recipes tab (DESIGN 5.5, 19.7, 19.12): how many recipes above the title, the round search field and the chips,
 * and the recipes as cards in a grid, or the three ways to add one when the box is empty.
 */
@Composable
internal fun RecipesTab(
    state: RecipesViewModel.UiState,
    snackbar: SnackbarHostState,
    navigation: RecipesNavigation,
    handlers: RecipesHandlers,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    TabScaffold(
        title = stringResource(R.string.tab_recipes),
        // How many recipes the box holds (DESIGN 19.7).
        eyebrow = if (state.loading || state.boxEmpty) {
            null
        } else {
            pluralStringResource(R.plurals.recipe_eyebrow_count, state.total, state.total)
        },
        onOpenSettings = navigation.openSettings,
        modifier = modifier.testTag("screen_recipes"),
        actions = { if (!state.boxEmpty) SortMenu(state.query.sort, handlers.onSort) },
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        floatingActionButton = {
            SpoilNotFab(
                text = stringResource(R.string.recipe_add),
                icon = Icons.Rounded.Add,
                onClick = handlers.onAdd,
                modifier = Modifier.testTag("fab_add_recipe").coachMark(TourTarget.AddRecipe.key),
            )
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

                // The three ways to add a recipe, right where the box is empty (after v1).
                state.boxEmpty -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    message = stringResource(R.string.empty_recipes),
                ) {
                    // The main button, a tonal second and a text button (DESIGN 19.9).
                    PrimaryButton(
                        text = stringResource(R.string.recipe_add_paste),
                        onClick = handlers.onPaste,
                        modifier = Modifier.testTag("empty_recipes_paste"),
                    )
                    TonalButton(
                        text = stringResource(R.string.recipe_add_browse),
                        onClick = navigation.openBrowser,
                        modifier = Modifier.fillMaxWidth().testTag("empty_recipes_browse"),
                    )
                    TextButton(
                        onClick = navigation.createManually,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("empty_recipes_manual"),
                    ) { Text(stringResource(R.string.recipe_add_manual), style = SpoilNotText.ButtonMedium) }
                }

                else -> {
                    // The page's round search field and the chips on the page (DESIGN 19.10), as on Pantry.
                    SearchField(
                        value = state.query.search,
                        onValueChange = handlers.onSearch,
                        placeholder = stringResource(R.string.recipe_search),
                        clearLabel = stringResource(R.string.action_clear_search),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("recipe_search"),
                    )
                    FilterRow(state.query.filter, state.tags, handlers.onFilter)
                    if (state.recipes.isEmpty()) {
                        Text(
                            stringResource(R.string.recipe_no_matches),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = listBottomPadding(),
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().testTag("recipe_grid"),
                    ) {
                        items(state.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onOpen = { navigation.openRecipe(recipe.id) },
                                onToggleFavorite = { handlers.onToggleFavorite(recipe) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The floating button's three ways to add a recipe (DESIGN 5.5, 19.11): a group of rows on the field color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipeSheet(onPaste: () -> Unit, onBrowse: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    SpoilNotSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.recipe_add), style = SpoilNotText.SheetTitle)
            Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                val ways = listOf(
                    AddWay(
                        Icons.Rounded.Link,
                        R.string.recipe_add_paste,
                        R.string.recipe_add_paste_hint,
                        "add_recipe_paste",
                        onPaste,
                    ),
                    AddWay(
                        Icons.Rounded.Language,
                        R.string.recipe_add_browse,
                        R.string.recipe_add_browse_hint,
                        "add_recipe_browse",
                        onBrowse,
                    ),
                    AddWay(Icons.Rounded.EditNote, R.string.recipe_add_manual, null, "add_recipe_manual", onManual),
                )
                ways.forEachIndexed { index, way ->
                    ListSegment(
                        position = segmentPosition(index, ways.size),
                        onClick = way.onClick,
                        color = SpoilNotSurfaces.current.field,
                        minHeight = if (way.hint == null) ListSegmentMinHeightOneLine else ListSegmentMinHeight,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag(way.tag),
                    ) {
                        Icon(way.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(way.title), style = SpoilNotText.RowTitle)
                            way.hint?.let {
                                Text(
                                    stringResource(it),
                                    style = SpoilNotText.RowSupporting,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One way to add a recipe: its icon, words and what it does. */
private class AddWay(val icon: ImageVector, val title: Int, val hint: Int?, val tag: String, val onClick: () -> Unit)

@Composable
private fun PasteLinkDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val submit = {
        val url = SourceUrl.fromPasted(text)
        if (url == null) invalid = true else onImport(url)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_add_paste), style = SpoilNotText.DialogTitle) },
        text = {
            FormTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                label = stringResource(R.string.recipe_link),
                error = if (invalid) stringResource(R.string.recipe_link_invalid) else null,
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val clip = clipboard.getClipEntry()?.clipData
                            val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                            if (pasted != null) {
                                text = pasted
                                invalid = false
                            }
                        }
                    }) { Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.action_paste)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.testTag("paste_link_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, modifier = Modifier.testTag("paste_link_import")) {
                Text(stringResource(R.string.recipe_import), style = SpoilNotText.ButtonMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}

/**
 * Favorites, then the user's tags (DESIGN 5.5). No All chip (after v1, R16), as on Pantry: everything is the default,
 * and tapping the chosen chip again goes back to it.
 */
@Composable
private fun FilterRow(selected: RecipeFilter, tags: List<String>, onSelect: (RecipeFilter) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chips = listOf(RecipeFilter.Favorites to stringResource(R.string.recipe_filter_favorites)) +
            tags.map { RecipeFilter.Tag(it) to it }
        chips.forEach { (filter, label) ->
            val isSelected = filter == selected
            SpoilNotFilterChip(
                text = label,
                selected = isSelected,
                onClick = { onSelect(if (isSelected) RecipeFilter.All else filter) },
                // Favorites keeps its star, as the cards show it (DESIGN 19.10).
                icon = if (filter == RecipeFilter.Favorites) Icons.Rounded.StarBorder else null,
            )
        }
    }
}

@Composable
private fun SortMenu(sort: RecipeSort, onSort: (RecipeSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.recipe_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                RecipeSort.Recent to R.string.recipe_sort_recent,
                RecipeSort.AToZ to R.string.recipe_sort_az,
            ).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                    leadingIcon = if (option == sort) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.recipe_sort_selected),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
