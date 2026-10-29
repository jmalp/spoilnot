package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlanDay
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.ui.CookedSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Where the Plan tab leads. */
class PlanNavigation(val openSettings: () -> Unit, val openRecipe: (String) -> Unit)

/** The Plan tab (DESIGN 5.9). */
@Composable
fun PlanScreen(navigation: PlanNavigation, modifier: Modifier = Modifier, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar, viewModel::undo)
    var adding by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }
    var openMealId by rememberSaveable { mutableStateOf<String?>(null) }
    var shopping by rememberSaveable { mutableStateOf(false) }

    TabScaffold(
        title = stringResource(R.string.tab_plan),
        onOpenSettings = navigation.openSettings,
        modifier = modifier.testTag("screen_plan"),
        actions = {
            if (state.canShop) {
                IconButton(onClick = { shopping = true }, modifier = Modifier.testTag("plan_shop")) {
                    Icon(Icons.Rounded.AddShoppingCart, contentDescription = stringResource(R.string.plan_shop))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.offline && !state.loading) OfflineBanner()
            if (state.loading) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PlanWeek(
                    state = state,
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    onThisWeek = viewModel::thisWeek,
                    onAdd = { date, slot -> adding = date to slot },
                    onOpen = { openMealId = it.id },
                )
            }
        }
    }

    adding?.let { (date, slot) ->
        // Collected only while the sheet is open, so the pantry is listened to only then; shown once loaded.
        val useItUp = viewModel.useItUp.collectAsStateWithLifecycle().value ?: return@let
        AddMealSheet(
            date = date,
            slot = slot,
            recipes = state.recipes,
            useItUp = useItUp,
            members = state.members,
            myUid = state.myUid,
            onAddRecipe = { recipe, cooks ->
                viewModel.addRecipe(date, slot, recipe, cooks)
                adding = null
            },
            onAddOther = { title, cooks ->
                viewModel.addOther(date, slot, title, cooks)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
    val openMeal = openMealId?.let { id ->
        state.days.flatMap { d -> d.slots.values.flatten() }.firstOrNull {
            it.id ==
                id
        }
    }
    if (openMealId != null && openMeal == null && !state.loading) openMealId = null
    openMeal?.let { meal ->
        MealSheet(
            meal = meal,
            members = state.members,
            myUid = state.myUid,
            recipeExists = meal.recipeId != null && state.recipes.any { it.id == meal.recipeId },
            onSave = { servings, cooks, note ->
                viewModel.update(meal, servings, cooks, note)
                openMealId = null
            },
            onCook = {
                openMealId = null
                viewModel.cook(meal)
            },
            onNotCooked = {
                viewModel.notCooked(meal)
                openMealId = null
            },
            onOpenRecipe = {
                openMealId = null
                meal.recipeId?.let(navigation.openRecipe)
            },
            onRemove = {
                viewModel.remove(meal)
                openMealId = null
            },
            onDismiss = { openMealId = null },
        )
    }
    val cook by viewModel.cook.collectAsStateWithLifecycle()
    cook?.let { c ->
        CookedSheet(
            lines = c.lines,
            onUse = viewModel::cooked,
            onDismiss = viewModel::closeCookSheet,
            markCooked = true,
        )
    }
    if (shopping) {
        val grocery by viewModel.weekGrocery.collectAsStateWithLifecycle()
        val from = stringResource(R.string.plan_shop_from)
        // Shown once loaded: a sheet that grows after it opens keeps its first resting place and hides its button.
        if (!grocery.loading) {
            WeekGrocerySheet(
                state = grocery,
                onChooseList = viewModel::chooseList,
                onAdd = { chosen ->
                    viewModel.addWeekToGrocery(chosen, from)
                    shopping = false
                },
                onDismiss = { shopping = false },
            )
        }
    }
}

@Composable
internal fun PlanWeek(
    state: PlanViewModel.UiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onAdd: (LocalDate, MealSlot) -> Unit,
    onOpen: (PlannedMeal) -> Unit,
) {
    val listState = rememberLazyListState()
    // This week opens on today, not on the first day.
    LaunchedEffect(state.weekStart) {
        val today = state.days.indexOfFirst { it.isToday }
        listState.scrollToItem(if (today >= 0) today else 0)
    }
    Column(Modifier.fillMaxSize()) {
        WeekHeader(state.weekStart, state.isThisWeek, onPrevious, onNext, onThisWeek)
        HorizontalDivider()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("plan_days"),
        ) {
            items(state.days, key = { it.date.toString() }) { day ->
                DayCard(
                    day = day,
                    members = state.members,
                    myUid = state.myUid,
                    recipeIds = state.recipes.mapTo(HashSet()) { it.id },
                    onAdd = { slot -> onAdd(day.date, slot) },
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(
    start: LocalDate,
    isThisWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("plan_previous_week")) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.plan_previous_week),
            )
        }
        Text(
            stringResource(R.string.plan_week_range, shortDate(start), shortDate(MealWeek.end(start))),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).testTag("plan_week_range"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, modifier = Modifier.testTag("plan_next_week")) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.plan_next_week),
            )
        }
    }
    if (!isThisWeek) {
        TextButton(
            onClick = onThisWeek,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("plan_this_week"),
        ) { Text(stringResource(R.string.plan_this_week)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayCard(
    day: PlanDay,
    members: List<Member>,
    myUid: String,
    recipeIds: Set<String>,
    onAdd: (MealSlot) -> Unit,
    onOpen: (PlannedMeal) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val headerColor = if (day.isPast) colors.onSurfaceVariant else colors.onSurface
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("plan_day_${day.date}"),
        // Today has a thicker border and a "Today" label, never color alone.
        border = if (day.isToday) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.material3.CardDefaults.outlinedCardBorder()
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    dayTitle(day.date),
                    style = MaterialTheme.typography.titleMedium,
                    color = headerColor,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (day.isToday) {
                    Text(
                        stringResource(R.string.plan_today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            MealSlot.entries.forEach { slot ->
                val meals = day.slots[slot].orEmpty()
                val slotName = slotLabel(slot)
                val addDescription = stringResource(R.string.plan_add_to, slotName, dayTitle(day.date))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        slotName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onAdd(slot) },
                        modifier = Modifier.testTag("plan_add_${day.date}_${slot.wire}"),
                    ) { Icon(Icons.Rounded.Add, contentDescription = addDescription) }
                }
                meals.forEach { meal ->
                    val deleted = meal.recipeId != null && meal.recipeId !in recipeIds
                    MealRow(meal, members, myUid, deleted) { onOpen(meal) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealRow(meal: PlannedMeal, members: List<Member>, myUid: String, deleted: Boolean, onOpen: () -> Unit) {
    val names = cookNames(meal.cooks, members, myUid)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.plan_open_meal)) { onOpen() }
            .heightIn(min = 48.dp)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .testTag("plan_meal_${meal.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            meal.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val details = listOfNotNull(
                if (deleted) stringResource(R.string.plan_recipe_deleted) else null,
                meal.servings?.let { pluralStringResource(R.plurals.plan_servings, it, it) },
            )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            names.forEach { name -> CookChip(name) }
            if (meal.cookedAt != null) CookedLabel()
        }
    }
}

/** "Cooked" with a check: an icon and a word, never color alone. */
@Composable
internal fun CookedLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.plan_cooked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A cook's name with a person icon, so who cooks never depends on color. */
@Composable
internal fun CookChip(name: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Cooks' names in household order; members who left are not shown, and the user is "You". */
@Composable
internal fun cookNames(cooks: List<String>, members: List<Member>, myUid: String): List<String> {
    val you = stringResource(R.string.plan_you)
    return members.filter { it.uid in cooks }.map { if (it.uid == myUid) you else it.displayName }
}

@Composable
internal fun slotLabel(slot: MealSlot): String = stringResource(
    when (slot) {
        MealSlot.Breakfast -> R.string.plan_slot_breakfast
        MealSlot.Lunch -> R.string.plan_slot_lunch
        MealSlot.Dinner -> R.string.plan_slot_dinner
    },
)

/** "Thursday, Oct 29". */
internal fun dayTitle(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " + shortDate(date)

/** "Oct 29" in the phone's language. */
internal fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
