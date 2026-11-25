package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/** What a [LabelChip] says about its row, which decides its colors. */
enum class LabelChipKind {
    /** Where something came from: Added automatically, Meal plan, Recipe. */
    Source,

    /** Who: a meal's cook. */
    Person,
}

/**
 * A small label on a row (DESIGN 19.10): an optional 14 dp [icon] and [text] on a tonal background with 8 dp
 * corners. It is a label, not a control, and never the only carrier of a status.
 */
@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: LabelChipKind = LabelChipKind.Source,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (kind == LabelChipKind.Source) colors.tertiaryContainer else colors.secondaryContainer
    val content = if (kind == LabelChipKind.Source) colors.onTertiaryContainer else colors.onSecondaryContainer
    Row(
        modifier
            .heightIn(min = 24.dp)
            .background(background, RoundedCornerShape(SpoilNotCorners.Chip))
            .padding(start = if (icon != null) 6.dp else 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = SpoilNotText.ChipLabel, color = content)
    }
}
