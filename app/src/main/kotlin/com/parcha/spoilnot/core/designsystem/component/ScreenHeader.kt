package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * A screen's header in the 2026-11 look (DESIGN 19.7): a small [eyebrow] line (the date, or how many items) above the
 * [title], with the screen's [actions] at the end, level with the title.
 *
 * At 1.0× text it is 64 dp tall, the height of the compact top app bar it replaces (DECISIONS 2026-10-19), and it
 * grows with the text. [titleContent] replaces the plain title where the title is a control, as on Groceries; it then
 * carries its own heading.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow,
                    style = SpoilNotText.ScreenEyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (titleContent != null) {
                titleContent()
            } else {
                Text(title, style = SpoilNotText.ScreenTitle, modifier = Modifier.semantics { heading() })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
