package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.feature.pantry.ui.formatQuantity
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.pantry.ui.parseQuantity
import com.parcha.spoilnot.feature.recipes.domain.CookLine

/**
 * "I cooked this" (DESIGN 5.5, after v1): the pantry products the recipe uses, each with the amount to take off.
 * Lines the pantry can count start chosen with the recipe's amount; the others start unchosen with the whole stock,
 * so choosing one means "I finished it". Amounts can be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookedSheet(state: RecipeDetailViewModel.CookState, onUse: (Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember { mutableStateMapOf<String, Boolean>() }
    val amounts = remember { mutableStateMapOf<String, String>() }
    fun isChosen(line: CookLine) = chosen[line.product.id] ?: line.selectedByDefault
    fun amountText(line: CookLine) = amounts[line.product.id] ?: formatQuantity(line.defaultAmount)
    val use = state.lines.filter(::isChosen).mapNotNull { line ->
        parseQuantity(amountText(line))?.takeIf { it > 0 }?.let { line.product.id to it }
    }.toMap()
    val sheet = rememberFormSheet()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet.state,
        contentWindowInsets = sheet.contentWindowInsets,
        modifier = sheet.modifier.testTag("sheet_cooked"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.recipe_cooked_title), style = MaterialTheme.typography.titleLarge)
            if (state.lines.isEmpty()) {
                Text(
                    stringResource(R.string.recipe_cooked_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Text(
                    stringResource(R.string.recipe_cooked_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 480.dp)) {
                    items(state.lines, key = { it.product.id }) { line ->
                        CookRow(
                            line = line,
                            chosen = isChosen(line),
                            amount = amountText(line),
                            onChosen = { chosen[line.product.id] = it },
                            onAmount = {
                                amounts[line.product.id] = it
                                chosen[line.product.id] = true
                            },
                        )
                    }
                }
            }
            PrimaryButton(
                text = pluralStringResource(R.plurals.recipe_cooked_use, use.size, use.size),
                onClick = { if (use.isNotEmpty()) onUse(use) },
                modifier = Modifier.padding(top = 12.dp).testTag("recipe_cooked_use"),
            )
        }
    }
}

@Composable
internal fun CookRow(
    line: CookLine,
    chosen: Boolean,
    amount: String,
    onChosen: (Boolean) -> Unit,
    onAmount: (String) -> Unit,
) {
    val unit = stringResource(line.product.unit.label())
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(
                1f,
            ).toggleable(chosen, role = Role.Checkbox, onValueChange = onChosen).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = chosen, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column {
                Text(line.product.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.recipe_cooked_have, "${formatQuantity(line.inStock)} $unit"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                line.lines.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = onAmount,
            label = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp).testTag("cook_amount_${line.product.id}"),
        )
    }
}
