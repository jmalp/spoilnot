package com.parcha.spoilnot.feature.recipes.ui

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.BottomActionBar
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.ExtractionMethod
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl

@Composable
fun RecipeImportScreen(
    url: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenExisting: (String) -> Unit,
    onTryBrowser: (url: String, prefill: RecipeContent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeImportViewModel = hiltViewModel<RecipeImportViewModel, RecipeImportViewModel.Factory>(
        key = "import_$url",
        creationCallback = { it.create(url) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar) {}

    LaunchedEffect(state) {
        (state as? RecipeImportViewModel.UiState.TryBrowser)?.let { onTryBrowser(it.url, it.prefill) }
    }
    SaveStateEffect(saveState, onSaved, onOpenExisting, viewModel)

    // A pushed screen of the 2026-11 look (DESIGN 19.7): the round Back over the title.
    PushedScaffold(
        title = stringResource(R.string.recipe_preview),
        onBack = onBack,
        modifier = modifier.testTag("screen_recipe_import"),
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
    ) { padding ->
        when (val s = state) {
            RecipeImportViewModel.UiState.Loading, is RecipeImportViewModel.UiState.TryBrowser -> Column(
                Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.recipe_looking), style = MaterialTheme.typography.bodyLarge)
                url?.let { SourceUrl.siteName(it) }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RecipeImportViewModel.UiState.NoLink -> Problem(
                icon = Icons.Rounded.LinkOff,
                message = stringResource(R.string.recipe_no_link),
                modifier = Modifier.padding(padding),
            )

            RecipeImportViewModel.UiState.Offline -> Problem(
                icon = Icons.Rounded.CloudOff,
                message = stringResource(R.string.error_offline),
                action = stringResource(R.string.action_try_again) to viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            is RecipeImportViewModel.UiState.Preview -> RecipePreview(
                content = s.content,
                method = s.method,
                saving = saveState == SavingRecipeViewModel.SaveState.Saving,
                onSave = { viewModel.save(s.content) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Navigates after a save, or asks "Already saved" with Open or Save a copy (DESIGN 5.5). */
@Composable
fun SaveStateEffect(
    saveState: SavingRecipeViewModel.SaveState,
    onSaved: (String) -> Unit,
    onOpenExisting: (String) -> Unit,
    viewModel: SavingRecipeViewModel,
) {
    LaunchedEffect(saveState) {
        if (saveState is SavingRecipeViewModel.SaveState.Saved) {
            viewModel.dismissSaveState()
            onSaved(saveState.recipeId)
        }
    }
    if (saveState is SavingRecipeViewModel.SaveState.AlreadySaved) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveState,
            title = { Text(stringResource(R.string.recipe_already_saved), style = SpoilNotText.DialogTitle) },
            text = { Text(stringResource(R.string.recipe_already_saved_body, saveState.existing.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissSaveState()
                        onOpenExisting(saveState.existing.id)
                    },
                    modifier = Modifier.testTag("already_saved_open"),
                ) { Text(stringResource(R.string.action_open), style = SpoilNotText.ButtonMedium) }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.save(saveState.content, saveCopy = true) },
                    modifier = Modifier.testTag("already_saved_copy"),
                ) { Text(stringResource(R.string.recipe_save_copy), style = SpoilNotText.ButtonMedium) }
            },
        )
    }
}

/**
 * What was found, before saving (DESIGN 11.4, 19.12): the photo, the title and its facts as pills, the ingredients as
 * a group of rows and the steps with their numbers in leaves, as the recipe will show them; Save in the bar below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipePreview(
    content: RecipeContent,
    method: ExtractionMethod,
    saving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (method == ExtractionMethod.Heuristic) CheckThisRecipe()
            if (content.imageUrl != null) {
                RecipeImage(
                    content.imageUrl,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(SpoilNotCorners.Card)),
                    content.title,
                )
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    content.title,
                    style = SpoilNotText.CardTitle,
                    modifier = Modifier.semantics { heading() }.testTag("preview_title"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    durationText(content.totalMinutes)?.let {
                        InfoPill(stringResource(R.string.recipe_total_time, it), Icons.Rounded.Schedule)
                    }
                    servingsLabel(content.servings, content.servingsText)?.let { InfoPill(it, Icons.Rounded.People) }
                    SourceUrl.siteName(content.sourceUrl)?.let { InfoPill(it, Icons.Rounded.Language) }
                }
            }
            if (content.ingredients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(stringResource(R.string.recipe_ingredients))
                    GroupedRows(content.ingredients.map { it.group to it.raw }) { _, line, position ->
                        Surface(
                            shape = segmentShape(position),
                            color = SpoilNotSurfaces.current.row,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ListSegmentMinHeightOneLine)
                                    .wrapContentHeight()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            if (content.steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(stringResource(R.string.recipe_steps))
                    StepList(content.steps.map { it.group to it.text })
                }
            }
        }
        BottomActionBar {
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                busy = saving,
                modifier = Modifier.testTag("preview_save"),
            )
        }
    }
}

/** DESIGN 11.4: heuristic results are labeled so the user checks them, on a rounded note (DESIGN 19.11). */
@Composable
fun CheckThisRecipe() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
            Text(
                stringResource(R.string.recipe_check_this),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("check_this_recipe"),
            )
        }
    }
}

@Composable
private fun Problem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            action?.let { (label, onClick) -> TonalButton(text = label, onClick = onClick) }
        }
    }
}
