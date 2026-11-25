package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.AccentCard
import com.parcha.spoilnot.core.designsystem.component.LeafBadge
import com.parcha.spoilnot.core.designsystem.component.PlainCard
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.component.isLargeText
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.insights.domain.InsightsSummary
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.insights.domain.Mood
import com.parcha.spoilnot.feature.pantry.ui.label
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: InsightsViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    InsightsPage(summary, onBack, modifier)
}

/**
 * Your kitchen (DESIGN 5.7, 19.8): a pushed screen with this month's name above its title, then this month on the
 * `primary` card, the last six months, the streak and why things were thrown away.
 */
@Composable
internal fun InsightsPage(summary: InsightsSummary?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PushedScaffold(
        title = stringResource(R.string.insights_title),
        onBack = onBack,
        eyebrow = summary?.let { monthName(it.current.month, TextStyle.FULL, it.current.month) },
        modifier = modifier.testTag("screen_insights"),
    ) { padding ->
        if (summary == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            InsightsContent(summary, Modifier.padding(padding))
        }
    }
}

@Composable
internal fun InsightsContent(summary: InsightsSummary, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthCard(summary)
        MonthsCard(summary)
        StreakCard(summary)
        PlainCard(Modifier.testTag("card_insights_reasons")) {
            CardHeading(stringResource(R.string.insights_section_reasons))
            if (summary.reasons.isEmpty()) {
                Text(stringResource(R.string.insights_reasons_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                ReasonBars(summary.reasons.map { (reason, count) -> stringResource(reason.label()) to count })
            }
        }
        Text(
            stringResource(R.string.insights_counting_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * This month on `primary` (DESIGN 19.8), like Home's card: the percentage used in [SpoilNotText.HeroNumber], the
 * counts as a legend, the ring at the end and the friendly sentence under them. TalkBack reads the title, the ring's
 * sentence and the friendly one; the numbers written out aren't read again (review A11Y-07). From 1.5× text the ring
 * goes under the numbers.
 */
@Composable
private fun MonthCard(summary: InsightsSummary) {
    val month = summary.current
    val share = month.usedShare
    val description = if (share == null) {
        stringResource(R.string.insights_ring_empty)
    } else {
        stringResource(R.string.insights_ring_description, month.used, month.thrownTotal, percent(share))
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().testTag("card_insights_month"),
        shape = RoundedCornerShape(SpoilNotCorners.Highlight),
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val numbers = @Composable {
                Column {
                    // onPrimary, not primaryContainer: that is under 4.5:1 on four light themes' primary (19.8).
                    Text(
                        stringResource(R.string.insights_card_title),
                        style = SpoilNotText.RowSupporting,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (share != null) {
                        Column(Modifier.clearAndSetSemantics { }) {
                            Text(
                                stringResource(R.string.insights_percent, percent(share)),
                                style = SpoilNotText.HeroNumber,
                            )
                            OnPrimaryLegend(month.used, month.thrownTotal)
                        }
                    }
                }
            }
            val ring = @Composable {
                UsageRing(
                    used = month.used,
                    thrown = month.thrownTotal,
                    centerText = "",
                    description = description,
                    size = 112.dp,
                    colors = UsageRingColors.onPrimary(),
                )
            }
            if (isLargeText()) {
                numbers()
                ring()
            } else {
                // With nothing counted the title alone sits at the top, not in the middle beside the ring.
                Row(
                    verticalAlignment = if (share == null) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.weight(1f)) { numbers() }
                    ring()
                }
            }
            Text(
                moodText(summary.mood),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The last six months on a plain card: the bars, their legend, the chosen month's details and the best month. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthsCard(summary: InsightsSummary) {
    PlainCard(
        Modifier.testTag("card_insights_months"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
    ) {
        CardHeading(stringResource(R.string.insights_section_months), Modifier.padding(horizontal = 6.dp))
        if (summary.months.all { it.total == 0 }) {
            Text(
                stringResource(R.string.insights_months_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        } else {
            // The latest month is selected first; tapping another shows its details (after v1).
            var selected by rememberSaveable(summary.months.size) { mutableIntStateOf(summary.months.lastIndex) }
            MonthBars(
                bars = summary.months.map {
                    MonthBar(
                        monthName(it.month, TextStyle.SHORT, summary.current.month),
                        it.used,
                        it.thrownTotal,
                        stringResource(
                            R.string.insights_month_entry,
                            monthName(it.month, TextStyle.FULL, summary.current.month),
                            it.used,
                            it.thrownTotal,
                        ),
                    )
                },
                selected = selected,
                onSelect = { selected = it },
            )
            FlowRow(
                Modifier.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegendItem(stringResource(R.string.insights_legend_used), thrown = false)
                LegendItem(stringResource(R.string.insights_legend_thrown), thrown = true)
            }
            summary.months.getOrNull(selected)?.let { MonthDetails(it, summary.current.month) }
        }
        summary.bestMonth?.let { best ->
            Row(
                Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(
                        R.string.insights_best_month,
                        monthName(best.month, TextStyle.FULL, summary.current.month),
                        percent(best.usedShare),
                    ),
                    style = SpoilNotText.RowTitle,
                )
            }
        }
    }
}

/**
 * The tapped month in a box of the page's color: its counts, the share used and why things were thrown away (after
 * v1). It is a polite live region, so TalkBack reads the new month when another is tapped.
 */
@Composable
private fun MonthDetails(stats: MonthStats, thisMonth: YearMonth) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SpoilNotSurfaces.current.page, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("insights_month_details")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            monthName(stats.month, TextStyle.FULL, thisMonth),
            style = SpoilNotText.RowTitle,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (stats.total == 0) {
                stringResource(R.string.insights_month_nothing)
            } else {
                stringResource(
                    R.string.insights_month_details,
                    stats.used,
                    stats.thrownTotal,
                    percent(stats.usedShare),
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        val reasons = stats.thrown.filterValues { it > 0 }.entries.sortedByDescending { it.value }
        if (reasons.isNotEmpty()) {
            // map is inline, so the composable labels can be read for each reason.
            val text = reasons.map { (reason, count) -> "${stringResource(reason.label())} $count" }.joinToString(", ")
            Text(
                stringResource(R.string.insights_month_reasons, text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The streak as an accent card (DESIGN 19.8): the fire in a leaf, "Streak", then the days in
 * [SpoilNotText.AccentNumber] over what they mean, read together. Before a streak can be counted, or on a day something was thrown away, a
 * sentence says so instead.
 */
@Composable
private fun StreakCard(summary: InsightsSummary) {
    AccentCard(Modifier.testTag("card_insights_streak")) {
        LeafBadge(size = 56.dp) {
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.insights_section_streak),
                style = SpoilNotText.RowSupporting,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            val days = summary.daysWithoutWaste
            when {
                days == null -> StreakSentence(stringResource(R.string.insights_streak_none))

                days == 0 && !summary.neverThrown -> StreakSentence(stringResource(R.string.insights_streak_today))

                else -> Column(Modifier.semantics(mergeDescendants = true) { }) {
                    Text(
                        pluralStringResource(R.plurals.insights_streak_count, days, days),
                        style = SpoilNotText.AccentNumber,
                    )
                    val rest = if (summary.neverThrown) {
                        R.string.insights_streak_never_rest
                    } else {
                        R.string.insights_streak_rest
                    }
                    StreakSentence(stringResource(rest))
                }
            }
        }
    }
}

@Composable
private fun StreakSentence(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

/** A plain card's title, a heading for screen readers. */
@Composable
private fun CardHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SpoilNotText.SectionTitle, modifier = modifier.semantics { heading() })
}

@Composable
fun moodText(mood: Mood): String = stringResource(
    when (mood) {
        Mood.NothingYet -> R.string.insights_mood_nothing
        Mood.Great -> R.string.insights_mood_great
        Mood.Good -> R.string.insights_mood_good
        Mood.Okay -> R.string.insights_mood_okay
        Mood.Low -> R.string.insights_mood_low
    },
)

/** A whole percentage; 99.6 % shows as 99, not 100, so "100 %" means nothing was thrown away. */
fun percent(share: Float?): Int {
    val value = ((share ?: 0f) * 100)
    return if (value > 99f && value < 100f) 99 else value.roundToInt()
}

/** "May", or "May 2025" for a full name outside [thisMonth]'s year. */
private fun monthName(month: YearMonth, style: TextStyle, thisMonth: YearMonth): String {
    val name = month.month.getDisplayName(style, Locale.getDefault())
    return if (style == TextStyle.SHORT || month.year == thisMonth.year) name else "$name ${month.year}"
}
