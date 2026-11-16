package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText

/**
 * A single-choice row of joined buttons (DESIGN 19.9): equal widths, 2 dp apart, the outside corners fully round and
 * the joined ones 8 dp; the chosen button is fully round and filled. [selected] may be null when nothing is chosen
 * yet, and [onPick] is also called for the chosen button, so a caller can clear the choice.
 *
 * From 1.5× text ([isLargeText]) it is a list of radio buttons, like [Segmented], so labels are never cut off. Each
 * button is a radio button in a group for screen readers either way (DESIGN 6.8). [optionTag] tags each button for
 * tests.
 */
@Composable
fun <T> ConnectedButtonGroup(
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: ((T) -> String)? = null,
) {
    if (isLargeText()) {
        Column(modifier.selectableGroup().testTag("connected_list")) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(option == selected, role = Role.RadioButton) { onPick(option) }
                        .then(optionTag?.let { Modifier.testTag(it(option)) } ?: Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(
                        optionLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth().selectableGroup().testTag("connected_row"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, option ->
                val chosen = option == selected
                val shape = connectedShape(index, options.size, chosen)
                Surface(
                    // Clipped first, so the ripple keeps to the button's shape.
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .selectable(chosen, role = Role.RadioButton) { onPick(option) }
                        .then(optionTag?.let { Modifier.testTag(it(option)) } ?: Modifier),
                    shape = shape,
                    color = if (chosen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (chosen) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        Text(
                            optionLabel(option),
                            style = SpoilNotText.ButtonMedium,
                            fontWeight = if (chosen) FontWeight.ExtraBold else FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            // Two lines before 1.5x text turns the group into a list, on narrow phones.
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/** Fully round on the group's outside and when chosen; 8 dp where two buttons join. */
private fun connectedShape(index: Int, count: Int, chosen: Boolean): Shape {
    val round = CornerSize(percent = 50)
    val joined = CornerSize(SpoilNotCorners.Connected)
    return when {
        chosen || count == 1 -> CircleShape

        index == 0 -> RoundedCornerShape(topStart = round, topEnd = joined, bottomEnd = joined, bottomStart = round)

        index == count - 1 ->
            RoundedCornerShape(topStart = joined, topEnd = round, bottomEnd = round, bottomStart = joined)

        else -> RoundedCornerShape(joined)
    }
}
