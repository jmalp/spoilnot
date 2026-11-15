package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * A second action beside or under content in the 2026-11 look (DESIGN 19.9), such as I cooked this:
 * `secondaryContainer`, fully round, at least 48 dp tall. The [icon] is decoration; the button is named by its
 * [text].
 *
 * A screen's or sheet's main button stays [PrimaryButton], which takes the look's size and weight with the first
 * screen that is rebuilt (DESIGN 19.12).
 */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = SpoilNotText.ButtonMedium, textAlign = TextAlign.Center)
    }
}
