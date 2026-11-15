package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.STATUS_TINT_ALPHA
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotStatus
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.StatusTone

/** The tile's size in a list row; inside a highlight card it is [StatusTileSizeNested]. */
val StatusTileSize = 44.dp

/** The tile's size in the rows of a highlight card. */
val StatusTileSizeNested = 40.dp

/**
 * The leading tile of a row (DESIGN 19.6): the status [icon] in the fixed status color, on that color at
 * [STATUS_TINT_ALPHA] over the row. A row without a status ([StatusTone.NoDate]) gets a neutral tile.
 *
 * The tile is decoration for screen readers. Status is never the tile alone: the row says it in words beside the
 * name, in the same color (DESIGN 6.4).
 */
@Composable
fun StatusTile(tone: StatusTone, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = StatusTileSize) {
    val color = SpoilNotStatus.colors.forTone(tone)
    val background = if (tone == StatusTone.NoDate) {
        SpoilNotSurfaces.current.tile
    } else {
        color.copy(alpha = STATUS_TINT_ALPHA)
    }
    Box(
        modifier.size(size).background(background, RoundedCornerShape(percent = TILE_CORNER_PERCENT)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * ICON_SHARE))
    }
}

/** 16 dp corners on the 44 dp tile, and the same curve at other sizes. */
private const val TILE_CORNER_PERCENT = 36

/** A 24 dp icon in the 44 dp tile. */
private const val ICON_SHARE = 24f / 44f
