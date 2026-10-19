package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.ui.formatQuantity
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery

/**
 * Choose all or some ingredients (DESIGN 5.5). Ones that look like pantry items start unchecked with
 * "Probably in pantry"; ones already on the list start unchecked with "On the list".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGrocerySheet(
    state: RecipeDetailViewModel.GroceryState,
    onChooseList: (String) -> Unit,
    onAdd: (List<GroceryCandidate>) -> Unit,
    onDismiss: () -> Unit,
) {
    // User choices, by line; lines not touched follow their default.
    val choices = remember { mutableStateMapOf<Int, Boolean>() }
    // Names typed in "Change" by line (DESIGN 5.5); applied on top of the latest candidates.
    val renames = remember { mutableStateMapOf<Int, String>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    val products = state.pantry.products
    val candidates = state.candidates.mapIndexed { i, c ->
        renames[i]?.let { RecipeGrocery.rename(c, it, products) } ?: c
    }
    fun selected(index: Int, c: GroceryCandidate) = choices[index] ?: c.selectedByDefault
    val count = candidates.indices.count { selected(it, candidates[it]) }
    val sheet = rememberFormSheet()
    editing?.let { index ->
        candidates.getOrNull(index)?.let { candidate ->
            RenameDialog(
                candidate = candidate,
                products = products,
                onSave = { name ->
                    renames[index] = name
                    choices[index] = true
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
        modifier = sheet.modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.recipe_add_to_list), style = MaterialTheme.typography.titleLarge)
            if (state.lists.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.lists.forEach { list ->
                        val isSelected = list.id == state.listId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onChooseList(list.id) },
                            label = { Text(list.name) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recipe_pantry_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val all = candidates.indices.all { selected(it, candidates[it]) }
                TextButton(onClick = { candidates.indices.forEach { choices[it] = !all } }) {
                    Text(stringResource(if (all) R.string.recipe_select_none else R.string.recipe_select_all))
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 480.dp)) {
                    itemsIndexed(candidates) { index, candidate ->
                        CandidateRow(
                            candidate,
                            selected(index, candidate),
                            onChange = { choices[index] = it },
                            onEdit = { editing = index },
                        )
                    }
                }
            }
            PrimaryButton(
                text = pluralStringResource(R.plurals.recipe_add_items, count, count),
                onClick = {
                    if (count > 0) onAdd(candidates.filterIndexed { i, c -> selected(i, c) })
                },
                modifier = Modifier.padding(top = 12.dp).testTag("recipe_add_items"),
            )
        }
    }
}

@Composable
internal fun CandidateRow(
    candidate: GroceryCandidate,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
    onEdit: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            selected,
            role = Role.Checkbox,
            onValueChange = onChange,
        ).testTag("candidate"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(candidate.ingredient.raw, style = MaterialTheme.typography.bodyLarge)
            // What lands on the list: the shopping name and amount, not the recipe's wording (DESIGN 5.5).
            val amount = candidate.quantity?.let {
                " × " + formatQuantity(it) + candidate.unit?.let { u -> " " + stringResource(u.label()) }.orEmpty()
            }.orEmpty()
            val changeDescription = descriptionFor(candidate)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recipe_adds_as, candidate.name + amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = changeDescription
                    },
                ) { Text(stringResource(R.string.recipe_change_item)) }
            }
            val tag = when {
                candidate.inPantry != null ->
                    Icons.Rounded.Kitchen to
                        stringResource(R.string.recipe_probably_in_pantry)

                candidate.onList ->
                    Icons.AutoMirrored.Rounded.PlaylistAddCheck to
                        stringResource(R.string.recipe_on_the_list)

                else -> null
            }
            tag?.let { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun descriptionFor(candidate: GroceryCandidate): String =
    stringResource(R.string.recipe_change_item_description, candidate.ingredient.raw)

/**
 * "Change": what a recipe line goes on the list as (DESIGN 5.5). Pantry products that fit what is typed are offered;
 * picking one uses its name and usual amount, and the household's app remembers the choice for this ingredient.
 */
@Composable
internal fun RenameDialog(
    candidate: GroceryCandidate,
    products: List<Product>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(candidate.name) }
    val key = NameKey.of(text)
    val suggestions = products
        .filter { key.isNotEmpty() && it.nameKey != key && it.nameKey.contains(key) }
        .sortedBy { it.nameKey.length }
        .take(MAX_SUGGESTIONS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(candidate.ingredient.raw, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(GroceryRules.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_field"),
                )
                if (suggestions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.recipe_change_in_pantry),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    suggestions.forEach { product ->
                        TextButton(onClick = { text = product.name }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(product.name)
                        }
                    }
                }
                Text(
                    stringResource(R.string.recipe_change_remembered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = NameKey.clean(text).isNotEmpty()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_SUGGESTIONS = 4
