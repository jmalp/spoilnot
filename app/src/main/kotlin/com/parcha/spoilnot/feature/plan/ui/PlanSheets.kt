package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.plan.domain.MealPlanLimits
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.UseItUpSuggestion
import java.time.LocalDate

/** Most recipes the picker lists at once; searching narrows it. */
private const val PICKER_LIMIT = 60

/**
 * Adding to a slot (DESIGN 5.9): who cooks, then a saved recipe (searchable) or something else typed in
 * ("Leftovers"). Tapping a recipe adds it at once. Recipes that use food expiring soon come first, under Use it up.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    slot: MealSlot,
    recipes: List<Recipe>,
    useItUp: List<UseItUpSuggestion>,
    members: List<Member>,
    myUid: String,
    onAddRecipe: (Recipe, List<String>) -> Unit,
    onAddOther: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    val cooks = remember { mutableStateListOf<String>() }
    var search by rememberSaveable { mutableStateOf("") }
    var other by rememberSaveable { mutableStateOf("") }
    val key = NameKey.of(search)
    val shown = recipes.filter { key.isEmpty() || NameKey.of(it.title).contains(key) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_add_meal"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.plan_add_title, slotLabel(slot), dayTitle(date)),
                style = MaterialTheme.typography.headlineSmall,
            )
            CookPicker(members, myUid, cooks)
            Text(stringResource(R.string.plan_something_else), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    value = other,
                    onValueChange = { other = it.take(MealPlanLimits.TITLE) },
                    label = stringResource(R.string.plan_something_else_hint),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (other.isNotBlank()) onAddOther(other, cooks) }),
                    modifier = Modifier.weight(1f).testTag("plan_other_field"),
                )
                TextButton(
                    onClick = { onAddOther(other, cooks.toList()) },
                    enabled = other.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("plan_add_other"),
                ) { Text(stringResource(R.string.plan_add)) }
            }
            HorizontalDivider()
            if (useItUp.isNotEmpty() && key.isEmpty()) {
                Text(stringResource(R.string.plan_use_it_up), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.testTag("plan_use_it_up")) {
                    useItUp.forEach { suggestion ->
                        val uses = suggestion.uses.joinToString(", ") { it.product.name }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAddRecipe(suggestion.recipe, cooks.toList()) }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                suggestion.recipe.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Hint(stringResource(R.string.home_use_it_up_uses, uses))
                        }
                    }
                }
                HorizontalDivider()
            }
            FormTextField(
                value = search,
                onValueChange = { search = it },
                label = stringResource(R.string.plan_search_recipes),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag("plan_recipe_search"),
            )
            when {
                recipes.isEmpty() -> Hint(stringResource(R.string.plan_no_recipes))

                shown.isEmpty() -> Hint(stringResource(R.string.plan_no_matches))

                else -> Column {
                    shown.take(PICKER_LIMIT).forEach { recipe ->
                        Text(
                            recipe.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddRecipe(recipe, cooks.toList()) }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 12.dp)
                                .testTag("plan_pick_${recipe.id}"),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * A planned meal (DESIGN 5.9): servings, who cooks and a note, Cooked, opening its recipe, and removing it from the
 * plan (with Undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSheet(
    meal: PlannedMeal,
    members: List<Member>,
    myUid: String,
    recipeExists: Boolean,
    onSave: (servings: Int?, cooks: List<String>, note: String?) -> Unit,
    onCook: () -> Unit,
    onNotCooked: () -> Unit,
    onOpenRecipe: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    var servings by rememberSaveable { mutableStateOf(meal.servings) }
    val cooks = remember { mutableStateListOf(*meal.cooks.toTypedArray()) }
    var note by rememberSaveable { mutableStateOf(meal.note.orEmpty()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_meal"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(meal.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.plan_add_title, slotLabel(meal.slot), dayTitle(meal.date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.plan_servings_label), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { servings = servings?.let { if (it <= MealPlanLimits.MIN_SERVINGS) null else it - 1 } },
                    enabled = servings != null,
                ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.plan_fewer)) }
                Text(
                    servings?.toString() ?: stringResource(R.string.plan_servings_unset),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 48.dp).testTag("plan_servings"),
                )
                IconButton(
                    onClick = {
                        servings = ((servings ?: 0) + 1).coerceAtMost(MealPlanLimits.MAX_SERVINGS)
                    },
                    enabled = (servings ?: 0) < MealPlanLimits.MAX_SERVINGS,
                ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.plan_more)) }
            }
            CookPicker(members, myUid, cooks)
            FormTextField(
                value = note,
                onValueChange = { note = it.take(MealPlanLimits.NOTE) },
                label = stringResource(R.string.plan_note),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.plan_save),
                onClick = { onSave(servings, cooks.toList(), note) },
                modifier = Modifier.testTag("plan_save_meal"),
            )
            if (meal.cookedAt == null) {
                OutlinedButton(
                    onClick = onCook,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan_cook_meal"),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.plan_cooked), Modifier.padding(start = 8.dp))
                }
            } else {
                val by = meal.cookedBy?.let { uid ->
                    if (uid == myUid) stringResource(R.string.plan_you) else members.find { it.uid == uid }?.displayName
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (by !=
                            null
                        ) {
                            stringResource(R.string.plan_cooked_by, by)
                        } else {
                            stringResource(R.string.plan_cooked)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onNotCooked,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("plan_not_cooked"),
                    ) { Text(stringResource(R.string.plan_not_cooked)) }
                }
            }
            if (recipeExists) {
                OutlinedButton(
                    onClick = onOpenRecipe,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.plan_open_recipe)) }
            }
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan_remove_meal"),
            ) { Text(stringResource(R.string.plan_remove)) }
        }
    }
}

/** "Who's cooking?": any number of members; the chips carry names, so the choice never depends on color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookPicker(members: List<Member>, myUid: String, cooks: MutableList<String>) {
    Text(stringResource(R.string.plan_whos_cooking), style = MaterialTheme.typography.labelLarge)
    val you = stringResource(R.string.plan_you)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            val selected = member.uid in cooks
            FilterChip(
                selected = selected,
                onClick = { if (selected) cooks.remove(member.uid) else cooks.add(member.uid) },
                label = { Text(if (member.uid == myUid) you else member.displayName) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                modifier = Modifier.testTag("plan_cook_${member.uid}"),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
