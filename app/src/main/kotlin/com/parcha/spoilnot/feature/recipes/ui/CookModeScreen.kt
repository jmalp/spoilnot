package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.BackButton
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.rememberSaveableList
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import kotlinx.coroutines.launch

/** Cook mode (DESIGN 5.5, 19.12): the screen stays awake, large type, one step at a time, ingredients in a sheet. */
@Composable
fun CookModeScreen(
    recipeId: String,
    servings: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = hiltViewModel<RecipeDetailViewModel, RecipeDetailViewModel.Factory>(
        key = "cook_$recipeId",
        creationCallback = { it.create(recipeId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val recipe = state.recipe
    Box(
        modifier
            .fillMaxSize()
            .background(SpoilNotSurfaces.current.page)
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag("screen_cook_mode"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
            if (recipe == null) {
                BackButton(onBack, Modifier.padding(start = 16.dp, top = 8.dp))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.recipe_gone))
                }
            } else {
                CookSteps(recipe, servings, onBack = onBack)
            }
        }
    }
}

/**
 * Cook mode's page (DESIGN 19.12): Back and the recipe's name with "Step 1 of 2" as a chip, a bar per step, the step
 * on a card with its number in a leaf, and Back, Ingredients and Next joined at the bottom. Swiping turns the page too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CookSteps(recipe: Recipe, servings: Int?, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val steps = recipe.content.steps
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val checked = rememberSaveableList<Int>()
    val current = pager.currentPage
    val progress = stringResource(R.string.recipe_step_of, current + 1, steps.size)
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackButton(onBack)
                Text(
                    recipe.title,
                    style = SpoilNotText.RowTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // Said again as the page turns (it was a line above the step).
                Text(
                    progress,
                    style = SpoilNotText.ButtonMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            StepBars(steps.size, current)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("cook_pager")) { page ->
            val step = steps[page]
            Surface(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                shape = RoundedCornerShape(SpoilNotCorners.Highlight),
                color = SpoilNotSurfaces.current.row,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // The chip says the step; the leaf is a picture of it.
                    StepLeaf(page + 1, 64.dp, Modifier.clearAndSetSemantics { })
                    step.group?.let {
                        Text(it, style = SpoilNotText.GroupHeading, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        step.text,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("cook_step"),
                    )
                }
            }
        }
        CookControls(
            canGoBack = current > 0,
            canGoNext = current < steps.lastIndex,
            onBack = { scope.launch { pager.animateScrollToPage(current - 1) } },
            onIngredients = { showIngredients = true },
            onNext = { scope.launch { pager.animateScrollToPage(current + 1) } },
        )
    }
    if (showIngredients) {
        val factor = Scaling.factor(recipe.content.servings, servings)
        val pinch = stringResource(R.string.recipe_scaled_pinch)
        SpoilNotSheet(onDismissRequest = { showIngredients = false }) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(stringResource(R.string.recipe_ingredients), style = SpoilNotText.SheetTitle)
                IngredientChecklist(
                    recipe.content.ingredients.map { it.group to Scaling.line(it.raw, factor, pinch) },
                    checked,
                    color = SpoilNotSurfaces.current.field,
                )
            }
        }
    }
}

/** One bar per step, those done and the current one filled: a picture of where the cook is, which the chip says. */
@Composable
private fun StepBars(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(if (count <= MANY_STEPS) 4.dp else 2.dp),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        if (index <= current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** More steps than this draw their bars closer together. */
private const val MANY_STEPS = 12

/** Cook mode's buttons are 60 dp tall (DESIGN 19.9), the joined ones 2 dp apart. */
private val CookButtonHeight = 60.dp

/**
 * Back, Ingredients and Next joined (DESIGN 19.9): Back and Ingredients tonal, Next filled. From 1.5× text
 * Ingredients has its own line, over Back and Next; between them it broke mid-word.
 */
@Composable
private fun CookControls(
    canGoBack: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onIngredients: () -> Unit,
    onNext: () -> Unit,
) {
    val round = CornerSize(percent = 50)
    val joined = CornerSize(SpoilNotCorners.Connected)
    val start = RoundedCornerShape(topStart = round, bottomStart = round, topEnd = joined, bottomEnd = joined)
    val end = RoundedCornerShape(topStart = joined, bottomStart = joined, topEnd = round, bottomEnd = round)
    // "Back" on screen, as before; TalkBack says which back, since the top has one too (review A11Y-08).
    val previous = stringResource(R.string.recipe_step_previous_description)
    val back = @Composable { buttonModifier: Modifier, shape: Shape ->
        CookButton(
            text = stringResource(R.string.recipe_step_previous),
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            onClick = onBack,
            enabled = canGoBack,
            shape = shape,
            filled = false,
            modifier = buttonModifier.semantics { contentDescription = previous },
        )
    }
    val next = @Composable { buttonModifier: Modifier, shape: Shape ->
        CookButton(
            text = stringResource(R.string.recipe_step_next),
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            iconAfter = true,
            onClick = onNext,
            enabled = canGoNext,
            shape = shape,
            filled = true,
            modifier = buttonModifier.testTag("cook_next"),
        )
    }
    val ingredients = @Composable { buttonModifier: Modifier, shape: Shape ->
        CookButton(
            text = stringResource(R.string.recipe_ingredients),
            icon = Icons.AutoMirrored.Rounded.List,
            onClick = onIngredients,
            shape = shape,
            filled = false,
            modifier = buttonModifier,
        )
    }
    if (isLargeText()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ingredients(Modifier.fillMaxWidth(), CircleShape)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                back(Modifier.weight(1f), start)
                next(Modifier.weight(1f), end)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            back(Modifier.widthIn(min = 92.dp), start)
            ingredients(Modifier.weight(1f), RoundedCornerShape(joined))
            next(Modifier.widthIn(min = 116.dp), end)
        }
    }
}

/** One of cook mode's buttons: tonal, or [filled] `primary`; greyed while it can't be used. */
@Composable
private fun CookButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shape: Shape,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconAfter: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f) to colors.onSurface.copy(alpha = 0.38f)
        filled -> colors.primary to colors.onPrimary
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = CookButtonHeight),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!iconAfter) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text,
                style = if (filled) SpoilNotText.ButtonLarge else SpoilNotText.ButtonMedium,
                textAlign = TextAlign.Center,
            )
            if (iconAfter) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}
