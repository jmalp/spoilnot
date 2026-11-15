package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** M3 defaults, except cards (medium) at 16 dp per DESIGN 6.4. Bottom sheets use extraLarge (28 dp). */
internal val SpoilNotShapes = Shapes(medium = RoundedCornerShape(16.dp))

/** Corner sizes of the 2026-11 look (DESIGN 19.3). */
object SpoilNotCorners {
    /** The outside corners of a group of rows, and a single row on its own. */
    val GroupOuter = 20.dp

    /** The corners where two rows of a group meet. */
    val GroupInner = 6.dp

    /** The outside corners of rows inside a highlight card. */
    val NestedOuter = 18.dp

    /** Plain and accent cards. */
    val Card = 24.dp

    /** Highlight cards, sheets and dialogs. */
    val Highlight = 28.dp

    /** Text fields and pickers. */
    val Field = 16.dp

    /** Label chips. */
    val Chip = 8.dp

    /** The joined corners of a connected button group. */
    val Connected = 8.dp
}

/**
 * The leaf from the app icon (DESIGN 19.3): two opposite corners fully round and the other two at 15%, at any size.
 * It holds a recipe's letter, a step number, an account's initial and the icon of an accent card.
 */
val LeafShape: Shape =
    RoundedCornerShape(topStartPercent = 50, topEndPercent = 15, bottomEndPercent = 50, bottomStartPercent = 15)
