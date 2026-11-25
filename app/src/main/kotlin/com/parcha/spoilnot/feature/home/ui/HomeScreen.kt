package com.parcha.spoilnot.feature.home.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.AccentCard
import com.parcha.spoilnot.core.designsystem.component.HighlightCard
import com.parcha.spoilnot.core.designsystem.component.LabelChip
import com.parcha.spoilnot.core.designsystem.component.LabelChipKind
import com.parcha.spoilnot.core.designsystem.component.LeafBadge
import com.parcha.spoilnot.core.designsystem.component.ListGroupGap
import com.parcha.spoilnot.core.designsystem.component.ListSegment
import com.parcha.spoilnot.core.designsystem.component.ListSegmentMinHeightOneLine
import com.parcha.spoilnot.core.designsystem.component.LoadError
import com.parcha.spoilnot.core.designsystem.component.NameWithTrailing
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.component.SegmentPosition
import com.parcha.spoilnot.core.designsystem.component.StatusTile
import com.parcha.spoilnot.core.designsystem.component.StatusTileSizeNested
import com.parcha.spoilnot.core.designsystem.component.TabScaffold
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.component.segmentPosition
import com.parcha.spoilnot.core.designsystem.motion.Motion
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.core.designsystem.theme.StatusTone
import com.parcha.spoilnot.feature.home.domain.ActivityEvent
import com.parcha.spoilnot.feature.home.domain.ActivityType
import com.parcha.spoilnot.feature.home.domain.HomeSummary
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.insights.domain.InsightsSummary
import com.parcha.spoilnot.feature.insights.ui.OnPrimaryLegend
import com.parcha.spoilnot.feature.insights.ui.UsageRing
import com.parcha.spoilnot.feature.insights.ui.UsageRingColors
import com.parcha.spoilnot.feature.insights.ui.moodText
import com.parcha.spoilnot.feature.insights.ui.percent
import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryRow
import com.parcha.spoilnot.feature.pantry.ui.Badge
import com.parcha.spoilnot.feature.pantry.ui.chipText
import com.parcha.spoilnot.feature.pantry.ui.icon
import com.parcha.spoilnot.feature.pantry.ui.label
import com.parcha.spoilnot.feature.pantry.ui.statusText
import com.parcha.spoilnot.feature.pantry.ui.tone
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.plan.ui.CookedLabel
import com.parcha.spoilnot.feature.plan.ui.cookNames
import com.parcha.spoilnot.feature.plan.ui.slotLabel
import com.parcha.spoilnot.feature.recipes.domain.UseItUpSuggestion
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the Home cards lead. */
class HomeNavigation(
    val openSettings: () -> Unit,
    val openPantry: (PantryFilter) -> Unit,
    val openGroceries: () -> Unit,
    val reviewJoinRequests: () -> Unit,
    val openInsights: () -> Unit = {},
    val openRecipe: (String) -> Unit = {},
    val openPlan: () -> Unit = {},
    /** Asks for notifications, or opens the system's settings for them (after v1, review UX-02). */
    val turnOnReminders: () -> Unit = {},
)

/** How many rows a card lists before "See all". */
private const val CARD_ROWS = 4

/**
 * Home's line above its title (DESIGN 19.7): today as the phone's language writes a weekday with its date,
 * "Saturday, Nov 14" in American English.
 */
fun homeDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale))

@Composable
fun HomeScreen(
    navigation: HomeNavigation,
    pendingJoinRequests: Int,
    modifier: Modifier = Modifier,
    remindersOff: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A start ends when Home's cards (or its error) replace the skeleton, not at the first frame: Android records
    // that as the time to full display, and StartupBenchmark measures it (DESIGN 13.1, after v1 review PERF-05).
    ReportDrawnWhen { state.summary != null || state.loadFailed }
    val locale = LocalConfiguration.current.locales[0]
    TabScaffold(
        title = stringResource(R.string.tab_home),
        eyebrow = state.today.takeIf { it != LocalDate.MIN }?.let { homeDate(it, locale) },
        onOpenSettings = navigation.openSettings,
        modifier = modifier.testTag("screen_home"),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loadFailed) {
                LoadError(onRetry = viewModel::retryLoad)
            } else {
                OfflineBanner(offline = state.offline && state.summary != null)
            }
            Column(
                Modifier.verticalScroll(
                    rememberScrollState(),
                ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pendingJoinRequests > 0) JoinRequestsCard(pendingJoinRequests, navigation.reviewJoinRequests)
                val summary = state.summary
                if (summary == null) {
                    if (!state.loadFailed) repeat(4) { SkeletonCard() }
                } else {
                    HomeCards(
                        summary,
                        state.today,
                        state.myUid,
                        navigation,
                        state.insights,
                        state.useItUp,
                        state.todayMeals,
                        state.members,
                        remindersOff,
                    )
                }
            }
        }
    }
}

/**
 * Home's cards in the 2026-11 look (DESIGN 5.6, 19.8, 19.12): Needs attention first as the highlight, then Today,
 * Your kitchen, Use it up, the grocery list and Recent activity.
 */
@Composable
internal fun HomeCards(
    summary: HomeSummary,
    today: LocalDate,
    myUid: String,
    navigation: HomeNavigation,
    insights: InsightsSummary? = null,
    useItUp: List<UseItUpSuggestion> = emptyList(),
    todayMeals: List<PlannedMeal> = emptyList(),
    members: List<Member> = emptyList(),
    remindersOff: Boolean = false,
) {
    NeedsAttentionCard(summary, today, navigation, remindersOff)
    if (todayMeals.isNotEmpty()) TodaySection(todayMeals, members, myUid, navigation.openPlan)
    insights?.let { KitchenCard(it, navigation.openInsights) }
    if (useItUp.isNotEmpty()) UseItUpCard(useItUp, today, navigation.openRecipe)
    GroceryCard(summary, navigation.openGroceries)
    PlainCard(
        title = stringResource(R.string.home_recent_activity),
        icon = Icons.Rounded.History,
        tag = "card_home_activity",
        calm = if (summary.recent.isEmpty()) stringResource(R.string.home_activity_none) else null,
    ) {
        summary.recent.forEach { ActivityLine(it, myUid) }
    }
}

/**
 * Needs attention (DESIGN 5.6, 19.8): the highlight card, its count in a badge, what can still be used first and the
 * expired folded into one row, as in Pantry (after v1, UX-01). With nothing to show it is a calm plain card that says
 * so, or for a new household how to start (UX-07). The tour's Home step points at it (DESIGN 5.8).
 */
@Composable
private fun NeedsAttentionCard(
    summary: HomeSummary,
    today: LocalDate,
    navigation: HomeNavigation,
    remindersOff: Boolean,
) {
    val attention = summary.attention
    val title = stringResource(R.string.home_attention)
    val modifier = Modifier.coachMark(TourTarget.HomeCards.key).testTag("card_home_attention")
    if (attention.isEmpty()) {
        CalmCard(
            title = title,
            message = stringResource(
                if (summary.pantryEmpty) R.string.home_attention_start else R.string.home_attention_none,
            ),
            // A new household has nothing done yet: no check mark (after v1, UX-07).
            icon = if (summary.pantryEmpty) Icons.Rounded.Kitchen else Icons.Rounded.CheckCircle,
            modifier = modifier,
            // What reminders would tell, and the phone that blocks them, belong together: a line, not another banner.
            footer = if (remindersOff) {
                { RemindersOffLine(navigation.turnOnReminders, MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        )
        return
    }
    HighlightCard(modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
            Text(title, style = SpoilNotText.CardTitle, modifier = Modifier.weight(1f).semantics { heading() })
            CountBadge(attention.size)
        }
        val usable = attention.usable.take(CARD_ROWS)
        val rows = usable.size + if (attention.expired.isEmpty()) 0 else 1
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupGap)) {
            usable.forEachIndexed { index, item -> AttentionRow(item, today, segmentPosition(index, rows)) }
            if (attention.expired.isNotEmpty()) ExpiredRow(attention.expired.size, segmentPosition(rows - 1, rows))
        }
        if (remindersOff) RemindersOffLine(navigation.turnOnReminders, MaterialTheme.colorScheme.onPrimaryContainer)
        // "See 1 in Pantry" next to the one item already shown led to the same thing (after v1, UX-18). The same set
        // and count as Pantry's Needs attention filter (after v1, R8).
        val allShown = attention.expired.isEmpty() && attention.usable.size <= CARD_ROWS
        CardButton(
            text = if (allShown) {
                stringResource(R.string.home_open_pantry)
            } else {
                pluralStringResource(R.plurals.home_see_all, attention.size, attention.size)
            },
            onClick = { navigation.openPantry(PantryFilter.NeedsAttention) },
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/**
 * The number of purchases needing attention, beside the card's title. It isn't read: the rows and *See all N* say it,
 * and a bare number after the heading would not.
 */
@Composable
private fun CountBadge(count: Int) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape)
            .padding(horizontal = 12.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = SpoilNotText.SectionTitle, color = MaterialTheme.colorScheme.primaryContainer)
    }
}

/**
 * One purchase that can still be used (DESIGN 19.6): its status tile, the name, and the status in words in its color,
 * under the name from 1.5× text (review A11Y-04). TalkBack reads one sentence with the full wording, "Milk, Expires
 * tomorrow" (review A11Y-07).
 */
@Composable
private fun AttentionRow(item: PantryItem.Purchase, today: LocalDate, position: SegmentPosition) {
    val batch = item.batch
    val status = ExpiryStatus.of(batch.expiryDate, today)
    val full = statusText(batch.expiryDate, batch.dateType, today)
    NestedRow(position, Modifier.semantics(mergeDescendants = true) {}.testTag("home_attention_row")) {
        StatusTile(status.tone(), status.icon(), size = StatusTileSizeNested)
        NameWithTrailing(
            modifier = Modifier.weight(1f),
            name = {
                Text(
                    item.name,
                    style = SpoilNotText.RowTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = it,
                )
            },
            trailing = {
                Text(
                    chipText(batch.expiryDate, batch.dateType, today),
                    style = SpoilNotText.RowStatus,
                    color = SpoilNotStatus.colors.forTone(status.tone()),
                    modifier = Modifier.semantics { contentDescription = full },
                )
            },
        )
    }
}

/** "3 expired items to throw away or check": the expired folded into the last row (after v1, UX-01). */
@Composable
private fun ExpiredRow(count: Int, position: SegmentPosition) {
    NestedRow(position, Modifier.semantics(mergeDescendants = true) {}.testTag("home_attention_expired")) {
        StatusTile(StatusTone.Expired, Icons.Rounded.DeleteOutline, size = StatusTileSizeNested)
        Text(
            pluralStringResource(R.plurals.home_attention_expired, count, count),
            style = SpoilNotText.RowTitle,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A row inside the highlight card: the nested row surface and corners (DESIGN 19.5). */
@Composable
private fun NestedRow(
    position: SegmentPosition,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    ListSegment(
        position = position,
        modifier = modifier,
        color = SpoilNotSurfaces.current.nestedRow,
        outerCorner = SpoilNotCorners.NestedOuter,
        minHeight = ListSegmentMinHeightOneLine,
        contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        content = content,
    )
}

/**
 * Today's planned meals and who cooks them (DESIGN 5.9, 19.12): a section on the page with *Open plan* beside its
 * heading, one tile per meal, two side by side and one per line from 1.5× text. Shown only when something is planned.
 */
@Composable
private fun TodaySection(meals: List<PlannedMeal>, members: List<Member>, myUid: String, onOpen: () -> Unit) {
    Column(Modifier.testTag("card_home_today"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_today),
                style = SpoilNotText.SectionTitle,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_today_open), style = SpoilNotText.ButtonMedium)
            }
        }
        val perRow = if (isLargeText()) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.chunked(perRow).forEach { line ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    line.forEach { meal ->
                        MealTile(meal, cookNames(meal.cooks, members, myUid), Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One meal: its slot, its title, the cooks and Cooked. One stop for TalkBack (review A11Y-07). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealTile(meal: PlannedMeal, cooks: List<String>, modifier: Modifier) {
    // "Breakfast: Porridge, cooking: You, Cooked".
    val description = listOfNotNull(
        stringResource(R.string.home_today_meal_description, slotLabel(meal.slot), meal.title),
        cooks.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.home_today_cooking, it.joinToString()) },
        meal.cookedAt?.let { stringResource(R.string.plan_cooked) },
    ).joinToString(", ")
    Surface(
        modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                slotLabel(meal.slot),
                style = SpoilNotText.RowSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(meal.title, style = SpoilNotText.RowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (cooks.isNotEmpty() || meal.cookedAt != null) {
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cooks.forEach { LabelChip(it, icon = Icons.Rounded.Person, kind = LabelChipKind.Person) }
                    if (meal.cookedAt != null) CookedLabel()
                }
            }
        }
    }
}

/**
 * Your kitchen this month (DESIGN 5.7, 19.8): the percentage used, large, with the counts and the ring on a `primary`
 * card. The whole card opens Your kitchen; TalkBack reads the title and the ring's sentence once.
 */
@Composable
private fun KitchenCard(insights: InsightsSummary, onOpen: () -> Unit) {
    val month = insights.current
    val share = month.usedShare
    val description = if (share == null) {
        stringResource(R.string.insights_ring_empty)
    } else {
        stringResource(R.string.insights_ring_description, month.used, month.thrownTotal, percent(share))
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SpoilNotCorners.Highlight)
    Surface(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.insights_see), onClick = onOpen)
            .testTag("card_home_insights"),
        shape = shape,
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // onPrimary, not the lighter primaryContainer: that is under 4.5:1 on four light themes' primary.
                Text(
                    stringResource(R.string.insights_card_title),
                    style = SpoilNotText.RowSupporting,
                    modifier = Modifier.semantics { heading() },
                )
                // The ring's sentence says the numbers; what is written here isn't read again.
                Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (share == null) {
                        Text(moodText(insights.mood), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.insights_percent, percent(share)), style = SpoilNotText.BigNumber)
                        OnPrimaryLegend(month.used, month.thrownTotal)
                    }
                }
            }
            UsageRing(
                used = month.used,
                thrown = month.thrownTotal,
                centerText = "",
                description = description,
                size = 84.dp,
                colors = UsageRingColors.onPrimary(),
            )
        }
    }
}

/**
 * "Use it up" (DESIGN 5.6, 19.8): an accent card with saved recipes that use food expiring soon, each with what it
 * uses and when that expires, each opening its recipe. Shown only when there is a match.
 */
@Composable
private fun UseItUpCard(suggestions: List<UseItUpSuggestion>, today: LocalDate, onOpen: (String) -> Unit) {
    AccentCard(Modifier.testTag("card_home_use_it_up")) {
        LeafBadge { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_use_it_up),
                style = SpoilNotText.RowSupporting,
                modifier = Modifier.semantics { heading() },
            )
            suggestions.forEach { suggestion ->
                // map is inline, so the composable chip text can be read for each use.
                val uses = suggestion.uses.map { use ->
                    "${use.product.name} (${chipText(use.batch.expiryDate, use.batch.dateType, today)})"
                }.joinToString(", ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(R.string.home_use_it_up_open)) {
                            onOpen(suggestion.recipe.id)
                        }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            suggestion.recipe.title,
                            style = SpoilNotText.RowTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(R.string.home_use_it_up_uses, uses), style = SpoilNotText.RowSupporting)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

/**
 * The grocery list: what is left to buy, and what is running low (after v1, R1 and R2: Low stock was its own card,
 * though every low product is already on the list). A household with no Keep some in stock yet is told how to start.
 */
@Composable
private fun GroceryCard(summary: HomeSummary, onOpen: () -> Unit) {
    PlainCard(
        title = stringResource(R.string.home_grocery_list),
        icon = Icons.AutoMirrored.Rounded.ListAlt,
        tag = "card_home_groceries",
        action = stringResource(R.string.home_open_list) to onOpen,
    ) {
        Text(
            if (summary.groceryCount == 0) {
                stringResource(R.string.home_grocery_empty)
            } else {
                pluralStringResource(R.plurals.home_grocery_count, summary.groceryCount, summary.groceryCount)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (summary.lowStock.isNotEmpty()) {
            Text(
                stringResource(R.string.home_running_low),
                style = SpoilNotText.RowSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            summary.lowStock.take(CARD_ROWS).forEach { row -> LowStockRow(row) }
            val more = summary.lowStock.size - CARD_ROWS
            if (more > 0) {
                Text(
                    pluralStringResource(R.plurals.home_running_low_more, more, more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (summary.noStaples) {
            Text(
                stringResource(R.string.home_grocery_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_grocery_start"),
            )
        }
    }
}

/**
 * A plain card (DESIGN 19.8): the row surface with 24 dp corners, an icon and a title, its content, and a filled
 * button at the end. With [calm] it is one compact row instead (after v1).
 */
@Composable
private fun PlainCard(
    title: String,
    icon: ImageVector,
    tag: String,
    action: Pair<String, () -> Unit>? = null,
    calm: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (calm != null) {
        CalmCard(title, calm, icon, Modifier.testTag(tag))
        return
    }
    Surface(
        Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = SpoilNotText.SectionTitle, modifier = Modifier.weight(1f).semantics { heading() })
            }
            content()
            action?.let { (label, onClick) -> CardButton(label, onClick, Modifier.align(Alignment.End)) }
        }
    }
}

/** Nothing to show: a compact plain card with an icon, the title and one line (after v1). */
@Composable
private fun CalmCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(title, style = SpoilNotText.RowTitle, modifier = Modifier.semantics { heading() })
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            footer?.invoke()
        }
    }
}

/** A card's filled button: `primary`, fully round, 48 dp (DESIGN 19.9). */
@Composable
private fun CardButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = SpoilNotText.ButtonMedium)
    }
}

/**
 * "Reminders are off on this phone · Turn on" (after v1, review UX-02): the member wants reminders and this phone blocks
 * them. Said in words with an icon, not by color. [buttonColor] keeps Turn on readable on the card it sits on.
 */
@Composable
private fun RemindersOffLine(onTurnOn: () -> Unit, buttonColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp).testTag("home_reminders_off"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.NotificationsOff, contentDescription = null)
        Text(
            stringResource(R.string.home_reminders_off),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onTurnOn,
            modifier = Modifier.heightIn(min = 48.dp).testTag("home_reminders_turn_on"),
            colors = ButtonDefaults.textButtonColors(contentColor = buttonColor),
        ) { Text(stringResource(R.string.home_reminders_turn_on), style = SpoilNotText.ButtonMedium) }
    }
}

/** "Olive oil · Low" as one sentence, the badge under the name from 1.5× text (review A11Y-04, A11Y-07). */
@Composable
private fun LowStockRow(row: PantryRow) {
    NameWithTrailing(
        Modifier.semantics(mergeDescendants = true) {},
        name = {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = it,
            )
        },
        trailing = { Badge(stringResource(if (row.isOut) R.string.badge_out else R.string.badge_low)) },
    )
}

/** "Sam added Eggs to Groceries · 5 min ago" (DESIGN 5.6). */
@Composable
private fun ActivityLine(event: ActivityEvent, myUid: String) {
    val resources = LocalResources.current
    val who = if (event.actorUid == myUid) stringResource(R.string.activity_you) else event.actorName
    val name = event.name.orEmpty()
    val text = when (event.type) {
        ActivityType.StockAdded -> stringResource(R.string.activity_stock_added, who, name)

        ActivityType.GroceryAdded -> when {
            event.count != null -> pluralStringResource(
                R.plurals.activity_grocery_added_from_recipe,
                event.count,
                who,
                event.count,
                name,
                event.listName ?: stringResource(R.string.activity_the_list),
            )

            else -> stringResource(
                R.string.activity_grocery_added,
                who,
                name,
                event.listName ?: stringResource(R.string.activity_the_list),
            )
        }

        ActivityType.GroceryChecked -> stringResource(R.string.activity_grocery_checked, who, name)

        ActivityType.RecipeSaved -> stringResource(R.string.activity_recipe_saved, who, name)

        ActivityType.Cooked -> stringResource(R.string.activity_recipe_cooked, who, name)

        ActivityType.MemberJoined -> stringResource(R.string.activity_member_joined, who)

        ActivityType.HouseholdReset -> stringResource(R.string.activity_household_reset, who)

        ActivityType.Discarded -> event.reason?.let {
            stringResource(R.string.activity_discarded_reason, who, name, stringResource(it.label()).lowercase())
        } ?: stringResource(R.string.activity_discarded, who, name)
    }
    val time = event.createdAt?.let {
        val now = System.currentTimeMillis()
        if (now - it.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) {
            resources.getString(R.string.activity_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        time?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shown on the first load only (DESIGN 6.7); still when reduce motion is on. */
@Composable
private fun SkeletonCard() {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                tween(Motion.SKELETON_PULSE_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "skeleton alpha",
        ).value
    }
    val bar = SpoilNotSurfaces.current.tile
    Surface(
        Modifier.fillMaxWidth().testTag("home_skeleton"),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
    ) {
        Column(Modifier.padding(16.dp).alpha(alpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(0.4f).height(20.dp).background(bar, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth(0.9f).height(16.dp).background(bar, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth(0.7f).height(16.dp).background(bar, MaterialTheme.shapes.small))
        }
    }
}

/** Shown to the owner while someone is waiting for approval (DESIGN 5.6). */
@Composable
private fun JoinRequestsCard(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().testTag("card_home_join_requests"),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        val text = @Composable { textModifier: Modifier ->
            Row(textModifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                Text(pluralStringResource(R.plurals.home_join_requests, count, count), style = SpoilNotText.RowTitle)
            }
        }
        val button = @Composable { CardButton(stringResource(R.string.action_review), onReview) }
        // From 1.5× text the button goes under the sentence; beside it, the sentence broke one syllable per line
        // (review A11Y-04).
        if (isLargeText()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                text(Modifier)
                Box(Modifier.align(Alignment.End)) { button() }
            }
        } else {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                text(Modifier.weight(1f))
                button()
            }
        }
    }
}
