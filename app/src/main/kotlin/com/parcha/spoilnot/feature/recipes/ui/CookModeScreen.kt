package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import kotlinx.coroutines.launch

/** Cook mode (DESIGN 5.5): the screen stays awake, large type, one step at a time, ingredients in a sheet. */
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
    DetailScaffold(
        title = recipe?.title.orEmpty(),
        onBack = onBack,
        modifier = modifier.testTag("screen_cook_mode"),
    ) { padding ->
        if (recipe == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.recipe_gone))
            }
        } else {
            CookSteps(recipe, servings, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookSteps(recipe: Recipe, servings: Int?, modifier: Modifier = Modifier) {
    val steps = recipe.content.steps
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val checked = remember { mutableStateListOf<Int>() }
    Column(modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1f) / steps.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("cook_pager")) { page ->
            val step = steps[page]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.recipe_step_of, page + 1, steps.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                step.group?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(
                    step.text,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("cook_step"),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.recipe_step_previous), Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = { showIngredients = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                Text(stringResource(R.string.recipe_ingredients), Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < steps.lastIndex,
                modifier = Modifier.testTag("cook_next"),
            ) {
                Text(stringResource(R.string.recipe_step_next))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, Modifier.padding(start = 4.dp))
            }
        }
    }
    if (showIngredients) {
        val factor = Scaling.factor(recipe.content.servings, servings)
        ModalBottomSheet(onDismissRequest = { showIngredients = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge)
                IngredientChecklist(
                    recipe.content.ingredients.map {
                        it.group to Scaling.line(it.raw, factor)
                    },
                    checked,
                )
            }
        }
    }
}
