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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.RecipeFilter
import com.parcha.spoilnot.feature.recipes.domain.RecipeSort
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl
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
    val fabExpanded by remember {
        derivedStateOf { !gridState.isScrollInProgress && gridState.firstVisibleItemIndex == 0 }
    }

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    TabScaffold(
        title = stringResource(R.string.tab_recipes),
        onOpenSettings = navigation.openSettings,
        modifier = modifier.testTag("screen_recipes"),
        actions = { if (!state.boxEmpty) SortMenu(state.query.sort, viewModel::setSort) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { choosing = true },
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.recipe_add)) },
                modifier = Modifier.testTag("fab_add_recipe"),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.offline && !state.loading) OfflineBanner()
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.boxEmpty -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    message = stringResource(R.string.empty_recipes),
                )

                else -> {
                    SearchField(state.query.search, viewModel::setSearch)
                    FilterRow(state.query.filter, state.tags, viewModel::setFilter)
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().testTag("recipe_grid"),
                    ) {
                        items(state.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onOpen = { navigation.openRecipe(recipe.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

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

/** The FAB's three choices (DESIGN 5.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipeSheet(onPaste: () -> Unit, onBrowse: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.recipe_add_paste)) },
                supportingContent = { Text(stringResource(R.string.recipe_add_paste_hint)) },
                leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onPaste).testTag("add_recipe_paste"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.recipe_add_browse)) },
                supportingContent = { Text(stringResource(R.string.recipe_add_browse_hint)) },
                leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onBrowse).testTag("add_recipe_browse"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.recipe_add_manual)) },
                leadingContent = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onManual).testTag("add_recipe_manual"),
            )
        }
    }
}

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
        title = { Text(stringResource(R.string.recipe_add_paste)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                label = { Text(stringResource(R.string.recipe_link)) },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.recipe_link_invalid)) }
                } else {
                    null
                },
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
                Text(stringResource(R.string.recipe_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.recipe_search)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("recipe_search"),
    )
}

/** All, Favorites, then the user's tags (DESIGN 5.5). */
@Composable
private fun FilterRow(selected: RecipeFilter, tags: List<String>, onSelect: (RecipeFilter) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chips = listOf(
            RecipeFilter.All to stringResource(R.string.recipe_filter_all),
            RecipeFilter.Favorites to stringResource(R.string.recipe_filter_favorites),
        ) + tags.map { RecipeFilter.Tag(it) to it }
        chips.forEach { (filter, label) ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                },
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
