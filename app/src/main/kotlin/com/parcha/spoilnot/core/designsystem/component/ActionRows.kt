package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/** The size of a row's [IconTile]. */
val IconTileSize = 40.dp

/**
 * A row's leading icon on a tile (DESIGN 19.5), as Settings' rows have it: `secondaryContainer`, 14 dp corners at
 * 40 dp. It is decoration for screen readers; the row's words say what it is.
 */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = IconTileSize) {
    Box(
        modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(percent = TILE_CORNER_PERCENT),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * ICON_SHARE),
        )
    }
}

/**
 * A row of a group that opens a page or does one thing (DESIGN 19.5), such as Settings' Household or Export: the
 * [icon] on a tile, the [title] over its [summary], and a chevron when it opens a page ([opensPage]). The whole row
 * is one button, read with both lines (DESIGN 6.8).
 */
@Composable
fun ActionRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    opensPage: Boolean = false,
    enabled: Boolean = true,
) {
    // Clipped before the click, so the ripple keeps to the row's corners.
    val click = Modifier.clip(
        segmentShape(position),
    ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ListSegment(
        position = position,
        modifier = modifier.then(click),
        minHeight = if (summary == null) ListSegmentMinHeightOneLine else ListSegmentMinHeight,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
    ) {
        IconTile(icon)
        RowLines(title, summary, Modifier.weight(1f))
        if (opensPage) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A row with a switch at its end (DESIGN 19.5), such as Send crash reports: the [icon] on a tile, the [title] over
 * its [summary]. The whole row is the switch, read with both lines and its state.
 */
@Composable
fun SwitchRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val toggle = Modifier
        .clip(segmentShape(position))
        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
    ListSegment(
        position = position,
        modifier = modifier.then(toggle),
        minHeight = if (summary == null) ListSegmentMinHeightOneLine else ListSegmentMinHeight,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
    ) {
        IconTile(icon)
        RowLines(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row's name in [SpoilNotText.RowTitle] over its second line, with room above and below when they wrap. */
@Composable
fun RowLines(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = SpoilNotText.RowTitle)
        if (summary != null) {
            Text(summary, style = SpoilNotText.RowSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 14 dp corners on the 40 dp tile. */
private const val TILE_CORNER_PERCENT = 35

/** A 22 dp icon in the 40 dp tile. */
private const val ICON_SHARE = 22f / 40f
