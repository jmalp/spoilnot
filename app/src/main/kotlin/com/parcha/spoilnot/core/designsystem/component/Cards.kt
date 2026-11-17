package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.LeafShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces

/**
 * The one card a screen wants read first (DESIGN 19.8): Home's Needs attention, Plan's today, the account in Settings.
 * It is `primaryContainer` with 28 dp corners; rows inside it are nested [ListSegment]s.
 */
@Composable
fun HighlightCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpoilNotCorners.Highlight),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * A card that suggests something (DESIGN 19.8), such as Use it up or the streak: `tertiaryContainer` with 24 dp
 * corners, its [content] in a row that starts with a [LeafBadge]. With [onClick] the whole card is the control.
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SpoilNotCorners.Card)
    val color = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val row: @Composable () -> Unit = {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick, modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor) { row() }
    } else {
        Surface(modifier.fillMaxWidth(), shape = shape, color = color, contentColor = contentColor) { row() }
    }
}

/**
 * A card for content that isn't the screen's highlight (DESIGN 19.8), such as a chart or the appearance choices: the
 * `row` color with 24 dp corners, its [content] in a column with 12 dp between the parts.
 */
@Composable
fun PlainCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/**
 * The leaf from the app icon holding an icon, a letter or a number (DESIGN 19.3). The default colors are the accent
 * card's, swapped, so the badge stands out on it.
 */
@Composable
fun LeafBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(size).background(color, LeafShape), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}
