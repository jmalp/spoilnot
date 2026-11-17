package com.parcha.spoilnot.feature.recipes.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.BackButton
import com.parcha.spoilnot.core.designsystem.component.BottomActionBar
import com.parcha.spoilnot.core.designsystem.component.CountStepper
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.NameWithTrailing
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.RoundIconButton
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.LeafShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
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
    var addingToList by rememberSaveable { mutableStateOf(false) }
    val recipe = state.recipe
    var servings by rememberSaveable(recipe?.content?.servings) { mutableStateOf(recipe?.content?.servings) }
    val context = LocalContext.current

    MessageEffect(viewModel.messages, snackbar, viewModel::undo)

    Scaffold(
        modifier = modifier.testTag("screen_recipe_detail"),
        containerColor = SpoilNotSurfaces.current.page,
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
        // In a bar under the content, not floating (after v1, UX-12): the floating Cook button covered I cooked this.
        bottomBar = {
            if (recipe != null && recipe.content.steps.isNotEmpty()) StartCookingBar { onCook(servings) }
        },
        // The picture runs under the status bar; the round buttons sit below it.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    recipe == null -> EmptyState(
                        icon = Icons.Rounded.Delete,
                        message = stringResource(R.string.recipe_gone),
                        modifier = Modifier.statusBarsPadding(),
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
                    )
                }
                // Back, the star, Edit and ⋮ stay over the picture and the page while it scrolls (DESIGN 19.7).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BackButton(onBack)
                    Spacer(Modifier.weight(1f))
                    if (recipe != null) {
                        // The same toggle as on the cards (review A11Y-08: here it was a plain button).
                        FavoriteToggle(
                            recipe.favorite,
                            viewModel::toggleFavorite,
                            Modifier.testTag("recipe_favorite"),
                            round = true,
                        )
                        RoundIconButton(
                            Icons.Rounded.Edit,
                            stringResource(R.string.recipe_edit),
                            onEdit,
                            Modifier.testTag("recipe_edit"),
                        )
                        // Undo, no dialog (after v1, R13 and DESIGN 6.1): the recipe box offers it after leaving.
                        OverflowMenu(
                            onShare = { share(context, recipe) },
                            onDelete = { viewModel.delete(onDeleted = onBack) },
                        )
                    }
                }
            }
        }
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

/** The block at the top of a recipe, under the status bar: its photo, or its letter in a leaf (DESIGN 19.12). */
private val HeaderHeight = 220.dp

/** How far the page overlaps the header, with its rounded top. */
private val PageOverlap = 28.dp

/**
 * A recipe (DESIGN 5.5, 19.12): the photo or the letter block, then the page with the title, the facts as pills, the
 * ingredients as a checklist with the servings stepper beside their heading, Add to grocery list and I cooked this,
 * and the steps with their numbers in leaves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeBody(
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
    val page = SpoilNotSurfaces.current.page
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RecipeImage(
            c.imageUrl,
            Modifier.fillMaxWidth().height(HeaderHeight + statusBar),
            recipe.title,
            RecipeLetterSize.Header,
        )
        Column(
            Modifier
                .offset(y = -PageOverlap)
                .fillMaxWidth()
                .background(page, RoundedCornerShape(topStart = PageOverlap, topEnd = PageOverlap))
                .padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    recipe.title,
                    style = SpoilNotText.ScreenTitle,
                    modifier = Modifier.semantics { heading() }.testTag("recipe_title"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    durationText(c.totalMinutes)?.let { time ->
                        InfoPill(stringResource(R.string.recipe_total_time, time), Icons.Rounded.Schedule)
                    }
                    // One servings control (after v1, R9): the pill only when there is no stepper to show the number.
                    val stepper = c.ingredients.isNotEmpty() && c.servings != null && servings != null
                    if (!stepper) servingsLabel(c.servings, c.servingsText)?.let { InfoPill(it, Icons.Rounded.People) }
                    val site = SourceUrl.siteName(c.sourceUrl)
                    if (site != null && c.sourceUrl != null) {
                        InfoPill(site, Icons.Rounded.Language, onClick = { onOpenOriginal(c.sourceUrl) })
                    }
                }
                // One source line (after v1, R10): "By …" only when it names someone other than the site.
                c.sourceName?.takeUnless { SourceUrl.namesSite(it, SourceUrl.siteName(c.sourceUrl)) }?.let {
                    Text(
                        stringResource(R.string.recipe_by, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                c.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            if (c.ingredients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // From 1.5× text the stepper goes under the title; beside it, "Ingredients" broke mid-word at 200%.
                    NameWithTrailing(
                        spacing = 8.dp,
                        name = { SectionTitle(stringResource(R.string.recipe_ingredients), it) },
                        trailing = {
                            if (c.servings != null && servings != null) {
                                ServingsStepper(servings, Scaling.maxServings(c.servings), onServings)
                            }
                        },
                    )
                    val pinch = stringResource(R.string.recipe_scaled_pinch)
                    IngredientChecklist(c.ingredients.map { it.group to Scaling.line(it.raw, factor, pinch) }, checked)
                    CookActions(onAddToList = onAddToList, onCooked = { onCooked(factor) })
                }
            }
            if (c.steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(stringResource(R.string.recipe_steps))
                    StepList(c.steps.map { it.group to it.text })
                }
            }
            c.notes?.let {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.recipe_notes))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (c.sourceUrl != null) {
                TextButton(
                    onClick = { onOpenOriginal(c.sourceUrl) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("recipe_open_original"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text(
                        stringResource(R.string.recipe_open_original),
                        style = SpoilNotText.ButtonMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Add to grocery list and I cooked this (DESIGN 19.9): tonal buttons side by side, one under the other from 1.5×. */
@Composable
private fun CookActions(onAddToList: () -> Unit, onCooked: () -> Unit) {
    val add = @Composable { buttonModifier: Modifier ->
        TonalButton(
            text = stringResource(R.string.recipe_add_to_list),
            onClick = onAddToList,
            icon = Icons.Rounded.AddShoppingCart,
            modifier = buttonModifier.testTag("recipe_add_to_list"),
        )
    }
    val cooked = @Composable { buttonModifier: Modifier ->
        TonalButton(
            text = stringResource(R.string.recipe_cooked_this),
            onClick = onCooked,
            icon = Icons.Rounded.SoupKitchen,
            modifier = buttonModifier.testTag("recipe_cooked"),
        )
    }
    if (isLargeText()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            add(Modifier.fillMaxWidth())
            cooked(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            add(Modifier.weight(1f).fillMaxHeight())
            cooked(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * Lines in their groups (DESIGN 19.5), each group under its heading: [lines] are (group, line). [row] draws each line
 * on a row of a group with its [color]; consecutive lines of the same group share it.
 */
@Composable
internal fun <T> GroupedRows(
    lines: List<Pair<String?, T>>,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, line: T, position: SegmentPosition) -> Unit,
) {
    // Consecutive lines with the same group, with each line's index in [lines].
    val groups = mutableListOf<Pair<String?, MutableList<Pair<Int, T>>>>()
    lines.forEachIndexed { index, (group, line) ->
        if (groups.isEmpty() || groups.last().first != group) groups += group to mutableListOf()
        groups.last().second += index to line
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (group, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (group != null) GroupHeading(group)
                Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                    items.forEachIndexed { i, (index, line) -> row(index, line, segmentPosition(i, items.size)) }
                }
            }
        }
    }
}

/**
 * Ingredient lines as a checklist (DESIGN 19.5): rows of a group with a checkbox, under each ingredient group's
 * heading; tapping a line ticks it off. On a sheet pass the field [color].
 */
@Composable
fun IngredientChecklist(
    lines: List<Pair<String?, String>>,
    checked: MutableList<Int>,
    modifier: Modifier = Modifier,
    color: Color = SpoilNotSurfaces.current.row,
) {
    GroupedRows(lines, modifier) { index, line, position ->
        val isChecked = index in checked
        Surface(shape = segmentShape(position), color = color, contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(isChecked, role = Role.Checkbox) { if (it) checked += index else checked -= index }
                    .heightIn(min = ListSegmentMinHeightOneLine)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Checkbox(checked = isChecked, onCheckedChange = null)
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
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

/**
 * The steps (DESIGN 19.12): rows of a group, each with its number in a leaf, under each step group's heading. [steps]
 * are (group, text); each row is read as one, "1, Whisk the eggs".
 */
@Composable
internal fun StepList(steps: List<Pair<String?, String>>, color: Color = SpoilNotSurfaces.current.row) {
    GroupedRows(steps) { index, text, position ->
        Surface(shape = segmentShape(position), color = color, contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ListSegmentMinHeightOneLine)
                    .semantics(mergeDescendants = true) {}
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepLeaf(index + 1, StepLeafSize)
                Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** A step's number in a leaf (DESIGN 19.3): 34 dp in the list, 64 dp in cook mode. */
@Composable
internal fun StepLeaf(number: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, LeafShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = SpoilNotText.ButtonMedium,
            fontSize = (size.value * 0.45f).sp,
            lineHeight = (size.value * 0.45f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The step leaf's size in the list of steps. */
private val StepLeafSize = 34.dp

/** The servings beside Ingredients (DESIGN 19.9): − and + joined to "4 servings", 48 dp, announced as it changes. */
@Composable
private fun ServingsStepper(servings: Int, max: Int, onChange: (Int) -> Unit) {
    CountStepper(
        value = pluralStringResource(R.plurals.recipe_servings, servings, servings),
        minusLabel = stringResource(R.string.recipe_fewer_servings),
        plusLabel = stringResource(R.string.recipe_more_servings),
        onMinus = { onChange(servings - 1) },
        onPlus = { onChange(servings + 1) },
        minusEnabled = servings > 1,
        plusEnabled = servings < max,
        valueTag = "recipe_servings",
        size = 48.dp,
        valueStyle = SpoilNotText.ButtonMedium,
        announce = true,
    )
}

/** "Start cooking" across the bottom, above the navigation bar; the content scrolls clear of it. */
@Composable
internal fun StartCookingBar(onClick: () -> Unit) {
    BottomActionBar {
        PrimaryButton(
            text = stringResource(R.string.recipe_cook),
            onClick = onClick,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.testTag("recipe_cook"),
        )
    }
}

/** A section's heading on a recipe: Ingredients, Steps, Notes. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SpoilNotText.SectionTitle,
        modifier = modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
}

@Composable
private fun OverflowMenu(onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(
            Icons.Rounded.MoreVert,
            stringResource(R.string.more_options),
            { open = true },
            Modifier.testTag("recipe_more"),
        )
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
