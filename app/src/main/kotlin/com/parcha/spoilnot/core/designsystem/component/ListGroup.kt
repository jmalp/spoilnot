package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/** Where a row sits in its group, which decides its corners (DESIGN 19.5). */
enum class SegmentPosition { Only, First, Middle, Last }

/** The position of row [index] in a group of [count] rows. */
fun segmentPosition(index: Int, count: Int): SegmentPosition {
    require(count > 0) { "A group has at least one row" }
    require(index in 0 until count) { "Row $index is outside a group of $count" }
    return when {
        count == 1 -> SegmentPosition.Only
        index == 0 -> SegmentPosition.First
        index == count - 1 -> SegmentPosition.Last
        else -> SegmentPosition.Middle
    }
}

/**
 * A row's shape: [outer] corners on the group's outside, [inner] corners where it meets the next row. A group then
 * reads as one rounded block cut by thin gaps, without dividers.
 */
fun segmentShape(
    position: SegmentPosition,
    outer: Dp = SpoilNotCorners.GroupOuter,
    inner: Dp = SpoilNotCorners.GroupInner,
): Shape = when (position) {
    SegmentPosition.Only -> RoundedCornerShape(outer)

    SegmentPosition.First -> RoundedCornerShape(
        topStart = outer,
        topEnd = outer,
        bottomEnd = inner,
        bottomStart = inner,
    )

    SegmentPosition.Middle -> RoundedCornerShape(inner)

    SegmentPosition.Last -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer)
}

/** The gap between two rows of a group; lists use it as their item spacing inside a group. */
val ListGroupGap = 2.dp

/** The least height of a row with two lines; one-line rows use [ListSegmentMinHeightOneLine]. */
val ListSegmentMinHeight = 68.dp

/** The least height of a row with one line. */
val ListSegmentMinHeightOneLine = 56.dp

/**
 * One row of a group (DESIGN 19.5): the row surface in the shape for its [position], at least [minHeight] tall, its
 * [content] in a row with 12 dp between the parts.
 *
 * With [onClick] the whole row is the control and the ripple keeps to the row's shape; the caller gives it its
 * sentence for screen readers (DESIGN 6.8). Inside a highlight card pass the nested row color and
 * [SpoilNotCorners.NestedOuter].
 */
@Composable
fun ListSegment(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = SpoilNotSurfaces.current.row,
    outerCorner: Dp = SpoilNotCorners.GroupOuter,
    minHeight: Dp = ListSegmentMinHeight,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = segmentShape(position, outer = outerCorner)
    val row: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = minHeight).padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    if (onClick != null) {
        Surface(onClick, modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor) { row() }
    } else {
        Surface(modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor) { row() }
    }
}

/** The heading above a group of rows, a heading for screen readers too (DESIGN 6.8, 19.5). Sentence case. */
@Composable
fun GroupHeading(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        style = SpoilNotText.GroupHeading,
        color = color,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp).semantics { heading() },
    )
}
