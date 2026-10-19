package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
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
    DetailScaffold(
        title = stringResource(R.string.insights_title),
        onBack = onBack,
        modifier = modifier.testTag("screen_insights"),
    ) { padding ->
        val loaded = summary
        if (loaded == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            InsightsContent(loaded, Modifier.padding(padding))
        }
    }
}

@Composable
internal fun InsightsContent(summary: InsightsSummary, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.insights_card_title), "card_insights_month") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MonthRing(summary.current)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendItem(
                        stringResource(R.string.insights_legend_used) + " " + summary.current.used,
                        thrown = false,
                    )
                    LegendItem(
                        stringResource(R.string.insights_legend_thrown) + " " + summary.current.thrownTotal,
                        thrown = true,
                    )
                }
            }
            Text(moodText(summary.mood), style = MaterialTheme.typography.bodyLarge)
        }
        Section(stringResource(R.string.insights_section_months), "card_insights_months") {
            if (summary.months.all { it.total == 0 }) {
                Text(stringResource(R.string.insights_months_none), style = MaterialTheme.typography.bodyLarge)
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(stringResource(R.string.insights_legend_used), thrown = false)
                    LegendItem(stringResource(R.string.insights_legend_thrown), thrown = true)
                }
                summary.months.getOrNull(selected)?.let { MonthDetails(it, summary.current.month) }
            }
            summary.bestMonth?.let { best ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(
                            R.string.insights_best_month,
                            monthName(best.month, TextStyle.FULL, summary.current.month),
                            percent(best.usedShare),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Section(stringResource(R.string.insights_section_streak), "card_insights_streak") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    streakText(summary),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Section(stringResource(R.string.insights_section_reasons), "card_insights_reasons") {
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
        )
    }
}

/** The tapped month: its counts, the share used and why things were thrown away (after v1). */
@Composable
private fun MonthDetails(stats: MonthStats, thisMonth: YearMonth) {
    Column(
        Modifier.fillMaxWidth().testTag("insights_month_details").semantics(mergeDescendants = true) {
            liveRegion =
                LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(monthName(stats.month, TextStyle.FULL, thisMonth), style = MaterialTheme.typography.titleSmall)
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

/** This month's ring with its percentage, shared by Home and Insights. */
@Composable
fun MonthRing(stats: MonthStats, modifier: Modifier = Modifier, small: Boolean = false) {
    val share = stats.usedShare
    UsageRing(
        used = stats.used,
        thrown = stats.thrownTotal,
        centerText = if (share == null) "–" else stringResource(R.string.insights_percent, percent(share)),
        description = if (share == null) {
            stringResource(R.string.insights_ring_empty)
        } else {
            stringResource(R.string.insights_ring_description, stats.used, stats.thrownTotal, percent(share))
        },
        modifier = modifier,
        size = if (small) 72.dp else 140.dp,
        centerStyle = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
    )
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

@Composable
private fun streakText(summary: InsightsSummary): String {
    val days = summary.daysWithoutWaste ?: return stringResource(R.string.insights_streak_none)
    return when {
        summary.neverThrown -> pluralStringResource(R.plurals.insights_streak_never, days, days)
        days == 0 -> stringResource(R.string.insights_streak_today)
        else -> pluralStringResource(R.plurals.insights_streak_days, days, days)
    }
}

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

@Composable
private fun Section(title: String, tag: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
