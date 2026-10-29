package com.parcha.spoilnot.feature.recipes.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.ImportMethod
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeLines
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl

@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCook: (servings: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = hiltViewModel<RecipeDetailViewModel, RecipeDetailViewModel.Factory>(
        key = recipeId,
        creationCallback = { it.create(recipeId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingToList by rememberSaveable { mutableStateOf(false) }
    val recipe = state.recipe
    var servings by rememberSaveable(recipe?.content?.servings) { mutableStateOf(recipe?.content?.servings) }
    val context = LocalContext.current

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    DetailScaffold(
        title = "",
        onBack = onBack,
        modifier = modifier.testTag("screen_recipe_detail"),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (recipe != null && recipe.content.steps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onCook(servings) },
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipe_cook)) },
                    modifier = Modifier.testTag("recipe_cook"),
                )
            }
        },
        actions = {
            if (recipe != null) {
                IconButton(onClick = viewModel::toggleFavorite, modifier = Modifier.testTag("recipe_favorite")) {
                    Icon(
                        if (recipe.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (recipe.favorite) R.string.recipe_unfavorite else R.string.recipe_favorite,
                        ),
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.testTag("recipe_edit")) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.recipe_edit))
                }
                OverflowMenu(
                    onShare = { share(context, recipe) },
                    onDelete = { confirmDelete = true },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            recipe == null -> EmptyState(
                icon = Icons.Rounded.Delete,
                message = stringResource(R.string.recipe_gone),
                modifier = Modifier.padding(padding),
            )

            else -> RecipeBody(
                recipe = recipe,
                servings = servings,
                onServings = { servings = it },
                onAddToList = {
                    addingToList = true
                    viewModel.openGrocerySheet(true)
                },
                onCooked = { factor -> viewModel.openCookSheet(factor) },
                onOpenOriginal = { url -> openOriginal(context, url) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmDelete && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_body, recipe.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted = onBack)
                    },
                    modifier = Modifier.testTag("recipe_delete_confirm"),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    val cook by viewModel.cook.collectAsStateWithLifecycle()
    if (!cook.loading && recipe != null) {
        CookedSheet(
            lines = cook.lines,
            onUse = viewModel::cooked,
            onDismiss = { viewModel.openCookSheet(null) },
        )
    }
    if (addingToList && recipe != null) {
        val grocery by viewModel.grocery.collectAsStateWithLifecycle()
        AddToGrocerySheet(
            state = grocery,
            onChooseList = viewModel::chooseList,
            onAdd = { chosen -> viewModel.addToGrocery(chosen) },
            onDismiss = {
                addingToList = false
                viewModel.openGrocerySheet(false)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeBody(
    recipe: Recipe,
    servings: Int?,
    onServings: (Int) -> Unit,
    onAddToList: () -> Unit,
    onOpenOriginal: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCooked: (Double) -> Unit = {},
) {
    val c = recipe.content
    val factor = Scaling.factor(c.servings, servings)
    val checked = remember(recipe.id) { mutableStateListOf<Int>() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        if (c.imageUrl != null) RecipeImage(c.imageUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }.testTag("recipe_title"),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                durationText(c.totalMinutes)?.let { time ->
                    InfoChip(stringResource(R.string.recipe_total_time, time), Icons.Rounded.Schedule)
                }
                servingsLabel(c.servings, c.servingsText)?.let { InfoChip(it, Icons.Rounded.People) }
                val site = SourceUrl.siteName(c.sourceUrl)
                if (site != null && c.sourceUrl != null) {
                    AssistChip(
                        onClick = { onOpenOriginal(c.sourceUrl) },
                        label = { Text(site) },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    )
                }
            }
            c.sourceName?.let {
                Text(
                    stringResource(R.string.recipe_by, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            c.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (c.ingredients.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.recipe_ingredients), Modifier.weight(1f))
                    if (c.servings != null && servings != null) ServingsStepper(servings, onServings)
                }
                IngredientChecklist(c.ingredients.map { it.group to Scaling.line(it.raw, factor) }, checked)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddToList, modifier = Modifier.testTag("recipe_add_to_list")) {
                        Icon(Icons.Rounded.AddShoppingCart, contentDescription = null)
                        Text(stringResource(R.string.recipe_add_to_list), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { onCooked(factor) }, modifier = Modifier.testTag("recipe_cooked")) {
                        Icon(Icons.Rounded.SoupKitchen, contentDescription = null)
                        Text(stringResource(R.string.recipe_cooked_this), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (c.steps.isNotEmpty()) {
                SectionTitle(stringResource(R.string.recipe_steps))
                var group: String? = null
                c.steps.forEachIndexed { index, step ->
                    if (step.group != null && step.group != group) GroupTitle(step.group)
                    group = step.group
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 32.dp),
                        )
                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            c.notes?.let {
                SectionTitle(stringResource(R.string.recipe_notes))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            if (c.sourceUrl != null) {
                TextButton(
                    onClick = { onOpenOriginal(c.sourceUrl) },
                    modifier = Modifier.testTag("recipe_open_original"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text(stringResource(R.string.recipe_open_original), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Ingredient lines with group headings; tapping a line ticks it off. */
@Composable
fun IngredientChecklist(lines: List<Pair<String?, String>>, checked: MutableList<Int>, modifier: Modifier = Modifier) {
    Column(modifier) {
        var group: String? = null
        lines.forEachIndexed { index, (g, line) ->
            if (g != null && g != group) GroupTitle(g)
            group = g
            val isChecked = index in checked
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(isChecked, role = Role.Checkbox) { if (it) checked += index else checked -= index },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isChecked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    val label = pluralStringResource(R.plurals.recipe_servings, servings, servings)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(servings - 1) }, enabled = servings > 1) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.recipe_fewer_servings))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("recipe_servings"),
        )
        FilledTonalIconButton(onClick = { onChange(servings + 1) }, enabled = servings < 100) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.recipe_more_servings))
        }
    }
}

/** A fact about the recipe, styled like a chip but not clickable. */
@Composable
private fun InfoChip(label: String, icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun OverflowMenu(onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("recipe_more")) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recipe_share)) },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = {
                    open = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
                modifier = Modifier.testTag("recipe_delete"),
            )
        }
    }
}

/** Open original in a Custom Tab (DESIGN 7.3). */
private fun openOriginal(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri()) }
}

/**
 * Imported recipes are shared as their title and link, never re-published (DESIGN 11.6); recipes typed in by the
 * household are shared in full.
 */
private fun share(context: Context, recipe: Recipe) {
    val c = recipe.content
    val text = if (c.importMethod == ImportMethod.Web && c.sourceUrl != null) {
        "${recipe.title}\n${c.sourceUrl}"
    } else {
        listOfNotNull(
            recipe.title,
            RecipeLines.ingredientsToText(c.ingredients).ifEmpty { null },
            c.steps.mapIndexed { i, s -> "${i + 1}. ${s.text}" }.joinToString("\n").ifEmpty { null },
            c.notes,
            c.sourceUrl,
        ).joinToString("\n\n")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_SUBJECT, recipe.title)
    context.startActivity(Intent.createChooser(send, null))
}
