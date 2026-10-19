package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Font scale from which [Segmented] stacks its options, since side by side they no longer fit (after v1). */
const val SEGMENTED_STACK_FONT_SCALE = 1.5f

/**
 * A single-choice control (DESIGN 6.5): segmented buttons, or a list of radio buttons at large text sizes so long
 * labels are never cut off.
 */
@Composable
fun <T> Segmented(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (LocalDensity.current.fontScale >= SEGMENTED_STACK_FONT_SCALE) {
            Column(Modifier.selectableGroup().testTag("segmented_list")) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(option == selected, role = Role.RadioButton) { onPick(option) },
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("segmented_row")) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onPick(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = { Text(optionLabel(option), maxLines = 1) },
                    )
                }
            }
        }
    }
}
