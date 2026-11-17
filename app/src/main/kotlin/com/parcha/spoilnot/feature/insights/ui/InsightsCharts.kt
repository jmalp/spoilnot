package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.motion.Motion
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import kotlin.math.max

/**
 * Chart colors. Thrown away is never told apart by color alone (DESIGN 6.4): it is dashed in the ring and striped
 * and outlined in bars, and every chart has a text legend and a spoken summary.
 */
private object ChartColors {
    val used: Color @Composable get() = MaterialTheme.colorScheme.primary
    val thrown: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val thrownFill: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val track: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}

/**
 * 0 to 1 over [Motion.CHART_GROW_MS] when first shown, or 1 at once with reduce motion on. Read it only while drawing
 * (review PERF-03): read in composition or layout, it recomposed and re-laid out the whole screen on every frame.
 */
@Composable
private fun rememberGrow(key: Any): () -> Float {
    val reduceMotion = rememberReduceMotion()
    val progress = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(Motion.CHART_GROW_MS, easing = Motion.Standard)) }
    return remember(progress) { { progress.value } }
}

/** The colors of a [UsageRing]: the used arc, the dashed thrown-away arc and the track under them. */
data class UsageRingColors(val used: Color, val thrown: Color, val track: Color) {
    companion object {
        /** The chart colors on a surface. */
        @Composable
        fun onSurface() = UsageRingColors(ChartColors.used, ChartColors.thrown, ChartColors.track)

        /**
         * On the `primary` month card of Home and Your kitchen (DESIGN 19.8): used in `primaryContainer`, thrown
         * away in `onPrimary` dashes, the track `onPrimary` at 24% over `primary`.
         */
        @Composable
        fun onPrimary(): UsageRingColors {
            val colors = MaterialTheme.colorScheme
            return UsageRingColors(
                used = colors.primaryContainer,
                thrown = colors.onPrimary,
                track = colors.onPrimary.copy(alpha = 0.24f).compositeOver(colors.primary),
            )
        }
    }
}

/**
 * The share used this month as a ring: a solid arc for used, a dashed one for thrown away, and [centerText] in the
 * middle. Empty months show only the track. The arcs grow in the draw phase, and the dash pattern is made once per
 * size (review PERF-03).
 */
@Composable
fun UsageRing(
    used: Int,
    thrown: Int,
    centerText: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    centerStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    colors: UsageRingColors = UsageRingColors.onSurface(),
) {
    val grow = rememberGrow(used to thrown)
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description }
            .drawWithCache {
                val stroke = this.size.minDimension * 0.12f
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                val track = Stroke(stroke)
                val solid = Stroke(stroke, cap = StrokeCap.Butt)
                val dash = stroke * 0.6f
                val dashed = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash / 2)))
                val total = used + thrown
                onDrawBehind {
                    drawArc(colors.track, 0f, 360f, false, topLeft, arcSize, style = track)
                    if (total > 0) {
                        val g = grow()
                        val usedSweep = 360f * used / total * g
                        drawArc(colors.used, -90f, usedSweep, false, topLeft, arcSize, style = solid)
                        if (thrown > 0) {
                            val thrownSweep = 360f * thrown / total * g
                            drawArc(
                                colors.thrown,
                                -90f + usedSweep,
                                thrownSweep,
                                false,
                                topLeft,
                                arcSize,
                                style = dashed,
                            )
                        }
                    }
                }
            },
        Alignment.Center,
    ) {
        if (centerText.isNotEmpty()) Text(centerText, style = centerStyle, textAlign = TextAlign.Center)
    }
}

/** One month in [MonthBars]; [description] is what TalkBack reads for it. */
data class MonthBar(val label: String, val used: Int, val thrown: Int, val description: String = label)

/** A bar's least height, so a month with nothing still shows a line. */
private val MinBar = 2.dp

/**
 * Used and thrown away per month, side by side (DESIGN 19.8): solid bars for used, striped outlined bars for thrown
 * away, each with its number above and the month below. Each month is a tab ([onSelect]) that shows its details; the
 * chosen one has a `secondaryContainer` background and a heavier label, and TalkBack reads each month on its own.
 * The bars grow in the draw phase: their layout never changes while they grow (review PERF-03).
 */
@Composable
fun MonthBars(
    bars: List<MonthBar>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
) {
    val usedColor = ChartColors.used
    val thrownColor = ChartColors.thrown
    val thrownFill = ChartColors.thrownFill
    val grow = rememberGrow(bars)
    val max = (bars.maxOfOrNull { maxOf(it.used, it.thrown) } ?: 0).coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .testTag("month_bar_$index")
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(isSelected, role = Role.Tab) { onSelect(index) }
                    .clearAndSetSemantics {
                        contentDescription = bar.description
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bar(bar.used, max, height) { solidBar(usedColor, grow) }
                    Bar(bar.thrown, max, height) { stripedBar(thrownColor, thrownFill, grow, vertical = true) }
                }
                Text(
                    bar.label,
                    style = SpoilNotText.RowSupporting,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else null,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** A month's bar: its number over a box of its final height, which [drawBar] fills as it grows. */
@Composable
private fun Bar(value: Int, max: Int, chartHeight: Dp, drawBar: Modifier.() -> Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = SpoilNotText.ChipLabel, maxLines = 1, softWrap = false)
        val barHeight = (chartHeight * (value.toFloat() / max)).coerceAtLeast(MinBar)
        Spacer(Modifier.width(16.dp).height(barHeight).drawBar())
    }
}

/** A solid rounded bar that grows from the bottom (or the start, with [vertical] false) as [grow] goes to 1. */
private fun Modifier.solidBar(color: Color, grow: () -> Float, vertical: Boolean = true): Modifier = drawBehind {
    val min = MinBar.toPx()
    val g = grow()
    val width = if (vertical) size.width else max(size.width * g, min)
    val height = if (vertical) max(size.height * g, min) else size.height
    drawRoundRect(
        color,
        topLeft = Offset(0f, size.height - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
}

/**
 * The "thrown away" pattern: [fill] with diagonal [stripe]s inside a [stripe] outline, rounded. The stripes are one
 * path made once per size (review PERF-03: they were a line each, every frame); while [grow] goes to 1 the bar is
 * clipped to the grown part, from the bottom ([vertical]) or the start.
 */
private fun Modifier.stripedBar(stripe: Color, fill: Color, grow: () -> Float, vertical: Boolean): Modifier =
    drawWithCache {
        val line = 2.dp.toPx()
        val gap = 5.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        val min = MinBar.toPx()
        val stripes = Path().apply {
            var x = -size.height
            while (x < size.width) {
                moveTo(x, size.height)
                lineTo(x + size.height, 0f)
                x += gap
            }
        }
        val outline = Stroke(line)
        val clip = Path()
        onDrawBehind {
            val g = grow()
            val width = if (vertical) size.width else max(size.width * g, min)
            val height = if (vertical) max(size.height * g, min) else size.height
            val top = size.height - height
            clip.reset()
            clip.addRoundRect(RoundRect(0f, top, width, size.height, radius))
            clipPath(clip) {
                drawRect(fill, topLeft = Offset(0f, top), size = Size(width, height))
                drawPath(stripes, stripe, style = outline)
            }
            // The outline sits inside the bar, so its full width shows.
            val inset = line / 2
            drawRoundRect(
                stripe,
                topLeft = Offset(inset, top + inset),
                size = Size(max(width - line, 0f), max(height - line, 0f)),
                cornerRadius = CornerRadius(max(radius.x - inset, 0f)),
                style = outline,
            )
        }
    }

/** The swatches of a chart legend: solid for used, striped and outlined for thrown away. */
@Composable
private fun Swatch(thrown: Boolean) {
    val used = ChartColors.used
    val stripe = ChartColors.thrown
    val fill = ChartColors.thrownFill
    Spacer(
        Modifier.size(12.dp).then(
            if (thrown) Modifier.stripedBar(stripe, fill, { 1f }, vertical = true) else Modifier.solidBar(used, { 1f }),
        ),
    )
}

/** A small swatch and its label, for chart legends. */
@Composable
fun LegendItem(text: String, thrown: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatch(thrown)
        Text(text, style = SpoilNotText.RowSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The month card's legend on `primary` (Home and Your kitchen, DESIGN 19.8): "Used 3" with a solid `primaryContainer`
 * swatch and "Thrown away 1" with an outlined one, so the two differ by more than color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnPrimaryLegend(used: Int, thrown: Int, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OnPrimaryLegendEntry(stringResource(R.string.insights_legend_used) + " " + used) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)))
        }
        OnPrimaryLegendEntry(stringResource(R.string.insights_legend_thrown) + " " + thrown) {
            Box(Modifier.size(12.dp).border(2.dp, LocalContentColor.current, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun OnPrimaryLegendEntry(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(text, style = SpoilNotText.RowSupporting)
    }
}

/**
 * Why things were thrown away (DESIGN 19.8): each reason's name and count over a striped bar as long as its share of
 * the longest, growing in the draw phase.
 */
@Composable
fun ReasonBars(reasons: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val stripe = ChartColors.thrown
    val fill = ChartColors.thrownFill
    val grow = rememberGrow(reasons)
    val max = (reasons.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reasons.forEach { (label, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = ReasonText, modifier = Modifier.weight(1f))
                    Text(count.toString(), style = ReasonText, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(
                    Modifier
                        .fillMaxWidth(count.toFloat() / max)
                        .height(12.dp)
                        .stripedBar(stripe, fill, grow, vertical = false),
                )
            }
        }
    }
}

/** A reason's name and count: the row title's size at the row's second-line weight. */
private val ReasonText = SpoilNotText.RowTitle.copy(fontWeight = FontWeight.SemiBold)
