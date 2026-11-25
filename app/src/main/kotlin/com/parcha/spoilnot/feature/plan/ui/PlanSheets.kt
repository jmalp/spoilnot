package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.CountStepper
import com.parcha.spoilnot.core.designsystem.component.FieldLabel
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.GroupHeading
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeight
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.SearchField
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotFilterChip
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSheet
import com.parcha.spoilnot.core.designsystem.component.SpoilNotTextField
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.rememberFormSheet
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.ui.rememberSaveableList
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.plan.domain.MealPlanLimits
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import com.parcha.spoilnot.feature.recipes.domain.UseItUpSuggestion
import java.time.LocalDate

/** Most recipes the picker lists at once; searching narrows it. */
private const val PICKER_LIMIT = 60

/** The space between two parts of a sheet (DESIGN 19.11). */
private val SheetGap = 18.dp

/** A plan sheet's content: scrolling, 20 dp from the sheet's sides, its parts [SheetGap] apart (DESIGN 19.11). */
private val SheetPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)

/**
 * Adding to a slot (DESIGN 5.9): who cooks, then something else typed in ("Leftovers") and added with its +, or a
 * saved recipe (searchable) added with a tap. Recipes that use food expiring soon come first, under Use it up. The
 * recipes are rows of a group on the field color (DESIGN 19.5, 19.11).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val cooks = rememberSaveableList<String>()
    var search by rememberSaveable { mutableStateOf("") }
    var other by rememberSaveable { mutableStateOf("") }
    val key = NameKey.of(search)
    val shown = recipes.filter { key.isEmpty() || NameKey.of(it.title).contains(key) }
    val addOther = { if (other.isNotBlank()) onAddOther(other, cooks.toList()) }
    SpoilNotSheet(
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
                .padding(SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SheetGap),
        ) {
            Text(
                stringResource(R.string.plan_add_title, slotLabel(slot), dayTitle(date)),
                style = SpoilNotText.SheetTitle,
            )
            CookPicker(members, myUid, cooks)
            // Something else, with its own + at the end of the field (DESIGN 19.10).
            SpoilNotTextField(
                value = other,
                onValueChange = { other = it.take(MealPlanLimits.TITLE) },
                label = stringResource(R.string.plan_something_else),
                placeholder = stringResource(R.string.plan_something_else_hint),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { addOther() }),
                trailingIcon = {
                    FilledIconButton(
                        onClick = addOther,
                        enabled = other.isNotBlank(),
                        modifier = Modifier.testTag("plan_add_other"),
                    ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.plan_add)) }
                },
                modifier = Modifier.testTag("plan_other_field"),
            )
            if (useItUp.isNotEmpty() && key.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupHeading(stringResource(R.string.plan_use_it_up))
                    Column(
                        Modifier.testTag("plan_use_it_up"),
                        verticalArrangement = Arrangement.spacedBy(ListGroupGap),
                    ) {
                        useItUp.forEachIndexed { index, suggestion ->
                            val uses = suggestion.uses.joinToString(", ") { it.product.name }
                            PickerRow(
                                position = segmentPosition(index, useItUp.size),
                                minHeight = ListSegmentMinHeight,
                                onClick = { onAddRecipe(suggestion.recipe, cooks.toList()) },
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        suggestion.recipe.title,
                                        style = SpoilNotText.RowTitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        stringResource(R.string.home_use_it_up_uses, uses),
                                        style = SpoilNotText.RowSupporting,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = stringResource(R.string.plan_search_recipes),
                    clearLabel = stringResource(R.string.action_clear_search),
                    modifier = Modifier.testTag("plan_recipe_search"),
                )
                when {
                    recipes.isEmpty() -> Hint(stringResource(R.string.plan_no_recipes))

                    shown.isEmpty() -> Hint(stringResource(R.string.plan_no_matches))

                    else -> Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
                        val listed = shown.take(PICKER_LIMIT)
                        listed.forEachIndexed { index, recipe ->
                            PickerRow(
                                position = segmentPosition(index, listed.size),
                                onClick = { onAddRecipe(recipe, cooks.toList()) },
                                modifier = Modifier.testTag("plan_pick_${recipe.id}"),
                            ) {
                                Text(
                                    recipe.title,
                                    style = SpoilNotText.RowTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A recipe to add: a row of a group on the sheet's field color, added with a tap. */
@Composable
private fun PickerRow(
    position: SegmentPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ListSegmentMinHeightOneLine,
    content: @Composable RowScope.() -> Unit,
) {
    ListSegment(
        position = position,
        onClick = onClick,
        onClickLabel = stringResource(R.string.plan_add),
        color = SpoilNotSurfaces.current.field,
        minHeight = minHeight,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier,
        content = content,
    )
}

/**
 * A planned meal (DESIGN 5.9): servings, who cooks and a note, then Save; Cooked, Move, opening its recipe, and
 * removing it from the plan (with Undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSheet(
    meal: PlannedMeal,
    members: List<Member>,
    myUid: String,
    recipeExists: Boolean,
    /** The planned recipe's own servings, so the stepper can reach them (after v1, LOGIC-13). */
    recipeServings: Int?,
    onSave: (servings: Int?, cooks: List<String>, note: String?) -> Unit,
    onCook: () -> Unit,
    onNotCooked: () -> Unit,
    weekDays: List<LocalDate>,
    onMove: (LocalDate, MealSlot) -> Unit,
    onOpenRecipe: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberFormSheet()
    var servings by rememberSaveable { mutableStateOf(meal.servings) }
    val maxServings = Scaling.maxServings(recipeServings, meal.servings)
    val cooks = rememberSaveableList(*meal.cooks.toTypedArray())
    var note by rememberSaveable { mutableStateOf(meal.note.orEmpty()) }
    var moving by rememberSaveable { mutableStateOf(false) }
    if (moving) {
        MoveDialog(
            meal = meal,
            days = weekDays,
            onMove = { date, slot ->
                moving = false
                onMove(date, slot)
            },
            onDismiss = { moving = false },
        )
    }
    SpoilNotSheet(
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
                .padding(SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SheetGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.title, style = SpoilNotText.SheetTitle)
                Text(
                    stringResource(R.string.plan_add_title, slotLabel(meal.slot), dayTitle(meal.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.plan_servings_label))
                // A stepper (DESIGN 19.9): − below the least servings leaves them unset.
                CountStepper(
                    value = servings?.toString() ?: stringResource(R.string.plan_servings_unset),
                    minusLabel = stringResource(R.string.plan_fewer),
                    plusLabel = stringResource(R.string.plan_more),
                    onMinus = { servings = servings?.let { if (it <= MealPlanLimits.MIN_SERVINGS) null else it - 1 } },
                    onPlus = { servings = ((servings ?: 0) + 1).coerceAtMost(maxServings) },
                    minusEnabled = servings != null,
                    plusEnabled = (servings ?: 0) < maxServings,
                    valueTag = "plan_servings",
                )
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
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.plan_save),
                    onClick = { onSave(servings, cooks.toList(), note) },
                    modifier = Modifier.testTag("plan_save_meal"),
                )
                if (meal.cookedAt == null) {
                    TonalButton(
                        text = stringResource(R.string.plan_cooked),
                        onClick = onCook,
                        icon = Icons.Rounded.Check,
                        modifier = Modifier.fillMaxWidth().testTag("plan_cook_meal"),
                    )
                } else {
                    CookedLine(cookedByName(meal, members, myUid), onNotCooked)
                }
                TonalButton(
                    text = stringResource(R.string.plan_move),
                    onClick = { moving = true },
                    modifier = Modifier.fillMaxWidth().testTag("plan_move_meal"),
                )
                if (recipeExists) {
                    TonalButton(
                        text = stringResource(R.string.plan_open_recipe),
                        onClick = onOpenRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan_remove_meal"),
                ) { Text(stringResource(R.string.plan_remove), style = SpoilNotText.ButtonMedium) }
            }
        }
    }
}

/** Who marked the meal cooked: "You", a member's name, or null when unknown or gone. */
@Composable
private fun cookedByName(meal: PlannedMeal, members: List<Member>, myUid: String): String? = meal.cookedBy?.let { uid ->
    if (uid == myUid) stringResource(R.string.plan_you) else members.find { it.uid == uid }?.displayName
}

/** "Cooked by Sam" with a check, and Not cooked yet to take it back. */
@Composable
private fun CookedLine(by: String?, onNotCooked: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp),
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
            if (by != null) stringResource(R.string.plan_cooked_by, by) else stringResource(R.string.plan_cooked),
            style = SpoilNotText.RowTitle,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onNotCooked,
            modifier = Modifier.heightIn(min = 48.dp).testTag("plan_not_cooked"),
        ) { Text(stringResource(R.string.plan_not_cooked), style = SpoilNotText.ButtonMedium) }
    }
}

/**
 * Move a meal to another day of its week or another slot (DESIGN 5.9, 19.11): the title in
 * [SpoilNotText.DialogTitle], and the choices as chips with a check on the dialog's row color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveDialog(
    meal: PlannedMeal,
    days: List<LocalDate>,
    onMove: (LocalDate, MealSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(meal.date) }
    var slot by rememberSaveable { mutableStateOf(meal.slot) }
    val chipColor = SpoilNotSurfaces.current.dialogRow
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_move_title, meal.title), style = SpoilNotText.DialogTitle) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.plan_move_day))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // "Fri 30" keeps the week to two rows; TalkBack reads the whole date.
                        days.forEach { date ->
                            val full = dayTitle(date)
                            SpoilNotFilterChip(
                                text = shortDay(date),
                                selected = date == day,
                                onClick = { day = date },
                                color = chipColor,
                                modifier = Modifier.testTag("plan_move_day_$date").semantics {
                                    contentDescription = full
                                },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.plan_move_slot))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MealSlot.entries.forEach { s ->
                            SpoilNotFilterChip(
                                text = slotLabel(s),
                                selected = s == slot,
                                onClick = { slot = s },
                                color = chipColor,
                                modifier = Modifier.testTag("plan_move_slot_${s.wire}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(day, slot) },
                enabled = day != meal.date || slot != meal.slot,
                modifier = Modifier.testTag("plan_move_confirm"),
            ) { Text(stringResource(R.string.plan_move), style = SpoilNotText.ButtonMedium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = SpoilNotText.ButtonMedium)
            }
        },
    )
}

/** "Who's cooking?": any number of members; the chips carry names, so the choice never depends on color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookPicker(members: List<Member>, myUid: String, cooks: MutableList<String>) {
    val you = stringResource(R.string.plan_you)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.plan_whos_cooking))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            members.forEach { member ->
                val selected = member.uid in cooks
                SpoilNotFilterChip(
                    text = if (member.uid == myUid) you else member.displayName,
                    selected = selected,
                    onClick = { if (selected) cooks.remove(member.uid) else cooks.add(member.uid) },
                    modifier = Modifier.testTag("plan_cook_${member.uid}"),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
