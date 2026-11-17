package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.FieldLabel
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.LabelChip
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFilterChip
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.component.segmentShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.rememberSaveableMap
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.ui.amountText
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery

/**
 * Choose all or some ingredients (DESIGN 5.5, 19.11). Ones that look like pantry items start unchecked with
 * "Probably in pantry"; ones already on the list start unchecked with "On the list". The lines are a group of rows on
 * the field color.
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
    val choices = rememberSaveableMap<Int, Boolean>()
    // Names typed in "Change" by line (DESIGN 5.5); applied on top of the latest candidates.
    val renames = rememberSaveableMap<Int, String>()
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
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
    SpoilNotSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.recipe_add_to_list), style = SpoilNotText.SheetTitle)
            ListChips(state.lists, state.listId, onChooseList)
            SelectAllRow(
                all = candidates.indices.all { selected(it, candidates[it]) },
                onAll = { all -> candidates.indices.forEach { choices[it] = all } },
            )
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(ListGroupGap),
                ) {
                    itemsIndexed(candidates) { index, candidate ->
                        CandidateRow(
                            candidate,
                            selected(index, candidate),
                            onChange = { choices[index] = it },
                            onEdit = { editing = index },
                            position = segmentPosition(index, candidates.size),
                        )
                    }
                }
            }
            PrimaryButton(
                // A button for nothing says what to do instead (after v1, UX-18: it read "Add 0 items").
                text = if (count > 0) {
                    pluralStringResource(R.plurals.recipe_add_items, count, count)
                } else {
                    stringResource(R.string.recipe_add_none)
                },
                enabled = count > 0,
                onClick = {
                    if (count > 0) onAdd(candidates.filterIndexed { i, c -> selected(i, c) })
                },
                modifier = Modifier.padding(top = 16.dp).testTag("recipe_add_items"),
            )
        }
    }
}

/** "Lines that look like pantry items start unchecked" with Select all or none beside it. */
@Composable
internal fun SelectAllRow(all: Boolean, onAll: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.recipe_pantry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onAll(!all) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                stringResource(if (all) R.string.recipe_select_none else R.string.recipe_select_all),
                style = SpoilNotText.ButtonMedium,
            )
        }
    }
}

/**
 * Which grocery list to add to, when there is more than one (DESIGN 19.10): filter chips, the chosen one filled with
 * a check, not only a color.
 */
@Composable
internal fun ListChips(lists: List<GroceryList>, listId: String?, onChoose: (String) -> Unit) {
    if (lists.size <= 1) return
    // One list at a time: radio buttons in a group for screen readers (review A11Y-08: they were checkboxes).
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lists.forEach { list ->
            SpoilNotFilterChip(
                text = list.name,
                selected = list.id == listId,
                onClick = { onChoose(list.id) },
                color = SpoilNotSurfaces.current.field,
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/**
 * One recipe line to add (DESIGN 5.5, 19.5): a row of a group on the field color with its checkbox, the line, what it
 * lands on the list as with Change, and "Probably in pantry" or "On the list".
 */
@Composable
internal fun CandidateRow(
    candidate: GroceryCandidate,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
    onEdit: () -> Unit = {},
    position: SegmentPosition = SegmentPosition.Only,
) {
    Surface(
        shape = segmentShape(position),
        color = SpoilNotSurfaces.current.field,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                selected,
                role = Role.Checkbox,
                onValueChange = onChange,
            ).testTag("candidate"),
            // The box lines up with the recipe line it belongs to, not the middle of three lines (after v1).
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp),
            )
            Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp, end = 8.dp)) {
                Text(candidate.ingredient.raw, style = SpoilNotText.RowTitle)
                // What lands on the list: the shopping name and amount, not the recipe's wording (DESIGN 5.5).
                val amount = candidate.quantity?.let { " × " + amountText(it, candidate.unit) }.orEmpty()
                val changeDescription = descriptionFor(candidate)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recipe_adds_as, candidate.name + amount),
                        style = SpoilNotText.RowSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = changeDescription
                        },
                    ) { Text(stringResource(R.string.recipe_change_item), style = SpoilNotText.ButtonMedium) }
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
                tag?.let { (icon, text) -> LabelChip(text, icon = icon) }
            }
        }
    }
}

@Composable
private fun descriptionFor(candidate: GroceryCandidate): String =
    stringResource(R.string.recipe_change_item_description, candidate.ingredient.raw)

/**
 * "Change": what a recipe line goes on the list as (DESIGN 5.5, 19.11). Pantry products that fit what is typed are
 * offered; picking one uses its name and usual amount, and the household's app remembers the choice for this
 * ingredient.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        title = { Text(stringResource(R.string.recipe_change_title), style = SpoilNotText.DialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(candidate.ingredient.raw, style = MaterialTheme.typography.bodyMedium)
                FormTextField(
                    value = text,
                    onValueChange = { text = it.take(GroceryRules.MAX_NAME_LENGTH) },
                    label = stringResource(R.string.field_name),
                    modifier = Modifier.testTag("rename_field"),
                )
                if (suggestions.isNotEmpty()) {
                    FieldLabel(stringResource(R.string.recipe_change_in_pantry))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { product ->
                            SpoilNotFilterChip(
                                text = product.name,
                                selected = false,
                                onClick = { text = product.name },
                                color = SpoilNotSurfaces.current.dialogRow,
                            )
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
                Text(stringResource(R.string.action_save), style = SpoilNotText.ButtonMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}

private const val MAX_SUGGESTIONS = 4
