package com.parcha.spoilnot.feature.plan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.HighlightCard
import com.parcha.spoilnot.core.designsystem.component.LabelChip
import com.parcha.spoilnot.core.designsystem.component.LabelChipKind
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.NameWithTrailing
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.TonalButton
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
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
import kotlinx.coroutines.launch

/** Where the Plan tab leads. */
class PlanNavigation(val openSettings: () -> Unit, val openRecipe: (String) -> Unit)

/** What the Plan tab's controls do; the sheets they open belong to [PlanScreen]. */
internal class PlanHandlers(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onThisWeek: () -> Unit,
    val onAdd: (LocalDate, MealSlot) -> Unit,
    val onOpen: (PlannedMeal) -> Unit,
    val onShop: () -> Unit,
    val onCopyLastWeek: () -> Unit,
    val onClearWeek: () -> Unit,
    val onRetry: () -> Unit,
)

/** The Plan tab (DESIGN 5.9). */
@Composable
fun PlanScreen(navigation: PlanNavigation, modifier: Modifier = Modifier, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar, viewModel::undo)
    // The day and slot of the add-meal sheet, saved so it stays open with what was typed (after v1, REL-09).
    var addingDay by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSlot by rememberSaveable { mutableStateOf<MealSlot?>(null) }
    val adding = addingDay?.let(LocalDate::parse)?.let { day -> addingSlot?.let { day to it } }
    val stopAdding = {
        addingDay = null
        addingSlot = null
    }
    var openMealId by rememberSaveable { mutableStateOf<String?>(null) }
    var shopping by rememberSaveable { mutableStateOf(false) }
    val handlers = remember(viewModel) {
        PlanHandlers(
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onThisWeek = viewModel::thisWeek,
            onAdd = { date, slot ->
                addingDay = date.toString()
                addingSlot = slot
            },
            onOpen = { openMealId = it.id },
            onShop = { shopping = true },
            onCopyLastWeek = viewModel::copyLastWeek,
            onClearWeek = viewModel::clearWeek,
            onRetry = viewModel::retryLoad,
        )
    }

    PlanTab(
        state = state,
        snackbar = snackbar,
        onOpenSettings = navigation.openSettings,
        handlers = handlers,
        modifier = modifier,
    )

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
                stopAdding()
            },
            onAddOther = { title, cooks ->
                viewModel.addOther(date, slot, title, cooks)
                stopAdding()
            },
            onDismiss = stopAdding,
        )
    }
    val openMeal = openMealId?.let(state::meal)
    if (openMealId != null && openMeal == null && !state.loading) openMealId = null
    openMeal?.let { meal ->
        MealSheet(
            meal = meal,
            members = state.members,
            myUid = state.myUid,
            recipeExists = meal.recipeId != null && state.recipes.any { it.id == meal.recipeId },
            recipeServings = state.recipes.firstOrNull { it.id == meal.recipeId }?.content?.servings,
            onSave = { servings, cooks, note ->
                viewModel.update(meal, servings, cooks, note)
                openMealId = null
            },
            onCook = {
                openMealId = null
                viewModel.cook(meal)
            },
            weekDays = MealWeek.days(MealWeek.start(meal.date, state.weekStart.dayOfWeek)),
            onMove = { date, slot ->
                openMealId = null
                viewModel.move(meal, date, slot)
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

/**
 * The Plan tab in the 2026-11 look (DESIGN 5.9, 19.7, 19.12): "This week" above the title while this week shows, the
 * week's ingredients and the ⋮ menu in the header, then the week.
 */
@Composable
internal fun PlanTab(
    state: PlanViewModel.UiState,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    handlers: PlanHandlers,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val weekMeals = state.days.sumOf { it.meals.size }
    TabScaffold(
        title = stringResource(R.string.tab_plan),
        eyebrow = if (state.isThisWeek) stringResource(R.string.plan_eyebrow_this_week) else null,
        onOpenSettings = onOpenSettings,
        modifier = modifier.testTag("screen_plan"),
        actions = {
            if (state.canShop) {
                IconButton(onClick = handlers.onShop, modifier = Modifier.testTag("plan_shop")) {
                    Icon(Icons.Rounded.AddShoppingCart, contentDescription = stringResource(R.string.plan_shop))
                }
            }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("plan_menu")) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.plan_more_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_copy_last_week)) },
                    onClick = {
                        menuOpen = false
                        handlers.onCopyLastWeek()
                    },
                    modifier = Modifier.testTag("plan_copy_last_week"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_clear_week)) },
                    // Undo, no dialog (after v1, R13 and DESIGN 6.1).
                    onClick = {
                        menuOpen = false
                        handlers.onClearWeek()
                    },
                    enabled = weekMeals > 0,
                    modifier = Modifier.testTag("plan_clear_week"),
                )
            }
        },
        snackbarHost = { SpoilNotSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loadFailed) {
                LoadError(onRetry = handlers.onRetry)
            } else if (state.offline && !state.loading) {
                OfflineBanner()
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!state.loadFailed) CircularProgressIndicator()
                }
            } else {
                PlanWeek(state = state, handlers = handlers)
            }
        }
    }
}

/**
 * The week (DESIGN 5.9, 19.12): the switcher and the row of days stay above the days, which scroll; today is the
 * highlight card and every other day a heading over its group of rows.
 */
@Composable
internal fun PlanWeek(
    state: PlanViewModel.UiState,
    handlers: PlanHandlers,
    listState: LazyListState = rememberLazyListState(),
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    // An empty week says how to fill it, with Copy last week out of the ⋮ menu too (after v1, UX-15).
    val empty = state.days.all { it.isEmpty }
    // The days follow the empty week's sentence when it shows.
    val firstDay = if (empty) 1 else 0
    // This week opens on today, not on the first day; an empty one at its sentence.
    LaunchedEffect(state.weekStart) {
        val today = state.days.indexOfFirst { it.isToday }
        listState.scrollToItem(if (today >= 0 && !empty) today else 0)
    }
    val recipeIds = remember(state.recipes) { state.recipes.mapTo(HashSet()) { it.id } }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeekSwitcher(state.weekStart, handlers.onPrevious, handlers.onNext)
            if (!state.isThisWeek) {
                TextButton(
                    onClick = handlers.onThisWeek,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                        .testTag("plan_this_week"),
                ) { Text(stringResource(R.string.plan_this_week), style = SpoilNotText.ButtonMedium) }
            }
            DayRow(state.days) { index ->
                scope.launch {
                    if (reduceMotion) {
                        listState.scrollToItem(firstDay + index)
                    } else {
                        listState.animateScrollToItem(firstDay + index)
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("plan_days"),
        ) {
            if (empty) item(key = "empty", contentType = "empty") { EmptyWeek(handlers.onCopyLastWeek) }
            items(
                state.days,
                key = { it.date.toString() },
                contentType = { if (it.isToday) "today" else "day" },
            ) { day ->
                DayCard(
                    day = day,
                    members = state.members,
                    myUid = state.myUid,
                    recipeIds = recipeIds,
                    onAdd = { slot -> handlers.onAdd(day.date, slot) },
                    onOpen = handlers.onOpen,
                )
            }
        }
    }
}

/** "Nothing planned this week…" with Copy last week (after v1, UX-15), on a plain card (DESIGN 19.8). */
@Composable
private fun EmptyWeek(onCopyLastWeek: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().testTag("plan_week_empty"),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plan_week_empty), style = MaterialTheme.typography.bodyLarge)
            TonalButton(
                text = stringResource(R.string.plan_copy_last_week),
                onClick = onCopyLastWeek,
                modifier = Modifier.testTag("plan_empty_copy"),
            )
        }
    }
}

/** "Nov 9 – 15" between Previous and Next week, on a round bar of the row color (DESIGN 19.12). */
@Composable
private fun WeekSwitcher(start: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(SpoilNotSurfaces.current.row, CircleShape)
            .padding(horizontal = 2.dp),
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
            style = SpoilNotText.SectionTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp).testTag("plan_week_range"),
        )
        IconButton(onClick = onNext, modifier = Modifier.testTag("plan_next_week")) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.plan_next_week),
            )
        }
    }
}

/** The least width of a day button once the row scrolls sideways, from 1.5× text. */
private val DayButtonMinWidth = 64.dp

/**
 * The week's days as buttons (DESIGN 19.12), in the week's order: the weekday's short name over the day of the month,
 * today's filled. [onPick] gets the day's index. From 1.5× text the row scrolls sideways instead of shrinking.
 */
@Composable
private fun DayRow(days: List<PlanDay>, onPick: (Int) -> Unit) {
    val large = isLargeText()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (large) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .testTag("plan_day_row"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEachIndexed { index, day ->
            DayButton(
                day = day,
                onClick = { onPick(index) },
                modifier = if (large) Modifier.widthIn(min = DayButtonMinWidth) else Modifier.weight(1f),
            )
        }
    }
}

/** One day: a button named in full for screen readers, "Saturday, Nov 14, today", that shows the day's card. */
@Composable
private fun DayButton(day: PlanDay, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val today = day.isToday
    val title = dayTitle(day.date)
    val name = if (today) stringResource(R.string.plan_day_today, title) else title
    val shape = if (today) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (today) colors.primary else SpoilNotSurfaces.current.row)
            .clickable(onClickLabel = stringResource(R.string.plan_show_day), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag("plan_day_button_${day.date}"),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (today) colors.onPrimary else colors.onSurface) {
            Column(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp).clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    shortWeekday(day.date),
                    style = SpoilNotText.ChipLabel,
                    color = if (today) colors.onPrimary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(day.date.dayOfMonth.toString(), style = SpoilNotText.SectionTitle, maxLines = 1)
            }
        }
    }
}

/**
 * One day (DESIGN 5.9, 19.8): today is the highlight card with a Today badge, never color alone; any other day is a
 * heading over a group of rows, a past day's heading in `onSurfaceVariant`. Each slot is a row with + to add to it, or
 * its meals, the first of them carrying the +.
 */
@Composable
internal fun DayCard(
    day: PlanDay,
    members: List<Member>,
    myUid: String,
    recipeIds: Set<String>,
    onAdd: (MealSlot) -> Unit,
    onOpen: (PlannedMeal) -> Unit,
) {
    val title = dayTitle(day.date)
    if (day.isToday) {
        HighlightCard(Modifier.testTag("plan_day_${day.date}")) {
            // From 1.5× text the badge goes under the title, which keeps the card's width (review A11Y-04).
            NameWithTrailing(
                modifier = Modifier.padding(horizontal = 8.dp),
                spacing = 10.dp,
                name = { Text(title, style = SpoilNotText.CardTitle, modifier = it.semantics { heading() }) },
                trailing = { TodayBadge() },
            )
            SlotRows(day, members, myUid, recipeIds, nested = true, onAdd = onAdd, onOpen = onOpen)
        }
    } else {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxWidth().testTag("plan_day_${day.date}"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = SpoilNotText.SectionTitle,
                color = if (day.isPast) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp).semantics { heading() },
            )
            SlotRows(day, members, myUid, recipeIds, nested = false, onAdd = onAdd, onOpen = onOpen)
        }
    }
}

/** "Today" beside today's title: the card's colors swapped. */
@Composable
private fun TodayBadge() {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.plan_today),
        style = SpoilNotText.ChipLabel,
        fontWeight = FontWeight.ExtraBold,
        color = colors.primaryContainer,
        modifier = Modifier
            .background(colors.onPrimaryContainer, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** A row of a day's group: an empty slot, or one of its meals. */
internal sealed interface SlotRow {
    val slot: MealSlot

    data class Empty(override val slot: MealSlot) : SlotRow

    /** A planned meal; the slot's [first] meal carries the slot's +. */
    data class Meal(val meal: PlannedMeal, val first: Boolean) : SlotRow {
        override val slot: MealSlot get() = meal.slot
    }
}

/** A day's rows in slot order: an empty slot is one row, a slot with meals one row per meal. */
internal fun slotRows(day: PlanDay): List<SlotRow> = MealSlot.entries.flatMap { slot ->
    val meals = day.slots[slot].orEmpty()
    if (meals.isEmpty()) listOf(SlotRow.Empty(slot)) else meals.mapIndexed { i, meal -> SlotRow.Meal(meal, i == 0) }
}

/** A day's slots as one group of rows (DESIGN 19.5); [nested] inside the highlight card. */
@Composable
private fun SlotRows(
    day: PlanDay,
    members: List<Member>,
    myUid: String,
    recipeIds: Set<String>,
    nested: Boolean,
    onAdd: (MealSlot) -> Unit,
    onOpen: (PlannedMeal) -> Unit,
) {
    val rows = slotRows(day)
    val surfaces = SpoilNotSurfaces.current
    val style = RowStyle(
        color = if (nested) surfaces.nestedRow else surfaces.row,
        outerCorner = if (nested) SpoilNotCorners.NestedOuter else SpoilNotCorners.GroupOuter,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
        rows.forEachIndexed { index, row ->
            val position = segmentPosition(index, rows.size)
            val add: (@Composable () -> Unit)? = if (row is SlotRow.Empty || (row as SlotRow.Meal).first) {
                { AddButton(day.date, row.slot) { onAdd(row.slot) } }
            } else {
                null
            }
            when (row) {
                is SlotRow.Empty -> EmptySlotRow(row.slot, position, style) { add?.invoke() }

                is SlotRow.Meal -> MealRow(
                    meal = row.meal,
                    cooks = cookNames(row.meal.cooks, members, myUid),
                    deleted = row.meal.recipeId != null && row.meal.recipeId !in recipeIds,
                    position = position,
                    style = style,
                    add = add,
                    onOpen = { onOpen(row.meal) },
                )
            }
        }
    }
}

/** The row color and outside corners of a day's group: nested rows inside today's card. */
private class RowStyle(val color: Color, val outerCorner: Dp)

/** A slot with nothing planned: its name and + (one line, DESIGN 19.5). */
@Composable
private fun EmptySlotRow(slot: MealSlot, position: SegmentPosition, style: RowStyle, add: @Composable () -> Unit) {
    ListSegment(
        position = position,
        color = style.color,
        outerCorner = style.outerCorner,
        minHeight = ListSegmentMinHeightOneLine,
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
    ) {
        Text(
            slotLabel(slot),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        add()
    }
}

/**
 * A planned meal (DESIGN 5.9, 19.5): its slot over its title, then the servings, who cooks and Cooked. The row is one
 * control that opens the meal, read as one sentence ("Dinner: Tacos, 4 servings, cooking: Sam, Cooked"); + stays its
 * own stop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealRow(
    meal: PlannedMeal,
    cooks: List<String>,
    deleted: Boolean,
    position: SegmentPosition,
    style: RowStyle,
    add: (@Composable () -> Unit)?,
    onOpen: () -> Unit,
) {
    val details = listOfNotNull(
        if (deleted) stringResource(R.string.plan_recipe_deleted) else null,
        meal.servings?.let { pluralStringResource(R.plurals.plan_servings, it, it) },
    )
    val cooked = meal.cookedAt != null
    val description = (
        listOf(stringResource(R.string.home_today_meal_description, slotLabel(meal.slot), meal.title)) +
            details +
            listOfNotNull(
                cooks.takeIf {
                    it.isNotEmpty()
                }?.let { stringResource(R.string.home_today_cooking, it.joinToString()) },
                if (cooked) stringResource(R.string.plan_cooked) else null,
            )
        ).joinToString(", ")
    ListSegment(
        position = position,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.plan_open_meal),
        color = style.color,
        outerCorner = style.outerCorner,
        modifier = Modifier.semantics { contentDescription = description }.testTag("plan_meal_${meal.id}"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = if (add !=
                null
            ) {
                4.dp
            } else {
                16.dp
            },
            top = 10.dp,
            bottom = 10.dp,
        ),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                slotLabel(meal.slot),
                style = SpoilNotText.RowSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(meal.title, style = SpoilNotText.RowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty() || cooks.isNotEmpty() || cooked) {
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = SpoilNotText.RowSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    cooks.forEach { LabelChip(it, icon = Icons.Rounded.Person, kind = LabelChipKind.Person) }
                    if (cooked) CookedLabel()
                }
            }
        }
        add?.invoke()
    }
}

/** + for a slot, named with the slot and the day ("Add to Dinner, Saturday, Nov 14"). */
@Composable
private fun AddButton(date: LocalDate, slot: MealSlot, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("plan_add_${date}_${slot.wire}")) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = stringResource(R.string.plan_add_to, slotLabel(slot), dayTitle(date)),
            tint = MaterialTheme.colorScheme.primary,
        )
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

/** "Thu" in the phone's language, for the row of days. */
internal fun shortWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Thu 29" in the phone's language, for the Move chips. */
internal fun shortDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))

/** "Oct 29" in the phone's language. */
internal fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
