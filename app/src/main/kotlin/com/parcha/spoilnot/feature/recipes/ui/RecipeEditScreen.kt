package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.EmptyState
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeForm
import com.parcha.spoilnot.feature.recipes.domain.RecipeForm.Problem

private val FormSaver = listSaver<RecipeForm?, String>(
    save = { f ->
        if (f == null) {
            emptyList()
        } else {
            listOf(
                f.title, f.imageUrl, f.servings, f.prepMinutes, f.cookMinutes, f.totalMinutes, f.ingredients,
                f.steps, f.notes, f.tags,
            )
        }
    },
    restore = { v ->
        if (v.size <
            10
        ) {
            null
        } else {
            RecipeForm(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9])
        }
    },
)

@Composable
fun RecipeEditScreen(
    recipeId: String?,
    prefill: RecipeContent?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditViewModel = hiltViewModel<RecipeEditViewModel, RecipeEditViewModel.Factory>(
        key = "edit_$recipeId",
        creationCallback = { it.create(recipeId, prefill) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var form by rememberSaveable(stateSaver = FormSaver) { mutableStateOf(null) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.initial) { if (form == null) form = state.initial }
    MessageEffect(viewModel.messages, snackbar) {}

    DetailScaffold(
        title = stringResource(if (recipeId == null) R.string.recipe_new else R.string.recipe_edit),
        onBack = onBack,
        modifier = modifier.testTag("screen_recipe_edit"),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = form
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            current == null -> EmptyState(
                icon = Icons.Rounded.Delete,
                message = stringResource(R.string.recipe_gone),
                modifier = Modifier.padding(padding),
            )

            else -> RecipeFormFields(
                form = current,
                problems = if (showProblems) current.problems() else emptySet(),
                saving = state.saving,
                onChange = { form = it },
                onSave = {
                    showProblems = true
                    viewModel.save(current, onSaved)
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun RecipeFormFields(
    form: RecipeForm,
    problems: Set<Problem>,
    saving: Boolean,
    onChange: (RecipeForm) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    val numberError = if (Problem.Number in problems) stringResource(R.string.recipe_number_invalid) else null
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormTextField(
            value = form.title,
            onValueChange = { onChange(form.copy(title = it)) },
            label = stringResource(R.string.recipe_field_title),
            error = when {
                Problem.TitleMissing in problems -> stringResource(R.string.recipe_title_missing)
                Problem.TitleTooLong in problems -> stringResource(R.string.recipe_title_too_long)
                else -> null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.testTag("recipe_field_title"),
        )
        FormTextField(
            value = form.imageUrl,
            onValueChange = { onChange(form.copy(imageUrl = it)) },
            label = stringResource(R.string.recipe_field_image),
            helper = stringResource(R.string.optional),
            error = if (Problem.ImageUrl in problems) stringResource(R.string.recipe_image_invalid) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        FormTextField(
            value = form.servings,
            onValueChange = { onChange(form.copy(servings = it)) },
            label = stringResource(R.string.recipe_field_servings),
            error = numberError?.takeIf { form.servings.isNotBlank() && !form.servings.isValidNumber() },
            keyboardOptions = numberKeyboard,
            modifier = Modifier.testTag("recipe_field_servings"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(form.prepMinutes, R.string.recipe_field_prep) { v: String -> form.copy(prepMinutes = v) },
                Triple(form.cookMinutes, R.string.recipe_field_cook) { v: String -> form.copy(cookMinutes = v) },
                Triple(form.totalMinutes, R.string.recipe_field_total) { v: String -> form.copy(totalMinutes = v) },
            ).forEach { (value, label, update) ->
                FormTextField(
                    value = value,
                    onValueChange = { onChange(update(it)) },
                    label = stringResource(label),
                    error = numberError?.takeIf { value.isNotBlank() && !value.isValidNumber() },
                    keyboardOptions = numberKeyboard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MultilineField(
            value = form.ingredients,
            onChange = { onChange(form.copy(ingredients = it)) },
            label = stringResource(R.string.recipe_field_ingredients),
            helper = stringResource(R.string.recipe_field_lines_hint),
            tag = "recipe_field_ingredients",
        )
        MultilineField(
            value = form.steps,
            onChange = { onChange(form.copy(steps = it)) },
            label = stringResource(R.string.recipe_field_steps),
            helper = stringResource(R.string.recipe_field_lines_hint),
            tag = "recipe_field_steps",
        )
        MultilineField(
            value = form.notes,
            onChange = { onChange(form.copy(notes = it)) },
            label = stringResource(R.string.recipe_notes),
            helper = stringResource(R.string.optional),
            tag = "recipe_field_notes",
        )
        FormTextField(
            value = form.tags,
            onValueChange = { onChange(form.copy(tags = it)) },
            label = stringResource(R.string.recipe_field_tags),
            helper = stringResource(R.string.recipe_field_tags_hint),
        )
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            busy = saving,
            modifier = Modifier.testTag("recipe_save"),
        )
    }
}

private fun String.isValidNumber() = trim().toIntOrNull()?.let { it in 1..10_000 } == true

@Composable
private fun MultilineField(value: String, onChange: (String) -> Unit, label: String, helper: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(helper) },
        minLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag(tag),
    )
}
