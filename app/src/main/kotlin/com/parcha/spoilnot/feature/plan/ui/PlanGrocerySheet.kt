package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.ui.PlanViewModel.WeekGroceryState
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.ui.CandidateRow
import com.parcha.spoilnot.feature.recipes.ui.ListChips
import com.parcha.spoilnot.feature.recipes.ui.RenameDialog
import java.time.LocalDate

/**
 * "Add to grocery list" for the week (DESIGN 5.9): the ingredients of each recipe still to cook, under the meal they
 * are for, chosen the same way as from one recipe (DESIGN 5.5). Items land with the badge Meal plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekGrocerySheet(
    state: WeekGroceryState,
    onChooseList: (String) -> Unit,
    onAdd: (List<GroceryCandidate>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Choices and renames by "mealId/line"; lines not touched follow their default.
    val choices = remember { mutableStateMapOf<String, Boolean>() }
    val renames = remember { mutableStateMapOf<String, String>() }
    var editing by remember { mutableStateOf<String?>(null) }
    val products = state.pantry.products
    val sections = state.sections.map { section ->
        section to section.candidates.mapIndexed { i, c ->
            val key = "${section.meal.id}/$i"
            key to (renames[key]?.let { RecipeGrocery.rename(c, it, products) } ?: c)
        }
    }
    val all = sections.flatMap { it.second }
    fun selected(key: String, c: GroceryCandidate) = choices[key] ?: c.selectedByDefault
    val chosen = all.filter { (key, c) -> selected(key, c) }.map { it.second }
    val count = RecipeGrocery.merge(chosen).size
    val sheet = rememberFormSheet()
    editing?.let { key ->
        all.firstOrNull { it.first == key }?.second?.let { candidate ->
            RenameDialog(
                candidate = candidate,
                products = products,
                onSave = { name ->
                    renames[key] = name
                    choices[key] = true
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_week_grocery"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.plan_shop_title), style = MaterialTheme.typography.titleLarge)
            ListChips(state.lists, state.listId, onChooseList)
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                sections.isEmpty() -> Text(
                    stringResource(R.string.plan_shop_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.recipe_pantry_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val allChosen = all.all { (key, c) -> selected(key, c) }
                        TextButton(onClick = { all.forEach { (key, _) -> choices[key] = !allChosen } }) {
                            Text(
                                stringResource(
                                    if (allChosen) R.string.recipe_select_none else R.string.recipe_select_all,
                                ),
                            )
                        }
                    }
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 480.dp)) {
                        sections.forEach { (section, lines) ->
                            item(key = section.meal.id) {
                                Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                    Text(
                                        section.recipe.content.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                    Text(
                                        mealWhen(section.meal.slot, section.meal.date, section.meal.servings),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            itemsIndexed(lines, key = { _, (key, _) -> key }) { _, (key, candidate) ->
                                CandidateRow(
                                    candidate,
                                    selected(key, candidate),
                                    onChange = { choices[key] = it },
                                    onEdit = { editing = key },
                                )
                            }
                        }
                    }
                }
            }
            PrimaryButton(
                text = pluralStringResource(R.plurals.recipe_add_items, count, count),
                onClick = { if (count > 0) onAdd(chosen) },
                modifier = Modifier.padding(top = 12.dp).testTag("plan_add_items"),
            )
        }
    }
}

/** "Dinner · Thursday, Oct 29 · 4 servings". */
@Composable
private fun mealWhen(slot: MealSlot, date: LocalDate, servings: Int?) = listOfNotNull(
    stringResource(R.string.plan_add_title, slotLabel(slot), dayTitle(date)),
    servings?.let { pluralStringResource(R.plurals.plan_servings, it, it) },
).joinToString(" · ")
