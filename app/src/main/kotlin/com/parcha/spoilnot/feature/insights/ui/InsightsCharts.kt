package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion

/**
 * Chart colors. Thrown away is never told apart by color alone (DESIGN 6.4): it is dashed in the ring and striped
 * in bars, and every chart has a text legend and a spoken summary.
 */
private object ChartColors {
    val used: Color @Composable get() = MaterialTheme.colorScheme.primary
    val thrown: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val track: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}

/** 0 to 1 over 700 ms when first shown, or at once with reduce motion on. */
@Composable
private fun rememberGrow(key: Any): Float {
    val reduceMotion = rememberReduceMotion()
    val progress = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return progress.value
}

/**
 * The share used this month as a ring: a solid arc for used, a dashed one for thrown away, and the percentage in the
 * middle. Empty months show only the track.
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
) {
    val usedColor = ChartColors.used
    val thrownColor = ChartColors.thrown
    val track = ChartColors.track
    val grow = rememberGrow(used to thrown)
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.12f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = used + thrown
            if (total > 0) {
                val usedSweep = 360f * used / total * grow
                val thrownSweep = 360f * thrown / total * grow
                drawArc(
                    usedColor,
                    -90f,
                    usedSweep,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                if (thrown > 0) {
                    val dash = stroke * 0.6f
                    drawArc(
                        thrownColor,
                        -90f + usedSweep,
                        thrownSweep,
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash / 2))),
                    )
                }
            }
        }
        Text(centerText, style = centerStyle, textAlign = TextAlign.Center)
    }
}

/** One month in [MonthBars]; [description] is what TalkBack reads for it. */
data class MonthBar(val label: String, val used: Int, val thrown: Int, val description: String = label)

/**
 * Used and thrown away per month, side by side: solid bars for used, striped outlined bars for thrown away, each with
 * its number above and the month below. After v1 each month can be tapped ([onSelect]) to show its details; the
 * selected one is marked by a tinted background and a bold label, and TalkBack reads each month on its own.
 */
@Composable
fun MonthBars(
    bars: List<MonthBar>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
) {
    val usedColor = ChartColors.used
    val thrownColor = ChartColors.thrown
    val grow = rememberGrow(bars)
    val max = (bars.maxOfOrNull { maxOf(it.used, it.thrown) } ?: 0).coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == selected
            Column(
                Modifier
                    .testTag("month_bar_$index")
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .selectable(isSelected, role = Role.Tab) { onSelect(index) }
                    .clearAndSetSemantics {
                        contentDescription = bar.description
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(bar.used, max, height, grow, usedColor, striped = false)
                    Bar(bar.thrown, max, height, grow, thrownColor, striped = true)
                }
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else null,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Bar(value: Int, max: Int, chartHeight: Dp, grow: Float, color: Color, striped: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val barHeight = chartHeight * (value.toFloat() / max) * grow
        Canvas(Modifier.width(14.dp).height(barHeight.coerceAtLeast(2.dp))) {
            if (striped) stripedRect(color) else drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

/** An outlined box with diagonal stripes, the "thrown away" pattern. */
private fun DrawScope.stripedRect(color: Color, size: Size = this.size) {
    val line = 2.dp.toPx()
    val gap = 5.dp.toPx()
    clipRect(0f, 0f, size.width, size.height) {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = line)
            x += gap
        }
    }
    drawRoundRect(color, size = size, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(line))
}

/** A small swatch and its label, for chart legends. */
@Composable
fun LegendItem(text: String, thrown: Boolean, modifier: Modifier = Modifier) {
    val color = if (thrown) ChartColors.thrown else ChartColors.used
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) {
            if (thrown) stripedRect(color) else drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx()))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Horizontal striped bars for why things were thrown away, longest first, each labeled with its count. */
@Composable
fun ReasonBars(reasons: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val color = ChartColors.thrown
    val grow = rememberGrow(reasons)
    val max = (reasons.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEach { (label, count) ->
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp)) {
                    val width = size.width * count / max * grow
                    stripedRect(color, Size(width.coerceAtLeast(2.dp.toPx()), size.height))
                }
            }
        }
    }
}
